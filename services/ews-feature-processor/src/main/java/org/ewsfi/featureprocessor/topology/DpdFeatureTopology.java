package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code current_dpd}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1) from
 * {@code ews.canonical.repayment} into {@code ews.derived.feature}
 * (docs/architecture/03c-topic-and-partition-strategy.md Section 2), per the "operational feature
 * processor" role named in ADR-004. This is the second Phase-1 signal family (roadmap item 1.12),
 * after payment-return proved the pattern.
 *
 * <p>Unlike {@link FeatureProcessorTopology}'s {@code returned_payment_count_30d}, which is a
 * genuinely windowed rolling count, {@code current_dpd} is a "latest known value" feature -- the
 * catalogue's own definition is the DPD as of the most recent observation, not an aggregate over a
 * window. This is implemented with {@code groupByKey().reduce((agg, next) -> next)}, which Kafka
 * Streams materializes as a non-windowed {@link KTable} always holding the latest value per key,
 * deliberately not a {@code SlidingWindows} aggregate.
 *
 * <p>{@code max_dpd_30d} (a genuinely windowed aggregate, like {@code returned_payment_count_30d})
 * is deferred to roadmap item 1.18, not implemented here -- see
 * docs/architecture/08-roadmap-progress-tracker.md.
 */
@Component
public class DpdFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.repayment";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "current_dpd";
    static final String FEATURE_DEFINITION_ID = "FD-CURRENT-DPD-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String STORE_NAME = "current-dpd-store";
    private static final String DPD_CHANGED_EVENT_TYPE = "obligation.dpd.changed";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> dpdChanges = canonical.filter(this::isDpdChangedEvent);

        KTable<String, String> latestDpd =
                dpdChanges
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .reduce((agg, next) -> next, Materialized.as(STORE_NAME));

        KStream<String, String> featureValues =
                latestDpd
                        .toStream()
                        .map((facilityId, eventJson) -> KeyValue.pair(facilityId, toFeatureValueJson(eventJson)));

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private boolean isDpdChangedEvent(String key, String value) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(value, JsonEventEnvelope.class);
            return DPD_CHANGED_EVENT_TYPE.equals(envelope.getEventType());
        } catch (Exception e) {
            return false;
        }
    }

    private String toFeatureValueJson(String eventJson) {
        Instant now = Instant.now();
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(eventJson, JsonEventEnvelope.class);
            Map<String, Object> data = envelope.getData();
            Object currentDpd = data.get("currentDpd");

            JsonFeatureValue featureValue =
                    new JsonFeatureValue(
                            UUID.randomUUID().toString(),
                            FEATURE_DEFINITION_ID,
                            FEATURE_NAME,
                            FEATURE_DEFINITION_VERSION,
                            "FACILITY",
                            envelope.getEntityId(),
                            "VALUE",
                            "INTEGER",
                            String.valueOf(currentDpd),
                            now.toString(),
                            now.toString(),
                            null,
                            null,
                            "COMPLETE",
                            TRANSFORMATION_VERSION);
            return objectMapper.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JsonFeatureValue for current_dpd", e);
        }
    }
}
