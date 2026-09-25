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
import org.ewsfi.signalpolicy.policy.OperatingCashFlowNegativeSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P14 OPERATING_CASH_FLOW_NEGATIVE policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link OperatingCashFlowNegativeSignalPolicyLoader})
 * against {@code operating_cash_flow} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8. Stateless
 * per-value threshold evaluation, mirroring {@link SignalPolicyTopology}/
 * {@link UtilizationSignalTopology} exactly.
 */
@Component
public class OperatingCashFlowNegativeSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "operating_cash_flow";

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
        // throw from inside a stateless .filter() -- see SignalPolicyTopology for why.
        double operatingCashFlow;
        try {
            operatingCashFlow = Double.parseDouble(featureValue.getValueNumeric());
        } catch (NumberFormatException e) {
            return false;
        }
        return OperatingCashFlowNegativeSignalPolicyLoader.evaluate(operatingCashFlow);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        OperatingCashFlowNegativeSignalPolicyLoader.SIGNAL_TYPE,
                        OperatingCashFlowNegativeSignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "HIGH",
                        0.75,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        OperatingCashFlowNegativeSignalPolicyLoader.POLICY_ID,
                        OperatingCashFlowNegativeSignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
