package org.ewsfi.experience.signals;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.ewsfi.persistence.feature.FeatureValue;

/**
 * One step of the evidence drill-down chain
 * (docs/architecture/01-architecture-blueprint.md Section 16): the governed feature value that
 * contributed to a proposed signal.
 */
public record FeatureValueEvidenceView(
        String featureValueId,
        String featureName,
        String entityId,
        BigDecimal valueNumeric,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OffsetDateTime knowledgeTime) {

    public static FeatureValueEvidenceView from(FeatureValue featureValue) {
        return new FeatureValueEvidenceView(
                featureValue.getFeatureValueId(),
                featureValue.getFeatureName(),
                featureValue.getEntityId(),
                featureValue.getValueNumeric(),
                featureValue.getWindowStart(),
                featureValue.getWindowEnd(),
                featureValue.getKnowledgeTime());
    }
}
