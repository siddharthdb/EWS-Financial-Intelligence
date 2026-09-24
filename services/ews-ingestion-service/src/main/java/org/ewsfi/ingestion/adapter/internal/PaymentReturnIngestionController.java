package org.ewsfi.ingestion.adapter.internal;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for the internal payment/core-banking system that would, in production, emit
 * {@code payment.instruction.returned} observations natively (per the acquisition-mode guidance in
 * docs/architecture/01-architecture-blueprint.md Section 4). No such integration exists yet -- this
 * endpoint is the Phase-1 payment-return slice's trigger point
 * (docs/architecture/06-gap-analysis-and-implementation-roadmap.md Section 7), documented as an
 * explicit stand-in, not a claim of real source integration.
 */
@RestController
@RequestMapping("/api/v1/internal/payment-returns")
public class PaymentReturnIngestionController {

    private final PaymentInstructionReturnedAdapter adapter;

    public PaymentReturnIngestionController(PaymentInstructionReturnedAdapter adapter) {
        this.adapter = adapter;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> recordPaymentReturn(
            @RequestBody PaymentInstructionReturnedRequest request) {
        String eventId = adapter.record(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("eventId", eventId));
    }
}
