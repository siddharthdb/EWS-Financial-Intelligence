package org.ewsfi.contracts.interim;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Logical JSON document stored in the outbox and rebuilt after Avro decode. The Kafka value is the
 * governed Avro payload ({@code AvroPayloadCodec}); this type is not the wire format.
 *
 * <p>Deliberately much smaller than the full canonical envelope in
 * schemas/events/canonical-event-envelope-v1.avsc. Event time and entity type travel in Kafka
 * headers beside the Avro payload. The durable {@code canonical_event_envelope} table remains the
 * source of the full governed envelope for replay and audit.
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
