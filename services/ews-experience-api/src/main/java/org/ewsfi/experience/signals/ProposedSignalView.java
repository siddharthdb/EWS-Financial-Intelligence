package org.ewsfi.experience.signals;

import java.time.OffsetDateTime;
import java.util.List;
import org.ewsfi.persistence.signal.SignalInstance;

/**
 * Read view of a signal for the Analyst Workbench-standing-in Experience API, with its evidence
 * drill-down chain resolved, per docs/architecture/01-architecture-blueprint.md Section 16
 * ("Explanation -> Signal -> Rule/model/correlation output -> Features -> Observations ->
 * Evidence -> Original source"). This slice resolves signal -> features only; features ->
 * observations -> evidence -> original source is not implemented yet (the current
 * {@code FeatureValue} entity does not carry that lineage -- see the 2026-09-24 experience-api
 * build-log entry).
 */
public record ProposedSignalView(
        String signalId,
        String signalType,
        String status,
        String severity,
        double confidenceValue,
        String materialityBand,
        String entityType,
        String entityId,
        OffsetDateTime detectedAt,
        String policyId,
        String policyVersion,
        List<FeatureValueEvidenceView> evidence) {

    public static ProposedSignalView from(
            SignalInstance signal, List<FeatureValueEvidenceView> evidence) {
        return new ProposedSignalView(
                signal.getSignalId(),
                signal.getSignalType(),
                signal.getStatus(),
                signal.getSeverity(),
                signal.getConfidenceValue(),
                signal.getMaterialityBand(),
                signal.getEntityType(),
                signal.getEntityId(),
                signal.getDetectedAt(),
                signal.getPolicyId(),
                signal.getPolicyVersion(),
                evidence);
    }
}
