package org.ewsfi.coreregistry.facility;

/**
 * REST request shape for {@link FacilityController}, mirroring the fields of
 * schemas/events/payloads/facility-outstanding-changed-v1.avsc.
 */
public record FacilityOutstandingChangedRequest(
        String counterpartyId, double currentOutstanding, String currency, String asOfDate) {
}
