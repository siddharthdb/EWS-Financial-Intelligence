package org.ewsfi.featureprocessor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Proves the retry mechanism {@link org.ewsfi.featureprocessor.config.KafkaListenerErrorHandlingConfig}
 * adds genuinely retries and recovers from a transient failure, rather than silently dropping the
 * message on the first attempt (the bug that class's Javadoc documents). Injects a repository that
 * fails {@code save()} exactly twice for a specific feature value before delegating to the real
 * (real-Postgres-backed) repository -- proving the container-level error handler re-invokes the
 * listener with the same record until it succeeds, using genuine Kafka redelivery-at-the-container
 * level and a genuine Postgres write for the eventual success, not a fully mocked path.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"ews.canonical.account-transaction", "ews.derived.feature"})
@DirtiesContext
class FeatureValuePersistenceListenerRetryTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    /**
     * Deliberately qualified to the original autoconfigured bean, not the {@code @Primary}
     * failure-injecting one below -- verification reads through the real repository so this test
     * checks actual Postgres state, not the spy's internal bookkeeping.
     */
    @Autowired
    @Qualifier("featureValueRepository")
    private FeatureValueRepository featureValueRepository;

    @BeforeAll
    static void requirePostgres() {
        boolean reachable;
        try (Connection ignored = DriverManager.getConnection(JDBC_URL, "ews", "ews")) {
            reachable = true;
        } catch (SQLException e) {
            reachable = false;
        }
        assumeTrue(reachable, "Postgres not reachable at " + JDBC_URL + "; skipping integration test");
    }

    @Test
    void recoversAfterTwoTransientFailuresAndPersistsOnTheThirdAttempt() throws Exception {
        String featureValueId = UUID.randomUUID().toString();
        String json =
                """
                {
                  "featureValueId": "%s",
                  "definitionId": "FD-RETURNED-PAYMENT-COUNT-30D-001",
                  "featureName": "returned_payment_count_30d",
                  "definitionVersion": "1.0",
                  "entityType": "ACCOUNT",
                  "entityId": "acct-retry-test",
                  "state": "VALUE",
                  "valueType": "INTEGER",
                  "valueNumeric": "3",
                  "knowledgeTime": "2026-09-24T00:00:00Z",
                  "calculatedAt": "2026-09-24T00:00:00Z",
                  "windowStart": "2026-08-25T00:00:00Z",
                  "windowEnd": "2026-09-24T00:00:00Z",
                  "qualityState": "COMPLETE",
                  "lineageTransformationVersion": "1.0"
                }
                """
                        .formatted(featureValueId);

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer
                    .send(new ProducerRecord<>("ews.derived.feature", "acct-retry-test", json))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        Optional<FeatureValue> found = Optional.empty();
        for (int attempt = 0; attempt < 20 && found.isEmpty(); attempt++) {
            Thread.sleep(Duration.ofMillis(500));
            found = featureValueRepository.findById(featureValueId);
        }

        assertThat(found)
                .as(
                        "the row must eventually be persisted once the container's retry-with-backoff"
                                + " gets past the two simulated transient failures")
                .isPresent();
        assertThat(found.get().getEntityId()).isEqualTo("acct-retry-test");
    }

    @TestConfiguration
    static class FailTwiceThenSucceedConfig {

        @Bean
        @Primary
        FeatureValueRepository failingTwiceFeatureValueRepository(
                @Qualifier("featureValueRepository") FeatureValueRepository real) {
            FeatureValueRepository wrapper = Mockito.mock(FeatureValueRepository.class);
            AtomicInteger remainingFailures = new AtomicInteger(2);
            Mockito.doAnswer(
                            invocation -> {
                                if (remainingFailures.getAndDecrement() > 0) {
                                    throw new DataAccessResourceFailureException(
                                            "simulated transient Postgres failure");
                                }
                                return real.save(invocation.getArgument(0));
                            })
                    .when(wrapper)
                    .save(Mockito.any(FeatureValue.class));
            return wrapper;
        }
    }
}
