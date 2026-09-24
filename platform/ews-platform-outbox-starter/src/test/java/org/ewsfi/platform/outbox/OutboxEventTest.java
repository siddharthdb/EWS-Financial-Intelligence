package org.ewsfi.platform.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test (no Spring/Kafka/Postgres) proving {@link OutboxEvent#markFailed} retries a
 * transient failure rather than permanently stranding the row -- the bug fixed as part of roadmap
 * item 3.6 (production hardening: failure tests). See {@link OutboxEvent}'s class Javadoc for the
 * bug this test guards against.
 */
class OutboxEventTest {

    @Test
    void aFailureBelowTheMaxAttemptsGoesBackToNewWithAFutureAvailableAt() throws Exception {
        OutboxEvent event = newEventWithAttempts(1);
        Instant before = Instant.now();

        event.markFailed("TimeoutException", "send timed out");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
        assertThat(event.getAvailableAt()).isAfter(before);
        assertThat(event.getLastErrorCode()).isEqualTo("TimeoutException");
    }

    @Test
    void aFailureAtTheMaxAttemptsBecomesPermanentlyFailed() throws Exception {
        OutboxEvent event = newEventWithAttempts(OutboxEvent.MAX_PUBLISH_ATTEMPTS);

        event.markFailed("TimeoutException", "send timed out");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    }

    @Test
    void aFailureJustBelowTheMaxAttemptsStillRetries() throws Exception {
        OutboxEvent event = newEventWithAttempts(OutboxEvent.MAX_PUBLISH_ATTEMPTS - 1);

        event.markFailed("TimeoutException", "send timed out");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.NEW);
    }

    /**
     * {@code publishAttempts} is incremented by {@link OutboxClaimStrategy}'s claim SQL before
     * {@code markFailed} is ever called, so this helper simulates "the row has already been claimed
     * {@code attempts} times" via reflection, since the entity has no public setter for it (by
     * design -- production code only mutates it through the claim query).
     */
    private static OutboxEvent newEventWithAttempts(int attempts) throws Exception {
        OutboxEvent event =
                OutboxEvent.newEvent(
                        "Account", "acct-1", "payment.instruction.returned", "v1", "acct-1",
                        "ews.canonical.account-transaction", "{}", null);
        Field field = OutboxEvent.class.getDeclaredField("publishAttempts");
        field.setAccessible(true);
        field.set(event, attempts);
        return event;
    }
}
