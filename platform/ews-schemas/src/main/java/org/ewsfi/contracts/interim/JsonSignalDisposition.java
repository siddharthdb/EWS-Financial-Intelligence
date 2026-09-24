package org.ewsfi.contracts.interim;

/**
 * Interim JSON wire shape for {@code signal.disposition.recorded} events, mirroring the fields of
 * schemas/events/payloads/signal-disposition-recorded-v1.avsc that this Phase-1 slice uses. See
 * {@link JsonEventEnvelope}'s Javadoc for why JSON rather than Avro binary is used on the wire for
 * this slice (roadmap item 1.17).
 */
public class JsonSignalDisposition {

    private String decisionId;
    private String signalId;
    private String entityId;
    private String decision;
    private String reasonCode;
    private String reasonText;
    private String actorId;
    private String actorRole;
    private String decidedAt;

    public JsonSignalDisposition() {
        // Jackson
    }

    public JsonSignalDisposition(
            String decisionId,
            String signalId,
            String entityId,
            String decision,
            String reasonCode,
            String reasonText,
            String actorId,
            String actorRole,
            String decidedAt) {
        this.decisionId = decisionId;
        this.signalId = signalId;
        this.entityId = entityId;
        this.decision = decision;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.decidedAt = decidedAt;
    }

    public String getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }

    public String getSignalId() {
        return signalId;
    }

    public void setSignalId(String signalId) {
        this.signalId = signalId;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(String decidedAt) {
        this.decidedAt = decidedAt;
    }
}
