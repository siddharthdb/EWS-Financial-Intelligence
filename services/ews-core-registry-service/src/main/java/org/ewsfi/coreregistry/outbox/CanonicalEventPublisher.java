package org.ewsfi.coreregistry.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.platform.outbox.OutboxEvent;
import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * Shared helper that stages a canonical domain event via the outbox (ADR-003), used by every
 * {@code ews-core-registry-service} adapter that records a counterparty/facility state change.
 * Mirrors the single-adapter staging logic in {@code ews-ingestion-service}'s
 * {@code PaymentInstructionReturnedAdapter}/{@code ObligationDpdChangedAdapter}, factored out here
 * since this service owns multiple facility event types (facility.limit.changed,
 * facility.outstanding.changed) that all stage the same way.
 */
@Component
public class CanonicalEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public CanonicalEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes {@code envelope} and stages it as a {@code NEW} outbox row in the caller's
     * transaction. The caller is responsible for having its own record method annotated
     * {@code @Transactional} so this staging happens atomically with the business write it
     * accompanies.
     */
    public void publish(
            String aggregateType,
            String aggregateId,
            String eventType,
            String eventVersion,
            String partitionKey,
            String kafkaTopic,
            JsonEventEnvelope envelope) {
        String payloadJson = toJson(envelope);
        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        aggregateType, aggregateId, eventType, eventVersion, partitionKey, kafkaTopic, payloadJson, null);
        outboxEventRepository.save(outboxEvent);
    }

    private String toJson(JsonEventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JsonEventEnvelope", e);
        }
    }
}
