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
import org.ewsfi.signalpolicy.policy.DpdWorseningSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P02 DPD_WORSENING policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link DpdWorseningSignalPolicyLoader})
 * against {@code max_dpd_30d} values on {@code ews.derived.feature}, producing
 * {@code signal.detected} events on {@code ews.derived.signal}, per roadmap item 1.18.
 *
 * <p>Mirrors {@link DpdSignalTopology}'s shape exactly (a stateful {@code groupByKey().aggregate()}
 * tracking {@code {previousMax, currentMax, ...}} per facility, since detecting a material increase
 * needs the previous window's max, which a single feature-value message doesn't carry) but with a
 * magnitude-threshold policy rather than a zero-crossing one.
 */
@Component
public class DpdWorseningSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "max_dpd_30d";
    private static final String STORE_NAME = "dpd-worsening-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> maxDpdFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                maxDpdFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new DpdWorseningState(null, null, null, null, null, null)),
                                (facilityId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    DpdWorseningState previous = parseState(aggJson);
                                    // Roadmap 3.6: leave state unchanged on a malformed valueNumeric
                                    // rather than throwing from inside .aggregate() -- see
                                    // DpdSignalTopology/MaxDpdFeatureTopology for why.
                                    Integer currentMax = tryParseInt(featureValue.getValueNumeric());
                                    if (currentMax == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new DpdWorseningState(
                                                    previous.currentMax,
                                                    currentMax,
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
                        .mapValues(this::toSignalIfWorsening)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfWorsening(String stateJson) {
        DpdWorseningState state = parseState(stateJson);
        if (state.previousMax == null || state.currentMax == null) {
            return null;
        }
        boolean worsening = DpdWorseningSignalPolicyLoader.evaluate(state.previousMax, state.currentMax);
        if (!worsening) {
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

    private Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toStateJson(DpdWorseningState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DpdWorseningState", e);
        }
    }

    private DpdWorseningState parseState(String json) {
        try {
            return objectMapper.readValue(json, DpdWorseningState.class);
        } catch (Exception e) {
            return new DpdWorseningState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(DpdWorseningState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        DpdWorseningSignalPolicyLoader.SIGNAL_TYPE,
                        DpdWorseningSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        DpdWorseningSignalPolicyLoader.POLICY_ID,
                        DpdWorseningSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-facility max_dpd_30d transition state, tracked across window observations. */
    public static class DpdWorseningState {
        public Integer previousMax;
        public Integer currentMax;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public DpdWorseningState() {
            // Jackson
        }

        public DpdWorseningState(
                Integer previousMax,
                Integer currentMax,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousMax = previousMax;
            this.currentMax = currentMax;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
