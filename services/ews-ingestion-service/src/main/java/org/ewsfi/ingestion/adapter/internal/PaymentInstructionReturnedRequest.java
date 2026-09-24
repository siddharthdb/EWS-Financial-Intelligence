package org.ewsfi.ingestion.adapter.internal;

/**
 * REST request shape for {@link PaymentReturnIngestionController}, mirroring the fields of
 * schemas/events/payloads/payment-instruction-returned-v1.avsc that this Phase-1 slice needs.
 * {@code returnReasonCategory} must be one of the Avro enum's symbols
 * ({@code FINANCIAL|TECHNICAL|BENEFICIARY_DETAIL|CUSTOMER_INSTRUCTION|REGULATORY|OTHER|UNKNOWN}).
 */
public record PaymentInstructionReturnedRequest(
        String paymentInstructionId,
        String accountId,
        String amount,
        String currency,
        String returnReasonCode,
        String returnReasonCategory,
        String returnDate) {
}
