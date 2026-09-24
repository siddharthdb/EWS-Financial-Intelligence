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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Proves {@link SignalInstancePersistenceListener} persists a {@code signal.detected} message into
 * the real {@code signal_instance} table (with its evidence child table), using an embedded broker
 * and the real local Postgres {@code ews} database. Also proves the listener's idempotent
 * re-delivery handling doesn't overwrite a signal whose status has already moved on.
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"ews.derived.feature", "ews.derived.signal"})
@DirtiesContext
class SignalInstancePersistenceListenerTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
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
    void persistsASignalDetectedMessageWithItsEvidence() throws Exception {
        String signalId = UUID.randomUUID().toString();
        String featureValueId = "fv-" + UUID.randomUUID();
        String json =
                """
                {
                  "signalId": "%s",
                  "signalType": "REPEATED_PAYMENT_RETURN",
                  "semanticScope": "GLOBAL_CORE",
                  "entityType": "ACCOUNT",
                  "entityId": "acct-signal-listener-test",
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

        publish(json, signalId);

        Optional<SignalInstance> found = pollForSignal(signalId);

        assertThat(found).isPresent();
        assertThat(found.get().getSignalType()).isEqualTo("REPEATED_PAYMENT_RETURN");
        assertThat(found.get().getStatus()).isEqualTo("PROPOSED");
        assertThat(found.get().getEvidenceIds()).containsExactly(featureValueId);
    }

    @Test
    void reDeliveryDoesNotOverwriteAnAlreadyAcceptedSignal() throws Exception {
        String signalId = UUID.randomUUID().toString();
        String featureValueId = "fv-" + UUID.randomUUID();
        String json =
                """
                {
                  "signalId": "%s",
                  "signalType": "REPEATED_PAYMENT_RETURN",
                  "semanticScope": "GLOBAL_CORE",
                  "entityType": "ACCOUNT",
                  "entityId": "acct-redelivery-test",
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

        publish(json, signalId);
        Optional<SignalInstance> firstDelivery = pollForSignal(signalId);
        assertThat(firstDelivery).isPresent();

        // Simulate an analyst having already accepted the signal before the duplicate re-delivery
        // arrives (Kafka at-least-once guarantee).
        SignalInstance accepted = firstDelivery.get();
        accepted.setStatus("ACCEPTED");
        signalInstanceRepository.save(accepted);

        publish(json, signalId);
        Thread.sleep(Duration.ofSeconds(2)); // let the (no-op) re-delivery be processed

        SignalInstance reloaded = signalInstanceRepository.findById(signalId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo("ACCEPTED");
    }

    private void publish(String json, String key) throws Exception {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            producer
                    .send(new ProducerRecord<>("ews.derived.signal", key, json))
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private Optional<SignalInstance> pollForSignal(String signalId) throws InterruptedException {
        Optional<SignalInstance> found = Optional.empty();
        for (int attempt = 0; attempt < 20 && found.isEmpty(); attempt++) {
            Thread.sleep(Duration.ofMillis(500));
            found = signalInstanceRepository.findById(signalId);
        }
        return found;
    }
}
