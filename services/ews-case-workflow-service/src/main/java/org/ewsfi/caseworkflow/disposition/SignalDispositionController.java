package org.ewsfi.caseworkflow.disposition;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analyst accept/reject endpoint shell for a proposed signal, per
 * docs/architecture/02-canonical-risk-model.md Section 12 ("Signal lifecycle"). Recording an
 * immutable {@code signal.disposition.recorded} event
 * (schemas/events/payloads/signal-disposition-recorded-v1.avsc) is not implemented yet.
 */
@RestController
@RequestMapping("/api/v1/signals")
public class SignalDispositionController {
}
