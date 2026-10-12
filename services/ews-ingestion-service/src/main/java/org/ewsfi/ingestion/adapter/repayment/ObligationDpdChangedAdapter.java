package org.ewsfi.ingestion.adapter.repayment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an {@code obligation.dpd.changed} observation
 * (schemas/events/payloads/obligation-dpd-changed-v1.avsc) and stages it on
 * {@code ews.canonical.repayment}, keyed by {@code facilityId}, in the same local transaction as
 * the business write, per ADR-003.
 *
 * <p>The REST controller that calls this is a stand-in for the servicing/LMS schedule feed. It is
 * not that integration.
 */
@Component
public class ObligationDpdChangedAdapter {

    static final String TARGET_TOPIC = "ews.canonical.repayment";
    static final String EVENT_TYPE = "obligation.dpd.changed";
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
        String eventId = UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", request.facilityId());
        data.put("counterpartyId", request.counterpartyId());
        data.put("obligationId", request.obligationId());
        data.put("previousDpd", request.previousDpd());
        data.put("currentDpd", request.currentDpd());
        data.put("earliestUnpaidDueDate", request.earliestUnpaidDueDate());
        data.put("asOfDate", request.asOfDate());
        data.put("calculationPolicyRef", request.calculationPolicyRef());
        data.put("sourceScheduleVersion", request.sourceScheduleVersion());
        data.put("reconciled", request.reconciled());

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        eventId, EVENT_TYPE, eventTime, "FACILITY", request.facilityId(), data);

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Facility",
                        request.facilityId(),
                        EVENT_TYPE,
                        EVENT_VERSION,
                        request.facilityId(),
                        TARGET_TOPIC,
                        toJson(envelope),
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
