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
import org.ewsfi.signalpolicy.policy.LimitExcessRecurringSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P07 LIMIT_EXCESS_RECURRING policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link LimitExcessRecurringSignalPolicyLoader})
 * against {@code wc_available_headroom} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 1.14's remaining
 * scope.
 *
 * <p>Mirrors {@link LeverageDeteriorationSignalTopology}/{@link DpdWorseningSignalTopology}'s shape
 * (a stateful {@code groupByKey().aggregate()} tracking {@code {previousHeadroom, currentHeadroom,
 * ...}} per facility, since detecting recurrence needs the previous observation). Unlike those two,
 * which fire on the *first* qualifying transition and rely on the transition itself resetting,
 * this policy re-evaluates the same two-consecutive-negative-observations rule on every new
 * observation while headroom remains negative -- so it fires on every qualifying consecutive pair,
 * not only the first, matching {@link SignalPolicyTopology}'s established precedent ("Emits a
 * signal on every window where the threshold is met, not only on the first crossing") rather than
 * DPD's "do not emit unchanged daily duplicates" rule, since each pair here reflects a genuinely
 * new observation, not a duplicate of an unchanged value.
 */
@Component
public class LimitExcessRecurringSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "wc_available_headroom";
    private static final String STORE_NAME = "limit-excess-recurring-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> headroomFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                headroomFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new HeadroomState(null, null, null, null, null, null)),
                                (facilityId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    HeadroomState previous = parseState(aggJson);
                                    // Defensive filtering (see LeverageDeteriorationSignalTopology /
                                    // MaxDpdFeatureTopology): a malformed valueNumeric must leave the
                                    // transition state unchanged, not throw from inside .aggregate().
                                    Double currentHeadroom = tryParseDouble(featureValue.getValueNumeric());
                                    if (currentHeadroom == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new HeadroomState(
                                                    previous.currentHeadroom,
                                                    currentHeadroom,
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
                        .mapValues(this::toSignalIfRecurring)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfRecurring(String stateJson) {
        HeadroomState state = parseState(stateJson);
        if (state.previousHeadroom == null || state.currentHeadroom == null) {
            return null;
        }
        boolean recurring =
                LimitExcessRecurringSignalPolicyLoader.evaluate(state.previousHeadroom, state.currentHeadroom);
        if (!recurring) {
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

    private String toStateJson(HeadroomState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize HeadroomState", e);
        }
    }

    private HeadroomState parseState(String json) {
        try {
            return objectMapper.readValue(json, HeadroomState.class);
        } catch (Exception e) {
            return new HeadroomState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(HeadroomState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        LimitExcessRecurringSignalPolicyLoader.SIGNAL_TYPE,
                        LimitExcessRecurringSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.75,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        LimitExcessRecurringSignalPolicyLoader.POLICY_ID,
                        LimitExcessRecurringSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-facility wc_available_headroom transition state, tracked across observations. */
    public static class HeadroomState {
        public Double previousHeadroom;
        public Double currentHeadroom;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public HeadroomState() {
            // Jackson
        }

        public HeadroomState(
                Double previousHeadroom,
                Double currentHeadroom,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousHeadroom = previousHeadroom;
            this.currentHeadroom = currentHeadroom;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
