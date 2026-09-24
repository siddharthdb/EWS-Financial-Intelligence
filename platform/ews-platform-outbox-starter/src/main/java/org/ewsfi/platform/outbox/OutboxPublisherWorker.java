package org.ewsfi.platform.outbox;

import org.springframework.stereotype.Component;

/**
 * Publishes claimed outbox rows to Kafka outside the row-lock-holding transaction, per
 * docs/architecture/03-event-architecture.md Section 11 ("Producer reliability and transactional
 * outbox"). Delivery is at-least-once with stable {@code eventId} identity; consumers must be
 * idempotent per ADR-003. No publish logic implemented in this skeleton.
 */
@Component
public class OutboxPublisherWorker {
}
