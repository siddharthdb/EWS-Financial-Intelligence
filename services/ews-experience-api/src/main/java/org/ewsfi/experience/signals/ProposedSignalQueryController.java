package org.ewsfi.experience.signals;

import java.util.List;
import org.ewsfi.persistence.feature.FeatureValueRepository;
import org.ewsfi.persistence.signal.SignalInstance;
import org.ewsfi.persistence.signal.SignalInstanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * View proposed signals and their evidence drill-down, per
 * docs/architecture/01-architecture-blueprint.md Section 16 ("Human-in-the-Loop"). Accept/reject
 * actions live in {@code ews-case-workflow-service}, not here -- this API is read-only by design,
 * matching the read/decision separation implied by {@code EA-01} ("Commands and queries are
 * separate") in docs/architecture/03-event-architecture.md Section 2.
 */
@RestController
@RequestMapping("/api/v1/proposed-signals")
public class ProposedSignalQueryController {

    private final SignalInstanceRepository signalInstanceRepository;
    private final FeatureValueRepository featureValueRepository;

    public ProposedSignalQueryController(
            SignalInstanceRepository signalInstanceRepository,
            FeatureValueRepository featureValueRepository) {
        this.signalInstanceRepository = signalInstanceRepository;
        this.featureValueRepository = featureValueRepository;
    }

    @GetMapping
    public List<ProposedSignalView> listProposedSignals(
            @RequestParam(name = "status", defaultValue = "PROPOSED") String status) {
        return signalInstanceRepository.findByStatus(status).stream()
                .map(signal -> ProposedSignalView.from(signal, resolveEvidence(signal)))
                .toList();
    }

    @GetMapping("/{signalId}")
    public ProposedSignalView getProposedSignal(@PathVariable("signalId") String signalId) {
        SignalInstance signal =
                signalInstanceRepository
                        .findById(signalId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "No signal with id " + signalId));
        return ProposedSignalView.from(signal, resolveEvidence(signal));
    }

    /**
     * Resolves each {@code evidenceId} on the signal as a {@code feature_value} row. Silently
     * skips an evidence ID that doesn't resolve to a known feature value -- in this slice every
     * evidence ID is a feature value ID, but the canonical model
     * (docs/architecture/02-canonical-risk-model.md Section 10) allows evidence IDs to reference
     * other evidence kinds (documents, transactions) not modeled here yet.
     */
    private List<FeatureValueEvidenceView> resolveEvidence(SignalInstance signal) {
        return signal.getEvidenceIds().stream()
                .map(featureValueRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(FeatureValueEvidenceView::from)
                .toList();
    }
}
