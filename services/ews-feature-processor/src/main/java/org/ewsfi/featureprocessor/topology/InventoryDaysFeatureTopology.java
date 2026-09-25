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
 * Builds the Kafka Streams topology that computes {@code inventory_days}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 7: "Baseline: policy-defined
 * average/trailing inventory relative to COGS, normalized to period days.") from
 * {@code ews.canonical.financial-statement}'s {@code financial.statement.validated} events into
 * {@code ews.derived.feature}, per roadmap item 2.8. Feeds P16 INVENTORY_DAYS_DERIORATION
 * (docs/architecture/02a-priority-signal-contracts.md: "Inventory holding period materially
 * increases.").
 *
 * <p>Formula: {@code inventory_days = InventoryNet / CostOfGoodsAndServicesSold * periodDays} (days
 * inventory outstanding) -- mirrors {@link ReceivableDaysFeatureTopology} exactly (closing balance,
 * not a trailing average, per the catalogue's own "state average vs closing" requirement; the same
 * {@code periodDays} the XBRL client derives from whichever duration concept resolved for the
 * filing). Unlike revenue, {@code CostOfGoodsAndServicesSold} has no known taxonomy-migration
 * fallback needed for the filers checked while building this -- Apple does not tag the alternative
 * {@code CostOfRevenue} concept at all, so no fallback logic is added speculatively.
 */
@Component
public class InventoryDaysFeatureTopology {

    static final String CANONICAL_TOPIC = "ews.canonical.financial-statement";
    static final String FEATURE_TOPIC = "ews.derived.feature";
    static final String FEATURE_NAME = "inventory_days";
    static final String FEATURE_DEFINITION_ID = "FD-INVENTORY-DAYS-001";
    static final String FEATURE_DEFINITION_VERSION = "1.0";
    static final String TRANSFORMATION_VERSION = "1.0";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";
    private static final String COGS_CONCEPT = "CostOfGoodsAndServicesSold";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KStream<String, String> build(StreamsBuilder builder) {
        KStream<String, String> canonical =
                builder.stream(CANONICAL_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> featureValues =
                canonical
                        .mapValues(this::tryComputeInventoryDaysFeature)
                        .filter((key, value) -> value != null);

        featureValues.to(FEATURE_TOPIC, Produced.with(Serdes.String(), Serdes.String()));
        return featureValues;
    }

    /**
     * Returns {@code null} (rather than throwing) for any input that isn't a validated statement
     * with usable inventory, COGS, and period-days facts -- see
     * {@link LeverageRatioFeatureTopology#tryComputeLeverageFeature} for the full rationale.
     */
    private String tryComputeInventoryDaysFeature(String eventJson) {
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

            if (!(facts.get("InventoryNet") instanceof Number) || !(facts.get(COGS_CONCEPT) instanceof Number)) {
                return null;
            }

            double inventory = ((Number) facts.get("InventoryNet")).doubleValue();
            double cogs = ((Number) facts.get(COGS_CONCEPT)).doubleValue();
            double periodDays = ((Number) data.get("periodDays")).doubleValue();
            if (cogs <= 0 || periodDays <= 0) {
                // Mirrors ReceivableDaysFeatureTopology's guardrail: a non-positive denominator
                // makes the ratio meaningless, not just unusual.
                return null;
            }

            double inventoryDays = (inventory / cogs) * periodDays;
            return toFeatureValueJson(envelope.getEntityId(), inventoryDays);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFeatureValueJson(String cik, double inventoryDays) {
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
                        String.valueOf(inventoryDays),
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
