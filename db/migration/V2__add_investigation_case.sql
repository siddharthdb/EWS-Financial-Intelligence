-- Adds the minimal investigation-case lifecycle table (open/assign/escalate/close), per
-- docs/architecture/03-event-architecture.md Section 3 ("Decision/workflow events": case.opened,
-- case.assigned, case.escalated, case.closed) and roadmap item 1.15
-- (docs/architecture/08-roadmap-progress-tracker.md).
--
-- No FK to signal_instance, consistent with V1's decoupling convention (entity resolution
-- deliberately decouples cross-bounded-context references) -- signal_instance.case_id already
-- exists in V1 as a plain, unenforced column for the same reason.

CREATE TABLE investigation_case (
    case_id            VARCHAR(200) PRIMARY KEY,
    signal_id           VARCHAR(200) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    opened_by           VARCHAR(200) NOT NULL,
    assigned_to          VARCHAR(200),
    escalation_reason      VARCHAR(2000),
    closure_outcome        VARCHAR(50),
    closure_reason        VARCHAR(2000),
    opened_at           TIMESTAMPTZ NOT NULL,
    assigned_at          TIMESTAMPTZ,
    escalated_at          TIMESTAMPTZ,
    closed_at            TIMESTAMPTZ
);

CREATE INDEX idx_investigation_case_status ON investigation_case (status);
CREATE INDEX idx_investigation_case_signal ON investigation_case (signal_id);
