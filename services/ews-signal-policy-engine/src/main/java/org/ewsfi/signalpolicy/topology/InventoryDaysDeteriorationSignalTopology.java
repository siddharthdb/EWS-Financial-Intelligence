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
import org.ewsfi.signalpolicy.policy.InventoryDaysDeteriorationSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P16 INVENTORY_DAYS_DERIORATION policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link InventoryDaysDeteriorationSignalPolicyLoader})
 * against {@code inventory_days} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 2.8.
 *
 * <p>Mirrors {@link ReceivableDaysDeteriorationSignalTopology}'s shape exactly (a stateful
 * {@code groupByKey().aggregate()} tracking {@code {previousDays, currentDays, ...}} per
 * counterparty, since detecting a material lengthening needs the previous observation).
 */
@Component
public class InventoryDaysDeteriorationSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "inventory_days";
    private static final String STORE_NAME = "inventory-days-deterioration-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> inventoryDaysFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                inventoryDaysFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new InventoryDaysState(null, null, null, null, null, null)),
                                (counterpartyId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    InventoryDaysState previous = parseState(aggJson);
                                    // Defensive filtering (see ReceivableDaysDeteriorationSignalTopology /
                                    // MaxDpdFeatureTopology): a malformed valueNumeric must leave the
                                    // transition state unchanged, not throw from inside .aggregate().
                                    Double currentDays = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentDays == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new InventoryDaysState(
                                                    previous.currentDays,
                                                    currentDays,
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
        InventoryDaysState state = parseState(stateJson);
        if (state.previousDays == null || state.currentDays == null) {
            return null;
        }
        boolean deteriorated =
                InventoryDaysDeteriorationSignalPolicyLoader.evaluate(state.previousDays, state.currentDays);
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

    private String toStateJson(InventoryDaysState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize InventoryDaysState", e);
        }
    }

    private InventoryDaysState parseState(String json) {
        try {
            return objectMapper.readValue(json, InventoryDaysState.class);
        } catch (Exception e) {
            return new InventoryDaysState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(InventoryDaysState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        InventoryDaysDeteriorationSignalPolicyLoader.SIGNAL_TYPE,
                        InventoryDaysDeteriorationSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "MEDIUM",
                        0.7,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        InventoryDaysDeteriorationSignalPolicyLoader.POLICY_ID,
                        InventoryDaysDeteriorationSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-counterparty inventory_days transition state, tracked across observations. */
    public static class InventoryDaysState {
        public Double previousDays;
        public Double currentDays;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public InventoryDaysState() {
            // Jackson
        }

        public InventoryDaysState(
                Double previousDays,
                Double currentDays,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousDays = previousDays;
            this.currentDays = currentDays;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
