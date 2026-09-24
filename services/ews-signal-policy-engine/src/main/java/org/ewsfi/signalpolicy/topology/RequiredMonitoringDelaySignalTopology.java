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
import org.ewsfi.signalpolicy.policy.RequiredMonitoringDelaySignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P18 REQUIRED_MONITORING_INFORMATION_DELAY
 * policy (docs/architecture/02a-priority-signal-contracts.md,
 * {@link RequiredMonitoringDelaySignalPolicyLoader}) against
 * {@code financial_statement_filing_delay_days} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8. Stateless
 * per-value threshold evaluation, mirroring {@link SignalPolicyTopology} and
 * {@link UtilizationSignalTopology} exactly.
 */
@Component
public class RequiredMonitoringDelaySignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "financial_statement_filing_delay_days";

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
        // Roadmap 3.6: same defensive filtering as SignalPolicyTopology/UtilizationSignalTopology.
        int delayDays;
        try {
            delayDays = Integer.parseInt(featureValue.getValueNumeric());
        } catch (NumberFormatException e) {
            return false;
        }
        return RequiredMonitoringDelaySignalPolicyLoader.evaluate(delayDays);
    }

    private String toSignalDetectedJson(JsonFeatureValue featureValue) {
        Instant now = Instant.now();
        String signalId =
                UUID.nameUUIDFromBytes(featureValue.getFeatureValueId().getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        RequiredMonitoringDelaySignalPolicyLoader.SIGNAL_TYPE,
                        RequiredMonitoringDelaySignalPolicyLoader.SEMANTIC_SCOPE,
                        featureValue.getEntityType(),
                        featureValue.getEntityId(),
                        "PROPOSED",
                        "MEDIUM",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        featureValue.getKnowledgeTime(),
                        RequiredMonitoringDelaySignalPolicyLoader.POLICY_ID,
                        RequiredMonitoringDelaySignalPolicyLoader.POLICY_VERSION,
                        featureValue.getQualityState() != null ? featureValue.getQualityState() : "COMPLETE",
                        List.of(featureValue.getFeatureValueId()));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }
}
