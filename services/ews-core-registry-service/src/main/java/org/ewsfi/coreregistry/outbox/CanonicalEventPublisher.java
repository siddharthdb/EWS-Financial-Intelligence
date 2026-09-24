package org.ewsfi.coreregistry.outbox;

import org.ewsfi.platform.outbox.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * Depends on {@code ews-platform-outbox-starter} to emit canonical domain events for counterparty/
 * facility state changes within the same local transaction as the business mutation (ADR-003).
 * No publication logic implemented yet.
 */
@Component
public class CanonicalEventPublisher {

    private final OutboxEventRepository outboxEventRepository;

    public CanonicalEventPublisher(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }
}
