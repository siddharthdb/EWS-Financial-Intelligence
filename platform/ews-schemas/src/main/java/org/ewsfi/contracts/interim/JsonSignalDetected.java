package org.ewsfi.contracts.interim;

import java.util.List;

/**
 * Interim JSON wire shape for {@code signal.detected} events published to
 * {@code ews.derived.signal}, mirroring the fields of
 * schemas/events/payloads/signal-detected-v1.avsc and schemas/signals/signal-instance-v1.schema.json
 * that the Phase-1 payment-return slice actually uses. See {@link JsonEventEnvelope}'s Javadoc for
 * why JSON rather than Avro binary is used on the wire for this slice (roadmap item 1.17).
 */
public class JsonSignalDetected {

    private String signalId;
    private String signalType;
    private String semanticScope;
    private String entityType;
    private String entityId;
    private String status;
    private String severity;
    private double confidenceValue;
    private String materialityBand;
    private String detectedAt;
    private String effectiveAt;
    private String knowledgeTime;
    private String policyId;
    private String policyVersion;
    private String dataQualityState;
    private List<String> evidenceIds;

    public JsonSignalDetected() {
        // Jackson
    }

    public JsonSignalDetected(
            String signalId,
            String signalType,
            String semanticScope,
            String entityType,
            String entityId,
            String status,
            String severity,
            double confidenceValue,
            String materialityBand,
            String detectedAt,
            String effectiveAt,
            String knowledgeTime,
            String policyId,
            String policyVersion,
            String dataQualityState,
            List<String> evidenceIds) {
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
        this.evidenceIds = evidenceIds;
    }

    public String getSignalId() {
        return signalId;
    }

    public void setSignalId(String signalId) {
        this.signalId = signalId;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSemanticScope() {
        return semanticScope;
    }

    public void setSemanticScope(String semanticScope) {
        this.semanticScope = semanticScope;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
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

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public double getConfidenceValue() {
        return confidenceValue;
    }

    public void setConfidenceValue(double confidenceValue) {
        this.confidenceValue = confidenceValue;
    }

    public String getMaterialityBand() {
        return materialityBand;
    }

    public void setMaterialityBand(String materialityBand) {
        this.materialityBand = materialityBand;
    }

    public String getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(String detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(String effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getKnowledgeTime() {
        return knowledgeTime;
    }

    public void setKnowledgeTime(String knowledgeTime) {
        this.knowledgeTime = knowledgeTime;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public String getDataQualityState() {
        return dataQualityState;
    }

    public void setDataQualityState(String dataQualityState) {
        this.dataQualityState = dataQualityState;
    }

    public List<String> getEvidenceIds() {
        return evidenceIds;
    }

    public void setEvidenceIds(List<String> evidenceIds) {
        this.evidenceIds = evidenceIds;
    }
}
