package org.ewsfi.ingestion.adapter.facility;

public record FacilityOutstandingRequest(
        String facilityId, String counterpartyId, String eligibleOutstanding, String currency) {}
