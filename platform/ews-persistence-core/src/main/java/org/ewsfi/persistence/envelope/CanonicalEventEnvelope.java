package org.ewsfi.persistence.envelope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Durable landing row for a governed event, per {@code canonical_event_envelope} in
 * db/migration/V1__init_phase1_baseline.sql, itself derived from
 * schemas/events/canonical-event-envelope-v1.avsc. Distinct from the transient
 * {@code org.ewsfi.platform.outbox.OutboxEvent} row that exists only until Kafka publication
 * succeeds -- this table is the durable replay/audit record.
 */
@Entity
@Table(name = "canonical_event_envelope")
public class CanonicalEventEnvelope {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private String eventVersion;

    @Column(name = "producer_service", nullable = false)
    private String producerService;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    @Column(name = "effective_time")
    private OffsetDateTime effectiveTime;

    @Column(name = "knowledge_time", nullable = false)
    private OffsetDateTime knowledgeTime;

    @Column(name = "ingested_at", nullable = false)
    private OffsetDateTime ingestedAt;

    @Column(name = "jurisdiction", length = 2)
    private String jurisdiction;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "causation_id")
    private String causationId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "sensitivity", nullable = false)
    private String sensitivity;

    @Column(name = "contains_pii", nullable = false)
    private boolean containsPii;

    @Column(name = "execution_mode", nullable = false)
    private String executionMode = "LIVE";

    protected CanonicalEventEnvelope() {
        // JPA
    }

    public CanonicalEventEnvelope(
            UUID eventId,
            String eventType,
            String eventVersion,
            String producerService,
            String entityType,
            String entityId,
            String partitionKey,
            OffsetDateTime eventTime,
            OffsetDateTime knowledgeTime,
            OffsetDateTime ingestedAt,
            String sourceSystem,
            String sensitivity,
            boolean containsPii) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.producerService = producerService;
        this.entityType = entityType;
        this.entityId = entityId;
        this.partitionKey = partitionKey;
        this.eventTime = eventTime;
        this.knowledgeTime = knowledgeTime;
        this.ingestedAt = ingestedAt;
        this.sourceSystem = sourceSystem;
        this.sensitivity = sensitivity;
        this.containsPii = containsPii;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public OffsetDateTime getKnowledgeTime() {
        return knowledgeTime;
    }

    public void setEffectiveTime(OffsetDateTime effectiveTime) {
        this.effectiveTime = effectiveTime;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public void setCausationId(String causationId) {
        this.causationId = causationId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTraceId() {
        return traceId;
    }
}
