package org.ewsfi.ingestion.adapter.external.sec;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 */
@Component
public class SecFilingIngestionAdapter {

    /** Matches the ews.canonical.financial-statement row in 03c-topic-and-partition-strategy.md Section 2. */
    private static final String TARGET_TOPIC = "ews.canonical.financial-statement";

    private static final String EVENT_TYPE = "financial.statement.received";
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
     * if one exists, stages a {@code financial.statement.received} outbox event in the same local
     * transaction. Returns the staged event's id, or empty if the company has no qualifying filing
     * in SEC's "recent" window.
     */
    @Transactional
    public Optional<String> syncMostRecentPeriodicStatement(String cik) throws IOException, InterruptedException {
        Optional<SecFiling> filing = secEdgarClient.fetchMostRecentPeriodicStatement(cik);
        if (filing.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(record(filing.get()));
    }

    String record(SecFiling filing) {
        String eventId = UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cik", filing.cik());
        data.put("companyName", filing.companyName());
        data.put("form", filing.form());
        data.put("filingDate", filing.filingDate());
        data.put("reportDate", filing.reportDate());
        data.put("accessionNumber", filing.accessionNumber());
        data.put("primaryDocumentUrl", filing.primaryDocumentUrl());

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(eventId, EVENT_TYPE, eventTime, "COUNTERPARTY", filing.cik(), data);

        String payloadJson = toJson(envelope);

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Counterparty",
                        filing.cik(),
                        EVENT_TYPE,
                        EVENT_VERSION,
                        filing.cik(),
                        TARGET_TOPIC,
                        payloadJson,
                        null);
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
