package org.ewsfi.ingestion.adapter.internal;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for the internal LMS/CBS system that would, in production, emit
 * {@code obligation.dpd.changed} observations natively, mirroring
 * {@link PaymentReturnIngestionController}'s documented stand-in pattern.
 */
@RestController
@RequestMapping("/api/v1/internal/obligation-dpd-changes")
public class ObligationDpdChangedController {

    private final ObligationDpdChangedAdapter adapter;

    public ObligationDpdChangedController(ObligationDpdChangedAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> recordDpdChange(
            @RequestBody ObligationDpdChangedRequest request) {
        String eventId = adapter.record(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", eventId));
    }
}
