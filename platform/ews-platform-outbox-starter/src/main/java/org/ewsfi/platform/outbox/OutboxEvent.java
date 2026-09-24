package org.ewsfi.platform.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable outbox row shape per ADR-003 "Required guarantees": {@code eventId} is generated once
 * before commit and never changes; the business mutation and this insert occur in the same local
 * database transaction. Field set mirrors the {@code outbox_event} table in
 * db/migration/V1__init_phase1_baseline.sql. Skeleton only -- no persistence behavior wired yet.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID eventId;

    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String eventVersion;
    private String partitionKey;

    // JSON payload/headers, canonical envelope fields, retry/publication bookkeeping intentionally
    // omitted from this skeleton entity -- to be added when the publisher worker is implemented.

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status;

    private Instant createdAt;
    private Instant availableAt;
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }
}
