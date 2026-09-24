package org.ewsfi.contracts.interim;

/**
 * Interim JSON wire shape for {@code feature.value.updated} events published to
 * {@code ews.derived.feature}, mirroring the fields of
 * schemas/events/payloads/feature-value-updated-v1.avsc that the Phase-1 payment-return slice
 * actually uses. See {@link JsonEventEnvelope}'s Javadoc for why JSON rather than Avro binary is
 * used on the wire for this slice (roadmap item 1.17).
 */
public class JsonFeatureValue {

    private String featureValueId;
    private String definitionId;
    private String featureName;
    private String definitionVersion;
    private String entityType;
    private String entityId;
    private String state;
    private String valueType;
    private String valueNumeric;
    private String knowledgeTime;
    private String calculatedAt;
    private String windowStart;
    private String windowEnd;
    private String qualityState;
    private String lineageTransformationVersion;

    public JsonFeatureValue() {
        // Jackson
    }

    public JsonFeatureValue(
            String featureValueId,
            String definitionId,
            String featureName,
            String definitionVersion,
            String entityType,
            String entityId,
            String state,
            String valueType,
            String valueNumeric,
            String knowledgeTime,
            String calculatedAt,
            String windowStart,
            String windowEnd,
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

    public void setFeatureValueId(String featureValueId) {
        this.featureValueId = featureValueId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }

    public String getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(String definitionVersion) {
        this.definitionVersion = definitionVersion;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValueNumeric() {
        return valueNumeric;
    }

    public void setValueNumeric(String valueNumeric) {
        this.valueNumeric = valueNumeric;
    }

    public String getKnowledgeTime() {
        return knowledgeTime;
    }

    public void setKnowledgeTime(String knowledgeTime) {
        this.knowledgeTime = knowledgeTime;
    }

    public String getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(String calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public String getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(String windowStart) {
        this.windowStart = windowStart;
    }

    public String getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(String windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getQualityState() {
        return qualityState;
    }

    public void setQualityState(String qualityState) {
        this.qualityState = qualityState;
    }

    public String getLineageTransformationVersion() {
        return lineageTransformationVersion;
    }

    public void setLineageTransformationVersion(String lineageTransformationVersion) {
        this.lineageTransformationVersion = lineageTransformationVersion;
    }
}
