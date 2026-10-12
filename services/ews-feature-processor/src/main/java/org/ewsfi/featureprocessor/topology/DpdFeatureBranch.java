package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.WindowStore;
import org.ewsfi.avro.AvroPayloadCodec;
import org.ewsfi.avro.AvroStreams;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;

/**
 * Computes {@code current_dpd} and {@code max_dpd_30d}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1) from
 * {@code obligation.dpd.changed} on {@code ews.canonical.repayment}.
 *
 * <p>An unreconciled schedule is emitted as {@code current_dpd} in state {@code INVALID} with no
 * numeric value and is excluded from the rolling maximum. The catalogue forbids defaulting a
 * missing DPD to zero.
 *
 * <p>Uses the Kafka record timestamp for the 30-day window, the same simplification already
 * documented for {@code returned_payment_count_30d}: the envelope {@code eventTime} is copied onto
 * the point-in-time feature, but it is not a custom {@code TimestampExtractor}.
 */
public class DpdFeatureBranch {

    static final String REPAYMENT_TOPIC = "ews.canonical.repayment";
    static final String EVENT_TYPE = "obligation.dpd.changed";
    static final String CURRENT_DPD = "current_dpd";
    static final String MAX_DPD_30D = "max_dpd_30d";
    private static final String MAX_STORE = "max-dpd-30d-store";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AvroPayloadCodec avroPayloadCodec;

    public DpdFeatureBranch(AvroPayloadCodec avroPayloadCodec) {
        this.avroPayloadCodec = avroPayloadCodec;
    }

    public void attach(StreamsBuilder builder) {
        KStream<String, DpdObservation> observations =
                builder.stream(REPAYMENT_TOPIC, Consumed.with(Serdes.String(), Serdes.ByteArray()))
                        .process(AvroStreams.decode(avroPayloadCodec))
                        .flatMapValues(this::parse);

        observations
                .mapValues(this::toCurrentDpdJson)
                .process(AvroStreams.encode(avroPayloadCodec, "feature.value.updated"))
                .to(FeatureProcessorTopology.FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));

        observations
                .filter((key, observation) -> observation.usable())
                .mapValues(DpdObservation::currentDpd)
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(
                        SlidingWindows.ofTimeDifferenceAndGrace(Duration.ofDays(30), Duration.ofHours(1)))
                .aggregate(
                        () -> 0L,
                        (key, value, aggregate) -> Math.max(aggregate, value),
                        Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(MAX_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Long()))
                .toStream()
                .map(this::toMaxDpdJson)
                .process(AvroStreams.encode(avroPayloadCodec, "feature.value.updated"))
                .to(FeatureProcessorTopology.FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.ByteArray()));
    }

    private List<DpdObservation> parse(String json) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(json, JsonEventEnvelope.class);
            if (!EVENT_TYPE.equals(envelope.getEventType()) || envelope.getData() == null) {
                return List.of();
            }
            Map<String, Object> data = envelope.getData();
            Long current = longOrNull(data.get("currentDpd"));
            boolean reconciled = Boolean.TRUE.equals(data.get("reconciled"));
            return List.of(
                    new DpdObservation(
                            envelope.getEventId(),
                            envelope.getEntityId(),
                            current,
                            longOrNull(data.get("previousDpd")),
                            reconciled,
                            envelope.getEventTime()));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toCurrentDpdJson(DpdObservation observation) {
        boolean usable = observation.usable();
        String state = !usable ? "INVALID" : (observation.currentDpd() == 0L ? "ZERO" : "VALUE");
        String point = observation.eventTime();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        featureId(CURRENT_DPD, observation.eventId(), point, point),
                        "FD-CURRENT-DPD-001",
                        CURRENT_DPD,
                        "1.0",
                        "FACILITY",
                        observation.facilityId(),
                        state,
                        "INTEGER",
                        usable ? Long.toString(observation.currentDpd()) : null,
                        point,
                        point,
                        point,
                        point,
                        usable ? "COMPLETE" : "INSUFFICIENT",
                        "1.0");
        if (observation.previousDpd() != null) {
            featureValue.setPriorValueNumeric(Long.toString(observation.previousDpd()));
        }
        return write(featureValue);
    }

    private KeyValue<String, String> toMaxDpdJson(Windowed<String> windowedKey, Long maxDpd) {
        Instant now = Instant.now();
        String start = windowedKey.window().startTime().toString();
        String end = windowedKey.window().endTime().toString();
        long value = maxDpd == null ? 0L : maxDpd;
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        featureId(MAX_DPD_30D, windowedKey.key(), start, end),
                        "FD-MAX-DPD-30D-001",
                        MAX_DPD_30D,
                        "1.0",
                        "FACILITY",
                        windowedKey.key(),
                        value == 0L ? "ZERO" : "VALUE",
                        "INTEGER",
                        Long.toString(value),
                        now.toString(),
                        now.toString(),
                        start,
                        end,
                        "COMPLETE",
                        "1.0");
        return KeyValue.pair(windowedKey.key(), write(featureValue));
    }

    private String write(JsonFeatureValue featureValue) {
        try {
            return objectMapper.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonFeatureValue", e);
        }
    }

    private static String featureId(String featureName, String identity, String windowStart, String windowEnd) {
        String raw = featureName + "|" + identity + "|" + windowStart + "|" + windowEnd;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Long longOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record DpdObservation(
            String eventId,
            String facilityId,
            Long currentDpd,
            Long previousDpd,
            boolean reconciled,
            String eventTime) {

        private boolean usable() {
            return reconciled && currentDpd != null && currentDpd >= 0;
        }
    }
}
