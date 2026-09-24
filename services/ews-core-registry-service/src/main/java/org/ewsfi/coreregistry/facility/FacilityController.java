package org.ewsfi.coreregistry.facility;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facility endpoints, per docs/architecture/02-canonical-risk-model.md Section 3 core ontology
 * (COUNTERPARTY -> FACILITY). Stands in for the internal limit-management/loan origination system
 * that would emit these observations natively in production, mirroring the documented stand-in
 * pattern used by every other Phase-1 ingestion adapter.
 */
@RestController
@RequestMapping("/api/v1/facilities")
public class FacilityController {

    private final FacilityEventAdapter adapter;

    public FacilityController(FacilityEventAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/{facilityId}/limit")
    public ResponseEntity<Map<String, String>> recordLimitChanged(
            @PathVariable("facilityId") String facilityId, @RequestBody FacilityLimitChangedRequest request) {
        String eventId = adapter.recordLimitChanged(facilityId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", eventId));
    }

    @PostMapping("/{facilityId}/outstanding")
    public ResponseEntity<Map<String, String>> recordOutstandingChanged(
            @PathVariable("facilityId") String facilityId,
            @RequestBody FacilityOutstandingChangedRequest request) {
        String eventId = adapter.recordOutstandingChanged(facilityId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", eventId));
    }
}
