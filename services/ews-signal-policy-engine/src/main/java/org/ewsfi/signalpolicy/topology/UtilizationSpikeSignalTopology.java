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
import org.ewsfi.signalpolicy.policy.UtilizationSpikeSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P06 UTILIZATION_SPIKE policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link UtilizationSpikeSignalPolicyLoader})
 * against {@code wc_utilization_delta_30d} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.3.
 *
 * <p>Stateless per-value threshold evaluation, mirroring {@link UtilizationSignalTopology} exactly
 * -- since the upstream feature ({@code wc_utilization_delta_30d}) already encodes the
 * statistical-baseline comparison, this topology itself needs no additional state, unlike
 * {@link DpdSignalTopology}/{@link DpdWorseningSignalTopology}, which compare consecutive raw
 * values and so must track state themselves.
 */
@Component
public class UtilizationSpikeSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "wc_utilization_delta_30d";

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
        double delta = Double.parseDouble(featureValue.getValueNumeric());
        return UtilizationSpikeSignalPolicyLoader.evaluate(delta);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        UtilizationSpikeSignalPolicyLoader.SIGNAL_TYPE,
                        UtilizationSpikeSignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "MEDIUM",
                        0.7,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        UtilizationSpikeSignalPolicyLoader.POLICY_ID,
                        UtilizationSpikeSignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
