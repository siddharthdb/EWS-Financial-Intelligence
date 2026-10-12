package org.ewsfi.ingestion.adapter.facility;

public record FacilityCapacityRequest(
        String facilityId,
        String counterpartyId,
        String sanctionedLimit,
        String currency,
        String capacityRule) {}
