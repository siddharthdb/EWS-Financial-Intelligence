package org.ewsfi.coreregistry.facility;

/**
 * REST request shape for {@link FacilityController}, mirroring the fields of
 * schemas/events/payloads/facility-limit-changed-v1.avsc.
 */
public record FacilityLimitChangedRequest(
        String counterpartyId, double currentLimit, String currency, String asOfDate) {
}
