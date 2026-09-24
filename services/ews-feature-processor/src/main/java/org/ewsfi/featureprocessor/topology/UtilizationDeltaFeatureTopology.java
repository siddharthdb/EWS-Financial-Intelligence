package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code wc_utilization_delta_30d}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1: "current utilization minus
 * configured 30-day baseline (mean/median/other versioned definition). Used by utilization-spike
 * detection."), the first genuinely statistical (method S) feature in the platform -- every prior
 * feature was a deterministic count, latest-value, max, or ratio, not a comparison against a
 * computed statistical baseline. Part of roadmap item 2.3.
 *
 * <p>Consumes {@code ews.derived.feature} filtered to {@code wc_utilization_ratio} (a "feature on a
 * feature": this topology composes {@link UtilizationFeatureTopology}'s output rather than
 * recomputing utilization from the raw canonical events, since the ratio is already the correct
 * per-observation input). Maintains a {@code SlidingWindows} aggregate tracking {@code {sum, count,
 * latestValue}} per facility over the same 30-day window {@link FeatureProcessorTopology} and
 * {@link MaxDpdFeatureTopology} use, and emits {@code delta = latestValue - (sum / count)} -- the
 * current observation's deviation from its own rolling 30-day mean.
 *
 * <p>Baseline definition: the mean is computed over the same window that includes the triggering
 * observation itself (not a strictly-prior baseline excluding it), a documented simplification --
 * with few observations per facility this keeps the aggregation single-pass rather than requiring
 * a two-store "history vs. current" design; the more sample-heavy a facility's history, the less
 * this matters.
 */
@Component
public class UtilizationDeltaFeatureTopology {

    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "wc_utilization_delta_30d";
    static final String SOURCE_FEATURE_NAME = "wc_utilization_ratio";
    static final String FEATURE_DEFINITION_ID = "FD-WC-UTILIZATION-DELTA-30D-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String STORE_NAME = "wc-utilization-baseline-store";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> features =
                builder.stream(FEATURE_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> utilizationRatios =
                features.filter((key, value) -> isTargetFeature(value));

        KTable<Windowed<String>, String> windowedBaseline =
                utilizationRatios
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .windowedBy(
                                SlidingWindows.ofTimeDifferenceAndGrace(
                                        Duration.ofDays(30), Duration.ofHours(1)))
                        .aggregate(
                                () -> toStateJson(new BaselineState(0.0, 0, 0.0)),
                                (facilityId, featureValueJson, aggJson) -> {
                                    double ratio = extractRatio(featureValueJson);
                                    BaselineState previous = parseState(aggJson);
                                    return toStateJson(
                                            new BaselineState(previous.sum + ratio, previous.count + 1, ratio));
                                },
                                Materialized.<String, String, WindowStore<Bytes, byte[]>>as(STORE_NAME)
                                        .withKeySerde(Serdes.String())
                                        .withValueSerde(Serdes.String()));

        KStream<String, String> featureValues =
                windowedBaseline
                        .toStream()
                        .map(
                                (windowedKey, stateJson) ->
                                        KeyValue.pair(windowedKey.key(), toFeatureValueJson(windowedKey, stateJson)));

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private boolean isTargetFeature(String value) {
        try {
            JsonFeatureValue featureValue = objectMapper.readValue(value, JsonFeatureValue.class);
            return SOURCE_FEATURE_NAME.equals(featureValue.getFeatureName())
                    && featureValue.getValueNumeric() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private double extractRatio(String featureValueJson) {
        try {
            JsonFeatureValue featureValue = objectMapper.readValue(featureValueJson, JsonFeatureValue.class);
            return Double.parseDouble(featureValue.getValueNumeric());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract wc_utilization_ratio value", e);
        }
    }

    private String toStateJson(BaselineState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize BaselineState", e);
        }
    }

    private BaselineState parseState(String json) {
        try {
            return objectMapper.readValue(json, BaselineState.class);
        } catch (Exception e) {
            return new BaselineState(0.0, 0, 0.0);
        }
    }

    private String toFeatureValueJson(Windowed<String> windowedKey, String stateJson) {
        BaselineState state = parseState(stateJson);
        double mean = state.count > 0 ? state.sum / state.count : 0.0;
        double delta = state.latestValue - mean;

        Instant now = Instant.now();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        FEATURE_DEFINITION_ID,
                        FEATURE_NAME,
                        FEATURE_DEFINITION_VERSION,
                        "FACILITY",
                        windowedKey.key(),
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(delta),
                        now.toString(),
                        now.toString(),
                        windowedKey.window().startTime().toString(),
                        windowedKey.window().endTime().toString(),
                        "COMPLETE",
                        TRANSFORMATION_VERSION);
        try {
            return objectMapper.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonFeatureValue", e);
        }
    }

    /** Per-facility, per-window running baseline: sum/count for the mean, plus the latest value. */
    public static class BaselineState {
        public double sum;
        public int count;
        public double latestValue;

        public BaselineState() {
            // Jackson
        }

        public BaselineState(double sum, int count, double latestValue) {
            this.sum = sum;
            this.count = count;
            this.latestValue = latestValue;
        }
    }
}
