package org.ewsfi.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Proves, against real Postgres, the claim-query half of the retry mechanism
 * {@link OutboxEvent#markFailed} implements (roadmap item 3.6, production hardening): a row
 * retried after a transient failure (status back to {@code NEW}, a future {@code availableAt}) is
 * correctly excluded from claiming until its backoff elapses, then correctly reclaimed once it has
 * -- and a row that has permanently failed ({@code FAILED}) is never reclaimed at all. Complements
 * {@link OutboxEventTest} (which tests the entity's retry-vs-permanent decision in isolation) by
 * testing the actual {@code WHERE status = 'NEW' AND available_at <= :now} SQL this depends on.
 */
@SpringBootTest(classes = OutboxTestApplication.class)
@EmbeddedKafka(partitions = 1, topics = {"test.ews.claim-strategy"})
@DirtiesContext
class OutboxClaimStrategyTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/ews";

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxClaimStrategy claimStrategy;

    @BeforeAll
    static void requirePostgres() {
        boolean reachable;
        try (Connection ignored = DriverManager.getConnection(JDBC_URL, "ews", "ews")) {
            reachable = true;
        } catch (SQLException e) {
            reachable = false;
        }
        assumeTrue(reachable, "Postgres not reachable at " + JDBC_URL + "; skipping integration test");
    }

    @Test
    void aRowRetriedAfterAFailureIsNotClaimedUntilItsBackoffElapsesThenIs() {
        OutboxEvent event = saveNewEvent();

        // Simulate the first publish attempt failing: claim it once (increments publishAttempts to
        // 1, well under MAX_PUBLISH_ATTEMPTS), then mark it failed -- markFailed should send it
        // back to NEW with a future availableAt, per the fixed retry behavior.
        List<OutboxEvent> firstClaim = claimStrategy.claimBatch(10);
        assertThat(firstClaim).extracting(OutboxEvent::getEventId).contains(event.getEventId());

        OutboxEvent claimed = repository.findById(event.getEventId()).orElseThrow();
        claimed.markFailed("SimulatedTimeout", "simulated transient failure");
        repository.save(claimed);
        repository.flush();

        OutboxEvent afterFailure = repository.findById(event.getEventId()).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(afterFailure.getAvailableAt()).isAfter(Instant.now());

        // Not yet reclaimable: its backoff window hasn't elapsed.
        List<OutboxEvent> tooSoon = claimStrategy.claimBatch(10);
        assertThat(tooSoon).extracting(OutboxEvent::getEventId).doesNotContain(event.getEventId());

        // Simulate the backoff having elapsed by moving availableAt into the past directly
        // (real time would require sleeping past RETRY_BACKOFF, which this test avoids for speed).
        OutboxEvent stillPending = repository.findById(event.getEventId()).orElseThrow();
        backdateAvailableAt(stillPending.getEventId());

        List<OutboxEvent> secondClaim = claimStrategy.claimBatch(10);
        assertThat(secondClaim).extracting(OutboxEvent::getEventId).contains(event.getEventId());

        OutboxEvent reclaimed = repository.findById(event.getEventId()).orElseThrow();
        assertThat(reclaimed.getPublishAttempts()).isEqualTo(2);
    }

    @Test
    void aPermanentlyFailedRowIsNeverReclaimed() {
        OutboxEvent event = saveNewEvent();

        // Drive publishAttempts up to MAX_PUBLISH_ATTEMPTS via repeated claim+fail cycles, then
        // fail once more so markFailed treats it as permanent.
        for (int i = 0; i < OutboxEvent.MAX_PUBLISH_ATTEMPTS; i++) {
            claimStrategy.claimBatch(10);
            OutboxEvent claimed = repository.findById(event.getEventId()).orElseThrow();
            claimed.markFailed("SimulatedTimeout", "simulated transient failure");
            repository.save(claimed);
            repository.flush();
            if (claimed.getStatus() == OutboxEventStatus.NEW) {
                backdateAvailableAt(event.getEventId());
            }
        }

        OutboxEvent finalState = repository.findById(event.getEventId()).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(OutboxEventStatus.FAILED);

        List<OutboxEvent> afterPermanentFailure = claimStrategy.claimBatch(10);
        assertThat(afterPermanentFailure).extracting(OutboxEvent::getEventId).doesNotContain(event.getEventId());
    }

    private OutboxEvent saveNewEvent() {
        OutboxEvent event =
                OutboxEvent.newEvent(
                        "Account",
                        "acct-retry-test-" + UUID.randomUUID(),
                        "payment.instruction.returned",
                        "v1",
                        "acct-retry-test",
                        "test.ews.claim-strategy",
                        "{}",
                        null);
        return repository.save(event);
    }

    private void backdateAvailableAt(UUID eventId) {
        OutboxEvent event = repository.findById(eventId).orElseThrow();
        try {
            var field = OutboxEvent.class.getDeclaredField("availableAt");
            field.setAccessible(true);
            field.set(event, Instant.now().minusSeconds(60));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        repository.save(event);
        repository.flush();
    }
}
