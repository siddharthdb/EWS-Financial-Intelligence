-- EWS Financial Intelligence Platform -- Phase-1 baseline schema
--
-- Skeleton DDL only: tables, primary keys and basic lookup indexes. No business constraints,
-- no triggers, no cross-bounded-context foreign keys (entity resolution deliberately decouples
-- e.g. signal_instance.entity_id from a counterparty table -- see
-- docs/architecture/03-event-architecture.md Section 6, "Identity and security resolution before
-- risk interpretation").
--
-- One table (plus child tables for array-shaped fields) per existing schema contract under
-- /schemas, except outbox_event and processed_event which derive from
-- docs/architecture/adr/ADR-003-application-managed-transactional-outbox.md (no schemas/ file --
-- the ADR is the contract). Array-shaped JSON Schema fields become child tables rather than JSONB,
-- except genuinely schemaless fields (payload, headers, policy condition) which stay JSONB.

-- =====================================================================================
-- Outbox / inbox -- ADR-003
-- =====================================================================================

CREATE TABLE outbox_event (
    event_id            UUID PRIMARY KEY,
    aggregate_type       VARCHAR(200) NOT NULL,
    aggregate_id         VARCHAR(200) NOT NULL,
    aggregate_sequence    BIGINT,
    event_type           VARCHAR(200) NOT NULL,
    event_version         VARCHAR(50) NOT NULL,
    partition_key         VARCHAR(200) NOT NULL,
    payload              JSONB NOT NULL,
    headers              JSONB,
    status               VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    available_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    publish_attempts      INT NOT NULL DEFAULT 0,
    last_attempt_at        TIMESTAMPTZ,
    published_at          TIMESTAMPTZ,
    last_error_code        VARCHAR(100),
    last_error_message     TEXT,
    kafka_topic           VARCHAR(200),
    kafka_partition        INT,
    kafka_offset          BIGINT
);

CREATE INDEX idx_outbox_event_status_available ON outbox_event (status, available_at);
CREATE INDEX idx_outbox_event_aggregate ON outbox_event (aggregate_type, aggregate_id, aggregate_sequence);

-- Consumer idempotency inbox, per docs/architecture/03-event-architecture.md Section 13
-- ("Consumer idempotency"). Composite PK because multiple consumers process the same event.
CREATE TABLE processed_event (
    event_id       UUID NOT NULL,
    consumer_name   VARCHAR(200) NOT NULL,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer_name)
);

-- =====================================================================================
-- Canonical event envelope -- schemas/events/canonical-event-envelope-v1.avsc
-- Durable landing table for replay/audit, distinct from the transient outbox above.
-- =====================================================================================

CREATE TABLE canonical_event_envelope (
    event_id                  UUID PRIMARY KEY,
    event_type                 VARCHAR(200) NOT NULL,
    event_version               VARCHAR(50) NOT NULL,
    producer_service             VARCHAR(200) NOT NULL,
    producer_instance            VARCHAR(200),
    producer_version             VARCHAR(50),
    entity_type                 VARCHAR(100) NOT NULL,
    entity_id                   VARCHAR(200) NOT NULL,
    entity_resolution_ref         VARCHAR(200),
    entity_resolution_confidence   DOUBLE PRECISION,
    aggregate_sequence            BIGINT,
    partition_key                VARCHAR(200) NOT NULL,
    event_time                  TIMESTAMPTZ NOT NULL,
    effective_time               TIMESTAMPTZ,
    knowledge_time               TIMESTAMPTZ NOT NULL,
    ingested_at                 TIMESTAMPTZ NOT NULL,
    jurisdiction                CHAR(2),
    market                     VARCHAR(50),
    source_system                VARCHAR(200) NOT NULL,
    source_provider              VARCHAR(200),
    source_record_id              VARCHAR(200),
    source_authority_tier          VARCHAR(2),
    source_evidence_ids           JSONB,
    source_rights_ref             VARCHAR(200),
    source_published_at           TIMESTAMPTZ,
    correlation_id               VARCHAR(200),
    causation_id                 VARCHAR(200),
    trace_id                    VARCHAR(200),
    sensitivity                 VARCHAR(20) NOT NULL,
    contains_pii                 BOOLEAN NOT NULL DEFAULT FALSE,
    retention_class               VARCHAR(100),
    execution_mode               VARCHAR(30) NOT NULL DEFAULT 'LIVE',
    run_id                     VARCHAR(200)
);

CREATE INDEX idx_canonical_event_entity ON canonical_event_envelope (entity_type, entity_id);
CREATE INDEX idx_canonical_event_knowledge_time ON canonical_event_envelope (knowledge_time);

-- =====================================================================================
-- Features -- schemas/features/feature-definition-v1.schema.json, feature-value-v1.schema.json
-- =====================================================================================

