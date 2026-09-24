package org.ewsfi.coreregistry.facility;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.ewsfi.contracts.interim.JsonEventEnvelope;
import org.ewsfi.coreregistry.outbox.CanonicalEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records {@code facility.limit.changed} and {@code facility.outstanding.changed} observations
 * (docs/architecture/03d-phase1-event-catalogue.md Section 3) and stages them via the outbox to
 * {@code ews.canonical.facility}, keyed by {@code facilityId}, per ADR-003. Feeds
 * {@code wc_utilization_ratio} (docs/architecture/02d-phase1-feature-catalogue.md Section 1) and
 * P05 UTILIZATION_HIGH (docs/architecture/02a-priority-signal-contracts.md), per roadmap item 1.14
 * (docs/architecture/08-roadmap-progress-tracker.md).
 *
 * <p>Both event types are recorded by one adapter class, unlike the single-event-type adapters in
 * {@code ews-ingestion-service}, since they share identical staging logic and this service's
 * facility aggregate is the natural place to keep them together. No real limit-management/loan
 * origination system integration exists yet -- these are invoked today only via
 * {@link FacilityController}'s REST endpoints, the same documented stand-in pattern used by every
 * other Phase-1 ingestion adapter.
 */
@Component
public class FacilityEventAdapter {

    /** Matches the ews.canonical.facility row in 03c-topic-and-partition-strategy.md Section 2. */
    private static final String TARGET_TOPIC = "ews.canonical.facility";

    private static final String LIMIT_CHANGED_EVENT_TYPE = "facility.limit.changed";
    private static final String OUTSTANDING_CHANGED_EVENT_TYPE = "facility.outstanding.changed";
    private static final String EVENT_VERSION = "v1";

    private final CanonicalEventPublisher publisher;

    public FacilityEventAdapter(CanonicalEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Transactional
    public String recordLimitChanged(String facilityId, FacilityLimitChangedRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", request.counterpartyId());
        data.put("currentLimit", request.currentLimit());
        data.put("currency", request.currency());
        data.put("asOfDate", request.asOfDate());

        return publish(facilityId, LIMIT_CHANGED_EVENT_TYPE, data);
    }

    @Transactional
    public String recordOutstandingChanged(String facilityId, FacilityOutstandingChangedRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("facilityId", facilityId);
        data.put("counterpartyId", request.counterpartyId());
        data.put("currentOutstanding", request.currentOutstanding());
        data.put("currency", request.currency());
        data.put("asOfDate", request.asOfDate());

        return publish(facilityId, OUTSTANDING_CHANGED_EVENT_TYPE, data);
    }

    private String publish(String facilityId, String eventType, Map<String, Object> data) {
        String eventId = UUID.randomUUID().toString();
        String eventTime = Instant.now().toString();

        JsonEventEnvelope envelope =
                new JsonEventEnvelope(eventId, eventType, eventTime, "FACILITY", facilityId, data);

        publisher.publish("Facility", facilityId, eventType, EVENT_VERSION, facilityId, TARGET_TOPIC, envelope);
        return eventId;
    }
}
