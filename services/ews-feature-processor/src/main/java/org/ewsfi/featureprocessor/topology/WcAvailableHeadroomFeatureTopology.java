package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code wc_available_headroom}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1: "Baseline: applicable_capacity -
 * eligible_outstanding... Negative values represent excess.") from {@code ews.canonical.facility}
 * into {@code ews.derived.feature}, per roadmap item 1.14's remaining scope. Feeds P07
 * LIMIT_EXCESS_RECURRING (docs/architecture/02a-priority-signal-contracts.md: "Exposure
 * repeatedly/continuously exceeds applicable approved/committed capacity.").
 *
 * <p>Deliberately a separate topology from {@link UtilizationFeatureTopology} rather than emitting
 * a second feature from the same join, even though both derive from the identical {@code
 * {limit, outstanding}} pair -- a Kafka Streams {@code KTable} join's {@code ValueJoiner} produces
 * exactly one output value per join event, and splitting that into two published features would
 * need restructuring an already-tested, working topology. Instead this topology independently joins
 * the same two upstream event types with its own state stores, mirroring the established pattern of
 * {@link DpdFeatureTopology}/{@link MaxDpdFeatureTopology} both independently reading {@code
 * ews.canonical.repayment}. The duplicated join cost is the same tradeoff already accepted there.
 *
 * <p>Scoping decision (mirrors {@link UtilizationFeatureTopology}'s own): {@code
 * applicable_capacity} is the baseline sanctioned limit from {@code facility.limit.changed}, not the
 * working-capital drawing-power/borrowing-base specialization. Unlike the ratio feature, no
 * divide-by-zero guardrail is needed here -- headroom is a plain subtraction, and the catalogue's
 * own definition explicitly expects negative values ("Negative values represent excess"), so a
 * negative result is a genuine, meaningful signal input, not an error state to suppress.
 */
@Component
public class WcAvailableHeadroomFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.facility";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "wc_available_headroom";
    static final String FEATURE_DEFINITION_ID = "FD-WC-AVAILABLE-HEADROOM-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String LIMIT_STORE_NAME = "headroom-facility-limit-store";
    private static final String OUTSTANDING_STORE_NAME = "headroom-facility-outstanding-store";
    private static final String HEADROOM_STORE_NAME = "wc-available-headroom-store";
    private static final String LIMIT_CHANGED_EVENT_TYPE = "facility.limit.changed";
    private static final String OUTSTANDING_CHANGED_EVENT_TYPE = "facility.outstanding.changed";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KTable<String, String> latestLimit =
                canonical
                        .filter((key, value) -> isEventType(value, LIMIT_CHANGED_EVENT_TYPE))
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .reduce((agg, next) -> next, Materialized.as(LIMIT_STORE_NAME));

        KTable<String, String> latestOutstanding =
                canonical
                        .filter((key, value) -> isEventType(value, OUTSTANDING_CHANGED_EVENT_TYPE))
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .reduce((agg, next) -> next, Materialized.as(OUTSTANDING_STORE_NAME));

        KTable<String, String> headroom =
                latestOutstanding.join(
                        latestLimit,
                        this::toHeadroomFeatureJson,
                        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(HEADROOM_STORE_NAME)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String()));

        KStream<String, String> featureValues =
                headroom.toStream().filter((key, value) -> value != null);
        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private boolean isEventType(String value, String eventType) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(value, JsonEventEnvelope.class);
            return eventType.equals(envelope.getEventType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns {@code null} (rather than throwing) when either side's numeric field is malformed --
     * see {@link UtilizationFeatureTopology#toUtilizationFeatureJson} for why a {@code null} here is
     * a legitimate join tombstone, not an error, and why throwing would risk a poison-pill
     * crash-loop.
     */
    private String toHeadroomFeatureJson(String outstandingJson, String limitJson) {
        Instant now = Instant.now();
        try {
            JsonEventEnvelope outstandingEnvelope =
                    objectMapper.readValue(outstandingJson, JsonEventEnvelope.class);
            JsonEventEnvelope limitEnvelope = objectMapper.readValue(limitJson, JsonEventEnvelope.class);

            Map<String, Object> outstandingData = outstandingEnvelope.getData();
            Map<String, Object> limitData = limitEnvelope.getData();

            if (!(outstandingData.get("currentOutstanding") instanceof Number)
                    || !(limitData.get("currentLimit") instanceof Number)) {
                return null;
            }

            double outstanding = ((Number) outstandingData.get("currentOutstanding")).doubleValue();
            double limit = ((Number) limitData.get("currentLimit")).doubleValue();
            double headroom = limit - outstanding;

            JsonFeatureValue featureValue =
                    new JsonFeatureValue(
                            UUID.randomUUID().toString(),
                            FEATURE_DEFINITION_ID,
                            FEATURE_NAME,
                            FEATURE_DEFINITION_VERSION,
                            "FACILITY",
                            outstandingEnvelope.getEntityId(),
                            "VALUE",
                            "DECIMAL",
                            String.valueOf(headroom),
                            now.toString(),
                            now.toString(),
                            null,
                            null,
                            "COMPLETE",
                            TRANSFORMATION_VERSION);
            return objectMapper.writeValueAsString(featureValue);
        } catch (Exception e) {
            return null;
        }
    }
}
