package org.ewsfi.platform.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a batch of {@code NEW} outbox rows for publication without contending with other
 * publisher instances, per ADR-003 ("Producer reliability and transactional outbox") and
 * docs/architecture/03b-outbox-reference-design.md's row-claiming guidance. Uses a single
 * {@code UPDATE ... WHERE event_id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING *} statement so
 * the select-and-claim is atomic and lock-free across concurrent publisher instances.
 */
@Component
public class OutboxClaimStrategy {

    private static final String CLAIM_SQL =
            """
            UPDATE outbox_event
            SET status = 'CLAIMED', publish_attempts = publish_attempts + 1, last_attempt_at = now()
            WHERE event_id IN (
                SELECT event_id FROM outbox_event
                WHERE status = 'NEW' AND available_at <= :now
                ORDER BY created_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Claims up to {@code maxBatchSize} rows in their own short transaction (separate from Kafka
     * publication, per ADR-003: "a short claim/lease transaction claims rows, Kafka publication
     * occurs outside DB row-lock holding").
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimBatch(int maxBatchSize) {
        return entityManager
                .createNativeQuery(CLAIM_SQL, OutboxEvent.class)
                .setParameter("now", Instant.now())
                .setParameter("batchSize", maxBatchSize)
                .getResultList();
    }
}
