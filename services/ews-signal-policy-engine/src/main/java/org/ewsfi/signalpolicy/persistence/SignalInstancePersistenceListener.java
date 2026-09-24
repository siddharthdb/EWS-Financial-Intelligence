package org.ewsfi.signalpolicy.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
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
 *
 * <p>Deliberately does not catch exceptions itself: letting them propagate lets
 * {@link org.ewsfi.signalpolicy.config.KafkaListenerErrorHandlingConfig}'s container-level error
 * handler retry a transient failure instead of silently dropping the signal on the first attempt
 * -- see that class's Javadoc for the bug this fixed.
 */
@Component
public class SignalInstancePersistenceListener {

    private final SignalInstanceRepository signalInstanceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalInstancePersistenceListener(SignalInstanceRepository signalInstanceRepository) {
        this.signalInstanceRepository = signalInstanceRepository;
    }

    @KafkaListener(topics = "ews.derived.signal", groupId = "ews-signal-policy-engine-persistence")
    public void onSignalDetected(String payload) throws Exception {
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
    }
}
