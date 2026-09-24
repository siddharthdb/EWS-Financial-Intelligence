package org.ewsfi.platform.outbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Empty Spring Data shell. Claim-and-lease query (per ADR-003, e.g. {@code SELECT ... FOR UPDATE
 * SKIP LOCKED}) is intentionally not implemented yet -- see {@link OutboxClaimStrategy}.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
