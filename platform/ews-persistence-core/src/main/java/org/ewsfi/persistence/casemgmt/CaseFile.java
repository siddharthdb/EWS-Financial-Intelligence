package org.ewsfi.persistence.casemgmt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A workflow case opened from an accepted signal. Status is OPEN, ASSIGNED, ESCALATED, or CLOSED. */
@Entity
@Table(name = "case_file")
public class CaseFile {

    @Id
    @Column(name = "case_id")
    private String caseId;

    @Column(name = "signal_id", nullable = false)
    private String signalId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "assignee_id")
    private String assigneeId;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "reason")
    private String reason;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected CaseFile() {}

    public CaseFile(
            String caseId,
            String signalId,
            String entityType,
            String entityId,
            String openedBy,
            OffsetDateTime openedAt,
            String reason) {
        this.caseId = caseId;
        this.signalId = signalId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.status = "OPEN";
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.reason = reason;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getSignalId() {
        return signalId;
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

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getOutcome() {
        return outcome;
    }

    public void assign(String assigneeId) {
        this.assigneeId = assigneeId;
        this.status = "ASSIGNED";
    }

    public void escalate(String reason) {
        this.reason = reason;
        this.status = "ESCALATED";
    }

    public void close(String outcome, OffsetDateTime closedAt) {
        this.outcome = outcome;
        this.closedAt = closedAt;
        this.status = "CLOSED";
    }
}
