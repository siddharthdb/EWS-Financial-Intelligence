package org.ewsfi.persistence.feature;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Per {@code feature_value} in db/migration/V1__init_phase1_baseline.sql, derived from
 * schemas/features/feature-value-v1.schema.json. Only the columns needed by the Phase-1
 * payment-return slice (docs/architecture/06-gap-analysis-and-implementation-roadmap.md Section 7)
 * are populated by {@code ews-feature-processor} today; the table has more columns (quality,
 * lineage) that remain nullable until a later slice needs them.
 */
@Entity
@Table(name = "feature_value")
public class FeatureValue {

    @Id
    @Column(name = "feature_value_id")
    private String featureValueId;

    @Column(name = "definition_id", nullable = false)
    private String definitionId;

    @Column(name = "feature_name", nullable = false)
    private String featureName;

    @Column(name = "definition_version", nullable = false)
    private String definitionVersion;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "value_type")
    private String valueType;

    @Column(name = "value_numeric")
    private BigDecimal valueNumeric;

    @Column(name = "knowledge_time", nullable = false)
    private OffsetDateTime knowledgeTime;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    @Column(name = "window_start")
    private OffsetDateTime windowStart;

    @Column(name = "window_end")
    private OffsetDateTime windowEnd;

    @Column(name = "revision", nullable = false)
    private int revision = 1;

    @Column(name = "quality_state", nullable = false)
    private String qualityState;

    @Column(name = "lineage_transformation_version", nullable = false)
    private String lineageTransformationVersion;

    protected FeatureValue() {
        // JPA
    }

    public FeatureValue(
            String featureValueId,
            String definitionId,
            String featureName,
            String definitionVersion,
            String entityType,
            String entityId,
            String state,
            String valueType,
            BigDecimal valueNumeric,
            OffsetDateTime knowledgeTime,
            OffsetDateTime calculatedAt,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd,
            String qualityState,
            String lineageTransformationVersion) {
        this.featureValueId = featureValueId;
        this.definitionId = definitionId;
        this.featureName = featureName;
        this.definitionVersion = definitionVersion;
        this.entityType = entityType;
        this.entityId = entityId;
        this.state = state;
        this.valueType = valueType;
        this.valueNumeric = valueNumeric;
        this.knowledgeTime = knowledgeTime;
        this.calculatedAt = calculatedAt;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.qualityState = qualityState;
        this.lineageTransformationVersion = lineageTransformationVersion;
    }

    public String getFeatureValueId() {
        return featureValueId;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getEntityId() {
        return entityId;
    }

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public OffsetDateTime getWindowStart() {
        return windowStart;
    }

    public OffsetDateTime getWindowEnd() {
        return windowEnd;
    }

    public OffsetDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public OffsetDateTime getKnowledgeTime() {
        return knowledgeTime;
    }
}
