package org.ewsfi.ingestion.adapter.internal;

/**
 * REST request shape for {@link ObligationDpdChangedController}, mirroring the fields of
 * schemas/events/payloads/obligation-dpd-changed-v1.avsc that this Phase-1 slice needs.
 */
public record ObligationDpdChangedRequest(
        String facilityId,
        String counterpartyId,
        Integer previousDpd,
        int currentDpd,
        String asOfDate) {
}
