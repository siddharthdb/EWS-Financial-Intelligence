package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code financial_statement_filing_delay_days}
 * from {@code ews.canonical.financial-statement} into {@code ews.derived.feature}, per roadmap
 * item 2.8 (expanding P10-P34 priority signal contract implementations). Feeds P18
 * REQUIRED_MONITORING_INFORMATION_DELAY (docs/architecture/02a-priority-signal-contracts.md:
 * "Required monitoring information was not received by governed due date").
 *
 * <p>The first feature built directly on top of the SEC EDGAR connector (roadmap item 2.1): each
 * {@code financial.statement.received} event already carries {@code filingDate} and
 * {@code reportDate} (the period the filing covers), so the delay between them is a stateless,
 * per-event computation -- unlike every other feature in this platform, this one needs no
 * windowing or cross-event state at all.
 */
@Component
public class FilingDelayFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "financial_statement_filing_delay_days";
    static final String FEATURE_DEFINITION_ID = "FD-FILING-DELAY-DAYS-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String STATEMENT_RECEIVED_EVENT_TYPE = "financial.statement.received";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryComputeDelayFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    private String tryComputeDelayFeature(String eventJson) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(eventJson, JsonEventEnvelope.class);
            if (!STATEMENT_RECEIVED_EVENT_TYPE.equals(envelope.getEventType())) {
                return null;
            }
            Map<String, Object> data = envelope.getData();
            if (data == null) {
                return null;
            }
            String filingDateStr = (String) data.get("filingDate");
            String reportDateStr = (String) data.get("reportDate");
            if (filingDateStr == null
                    || filingDateStr.isBlank()
                    || reportDateStr == null
                    || reportDateStr.isBlank()) {
                // Some SEC form types omit reportDate (e.g. purely event-driven filings); a delay
                // relative to a reporting period only makes sense when both dates are known.
                return null;
            }

            LocalDate filingDate = LocalDate.parse(filingDateStr);
            LocalDate reportDate = LocalDate.parse(reportDateStr);
            long delayDays = ChronoUnit.DAYS.between(reportDate, filingDate);

            return toFeatureValueJson(envelope.getEntityId(), delayDays);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String entityId, long delayDays) {
        Instant now = Instant.now();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        FEATURE_DEFINITION_ID,
                        FEATURE_NAME,
                        FEATURE_DEFINITION_VERSION,
                        "COUNTERPARTY",
                        entityId,
                        "VALUE",
                        "INTEGER",
                        String.valueOf(delayDays),
                        now.toString(),
                        now.toString(),
                        null,
                        null,
                        "COMPLETE",
                        TRANSFORMATION_VERSION);
        try {
            return objectMapper.writeValueAsString(featureValue);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonFeatureValue", e);
        }
    }
}
