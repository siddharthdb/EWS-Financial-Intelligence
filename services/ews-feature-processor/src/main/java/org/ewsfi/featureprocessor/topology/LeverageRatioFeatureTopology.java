package org.ewsfi.featureprocessor.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.contracts.interim.JsonFeatureValue;
import org.springframework.stereotype.Component;

/**
 * Builds the Kafka Streams topology that computes {@code total_liabilities_to_equity}
 * from {@code ews.canonical.financial-statement}'s {@code financial.statement.validated} events
 * (staged by {@code SecFilingIngestionAdapter} once real XBRL balance-sheet facts are extracted for
 * a filing) into {@code ews.derived.feature}, per roadmap item 2.8. Feeds P12 LEVERAGE_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Governed leverage materially worsens.
 * Implementations may use debt/EBITDA, debt/equity, TOL/ATNW or other approved measures.").
 *
 * <p>Deliberately not the catalogue's {@code tol_atnw} feature (docs/architecture/02d-phase1-feature-catalogue.md
 * Section 4: "total outside liabilities / adjusted tangible net worth... exact treatment of
 * quasi-equity, revaluation reserves, intangibles and related-party items is definition-versioned")
 * -- this platform has no quasi-equity/intangibles adjustment data, only raw XBRL
 * {@code Liabilities}/{@code StockholdersEquity}. {@code total_liabilities_to_equity} is a simpler,
 * documented debt/equity measure P12's own contract text explicitly permits ("debt/equity... or
 * other approved measures"), not a claim of equivalence to {@code tol_atnw}.
 *
 * <p>Guardrail (mirroring {@code debt_ebitda}'s own documented guardrail: "negative or near-zero
 * EBITDA produces a governed special state rather than misleading numeric ratio"): a filing with
 * zero or negative {@code StockholdersEquity} (a real, if uncommon, distress condition) is skipped
 * entirely rather than emitting a negative or arbitrarily large ratio that would misrepresent
 * leverage severity.
 */
@Component
public class LeverageRatioFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "total_liabilities_to_equity";
    static final String FEATURE_DEFINITION_ID = "FD-TOTAL-LIABILITIES-TO-EQUITY-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryComputeLeverageFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with usable numeric facts -- an unrelated event type, missing facts, or a non-positive equity
     * guardrail case are all legitimate "no feature value" outcomes, not errors. This mirrors
     * {@code FilingDelayFeatureTopology}'s established defensive-extraction idiom, and avoids the
     * poison-pill crash vector documented in {@code MaxDpdFeatureTopology} (an uncaught exception
     * inside a stateless {@code .filter()}/{@code .mapValues()} is not skipped under Kafka's
     * at-least-once redelivery, so the stream thread would crash-loop on the same record forever).
     */
    private String tryComputeLeverageFeature(String eventJson) {
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

            if (!(facts.get("Liabilities") instanceof Number) || !(facts.get("StockholdersEquity") instanceof Number)) {
                return null;
            }
            double liabilities = ((Number) facts.get("Liabilities")).doubleValue();
            double equity = ((Number) facts.get("StockholdersEquity")).doubleValue();
            if (equity <= 0) {
                // Guardrail: non-positive equity makes a liabilities/equity ratio misleading
                // (negative or unboundedly large), not a genuine leverage measure.
                return null;
            }

            double ratio = liabilities / equity;
            return toFeatureValueJson(envelope.getEntityId(), ratio);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String cik, double ratio) {
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
                        String.valueOf(ratio),
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
