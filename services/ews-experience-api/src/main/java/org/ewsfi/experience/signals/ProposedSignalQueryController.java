package org.ewsfi.experience.signals;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * View proposed signals and their evidence drill-down, per
 * docs/architecture/01-architecture-blueprint.md Section 16 ("Human-in-the-Loop": Explanation ->
 * Signal -> Rule/model/correlation output -> Features -> Observations -> Evidence -> Original
 * source). No query handlers implemented yet.
 */
@RestController
@RequestMapping("/api/v1/proposed-signals")
public class ProposedSignalQueryController {
}
