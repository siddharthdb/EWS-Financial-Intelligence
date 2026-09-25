package org.ewsfi.signalpolicy.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.ewsfi.contracts.interim.JsonSignalDetected;
import org.ewsfi.signalpolicy.policy.CurrentRatioDeteriorationSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P11 CURRENT_RATIO_DERIORATION policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link CurrentRatioDeteriorationSignalPolicyLoader})
 * against {@code current_ratio} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8.
 *
 * <p>Mirrors {@link LeverageDeteriorationSignalTopology}'s shape exactly (a stateful
 * {@code groupByKey().aggregate()} tracking {@code {previousRatio, currentRatio, ...}} per
 * counterparty), evaluating a materially *decreasing* ratio rather than an increasing one -- see
 * {@link CurrentRatioDeteriorationSignalPolicyLoader}.
 */
@Component
public class CurrentRatioDeteriorationSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "current_ratio";
    private static final String STORE_NAME = "current-ratio-deterioration-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> currentRatioFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                currentRatioFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new CurrentRatioState(null, null, null, null, null, null)),
                                (counterpartyId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    CurrentRatioState previous = parseState(aggJson);
                                    // Defensive filtering (see LeverageDeteriorationSignalTopology /
                                    // MaxDpdFeatureTopology): a malformed valueNumeric must leave the
                                    // transition state unchanged, not throw from inside .aggregate().
                                    Double currentRatio = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentRatio == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new CurrentRatioState(
                                                    previous.currentRatio,
                                                    currentRatio,
                                                    featureValue.getFeatureValueId(),
                                                    featureValue.getEntityType(),
                                                    featureValue.getEntityId(),
                                                    featureValue.getKnowledgeTime()));
                                },
                                Materialized.<String, String, KeyValueStore<org.apache.kafka.common.utils.Bytes, byte[]>>as(
                                                STORE_NAME)
                                        .withKeySerde(Serdes.String())
                                        .withValueSerde(Serdes.String()));

        KStream<String, String> signals =
                transitions
                        .toStream()
                        .mapValues(this::toSignalIfDeteriorated)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfDeteriorated(String stateJson) {
        CurrentRatioState state = parseState(stateJson);
        if (state.previousRatio == null || state.currentRatio == null) {
            return null;
        }
        boolean deteriorated =
                CurrentRatioDeteriorationSignalPolicyLoader.evaluate(state.previousRatio, state.currentRatio);
        if (!deteriorated) {
            return null;
        }
        return toSignalDetectedJson(state);
    }

    private JsonFeatureValue tryParse(String json) {
        try {
            return objectMapper.readValue(json, JsonFeatureValue.class);
        } catch (Exception e) {
            return null;
        }
    }

    private Double tryParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String toStateJson(CurrentRatioState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize CurrentRatioState", e);
        }
    }

    private CurrentRatioState parseState(String json) {
        try {
            return objectMapper.readValue(json, CurrentRatioState.class);
        } catch (Exception e) {
            return new CurrentRatioState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(CurrentRatioState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        CurrentRatioDeteriorationSignalPolicyLoader.SIGNAL_TYPE,
                        CurrentRatioDeteriorationSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "MEDIUM",
                        0.75,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        CurrentRatioDeteriorationSignalPolicyLoader.POLICY_ID,
                        CurrentRatioDeteriorationSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-counterparty current_ratio transition state, tracked across observations. */
    public static class CurrentRatioState {
        public Double previousRatio;
        public Double currentRatio;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public CurrentRatioState() {
            // Jackson
        }

        public CurrentRatioState(
                Double previousRatio,
                Double currentRatio,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousRatio = previousRatio;
            this.currentRatio = currentRatio;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