CREATE TABLE feature_definition (
    definition_id                VARCHAR(200) PRIMARY KEY,
    feature_name                 VARCHAR(200) NOT NULL,
    version                     VARCHAR(50) NOT NULL,
    semantic_scope                VARCHAR(40) NOT NULL,
    entity_grain                 VARCHAR(50) NOT NULL,
    value_type                  VARCHAR(20) NOT NULL,
    unit                       VARCHAR(50),
    currency_semantics             VARCHAR(40),
    window_spec                  VARCHAR(50),
    calculation_method             VARCHAR(20) NOT NULL,
    calculation_transformation_version VARCHAR(50) NOT NULL,
    calculation_expression_ref       VARCHAR(200),
    effective_from                TIMESTAMPTZ NOT NULL,
    effective_to                 TIMESTAMPTZ,
    owner                      VARCHAR(200) NOT NULL,
    approval_ref                 VARCHAR(200)
);

CREATE TABLE feature_definition_applicability (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id       VARCHAR(200) NOT NULL,
    applicability_kind    VARCHAR(20) NOT NULL, -- JURISDICTION | MARKET | PRODUCT | SEGMENT
    applicability_value    VARCHAR(100) NOT NULL
);

CREATE TABLE feature_value (
    feature_value_id           VARCHAR(200) PRIMARY KEY,
    definition_id              VARCHAR(200) NOT NULL,
    feature_name               VARCHAR(200) NOT NULL,
    definition_version           VARCHAR(50) NOT NULL,
    semantic_scope              VARCHAR(40),
    entity_type                VARCHAR(100) NOT NULL,
    entity_id                 VARCHAR(200) NOT NULL,
    primary_jurisdiction          CHAR(2),
    market                    VARCHAR(50),
    accounting_basis             VARCHAR(100),
    state                     VARCHAR(20) NOT NULL,
    value_type                 VARCHAR(20),
    value_numeric               NUMERIC(38, 10),
    value_boolean               BOOLEAN,
    value_string                TEXT,
    unit                     VARCHAR(50),
    currency                   CHAR(3),
    effective_from              TIMESTAMPTZ,
    effective_to               TIMESTAMPTZ,
    knowledge_time              TIMESTAMPTZ NOT NULL,
    calculated_at               TIMESTAMPTZ NOT NULL,
    window_start                TIMESTAMPTZ,
    window_end                 TIMESTAMPTZ,
    source_as_of                TIMESTAMPTZ,
    revision                  INT NOT NULL DEFAULT 1,
    supersedes                 VARCHAR(200),
    quality_state               VARCHAR(20) NOT NULL,
    completeness                DOUBLE PRECISION,
    freshness                  DOUBLE PRECISION,
    source_authority             DOUBLE PRECISION,
    source_authority_tier          VARCHAR(2),
    extraction_confidence          DOUBLE PRECISION,
    entity_resolution_confidence      DOUBLE PRECISION,
    corroboration_count           INT,
    lineage_transformation_version    VARCHAR(50) NOT NULL,
    trace_id                  VARCHAR(200)
);

CREATE INDEX idx_feature_value_entity ON feature_value (entity_type, entity_id, feature_name);
CREATE INDEX idx_feature_value_knowledge_time ON feature_value (knowledge_time);

CREATE TABLE feature_value_component (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_value_id    VARCHAR(200) NOT NULL REFERENCES feature_value (feature_value_id),
    component_name       VARCHAR(200) NOT NULL,
    value_numeric        NUMERIC(38, 10),
    value_boolean        BOOLEAN,
    value_string         TEXT,
    unit               VARCHAR(50),
    ref_feature_value_id    VARCHAR(200),
    ref_observation_id     VARCHAR(200)
);

CREATE TABLE feature_value_lineage_ref (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_value_id    VARCHAR(200) NOT NULL REFERENCES feature_value (feature_value_id),
    ref_kind            VARCHAR(30) NOT NULL, -- INPUT_FEATURE_VALUE | OBSERVATION | EVIDENCE | SOURCE_RECORD | SOURCE_RIGHTS | ENTITY_RESOLUTION
    ref_value           VARCHAR(200) NOT NULL
);

-- =====================================================================================
-- Signals -- schemas/signals/signal-instance-v1.schema.json, signal-policy-v1.schema.json
-- =====================================================================================

