-- Case lifecycle for an accepted signal. Official EWS state remains a separate record.

CREATE TABLE case_file (
    case_id       VARCHAR(200) PRIMARY KEY,
    signal_id     VARCHAR(200) NOT NULL REFERENCES signal_instance (signal_id),
    entity_type   VARCHAR(100) NOT NULL,
    entity_id     VARCHAR(200) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    assignee_id   VARCHAR(200),
    opened_by     VARCHAR(200) NOT NULL,
    opened_at     TIMESTAMPTZ NOT NULL,
    reason        TEXT,
    outcome       VARCHAR(200),
    closed_at     TIMESTAMPTZ
);

CREATE INDEX idx_case_file_signal ON case_file (signal_id);
CREATE INDEX idx_case_file_status ON case_file (status);
