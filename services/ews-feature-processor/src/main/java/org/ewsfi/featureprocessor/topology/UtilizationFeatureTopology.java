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
 * Builds the Kafka Streams topology that computes {@code wc_utilization_ratio}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1: {@code eligible_outstanding /
 * applicable_capacity}) from {@code ews.canonical.facility} into {@code ews.derived.feature}, per
 * roadmap item 1.14 (docs/architecture/08-roadmap-progress-tracker.md).
 *
 * <p>Unlike {@link DpdFeatureTopology}, which reduces a single event stream to a latest-value
 * table, this feature needs two independently-changing inputs joined together: {@code
 * facility.limit.changed} (applicable capacity) and {@code facility.outstanding.changed} (eligible
 * outstanding), both published on the same {@code ews.canonical.facility} topic keyed by {@code
 * facilityId}. Since the two event types share a topic and key, a single {@code builder.table(...)}
 * over the raw topic would incorrectly let one event type's latest value overwrite the other's.
 * Instead, each event type is filtered into its own {@code groupByKey().reduce(...)}
 * latest-value {@link KTable} (own {@code Materialized} store), and the two tables are joined --
 * an inner join, so a feature value is only emitted once both a limit and an outstanding
 * observation exist for a facility, and re-emitted whenever either side changes.
 *
 * <p>Scoping decision (roadmap item 1.14): {@code applicable_capacity} here is the baseline
 * sanctioned limit from {@code facility.limit.changed}, not the working-capital drawing-power/
 * borrowing-base specialization ({@code facility.drawing_power.changed}, still unimplemented). If
 * the configured limit is zero, the ratio is reported as {@code 0.0} rather than dividing by zero
 * -- a documented simplification; a facility with a genuinely zero limit and positive outstanding
 * (an over-limit condition) deserves its own signal, not a divide-by-zero feature value, and that
 * refinement is left for when {@code limit_excess_days_30d} (a separate catalogue feature) is
 * implemented.
 */
@Component
public class UtilizationFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.facility";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "wc_utilization_ratio";
    static final String FEATURE_DEFINITION_ID = "FD-WC-UTILIZATION-RATIO-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String LIMIT_STORE_NAME = "facility-limit-store";
    private static final String OUTSTANDING_STORE_NAME = "facility-outstanding-store";
    private static final String UTILIZATION_STORE_NAME = "wc-utilization-store";
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

        KTable<String, String> utilization =
                latestOutstanding.join(
                        latestLimit,
                        this::toUtilizationFeatureJson,
                        Materialized.<String, String, KeyValueStore<Bytes, byte[]>>as(UTILIZATION_STORE_NAME)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.String()));

        KStream<String, String> featureValues =
                utilization.toStream().filter((key, value) -> value != null);
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
     * Roadmap 3.6: returns {@code null} (rather than throwing) when either side's numeric field is
     * malformed. A {@code null} from a {@code KTable-KTable} join's {@code ValueJoiner} is not an
     * error -- Kafka Streams treats it as a legitimate tombstone/delete for that key in the result
     * table, which {@link #build} then filters out of the published stream. Throwing here instead
     * would crash the stream thread from inside a join computation; per {@link MaxDpdFeatureTopology},
     * an uncaught exception does not skip the record under at-least-once redelivery, so the thread
     * would crash-loop on the same record forever even with {@code REPLACE_THREAD} configured.
     */
    private String toUtilizationFeatureJson(String outstandingJson, String limitJson) {
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
            double ratio = limit > 0 ? outstanding / limit : 0.0;

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
                            String.valueOf(ratio),
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
