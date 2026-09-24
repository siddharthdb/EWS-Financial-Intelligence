package org.ewsfi.signalpolicy.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Persists each {@code signal.detected} event the Kafka Streams topology emits to
 * {@code ews.derived.signal} as a {@code signal_instance} row, via a plain {@code @KafkaListener}
 * (spring-kafka), mirroring the split between Streams computation and database persistence
 * established by {@code ews-feature-processor}'s {@code FeatureValuePersistenceListener}.
 *
 * <p>Uses {@code save()} (an upsert on the deterministic {@code signalId} primary key from
 * {@link org.ewsfi.signalpolicy.topology.SignalPolicyTopology}), so re-delivery of the same
 * {@code signal.detected} message under Kafka's at-least-once guarantee is idempotent rather than
 * creating a duplicate {@code PROPOSED} signal.
 */
@Component
public class SignalInstancePersistenceListener {

    private static final Logger log = LoggerFactory.getLogger(SignalInstancePersistenceListener.class);

    private final SignalInstanceRepository signalInstanceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalInstancePersistenceListener(SignalInstanceRepository signalInstanceRepository) {
        this.signalInstanceRepository = signalInstanceRepository;
    }

    @KafkaListener(topics = "ews.derived.signal", groupId = "ews-signal-policy-engine-persistence")
    public void onSignalDetected(String payload) {
        try {
            JsonSignalDetected json = objectMapper.readValue(payload, JsonSignalDetected.class);
            if (signalInstanceRepository.existsById(json.getSignalId())) {
                // Idempotent re-delivery: the row already exists from an earlier processing of the
                // same feature value, and signal status may have moved on since (e.g. an analyst
                // already accepted/rejected it) -- do not overwrite it.
                return;
            }
            SignalInstance entity =
                    new SignalInstance(
                            json.getSignalId(),
                            json.getSignalType(),
                            json.getSemanticScope(),
                            json.getEntityType(),
                            json.getEntityId(),
                            json.getStatus(),
                            json.getSeverity(),
                            json.getConfidenceValue(),
                            json.getMaterialityBand(),
                            OffsetDateTime.parse(json.getDetectedAt()),
                            OffsetDateTime.parse(json.getEffectiveAt()),
                            OffsetDateTime.parse(json.getKnowledgeTime()),
                            json.getPolicyId(),
                            json.getPolicyVersion(),
                            json.getDataQualityState(),
                            new LinkedHashSet<>(json.getEvidenceIds()));
            signalInstanceRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist signal instance from payload {}: {}", payload, e.getMessage());
        }
    }
}
