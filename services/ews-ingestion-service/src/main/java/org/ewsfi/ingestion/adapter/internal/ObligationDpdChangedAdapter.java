package org.ewsfi.ingestion.adapter.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an {@code obligation.dpd.changed} observation
 * (schemas/events/payloads/obligation-dpd-changed-v1.avsc) and stages it for publication via the
 * outbox, in the same local transaction, per ADR-003. Feeds
 * docs/architecture/02a-priority-signal-contracts.md P01 (DPD_EMERGED) via {@code current_dpd}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1) -- the second Phase-1 signal family
 * after payment-return, per docs/architecture/08-roadmap-progress-tracker.md item 1.12.
 *
 * <p>No real LMS/CBS integration exists yet -- this adapter is invoked today only via
 * {@link ObligationDpdChangedController}'s REST endpoint, mirroring
 * {@link PaymentInstructionReturnedAdapter}'s documented stand-in pattern.
 */
@Component
public class ObligationDpdChangedAdapter {

    /** Matches the ews.canonical.repayment row in 03c-topic-and-partition-strategy.md Section 2. */
    private static final String TARGET_TOPIC = "ews.canonical.repayment";

    private static final String EVENT_TYPE = "obligation.dpd.changed";
    private static final String EVENT_VERSION = "v1";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ObligationDpdChangedAdapter(
            OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String record(ObligationDpdChangedRequest request) {
        String eventId = java.util.UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", request.facilityId());
        data.put("counterpartyId", request.counterpartyId());
        data.put("previousDpd", request.previousDpd());
        data.put("currentDpd", request.currentDpd());
        data.put("asOfDate", request.asOfDate());

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        eventId, EVENT_TYPE, eventTime, "FACILITY", request.facilityId(), data);

        String payloadJson = toJson(envelope);

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Facility",
                        request.facilityId(),
                        EVENT_TYPE,
                        EVENT_VERSION,
                        request.facilityId(),
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
