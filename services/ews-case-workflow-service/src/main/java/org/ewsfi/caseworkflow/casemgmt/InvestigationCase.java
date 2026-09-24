package org.ewsfi.caseworkflow.casemgmt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Per {@code investigation_case} in db/migration/V2__add_investigation_case.sql. The minimal
 * case/decision model beyond a single signal disposition (roadmap item 1.15): an investigation
 * case opened against a signal, optionally assigned to an analyst, optionally escalated, and
 * eventually closed with an outcome -- the {@code case.opened}/{@code case.assigned}/
 * {@code case.escalated}/{@code case.closed} lifecycle named in
 * docs/architecture/03-event-architecture.md Section 3.
 *
 * <p>Lives in this service rather than the shared {@code ews-persistence-core} module, unlike
 * {@code SignalInstance}/{@code FeatureValue}, since only {@code ews-case-workflow-service} reads
 * or writes it today; promote it to the shared module if another service needs to read case state
 * directly rather than via published {@code case.*} events.
 */
@Entity
@Table(name = "investigation_case")
public class InvestigationCase {

    @Id
    @Column(name = "case_id")
    private String caseId;

    @Column(name = "signal_id", nullable = false)
    private String signalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "escalation_reason")
    private String escalationReason;

    @Column(name = "closure_outcome")
    private String closureOutcome;

    @Column(name = "closure_reason")
    private String closureReason;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "escalated_at")
    private OffsetDateTime escalatedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected InvestigationCase() {
        // JPA
    }

    public InvestigationCase(String caseId, String signalId, String openedBy, OffsetDateTime openedAt) {
        this.caseId = caseId;
        this.signalId = signalId;
        this.status = "OPEN";
        this.openedBy = openedBy;
        this.openedAt = openedAt;
    }

    public String getCaseId() {
        return caseId;
    }

    public String getSignalId() {
        return signalId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public String getClosureOutcome() {
        return closureOutcome;
    }

    public void setClosureOutcome(String closureOutcome) {
        this.closureOutcome = closureOutcome;
    }

    public String getClosureReason() {
        return closureReason;
    }

    public void setClosureReason(String closureReason) {
        this.closureReason = closureReason;
    }

    public OffsetDateTime getOpenedAt() {
        return openedAt;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(OffsetDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public OffsetDateTime getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(OffsetDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }
}
