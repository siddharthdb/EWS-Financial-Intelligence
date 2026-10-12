package org.ewsfi.featureprocessor.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.persistence.feature.FeatureValue;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Proves {@link FeatureValuePersistenceListener} actually persists a {@code feature.value.updated}
 * message consumed from Kafka into the real {@code feature_value} table, using an embedded broker
 * (in-process, no Docker needed) and the real local Postgres {@code ews} database.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
            "ews.canonical.account-transaction",
            "ews.canonical.repayment",
            "ews.canonical.facility",
            "ews.derived.feature"
        })
@DirtiesContext
class FeatureValuePersistenceListenerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
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
    void persistsAFeatureValuePublishedToTheDerivedFeatureTopic() throws Exception {
        String featureValueId = UUID.randomUUID().toString();
        String json =
                """
                {
                  "featureValueId": "%s",
                  "definitionId": "FD-RETURNED-PAYMENT-COUNT-30D-001",
                  "featureName": "returned_payment_count_30d",
                  "definitionVersion": "1.0",
                  "entityType": "ACCOUNT",
                  "entityId": "acct-listener-test",
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

        AvroPayloadCodec.Encoded encoded = AvroPayloadCodec.forTests().encode("feature.value.updated", json);
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(producerProps)) {
            ProducerRecord<String, byte[]> record =
                    new ProducerRecord<>("ews.derived.feature", "acct-listener-test", encoded.payload());
            encoded.headers().forEach(header -> record.headers().add(header.key(), header.value()));
            producer.send(record).get(10, java.util.concurrent.TimeUnit.SECONDS);
        }

        Optional<FeatureValue> found = Optional.empty();
        for (int attempt = 0; attempt < 20 && found.isEmpty(); attempt++) {
            Thread.sleep(Duration.ofMillis(500));
            found = featureValueRepository.findById(featureValueId);
        }

        assertThat(found).isPresent();
        assertThat(found.get().getFeatureName()).isEqualTo("returned_payment_count_30d");
        assertThat(found.get().getEntityId()).isEqualTo("acct-listener-test");
        assertThat(found.get().getValueNumeric()).isEqualByComparingTo("3");

        List<FeatureValue> byAccount =
                featureValueRepository
                        .findByEntityTypeAndEntityIdAndFeatureNameOrderByKnowledgeTimeDesc(
                                "ACCOUNT", "acct-listener-test", "returned_payment_count_30d");
        assertThat(byAccount).isNotEmpty();
    }
}
