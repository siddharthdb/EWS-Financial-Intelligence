package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code max_dpd_30d}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1: "maximum point-in-time DPD
 * observed in window") from {@code ews.canonical.repayment} into {@code ews.derived.feature}, per
 * roadmap item 1.18 -- the remainder of item 1.12 deferred when {@link DpdFeatureTopology}
 * (`current_dpd`) and DPD_EMERGED were implemented.
 *
 * <p>Unlike {@link DpdFeatureTopology}'s non-windowed "latest value" reduce, this is a genuinely
 * windowed aggregate, mirroring {@link FeatureProcessorTopology}'s {@code SlidingWindows} pattern
 * for {@code returned_payment_count_30d} -- but aggregating the maximum {@code currentDpd} seen in
 * each rolling 30-day window per facility, rather than a count.
 */
@Component
public class MaxDpdFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.repayment";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "max_dpd_30d";
    static final String FEATURE_DEFINITION_ID = "FD-MAX-DPD-30D-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String STORE_NAME = "max-dpd-30d-store";
    private static final String DPD_CHANGED_EVENT_TYPE = "obligation.dpd.changed";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> dpdChanges =
                canonical.filter((key, value) -> isDpdChangedEvent(value) && hasNumericCurrentDpd(value));

        KTable<Windowed<String>, Integer> windowedMax =
                dpdChanges
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .windowedBy(
                                SlidingWindows.ofTimeDifferenceAndGrace(
                                        Duration.ofDays(30), Duration.ofHours(1)))
                        .aggregate(
                                () -> 0,
                                (facilityId, eventJson, currentMax) ->
                                        Math.max(currentMax, extractCurrentDpd(eventJson)),
                                Materialized.<String, Integer, WindowStore<Bytes, byte[]>>as(STORE_NAME)
                                        .withKeySerde(Serdes.String())
                                        .withValueSerde(Serdes.Integer()));

        KStream<String, String> featureValues =
                windowedMax
                        .toStream()
                        .map(
                                (windowedKey, max) ->
                                        KeyValue.pair(windowedKey.key(), toFeatureValueJson(windowedKey, max)));

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private boolean isDpdChangedEvent(String value) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(value, JsonEventEnvelope.class);
            return DPD_CHANGED_EVENT_TYPE.equals(envelope.getEventType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Defensive input validation (roadmap 3.6): a record whose {@code currentDpd} is not numeric
     * would otherwise reach {@link #extractCurrentDpd(String)} inside the {@code .aggregate()} call
     * and throw, crashing the stream thread. Kafka Streams' at-least-once semantics mean an uncaught
     * exception there does not skip the record -- even with a {@code StreamsUncaughtExceptionHandler}
     * replacing the thread, the offset is never committed and the replacement thread re-reads and
     * re-crashes on the same record indefinitely, permanently blocking that partition. Filtering the
     * record out here, before it ever reaches the aggregator, is the only way to actually recover.
     */
    private boolean hasNumericCurrentDpd(String value) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(value, JsonEventEnvelope.class);
            Map<String, Object> data = envelope.getData();
            return data.get("currentDpd") instanceof Number;
        } catch (Exception e) {
            return false;
        }
    }

    private int extractCurrentDpd(String eventJson) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(eventJson, JsonEventEnvelope.class);
            Map<String, Object> data = envelope.getData();
            return ((Number) data.get("currentDpd")).intValue();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract currentDpd from event", e);
        }
    }

    private String toFeatureValueJson(Windowed<String> windowedKey, Integer max) {
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
                        "INTEGER",
                        String.valueOf(max),
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
}
