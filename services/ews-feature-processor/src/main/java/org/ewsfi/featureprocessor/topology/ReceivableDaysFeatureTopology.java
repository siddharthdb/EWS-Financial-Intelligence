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
 * Builds the Kafka Streams topology that computes {@code receivable_days}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 7: "Baseline: policy-defined
 * average/trailing receivables relative to credit sales/revenue, normalized to period days.") from
 * {@code ews.canonical.financial-statement}'s {@code financial.statement.validated} events into
 * {@code ews.derived.feature}, per roadmap item 2.8. Feeds P15 RECEIVABLE_DAYS_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Collection cycle materially lengthens.").
 *
 * <p>Formula: {@code receivable_days = AccountsReceivableNetCurrent / revenue * periodDays} (days
 * sales outstanding) -- uses the filing's <em>closing</em> receivables balance, not a trailing
 * average, a documented simplification per the catalogue's own requirement to "state average vs
 * closing balance" explicitly. {@code periodDays} is the discrete reporting period's length
 * {@code SecEdgarClient} now derives alongside the duration-concept facts (a 10-Q's ~90 days, a
 * 10-K's ~365), needed to normalize the ratio independent of filing frequency.
 *
 * <p>Revenue concept fallback: prefers {@code RevenueFromContractWithCustomerExcludingAssessedTax}
 * (the modern ASC 606 tag) and falls back to {@code Revenues} only if the newer concept is absent --
 * see {@code SecEdgarClient.DURATION_CONCEPTS}'s javadoc for why both are extracted (a real XBRL
 * taxonomy migration many large filers, including Apple, underwent around fiscal 2018).
 */
@Component
public class ReceivableDaysFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "receivable_days";
    static final String FEATURE_DEFINITION_ID = "FD-RECEIVABLE-DAYS-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";
    private static final String PREFERRED_REVENUE_CONCEPT =
            "RevenueFromContractWithCustomerExcludingAssessedTax";
    private static final String FALLBACK_REVENUE_CONCEPT = "Revenues";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryComputeReceivableDaysFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with usable receivables, revenue, and period-days facts -- see
     * {@link LeverageRatioFeatureTopology#tryComputeLeverageFeature} for the full rationale.
     */
    private String tryComputeReceivableDaysFeature(String eventJson) {
        try {
            JsonEventEnvelope envelope = objectMapper.readValue(eventJson, JsonEventEnvelope.class);
            if (!VALIDATED_EVENT_TYPE.equals(envelope.getEventType())) {
                return null;
            }
            Map<String, Object> data = envelope.getData();
            if (data == null || !(data.get("facts") instanceof Map) || !(data.get("periodDays") instanceof Number)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> facts = (Map<String, Object>) data.get("facts");

            if (!(facts.get("AccountsReceivableNetCurrent") instanceof Number)) {
                return null;
            }
            Object revenueValue =
                    facts.get(PREFERRED_REVENUE_CONCEPT) instanceof Number
                            ? facts.get(PREFERRED_REVENUE_CONCEPT)
                            : facts.get(FALLBACK_REVENUE_CONCEPT);
            if (!(revenueValue instanceof Number)) {
                return null;
            }

            double receivables = ((Number) facts.get("AccountsReceivableNetCurrent")).doubleValue();
            double revenue = ((Number) revenueValue).doubleValue();
            double periodDays = ((Number) data.get("periodDays")).doubleValue();
            if (revenue <= 0 || periodDays <= 0) {
                // Mirrors LeverageRatioFeatureTopology's/CurrentRatioFeatureTopology's guardrail: a
                // non-positive denominator makes the ratio meaningless, not just unusual.
                return null;
            }

            double receivableDays = (receivables / revenue) * periodDays;
            return toFeatureValueJson(envelope.getEntityId(), receivableDays);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String cik, double receivableDays) {
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
                        String.valueOf(receivableDays),
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
