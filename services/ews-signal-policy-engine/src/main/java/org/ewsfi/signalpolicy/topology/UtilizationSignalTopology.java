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
import org.ewsfi.signalpolicy.policy.UtilizationSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P05 UTILIZATION_HIGH policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link UtilizationSignalPolicyLoader})
 * against {@code wc_utilization_ratio} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 1.14. Stateless
 * per-value threshold evaluation, mirroring {@link SignalPolicyTopology} exactly (unlike
 * {@link DpdSignalTopology}, this signal needs no transition/previous-value state).
 */
@Component
public class UtilizationSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "wc_utilization_ratio";

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
        // Roadmap 3.6: filter out a malformed valueNumeric here rather than letting parseDouble
        // throw from inside a stateless .filter() -- see SignalPolicyTopology for why an uncaught
        // exception here would crash-loop the stream thread on the same record indefinitely.
        double ratio;
        try {
            ratio = Double.parseDouble(featureValue.getValueNumeric());
        } catch (NumberFormatException e) {
            return false;
        }
        return UtilizationSignalPolicyLoader.evaluate(ratio);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        UtilizationSignalPolicyLoader.SIGNAL_TYPE,
                        UtilizationSignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        UtilizationSignalPolicyLoader.POLICY_ID,
                        UtilizationSignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