CREATE TABLE signal_instance (
    signal_id                VARCHAR(200) PRIMARY KEY,
    signal_type               VARCHAR(200) NOT NULL,
    semantic_scope             VARCHAR(40) NOT NULL,
    primary_jurisdiction         CHAR(2),
    market                   VARCHAR(50),
    entity_type               VARCHAR(100) NOT NULL,
    entity_id                 VARCHAR(200) NOT NULL,
    status                   VARCHAR(20) NOT NULL,
    severity                  VARCHAR(20) NOT NULL,
    confidence_value             DOUBLE PRECISION NOT NULL,
    confidence_source_authority     DOUBLE PRECISION,
    confidence_data_quality        DOUBLE PRECISION,
    confidence_entity_resolution     DOUBLE PRECISION,
    confidence_detection_reliability  DOUBLE PRECISION,
    confidence_corroboration        DOUBLE PRECISION,
    materiality_band             VARCHAR(20) NOT NULL,
    materiality_value            DOUBLE PRECISION,
    materiality_basis            VARCHAR(200),
    detected_at                TIMESTAMPTZ NOT NULL,
    effective_at               TIMESTAMPTZ NOT NULL,
    knowledge_time              TIMESTAMPTZ NOT NULL,
    policy_id                 VARCHAR(200) NOT NULL,
    policy_version              VARCHAR(50) NOT NULL,
    correlation_id              VARCHAR(200),
    episode_id                 VARCHAR(200),
    proposed_risk_impact          DOUBLE PRECISION,
    explanation_ref              VARCHAR(200),
    case_id                   VARCHAR(200),
    data_quality_state            VARCHAR(20) NOT NULL,
    data_quality_source_authority_tier VARCHAR(2),
    trace_id                  VARCHAR(200)
);

CREATE INDEX idx_signal_instance_entity ON signal_instance (entity_type, entity_id);
CREATE INDEX idx_signal_instance_status ON signal_instance (status);
CREATE INDEX idx_signal_instance_knowledge_time ON signal_instance (knowledge_time);

-- evidenceIds has minItems:1 in the JSON Schema contract; enforce non-empty at the application layer
-- once write logic exists (not enforceable declaratively for a child table in this skeleton).
CREATE TABLE signal_instance_evidence (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id      VARCHAR(200) NOT NULL REFERENCES signal_instance (signal_id),
    evidence_id     VARCHAR(200) NOT NULL
);

CREATE TABLE signal_instance_risk_intent (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id      VARCHAR(200) NOT NULL REFERENCES signal_instance (signal_id),
    risk_intent     VARCHAR(40) NOT NULL
);

CREATE TABLE signal_instance_risk_dimension (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id      VARCHAR(200) NOT NULL REFERENCES signal_instance (signal_id),
    risk_dimension   VARCHAR(60) NOT NULL
);

CREATE TABLE signal_instance_disposition_tag (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_id        VARCHAR(200) NOT NULL REFERENCES signal_instance (signal_id),
    disposition_tag    VARCHAR(40) NOT NULL
);

CREATE TABLE signal_policy (
    policy_id            VARCHAR(200) PRIMARY KEY,
    signal_type           VARCHAR(200) NOT NULL,
    semantic_scope          VARCHAR(40) NOT NULL,
    version              VARCHAR(50) NOT NULL,
    status               VARCHAR(20) NOT NULL,
    applicability_domain      VARCHAR(20) NOT NULL,
    detection_method         VARCHAR(20) NOT NULL,
    detection_window         VARCHAR(50),
    detection_condition       JSONB,
    model_ref             VARCHAR(200),
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_to           TIMESTAMPTZ,
    owner               VARCHAR(200) NOT NULL,
    approved_by            VARCHAR(200),
    approved_at            TIMESTAMPTZ,
    validation_ref          VARCHAR(200),
    human_validation_required   BOOLEAN NOT NULL DEFAULT TRUE,
    cooldown              VARCHAR(50)
);

CREATE TABLE signal_policy_applicability (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id      VARCHAR(200) NOT NULL REFERENCES signal_policy (policy_id),
    applicability_kind VARCHAR(20) NOT NULL, -- INSTITUTION | LEGAL_ENTITY | JURISDICTION | MARKET | SEGMENT | PRODUCT | CURRENCY
    applicability_value VARCHAR(100) NOT NULL
);

-- =====================================================================================
-- Classification state -- schemas/classifications/classification-state-v1.schema.json
-- =====================================================================================

CREATE TABLE classification_state (
    classification_id       VARCHAR(200) PRIMARY KEY,
    namespace              VARCHAR(60) NOT NULL,
    value                 VARCHAR(200) NOT NULL,
    jurisdiction             CHAR(2),
    entity_type              VARCHAR(100) NOT NULL,
    entity_id               VARCHAR(200) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    effective_at             TIMESTAMPTZ NOT NULL,
    knowledge_time            TIMESTAMPTZ NOT NULL,
    policy_id               VARCHAR(200) NOT NULL,
    policy_version            VARCHAR(50) NOT NULL,
    policy_authority           VARCHAR(200),
    decision_id              VARCHAR(200),
    supersedes_classification_id    VARCHAR(200),
    trace_id                VARCHAR(200)
);

CREATE INDEX idx_classification_state_entity ON classification_state (entity_type, entity_id, namespace);

