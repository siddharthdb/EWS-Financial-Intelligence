package org.ewsfi.platform.outbox;

import java.util.List;

/**
 * Strategy for claiming a batch of {@code NEW} outbox rows for publication without contending with
 * other publisher instances (e.g. {@code SELECT ... FOR UPDATE SKIP LOCKED}), per ADR-003. No
 * implementation yet -- this interface only fixes the contract shape for the Phase-1 skeleton.
 */
public interface OutboxClaimStrategy {

    List<OutboxEvent> claimBatch(int maxBatchSize);
}
