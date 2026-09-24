package org.ewsfi.coreregistry.facility;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Facility endpoint shell, per docs/architecture/02-canonical-risk-model.md Section 3 core ontology
 * (COUNTERPARTY -> FACILITY). No handlers implemented yet.
 */
@RestController
@RequestMapping("/api/v1/facilities")
public class FacilityController {
}
