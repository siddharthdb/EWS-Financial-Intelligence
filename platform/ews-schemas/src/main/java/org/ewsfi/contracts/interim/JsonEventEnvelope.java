package org.ewsfi.contracts.interim;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Interim JSON wire envelope shared by the outbox publisher and Kafka Streams consumers for the
 * Phase-1 payment-return slice, while the platform's Kafka wire format is JSON rather than the
 * documented Avro + Schema Registry target (docs/architecture/03-event-architecture.md Section 10,
 * ADR-011). Tracked as roadmap item 1.17 in docs/architecture/08-roadmap-progress-tracker.md.
 *
 * <p>Deliberately much smaller than the full canonical envelope in
 * schemas/events/canonical-event-envelope-v1.avsc -- only the fields the current slice's feature
 * and signal topologies actually need (entity identity, event time, and the domain payload) are
 * carried on the wire; the durable {@code canonical_event_envelope} table (populated at ingestion
 * time by {@code ews-persistence-core}) remains the source of the full governed envelope for
 * replay/audit.
 */
public class JsonEventEnvelope {

    private String eventId;
    private String eventType;
    private String eventTime;
    private String entityType;
    private String entityId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> data;

    public JsonEventEnvelope() {
        // Jackson
    }

    public JsonEventEnvelope(
            String eventId,
            String eventType,
            String eventTime,
            String entityType,
            String entityId,
            Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.entityType = entityType;
        this.entityId = entityId;
        this.data = data;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