CREATE TABLE classification_state_evidence (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classification_id  VARCHAR(200) NOT NULL REFERENCES classification_state (classification_id),
    evidence_id       VARCHAR(200) NOT NULL
);

CREATE TABLE classification_state_feature_snapshot (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classification_id  VARCHAR(200) NOT NULL REFERENCES classification_state (classification_id),
    feature_snapshot_id VARCHAR(200) NOT NULL
);

CREATE TABLE classification_state_signal (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classification_id  VARCHAR(200) NOT NULL REFERENCES classification_state (classification_id),
    signal_id         VARCHAR(200) NOT NULL
);

-- =====================================================================================
-- Entity resolution -- schemas/identity/entity-resolution-v1.schema.json
-- =====================================================================================

CREATE TABLE entity_resolution (
    resolution_id           VARCHAR(200) PRIMARY KEY,
    source_id              VARCHAR(200) NOT NULL,
    source_entity_id          VARCHAR(200) NOT NULL,
    source_name             VARCHAR(400),
    source_jurisdiction        CHAR(2),
    canonical_entity_type       VARCHAR(100),
    canonical_entity_id        VARCHAR(200),
    status                 VARCHAR(20) NOT NULL,
    method                 VARCHAR(40) NOT NULL,
    confidence              DOUBLE PRECISION NOT NULL,
    model_or_rule_ref          VARCHAR(200),
    human_decision_ref         VARCHAR(200),
    effective_at             TIMESTAMPTZ,
    knowledge_time            TIMESTAMPTZ NOT NULL,
    supersedes_resolution_id      VARCHAR(200)
);

CREATE INDEX idx_entity_resolution_source ON entity_resolution (source_id, source_entity_id);
CREATE INDEX idx_entity_resolution_canonical ON entity_resolution (canonical_entity_type, canonical_entity_id);

CREATE TABLE entity_resolution_identifier (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resolution_id    VARCHAR(200) NOT NULL REFERENCES entity_resolution (resolution_id),
    identifier_type   VARCHAR(100) NOT NULL,
    identifier_value   VARCHAR(200) NOT NULL,
    issuer         VARCHAR(200)
);

CREATE TABLE entity_resolution_matched_attribute (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resolution_id    VARCHAR(200) NOT NULL REFERENCES entity_resolution (resolution_id),
    attribute_kind   VARCHAR(20) NOT NULL, -- MATCHED | CONFLICTING | CANDIDATE_ENTITY
    attribute_value   VARCHAR(200) NOT NULL
);

CREATE TABLE entity_resolution_evidence (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resolution_id    VARCHAR(200) NOT NULL REFERENCES entity_resolution (resolution_id),
    evidence_id      VARCHAR(200) NOT NULL
);

-- =====================================================================================
-- Source registry -- schemas/sources/source-registry-v1.schema.json
-- =====================================================================================

CREATE TABLE source_registry (
    source_id                 VARCHAR(200) PRIMARY KEY,
    provider                 VARCHAR(200) NOT NULL,
    source_domain               VARCHAR(60) NOT NULL,
    authority_tier               VARCHAR(2) NOT NULL,
    access_class                VARCHAR(30) NOT NULL,
    acquisition_mode              VARCHAR(40) NOT NULL,
    structuredness               VARCHAR(20),
    freshness_slo                VARCHAR(50),
    rate_limit_ref               VARCHAR(200),
    credential_class              VARCHAR(100),
    checkpoint_strategy             VARCHAR(200),
    entity_resolution_required        BOOLEAN NOT NULL DEFAULT TRUE,
    risk_analytics_allowed           BOOLEAN NOT NULL,
    raw_retention_allowed            BOOLEAN NOT NULL,
    derived_retention_allowed         BOOLEAN NOT NULL,
    redistribution_allowed           BOOLEAN NOT NULL,
    llm_processing_allowed           BOOLEAN NOT NULL,
    model_inference_allowed          BOOLEAN NOT NULL,
    model_training_allowed           BOOLEAN NOT NULL,
    cross_border_transfer_allowed       BOOLEAN NOT NULL,
    raw_retention_period            VARCHAR(50),
    derived_retention_period          VARCHAR(50),
    licence_ref                 VARCHAR(200),
    evidence_policy_ref             VARCHAR(200),
    owner                    VARCHAR(200) NOT NULL,
    status                   VARCHAR(20) NOT NULL
);

CREATE TABLE source_registry_jurisdiction (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id    VARCHAR(200) NOT NULL REFERENCES source_registry (source_id),
    jurisdiction  CHAR(2) NOT NULL
);

CREATE TABLE source_registry_restriction (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id    VARCHAR(200) NOT NULL REFERENCES source_registry (source_id),
    restriction  VARCHAR(400) NOT NULL
);
