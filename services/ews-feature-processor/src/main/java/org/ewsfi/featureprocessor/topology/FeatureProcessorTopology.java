package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
import org.apache.kafka.streams.kstream.SlidingWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code returned_payment_count_30d}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1) from
 * {@code ews.canonical.account-transaction} into {@code ews.derived.feature}
 * (docs/architecture/03c-topic-and-partition-strategy.md Section 2), per the "operational feature
 * processor" role named in ADR-004.
 *
 * <p>Uses {@link SlidingWindows} rather than a tumbling/hopping window, since the feature
 * catalogue's definition is a genuinely rolling 30-day count as of each new event, not a
 * fixed-bucket periodic count. Filters out {@code TECHNICAL} and {@code BENEFICIARY_DETAIL} return
 * reasons per docs/architecture/02a-priority-signal-contracts.md P03
 * (REPEATED_PAYMENT_RETURN: "Technical/network/beneficiary-detail returns are excluded by reason
 * policy").
 *
 * <p>Uses Kafka's default record timestamp (producer send time) rather than a custom
 * {@code TimestampExtractor} keyed off the JSON envelope's own {@code eventTime} field -- a known
 * simplification acceptable for this slice since ingestion publishes close to real time; true
 * bitemporal point-in-time correctness (docs/architecture/02-canonical-risk-model.md Section 6)
 * would need the latter and is a follow-up, not implemented here.
 */
@Component
public class FeatureProcessorTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.account-transaction";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "returned_payment_count_30d";
    static final String FEATURE_DEFINITION_ID = "FD-RETURNED-PAYMENT-COUNT-30D-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String STORE_NAME = "returned-payment-count-30d-store";
    private static final String PAYMENT_RETURN_EVENT_TYPE = "payment.instruction.returned";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> paymentReturns = canonical.filter(this::isCountablePaymentReturn);

        KTable<Windowed<String>, Long> windowedCounts =
                paymentReturns
                        .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
                        .windowedBy(
                                SlidingWindows.ofTimeDifferenceAndGrace(
                                        Duration.ofDays(30), Duration.ofHours(1)))
                        .count(Materialized.as(STORE_NAME));

        KStream<String, String> featureValues =
                windowedCounts
                        .toStream()
                        .map(
                                (windowedKey, count) ->
                                        KeyValue.pair(
                                                windowedKey.key(), toFeatureValueJson(windowedKey, count)));

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private boolean isCountablePaymentReturn(String key, String value) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(value, JsonEventEnvelope.class);
            if (!PAYMENT_RETURN_EVENT_TYPE.equals(envelope.getEventType())) {
                return false;
            }
            Map<String, Object> data = envelope.getData();
            if (data == null) {
                return false;
            }
            Object category = data.get("returnReasonCategory");
            return category != null
                    && !"TECHNICAL".equals(category)
                    && !"BENEFICIARY_DETAIL".equals(category);
        } catch (Exception e) {
            return false;
        }
    }

    private String toFeatureValueJson(Windowed<String> windowedKey, Long count) {
        Instant now = Instant.now();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        FEATURE_DEFINITION_ID,
                        FEATURE_NAME,
                        FEATURE_DEFINITION_VERSION,
                        "ACCOUNT",
                        windowedKey.key(),
                        "VALUE",
                        "INTEGER",
                        String.valueOf(count),
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
