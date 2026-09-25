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
import org.ewsfi.signalpolicy.policy.NetLossEmergenceSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the NET_LOSS_EMERGENCE policy
 * (docs/architecture/04-signal-taxonomy.md Section 4, {@link NetLossEmergenceSignalPolicyLoader})
 * against {@code net_income} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.3's remaining
 * scope.
 *
 * <p>Mirrors {@link LeverageDeteriorationSignalTopology}/{@link CurrentRatioDeteriorationSignalTopology}'s
 * shape (a stateful {@code groupByKey().aggregate()} tracking {@code {previousIncome,
 * currentIncome, ...}} per counterparty, since detecting a transition needs the previous
 * observation) but a fixed zero-crossing check rather than a relative-threshold one -- "emergence"
 * is a discrete profit-to-loss crossing, not a graduated deterioration.
 */
@Component
public class NetLossEmergenceSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "net_income";
    private static final String STORE_NAME = "net-loss-emergence-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> netIncomeFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                netIncomeFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new NetIncomeState(null, null, null, null, null, null)),
                                (counterpartyId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    NetIncomeState previous = parseState(aggJson);
                                    // Defensive filtering (see LeverageDeteriorationSignalTopology /
                                    // MaxDpdFeatureTopology): a malformed valueNumeric must leave the
                                    // transition state unchanged, not throw from inside .aggregate().
                                    Double currentIncome = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentIncome == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new NetIncomeState(
                                                    previous.currentIncome,
                                                    currentIncome,
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
                        .mapValues(this::toSignalIfLossEmerged)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfLossEmerged(String stateJson) {
        NetIncomeState state = parseState(stateJson);
        if (state.previousIncome == null || state.currentIncome == null) {
            return null;
        }
        boolean lossEmerged =
                NetLossEmergenceSignalPolicyLoader.evaluate(state.previousIncome, state.currentIncome);
        if (!lossEmerged) {
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

    private String toStateJson(NetIncomeState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize NetIncomeState", e);
        }
    }

    private NetIncomeState parseState(String json) {
        try {
            return objectMapper.readValue(json, NetIncomeState.class);
        } catch (Exception e) {
            return new NetIncomeState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(NetIncomeState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        NetLossEmergenceSignalPolicyLoader.SIGNAL_TYPE,
                        NetLossEmergenceSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        NetLossEmergenceSignalPolicyLoader.POLICY_ID,
                        NetLossEmergenceSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-counterparty net_income transition state, tracked across observations. */
    public static class NetIncomeState {
        public Double previousIncome;
        public Double currentIncome;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public NetIncomeState() {
            // Jackson
        }

        public NetIncomeState(
                Double previousIncome,
                Double currentIncome,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousIncome = previousIncome;
            this.currentIncome = currentIncome;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
