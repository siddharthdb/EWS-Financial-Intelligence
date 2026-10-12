package org.ewsfi.ingestion.adapter.facility;

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
 * Stand-in for the servicing system that would emit {@code facility.limit.changed} and
 * {@code facility.outstanding.changed}. Both facts land on {@code ews.canonical.facility}, keyed
 * by facility id, in the same transaction as the outbox row.
 */
@Component
public class FacilityCapacityAdapter {

    static final String TARGET_TOPIC = "ews.canonical.facility";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public FacilityCapacityAdapter(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String recordLimit(FacilityCapacityRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", request.facilityId());
        data.put("counterpartyId", request.counterpartyId());
        data.put("sanctionedLimit", request.sanctionedLimit());
        data.put("currency", request.currency());
        data.put("capacityRule", request.capacityRule() == null ? "SANCTIONED_LIMIT" : request.capacityRule());
        return stage("facility.limit.changed", request.facilityId(), data);
    }

    @Transactional
    public String recordOutstanding(FacilityOutstandingRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", request.facilityId());
        data.put("counterpartyId", request.counterpartyId());
        data.put("eligibleOutstanding", request.eligibleOutstanding());
        data.put("currency", request.currency());
        return stage("facility.outstanding.changed", request.facilityId(), data);
    }

    private String stage(String eventType, String facilityId, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        JsonEventEnvelope envelope =
                new JsonEventEnvelope(eventId, eventType, Instant.now().toString(), "FACILITY", facilityId, data);
        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Facility",
                        facilityId,
                        eventType,
                        "v1",
                        facilityId,
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
            throw new IllegalStateException("Failed to serialize facility event", e);
        }
    }
}
