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
import org.ewsfi.signalpolicy.policy.DpdSignalPolicyLoader;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology evaluating the P01 DPD_EMERGED policy
 * (docs/architecture/02a-priority-signal-contracts.md, {@link DpdSignalPolicyLoader}) against
 * {@code current_dpd} values on {@code ews.derived.feature}, producing {@code signal.detected}
 * events on {@code ews.derived.signal}, per the "signal policy engine" role named in ADR-004. This
 * is the second Phase-1 signal family (roadmap item 1.12).
 *
 * <p>Unlike {@link SignalPolicyTopology}, which evaluates each incoming feature value
 * independently, detecting a DPD_EMERGED transition needs the <em>previous</em> {@code current_dpd}
 * value, which a single {@code current_dpd} feature-value message doesn't itself carry. This
 * topology therefore keeps a small stateful {@code groupByKey().aggregate(...)} per facility
 * (JSON-encoded {@link DpdState}, mirroring the project's JSON-everywhere wire/state convention
 * rather than introducing a bespoke binary Serde) that carries both the DPD transition and the
 * fields needed to emit a signal, so the aggregate's output stream alone is enough to produce
 * {@code signal.detected} events -- no secondary stream-stream join is needed.
 */
@Component
public class DpdSignalTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String SIGNAL_TOPIC = "ews.derived.signal";
    private static final String TARGET_FEATURE_NAME = "current_dpd";
    private static final String STORE_NAME = "dpd-transition-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> dpdFeatures =
                features.filter((key, value) -> isTargetFeature(tryParse(value)));

        KTable<String, String> transitions =
                dpdFeatures
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .aggregate(
                                () -> toStateJson(new DpdState(null, null, null, null, null, null)),
                                (facilityId, featureValueJson, aggJson) -> {
                                    JsonFeatureValue featureValue = tryParse(featureValueJson);
                                    DpdState previous = parseState(aggJson);
                                    // Roadmap 3.6: a malformed (non-numeric) valueNumeric must leave
                                    // the transition state unchanged, not throw from inside
                                    // .aggregate() -- an uncaught exception here does not skip the
                                    // record under Kafka's at-least-once redelivery, so the stream
                                    // thread would crash-loop on it forever (see MaxDpdFeatureTopology).
                                    Integer currentDpd = tryParseInt(featureValue.getValueNumeric());
                                    if (currentDpd == null) {
                                        return aggJson;
                                    }
                                    return toStateJson(
                                            new DpdState(
                                                    previous.currentDpd,
                                                    currentDpd,
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
                        .mapValues(this::toSignalIfEmerged)
                        .filter((key, value) -> value != null);

        signals.to(SIGNAL_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return signals;
    }

    private boolean isTargetFeature(JsonFeatureValue featureValue) {
        return featureValue != null
                && TARGET_FEATURE_NAME.equals(featureValue.getFeatureName())
                && featureValue.getValueNumeric() != null;
    }

    private String toSignalIfEmerged(String stateJson) {
        DpdState state = parseState(stateJson);
        if (state.currentDpd == null) {
            return null;
        }
        boolean emerged = DpdSignalPolicyLoader.evaluate(state.previousDpd, state.currentDpd);
        if (!emerged) {
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

    private String toStateJson(DpdState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DpdState", e);
        }
    }

    private DpdState parseState(String json) {
        try {
            return objectMapper.readValue(json, DpdState.class);
        } catch (Exception e) {
            return new DpdState(null, null, null, null, null, null);
        }
    }

    private String toSignalDetectedJson(DpdState state) {
        Instant now = Instant.now();
        String signalId = UUID.nameUUIDFromBytes(state.featureValueId.getBytes()).toString();
        JsonSignalDetected signal =
                new JsonSignalDetected(
                        signalId,
                        DpdSignalPolicyLoader.SIGNAL_TYPE,
                        DpdSignalPolicyLoader.SEMANTIC_SCOPE,
                        state.entityType,
                        state.entityId,
                        "PROPOSED",
                        "HIGH",
                        0.8,
                        "MEDIUM",
                        now.toString(),
                        now.toString(),
                        state.knowledgeTime,
                        DpdSignalPolicyLoader.POLICY_ID,
                        DpdSignalPolicyLoader.POLICY_VERSION,
                        "COMPLETE",
                        List.of(state.featureValueId));
        try {
            return objectMapper.writeValueAsString(signal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonSignalDetected", e);
        }
    }

    /** Per-facility DPD transition state, tracked across {@code current_dpd} observations. */
    public static class DpdState {
        public Integer previousDpd;
        public Integer currentDpd;
        public String featureValueId;
        public String entityType;
        public String entityId;
        public String knowledgeTime;

        public DpdState() {
            // Jackson
        }

        public DpdState(
                Integer previousDpd,
                Integer currentDpd,
                String featureValueId,
                String entityType,
                String entityId,
                String knowledgeTime) {
            this.previousDpd = previousDpd;
            this.currentDpd = currentDpd;
            this.featureValueId = featureValueId;
            this.entityType = entityType;
            this.entityId = entityId;
            this.knowledgeTime = knowledgeTime;
        }
    }
}
