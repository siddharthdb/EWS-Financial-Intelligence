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
 * Builds the Kafka Streams topology that computes {@code operating_income} from
 * {@code ews.canonical.financial-statement}'s {@code financial.statement.validated} events
 * (specifically the {@code OperatingIncomeLoss} XBRL duration concept {@code SecEdgarClient} now
 * extracts, per its own period-disambiguation logic) into {@code ews.derived.feature}, per roadmap
 * item 2.8. Feeds P13 OPERATING_PROFIT_MATERIAL_DECLINE
 * (docs/architecture/02a-priority-signal-contracts.md: "Operating performance materially
 * deteriorates against history, plan or peers.") -- the "against history" case specifically, via a
 * consecutive-observation comparison; the catalogue's own {@code operating_profit_projection_variance}
 * ("against... plan") needs a sanctioned/approved projection this platform has no source for, so is
 * not implemented.
 *
 * <p>A pass-through feature (no ratio/computation beyond unit conversion), stateless and structured
 * identically to {@link LeverageRatioFeatureTopology}/{@link CurrentRatioFeatureTopology} for the
 * same defensive-extraction reasons (roadmap 3.6).
 */
@Component
public class OperatingIncomeFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "operating_income";
    static final String FEATURE_DEFINITION_ID = "FD-OPERATING-INCOME-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryExtractOperatingIncomeFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with a usable {@code OperatingIncomeLoss} fact -- see
     * {@link LeverageRatioFeatureTopology#tryComputeLeverageFeature} for the full rationale.
     */
    private String tryExtractOperatingIncomeFeature(String eventJson) {
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

            if (!(facts.get("OperatingIncomeLoss") instanceof Number)) {
                return null;
            }
            double operatingIncome = ((Number) facts.get("OperatingIncomeLoss")).doubleValue();
            return toFeatureValueJson(envelope.getEntityId(), operatingIncome);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String cik, double operatingIncome) {
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
                        String.valueOf(operatingIncome),
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
