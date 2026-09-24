package org.ewsfi.signalpolicy.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.SignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P03 REPEATED_PAYMENT_RETURN policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link SignalPolicyLoader}) against
 * {@code returned_payment_count_30d} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}
 * (docs/architecture/03c-topic-and-partition-strategy.md Section 2), per the "signal policy engine"
 * role named in ADR-004.
 *
 * <p>Emits a signal on every window where the threshold is met, not only on the first crossing --
 * P02's guidance ("Do not emit unchanged daily duplicates") applies to DPD_WORSENING, a different
 * signal not yet implemented; deduplication/episode-tracking for repeated
 * REPEATED_PAYMENT_RETURN detections is a follow-up, not implemented in this slice. The signal
 * {@code signalId} is deterministically derived from the triggering {@code featureValueId}, so
 * re-processing the same feature value (Kafka at-least-once delivery) produces the same signal row
 * rather than a duplicate -- see {@link org.ewsfi.signalpolicy.persistence.SignalInstancePersistenceListener}.
 */
@Component
public class SignalPolicyTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "returned_payment_count_30d";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> signals =
                features
                        .mapValues(this::tryParse)
                        .filter((key, featureValue) -> matchesPolicy(featureValue))
                        .mapValues(this::toSignalDetectedJson);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private JsonFeatureValue tryParse(String json) {
        try {
            return objectMapper.readValue(json, JsonFeatureValue.class);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesPolicy(JsonFeatureValue featureValue) {
        if (featureValue == null || !TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())) {
            return false;
        }
        if (featureValue.getValueNumeric() == null) {
            return false;
        }
        long count = Long.parseLong(featureValue.getValueNumeric());
        return SignalPolicyLoader.evaluate(count);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        SignalPolicyLoader.SIGNAL_TYPE,
                        SignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        SignalPolicyLoader.POLICY_ID,
                        SignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
