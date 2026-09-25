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
 * Builds the Kafka Streams topology that computes {@code current_ratio}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 3: "Baseline: current_assets /
 * current_liabilities... Missingness: INVALID if denominator is zero/invalid; no synthetic neutral
 * value.") from {@code ews.canonical.financial-statement}'s {@code financial.statement.validated}
 * events into {@code ews.derived.feature}, per roadmap item 2.8. Feeds P11
 * CURRENT_RATIO_DERIORATION (docs/architecture/02a-priority-signal-contracts.md: "Short-term
 * balance-sheet liquidity materially weakens; interpretation is sector/portfolio contextual.").
 *
 * <p>Mirrors {@link LeverageRatioFeatureTopology}'s shape exactly (both are stateless per-event
 * ratios computed from the same {@code financial.statement.validated} event, using the same
 * accession-number-scoped XBRL facts {@code SecEdgarClient} already extracts --
 * {@code AssetsCurrent}/{@code LiabilitiesCurrent} here rather than
 * {@code Liabilities}/{@code StockholdersEquity}), with the same "skip rather than emit a
 * misleading value" guardrail applied to a zero-or-negative denominator, matching the catalogue's
 * own explicit missingness rule for this exact feature ("INVALID if denominator is zero/invalid").
 */
@Component
public class CurrentRatioFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "current_ratio";
    static final String FEATURE_DEFINITION_ID = "FD-CURRENT-RATIO-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryComputeCurrentRatioFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with usable numeric facts -- see {@link LeverageRatioFeatureTopology#tryComputeLeverageFeature}
     * for the full rationale (defensive extraction avoids the poison-pill crash vector documented in
     * {@code MaxDpdFeatureTopology}).
     */
    private String tryComputeCurrentRatioFeature(String eventJson) {
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

            if (!(facts.get("AssetsCurrent") instanceof Number)
                    || !(facts.get("LiabilitiesCurrent") instanceof Number)) {
                return null;
            }
            double currentAssets = ((Number) facts.get("AssetsCurrent")).doubleValue();
            double currentLiabilities = ((Number) facts.get("LiabilitiesCurrent")).doubleValue();
            if (currentLiabilities <= 0) {
                // Catalogue's own missingness rule: "INVALID if denominator is zero/invalid; no
                // synthetic neutral value" -- skip rather than emit a divide-by-zero or negative
                // ratio.
                return null;
            }

            double ratio = currentAssets / currentLiabilities;
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
