package org.ewsfi.platform.outbox;

/**
 * Lifecycle states for a row in {@code outbox_event}, per
 * docs/architecture/adr/ADR-003-application-managed-transactional-outbox.md.
 * CLAIMED is a transient lease state, not a persisted terminal status.
 */
public enum OutboxEventStatus {
    NEW,
    CLAIMED,
    PUBLISHED,
    FAILED
}
