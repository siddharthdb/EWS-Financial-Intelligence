package org.ewsfi.ingestion.adapter.external.sec;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a {@code financial.statement.received} observation
 * (docs/architecture/03d-phase1-event-catalogue.md Section 3) from a real SEC EDGAR filing fetched
 * by {@link SecEdgarClient}, and stages it via the outbox to {@code ews.canonical.financial-statement}
 * (ADR-003), per roadmap item 2.1 -- the platform's first genuine external-source connector (every
 * prior ingestion adapter records observations posted by an internal REST caller standing in for an
 * internal system; this one calls a real external API itself).
 *
 * <p>{@code entityId} is the SEC CIK, used directly as the interim counterparty identifier -- real
 * entity resolution (mapping a CIK to this platform's own counterparty identity,
 * schemas/identity/entity-resolution-v1.schema.json) is not implemented; this is a documented
 * simplification, not a claim that CIK and counterpartyId are the same concept long-term.
 *
 * <p>Roadmap item 2.8's follow-up: after recording {@code financial.statement.received}, this
 * adapter also fetches the filing's real XBRL balance-sheet facts
 * ({@link SecEdgarClient#BALANCE_SHEET_CONCEPTS}) and, if any are reported for this exact filing,
 * stages a second {@code financial.statement.validated} event (docs/architecture/03d-phase1-event-catalogue.md
 * Section 2: "financial facts validated -&gt; ratios/trends") carrying them -- the event this
 * platform's event catalogue already names as the input to ratio/trend features, which every
 * remaining P10-P34 priority signal contract needs and this platform did not previously parse at
 * all (2.8's own "DONE (partial)" note: "every other P10-P34 contract needs financial-statement
 * XBRL contents this platform doesn't parse yet").
 */
@Component
public class SecFilingIngestionAdapter {

    /** Matches the ews.canonical.financial-statement row in 03c-topic-and-partition-strategy.md Section 2. */
    private static final String TARGET_TOPIC = "ews.canonical.financial-statement";

    private static final String RECEIVED_EVENT_TYPE = "financial.statement.received";
    private static final String VALIDATED_EVENT_TYPE = "financial.statement.validated";
    private static final String EVENT_VERSION = "v1";

    private final SecEdgarClient secEdgarClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public SecFilingIngestionAdapter(
            SecEdgarClient secEdgarClient,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.secEdgarClient = secEdgarClient;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches {@code cik}'s most recent periodic statement filing from the live SEC EDGAR API and,
     * if one exists, stages a {@code financial.statement.received} outbox event, then attempts to
     * fetch that exact filing's real XBRL balance-sheet facts and, if any are reported, stages a
     * second {@code financial.statement.validated} event -- both in the same local transaction.
     * Returns the staged event ids (one or two), or empty if the company has no qualifying filing
     * in SEC's "recent" window. A filing with no matching XBRL facts (e.g. one that predates SEC's
     * XBRL company-facts coverage) still yields the {@code received} event alone -- XBRL fact
     * availability is best-effort, not a precondition for recording that the filing exists.
     */
    @Transactional
    public List<String> syncMostRecentPeriodicStatement(String cik) throws IOException, InterruptedException {
        Optional<SecFiling> filing = secEdgarClient.fetchMostRecentPeriodicStatement(cik);
        if (filing.isEmpty()) {
            return List.of();
        }

        List<String> eventIds = new java.util.ArrayList<>();
        eventIds.add(recordReceived(filing.get()));

        Map<String, Long> xbrlFacts =
                secEdgarClient.fetchXbrlFactsForFiling(cik, filing.get().accessionNumber());
        if (!xbrlFacts.isEmpty()) {
            eventIds.add(recordValidated(filing.get(), xbrlFacts));
        }
        return List.copyOf(eventIds);
    }

    String recordReceived(SecFiling filing) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", filing.cik());
        data.put("companyName", filing.companyName());
        data.put("form", filing.form());
        data.put("filingDate", filing.filingDate());
        data.put("reportDate", filing.reportDate());
        data.put("accessionNumber", filing.accessionNumber());
        data.put("primaryDocumentUrl", filing.primaryDocumentUrl());

        return stage(filing.cik(), RECEIVED_EVENT_TYPE, data);
    }

    String recordValidated(SecFiling filing, Map<String, Long> xbrlFacts) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", filing.cik());
        data.put("companyName", filing.companyName());
        data.put("form", filing.form());
        data.put("reportDate", filing.reportDate());
        data.put("accessionNumber", filing.accessionNumber());
        data.put("facts", xbrlFacts);

        return stage(filing.cik(), VALIDATED_EVENT_TYPE, data);
    }

    private String stage(String cik, String eventType, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        JsonEventEnvelope envelope = new JsonEventEnvelope(eventId, eventType, eventTime, "COUNTERPARTY", cik, data);
        String payloadJson = toJson(envelope);

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Counterparty", cik, eventType, EVENT_VERSION, cik, TARGET_TOPIC, payloadJson, null);
        outboxEventRepository.save(outboxEvent);
        return eventId;
    }

    private String toJson(JsonEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonEventEnvelope", e);
        }
    }
}
