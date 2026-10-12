package org.ewsfi.ingestion.adapter.repayment;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for the loan-servicing schedule feed that would emit {@code obligation.dpd.changed}
 * natively. This endpoint is the Phase-1 DPD slice's trigger, not a production LMS adapter.
 */
@RestController
@RequestMapping("/api/v1/internal/obligation-dpd")
public class ObligationDpdIngestionController {

    private final ObligationDpdChangedAdapter adapter;

    public ObligationDpdIngestionController(ObligationDpdChangedAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> recordDpdChange(@RequestBody ObligationDpdChangedRequest request) {
        String eventId = adapter.record(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", eventId));
    }
}
