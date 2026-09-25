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
import org.ewsfi.signalpolicy.policy.OperatingProfitDeclineSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P13 OPERATING_PROFIT_MATERIAL_DECLINE policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link OperatingProfitDeclineSignalPolicyLoader})
 * against {@code operating_income} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8.
 *
 * <p>Mirrors {@link CurrentRatioDeteriorationSignalTopology}/{@link LeverageDeteriorationSignalTopology}'s
 * shape exactly (a stateful {@code groupByKey().aggregate()} tracking {@code {previousIncome,
 * currentIncome, ...}} per counterparty, since detecting a material decline needs the previous
 * observation).
 */
@Component
public class OperatingProfitDeclineSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "operating_income";
    private static final String STORE_NAME = "operating-profit-decline-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> operatingIncomeFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                operatingIncomeFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new OperatingIncomeState(null, null, null, null, null, null)),
                                (counterpartyId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    OperatingIncomeState previous = parseState(aggJson);
                                    // Defensive filtering (see LeverageDeteriorationSignalTopology /
                                    // MaxDpdFeatureTopology): a malformed valueNumeric must leave the
                                    // transition state unchanged, not throw from inside .aggregate().
                                    Double currentIncome = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentIncome == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new OperatingIncomeState(
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
                        .mapValues(this::toSignalIfDeclined)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfDeclined(String stateJson) {
        OperatingIncomeState state = parseState(stateJson);
        if (state.previousIncome == null || state.currentIncome == null) {
            return null;
        }
        boolean declined =
                OperatingProfitDeclineSignalPolicyLoader.evaluate(state.previousIncome, state.currentIncome);
        if (!declined) {
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

    private String toStateJson(OperatingIncomeState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OperatingIncomeState", e);
        }
    }

    private OperatingIncomeState parseState(String json) {
        try {
            return objectMapper.readValue(json, OperatingIncomeState.class);
        } catch (Exception e) {
            return new OperatingIncomeState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(OperatingIncomeState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        OperatingProfitDeclineSignalPolicyLoader.SIGNAL_TYPE,
                        OperatingProfitDeclineSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.75,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        OperatingProfitDeclineSignalPolicyLoader.POLICY_ID,
                        OperatingProfitDeclineSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-counterparty operating_income transition state, tracked across observations. */
    public static class OperatingIncomeState {
        public Double previousIncome;
        public Double currentIncome;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public OperatingIncomeState() {
            // Jackson
        }

        public OperatingIncomeState(
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
