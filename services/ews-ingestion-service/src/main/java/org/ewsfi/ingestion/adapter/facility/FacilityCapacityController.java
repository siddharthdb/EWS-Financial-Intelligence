package org.ewsfi.ingestion.adapter.facility;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stand-in triggers for facility capacity and outstanding. Not a loan-origination integration. */
@RestController
@RequestMapping("/api/v1/internal/facilities")
public class FacilityCapacityController {

    private final FacilityCapacityAdapter adapter;

    public FacilityCapacityController(FacilityCapacityAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping("/limits")
    public ResponseEntity<Map<String, String>> recordLimit(@RequestBody FacilityCapacityRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", adapter.recordLimit(request)));
    }

    @PostMapping("/outstandings")
    public ResponseEntity<Map<String, String>> recordOutstanding(@RequestBody FacilityOutstandingRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("eventId", adapter.recordOutstanding(request)));
    }
}
