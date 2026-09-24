package org.ewsfi.coreregistry.counterparty;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Counterparty identity/master endpoint shell, per docs/architecture/02-canonical-risk-model.md
 * Section 4 ("Counterparty and identity aggregate"). No handlers implemented yet.
 */
@RestController
@RequestMapping("/api/v1/counterparties")
public class CounterpartyController {
}
