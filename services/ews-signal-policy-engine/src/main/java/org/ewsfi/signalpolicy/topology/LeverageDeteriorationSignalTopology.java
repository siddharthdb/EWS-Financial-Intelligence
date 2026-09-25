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
import org.ewsfi.signalpolicy.policy.LeverageDeteriorationSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P12 LEVERAGE_DERIORATION policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link LeverageDeteriorationSignalPolicyLoader})
 * against {@code total_liabilities_to_equity} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8.
 *
 * <p>Mirrors {@link DpdWorseningSignalTopology}'s shape exactly (a stateful
 * {@code groupByKey().aggregate()} tracking {@code {previousRatio, currentRatio, ...}} per
 * counterparty, since detecting a material increase needs the previous observation, which a single
 * feature-value message doesn't carry) but with a relative (percentage), not absolute, threshold --
 * see {@link LeverageDeteriorationSignalPolicyLoader} for why.
 */
@Component
public class LeverageDeteriorationSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "total_liabilities_to_equity";
    private static final String STORE_NAME = "leverage-deterioration-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> leverageFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                leverageFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new LeverageState(null, null, null, null, null, null)),
                                (counterpartyId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    LeverageState previous = parseState(aggJson);
                                    // Roadmap 3.6-style defensive filtering: a malformed valueNumeric
                                    // must leave the transition state unchanged, not throw from
                                    // inside .aggregate() -- see DpdWorseningSignalTopology/
                                    // MaxDpdFeatureTopology for why an uncaught exception here would
                                    // crash-loop the stream thread on the same record forever.
                                    Double currentRatio = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentRatio == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new LeverageState(
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
        LeverageState state = parseState(stateJson);
        if (state.previousRatio == null || state.currentRatio == null) {
            return null;
        }
        boolean deteriorated =
                LeverageDeteriorationSignalPolicyLoader.evaluate(state.previousRatio, state.currentRatio);
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

    private String toStateJson(LeverageState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize LeverageState", e);
        }
    }

    private LeverageState parseState(String json) {
        try {
            return objectMapper.readValue(json, LeverageState.class);
        } catch (Exception e) {
            return new LeverageState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(LeverageState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        LeverageDeteriorationSignalPolicyLoader.SIGNAL_TYPE,
                        LeverageDeteriorationSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.75,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        LeverageDeteriorationSignalPolicyLoader.POLICY_ID,
                        LeverageDeteriorationSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-counterparty total_liabilities_to_equity transition state, tracked across observations. */
    public static class LeverageState {
        public Double previousRatio;
        public Double currentRatio;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public LeverageState() {
            // Jackson
        }

        public LeverageState(
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
