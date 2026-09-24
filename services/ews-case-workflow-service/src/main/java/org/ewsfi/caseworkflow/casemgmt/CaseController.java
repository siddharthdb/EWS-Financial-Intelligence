package org.ewsfi.caseworkflow.casemgmt;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case open/assign/escalate/close endpoint shell, per docs/architecture/03-event-architecture.md
 * Section 3 ("Decision/workflow events": {@code case.opened}, {@code case.assigned},
 * {@code case.escalated}, {@code case.closed}). No handlers implemented yet.
 */
@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {
}
