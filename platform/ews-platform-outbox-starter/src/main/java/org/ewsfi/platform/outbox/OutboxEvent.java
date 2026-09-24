package org.ewsfi.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Outbox row shape per ADR-003 "Required guarantees": {@code eventId} is generated once before
 * commit and never changes; the business mutation and this insert occur in the same local database
 * transaction. Field set mirrors the {@code outbox_event} table in
 * db/migration/V1__init_phase1_baseline.sql.
 *
 * <p>{@code payload} and {@code headers} are stored as JSON (Postgres {@code jsonb}), not Avro
 * binary. This is a deliberate, documented interim simplification for the Phase-1 payment-return
 * slice (docs/architecture/07-build-log.md, 2026-09-24 entry) -- the target production wire format
 * remains Avro + Schema Registry per docs/architecture/03-event-architecture.md Section 10 and
 * ADR-011; switching to it is tracked as roadmap item 1.17 in
 * docs/architecture/08-roadmap-progress-tracker.md.
 *
 * <p>{@link #markFailed} retries a failed publish (row returns to {@code NEW} with a future
 * {@code availableAt}) up to {@link #MAX_PUBLISH_ATTEMPTS} times before treating it as a permanent,
 * terminal {@code FAILED} -- a real bug in the original implementation, found and fixed while
 * building the first production-hardening (roadmap 3.6) failure test: {@code markFailed} always set
 * status to the terminal {@code FAILED}, and {@link OutboxClaimStrategy}'s claim query only ever
 * selects {@code status = 'NEW'} rows, so a single transient Kafka publish failure (a broker
 * hiccup, a timeout) permanently stranded the event with no retry, contrary to ADR-003's
 * at-least-once delivery guarantee.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    /**
     * Maximum publish attempts (including the current one, already incremented by
     * {@link OutboxClaimStrategy}'s claim query before this row reaches {@link #markFailed}) before
     * a failure is treated as permanent rather than retried. Roadmap item 3.6 (production
     * hardening): a transient Kafka publish failure must not permanently strand an event -- see
     * this class's and {@link OutboxPublisherWorker}'s Javadoc for the bug this fixed.
     */
    public static final int MAX_PUBLISH_ATTEMPTS = 5;

    /** Fixed backoff before a retryable failure becomes reclaimable again. A documented
     * simplification: true exponential backoff would need per-attempt-count scaling, not
     * implemented here since the scheduled publisher already polls every second regardless. */
    static final Duration RETRY_BACKOFF = Duration.ofSeconds(5);

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "aggregate_sequence")
    private Long aggregateSequence;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private String eventVersion;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "publish_attempts", nullable = false)
    private int publishAttempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "kafka_topic")
    private String kafkaTopic;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    protected OutboxEvent() {
        // JPA
    }

    /**
     * Creates a new, unpublished outbox row. {@code eventId} is generated here, per ADR-003's
     * requirement that it never change across retries.
     */
    public static OutboxEvent newEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String eventVersion,
            String partitionKey,
            String kafkaTopic,
            String payloadJson,
            String headersJson) {
        OutboxEvent event = new OutboxEvent();
        event.eventId = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.partitionKey = partitionKey;
        event.kafkaTopic = kafkaTopic;
        event.payload = payloadJson;
        event.headers = headersJson;
        event.status = OutboxEventStatus.NEW;
        Instant now = Instant.now();
        event.createdAt = now;
        event.availableAt = now;
        event.publishAttempts = 0;
        return event;
    }

    public void markPublished(int kafkaPartition, long kafkaOffset) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.kafkaPartition = kafkaPartition;
        this.kafkaOffset = kafkaOffset;
    }

    /**
     * Records a publish failure. If {@code publishAttempts} (already incremented by this attempt's
     * claim) hasn't exceeded {@link #MAX_PUBLISH_ATTEMPTS}, the row goes back to {@code NEW} with
     * {@code availableAt} pushed into the future by {@link #RETRY_BACKOFF} -- reclaimable again by
     * {@link OutboxClaimStrategy}'s {@code WHERE status = 'NEW' AND available_at <= :now} claim
     * query once the backoff elapses. Only after exhausting all attempts does the row become
     * terminally {@code FAILED} (a real dead-letter state, needing operational intervention).
     */
    public void markFailed(String errorCode, String errorMessage) {
        this.lastErrorCode = errorCode;
        this.lastErrorMessage = errorMessage;
        if (this.publishAttempts >= MAX_PUBLISH_ATTEMPTS) {
            this.status = OutboxEventStatus.FAILED;
        } else {
            this.status = OutboxEventStatus.NEW;
            this.availableAt = Instant.now().plus(RETRY_BACKOFF);
        }
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getPublishAttempts() {
        return publishAttempts;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }
}
