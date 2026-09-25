package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * Builds the Kafka Streams topology that computes {@code operating_cash_flow}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 6: "Source: canonical cash-flow
 * statement or governed derivation.") from {@code ews.canonical.financial-statement}'s
 * {@code financial.statement.validated} events (the {@code NetCashProvidedByUsedInOperatingActivities}
 * XBRL duration concept {@code SecEdgarClient} now extracts) into {@code ews.derived.feature}, per
 * roadmap item 2.8. Feeds P14 OPERATING_CASH_FLOW_NEGATIVE
 * (docs/architecture/02a-priority-signal-contracts.md: "Operating cash flow is negative for a
 * relevant period, interpreted with seasonality/business-model context.").
 *
 * <p>A pass-through feature, stateless and structured identically to
 * {@link OperatingIncomeFeatureTopology} for the same defensive-extraction reasons (roadmap 3.6).
 */
@Component
public class OperatingCashFlowFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "operating_cash_flow";
    static final String FEATURE_DEFINITION_ID = "FD-OPERATING-CASH-FLOW-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryExtractOperatingCashFlowFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with a usable {@code NetCashProvidedByUsedInOperatingActivities} fact -- see
     * {@link LeverageRatioFeatureTopology#tryComputeLeverageFeature} for the full rationale.
     */
    private String tryExtractOperatingCashFlowFeature(String eventJson) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(eventJson, JsonEventEnvelope.class);
            if (!VALIDATED_EVENT_TYPE.equals(envelope.getEventType())) {
                return null;
            }
            Map<String, Object> data = envelope.getData();
            if (data == null || !(data.get("facts") instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> facts = (Map<String, Object>) data.get("facts");

            if (!(facts.get("NetCashProvidedByUsedInOperatingActivities") instanceof Number)) {
                return null;
            }
            double operatingCashFlow =
                    ((Number) facts.get("NetCashProvidedByUsedInOperatingActivities")).doubleValue();
            return toFeatureValueJson(envelope.getEntityId(), operatingCashFlow);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String cik, double operatingCashFlow) {
        Instant now = Instant.now();
        JsonFeatureValue featureValue =
                new JsonFeatureValue(
                        UUID.randomUUID().toString(),
                        FEATURE_DEFINITION_ID,
                        FEATURE_NAME,
                        FEATURE_DEFINITION_VERSION,
                        "COUNTERPARTY",
                        cik,
                        "VALUE",
                        "DECIMAL",
                        String.valueOf(operatingCashFlow),
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
