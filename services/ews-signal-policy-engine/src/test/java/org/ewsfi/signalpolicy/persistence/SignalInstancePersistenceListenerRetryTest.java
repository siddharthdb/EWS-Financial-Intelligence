package org.ewsfi.signalpolicy.persistence;

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
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
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
 * Proves the retry mechanism {@link org.ewsfi.signalpolicy.config.KafkaListenerErrorHandlingConfig}
 * adds genuinely retries and recovers from a transient failure, mirroring
 * {@code ews-feature-processor}'s {@code FeatureValuePersistenceListenerRetryTest}. Injects a
 * repository that fails {@code save()} exactly twice for a specific signal before delegating to
 * the real (real-Postgres-backed) repository.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"ews.derived.feature", "ews.derived.signal"})
@DirtiesContext
class SignalInstancePersistenceListenerRetryTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    @Qualifier("signalInstanceRepository")
    private SignalInstanceRepository signalInstanceRepository;

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
        String signalId = UUID.randomUUID().toString();
        String featureValueId = "fv-" + UUID.randomUUID();
        String json =
                """
                {
                  "signalId": "%s",
                  "signalType": "REPEATED_PAYMENT_RETURN",
                  "semanticScope": "GLOBAL_CORE",
                  "entityType": "ACCOUNT",
                  "entityId": "acct-signal-retry-test",
                  "status": "PROPOSED",
                  "severity": "MEDIUM",
                  "confidenceValue": 0.8,
                  "materialityBand": "MEDIUM",
                  "detectedAt": "2026-09-24T00:00:00Z",
                  "effectiveAt": "2026-09-24T00:00:00Z",
                  "knowledgeTime": "2026-09-24T00:00:00Z",
                  "policyId": "POL-PAYMENT-RETURN-CORP-001",
                  "policyVersion": "2.0",
                  "dataQualityState": "COMPLETE",
                  "evidenceIds": ["%s"]
                }
                """
                        .formatted(signalId, featureValueId);

        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer
                    .send(new ProducerRecord<>("ews.derived.signal", signalId, json))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        Optional<SignalInstance> found = Optional.empty();
        for (int attempt = 0; attempt < 20 && found.isEmpty(); attempt++) {
            Thread.sleep(Duration.ofMillis(500));
            found = signalInstanceRepository.findById(signalId);
        }

        assertThat(found)
                .as(
                        "the signal must eventually be persisted once the container's"
                                + " retry-with-backoff gets past the two simulated transient failures")
                .isPresent();
        assertThat(found.get().getEntityId()).isEqualTo("acct-signal-retry-test");
    }

    @TestConfiguration
    static class FailTwiceThenSucceedConfig {

        @Bean
        @Primary
        SignalInstanceRepository failingTwiceSignalInstanceRepository(
                @Qualifier("signalInstanceRepository") SignalInstanceRepository real) {
            SignalInstanceRepository wrapper = Mockito.mock(SignalInstanceRepository.class);
            AtomicInteger remainingFailures = new AtomicInteger(2);
            Mockito.doAnswer(invocation -> real.existsById(invocation.getArgument(0)))
                    .when(wrapper)
                    .existsById(Mockito.any());
            Mockito.doAnswer(
                            invocation -> {
                                if (remainingFailures.getAndDecrement() > 0) {
                                    throw new DataAccessResourceFailureException(
                                            "simulated transient Postgres failure");
                                }
                                return real.save(invocation.getArgument(0));
                            })
                    .when(wrapper)
                    .save(Mockito.any(SignalInstance.class));
            return wrapper;
        }
    }
}
