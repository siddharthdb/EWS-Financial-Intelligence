package org.ewsfi.ingestion.adapter.repayment;

/**
 * REST request shape for {@link ObligationDpdIngestionController}, mirroring the fields of
 * schemas/events/payloads/obligation-dpd-changed-v1.avsc that the DPD slice needs.
 * {@code reconciled} false means the schedule could not be reconciled; downstream features must
 * not treat that as zero DPD.
 */
public record ObligationDpdChangedRequest(
        String facilityId,
        String counterpartyId,
        String obligationId,
        Integer previousDpd,
        Integer currentDpd,
        String earliestUnpaidDueDate,
        String asOfDate,
        String calculationPolicyRef,
        String sourceScheduleVersion,
        boolean reconciled) {
}
