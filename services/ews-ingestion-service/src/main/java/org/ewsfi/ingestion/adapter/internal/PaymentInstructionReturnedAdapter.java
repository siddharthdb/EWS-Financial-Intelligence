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
 * Records a {@code payment.instruction.returned} observation
 * (schemas/events/payloads/payment-instruction-returned-v1.avsc) and stages it for publication via
 * the outbox, in the same local transaction, per ADR-003. Feeds
 * docs/architecture/02a-priority-signal-contracts.md P03 (REPEATED_PAYMENT_RETURN) / P04
 * (HIGH_VALUE_PAYMENT_RETURN) via {@code returned_payment_count_30d}
 * (docs/architecture/02d-phase1-feature-catalogue.md Section 1).
 *
 * <p>No real internal payment-system integration exists yet -- this adapter is invoked today only
 * via {@link PaymentReturnIngestionController}'s REST endpoint, which stands in for "the internal
 * source that would call this in production" per docs/architecture/06-gap-analysis-and-implementation-roadmap.md
 * Section 7. Real source integration (CDC, native event stream, or API) is future work.
 */
@Component
public class PaymentInstructionReturnedAdapter {

    /** Matches the ews.canonical.account-transaction row in 03c-topic-and-partition-strategy.md Section 2. */
    private static final String TARGET_TOPIC = "ews.canonical.account-transaction";

    private static final String EVENT_TYPE = "payment.instruction.returned";
    private static final String EVENT_VERSION = "v1";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentInstructionReturnedAdapter(
            OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String record(PaymentInstructionReturnedRequest request) {
        String eventId = java.util.UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentInstructionId", request.paymentInstructionId());
        data.put("accountId", request.accountId());
        data.put("amount", request.amount());
        data.put("currency", request.currency());
        data.put("returnReasonCode", request.returnReasonCode());
        data.put("returnReasonCategory", request.returnReasonCategory());
        data.put("returnDate", request.returnDate());

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(
                        eventId, EVENT_TYPE, eventTime, "ACCOUNT", request.accountId(), data);

        String payloadJson = toJson(envelope);

        OutboxEvent outboxEvent =
                OutboxEvent.newEvent(
                        "Account",
                        request.accountId(),
                        EVENT_TYPE,
                        EVENT_VERSION,
                        request.accountId(),
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
