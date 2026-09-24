package org.ewsfi.persistence.signal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per {@code signal_instance} in db/migration/V1__init_phase1_baseline.sql, derived from
 * schemas/signals/signal-instance-v1.schema.json. Child tables ({@code signal_instance_evidence},
 * {@code _risk_intent}, {@code _risk_dimension}, {@code _disposition_tag}) are mapped as
 * {@link ElementCollection}s rather than separate entities, since they are simple value sets with
 * no identity of their own.
 *
 * <p>{@code evidenceIds} has {@code minItems: 1} in the governed JSON Schema contract per
 * ADR-005 (evidence-first AI architecture) -- this is enforced at construction time below, not
 * only documented, so a signal instance can never be built without at least one evidence
 * reference.
 */
@Entity
@Table(name = "signal_instance")
public class SignalInstance {

    @Id
    @Column(name = "signal_id")
    private String signalId;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(name = "semantic_scope", nullable = false)
    private String semanticScope;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "confidence_value", nullable = false)
    private double confidenceValue;

    @Column(name = "materiality_band", nullable = false)
    private String materialityBand;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    @Column(name = "knowledge_time", nullable = false)
    private OffsetDateTime knowledgeTime;

    @Column(name = "policy_id", nullable = false)
    private String policyId;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "data_quality_state", nullable = false)
    private String dataQualityState;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signal_instance_evidence", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "evidence_id", nullable = false)
    private Set<String> evidenceIds = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signal_instance_risk_intent", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "risk_intent", nullable = false)
    private Set<String> riskIntents = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signal_instance_risk_dimension", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "risk_dimension", nullable = false)
    private Set<String> riskDimensions = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signal_instance_disposition_tag", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "disposition_tag", nullable = false)
    private Set<String> dispositionTags = new LinkedHashSet<>();

    protected SignalInstance() {
        // JPA
    }

    public SignalInstance(
            String signalId,
            String signalType,
            String semanticScope,
            String entityType,
            String entityId,
            String status,
            String severity,
            double confidenceValue,
            String materialityBand,
            OffsetDateTime detectedAt,
            OffsetDateTime effectiveAt,
            OffsetDateTime knowledgeTime,
            String policyId,
            String policyVersion,
            String dataQualityState,
            Set<String> evidenceIds) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "signal_instance requires at least one evidenceId, per ADR-005 and "
                            + "schemas/signals/signal-instance-v1.schema.json (evidenceIds minItems:1)");
        }
        this.signalId = signalId;
        this.signalType = signalType;
        this.semanticScope = semanticScope;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = status;
        this.severity = severity;
        this.confidenceValue = confidenceValue;
        this.materialityBand = materialityBand;
        this.detectedAt = detectedAt;
        this.effectiveAt = effectiveAt;
        this.knowledgeTime = knowledgeTime;
        this.policyId = policyId;
        this.policyVersion = policyVersion;
        this.dataQualityState = dataQualityState;
        this.evidenceIds = new LinkedHashSet<>(evidenceIds);
    }

    public String getSignalId() {
        return signalId;
    }

    public String getSignalType() {
        return signalType;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSeverity() {
        return severity;
    }

    public double getConfidenceValue() {
        return confidenceValue;
    }

    public String getMaterialityBand() {
        return materialityBand;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public OffsetDateTime getKnowledgeTime() {
        return knowledgeTime;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Set<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void addDispositionTag(String tag) {
        this.dispositionTags.add(tag);
    }

    public Set<String> getDispositionTags() {
        return dispositionTags;
    }
}
