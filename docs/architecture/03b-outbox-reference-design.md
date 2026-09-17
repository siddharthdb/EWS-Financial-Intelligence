# EWS 2.0 — Application-Managed Outbox Reference Design

**Status:** Draft / Part III  
**Applies to:** EWS-owned transactional services

## 1. Objective

Persist business state and publication intent atomically in the service database, then publish asynchronously to Kafka without holding database locks during broker I/O. Target guarantee: **effectively-once business outcome**, not cross-system distributed exactly-once.

## 2. State machine

```text
NEW -> CLAIMED -> PUBLISHED -> ARCHIVED/deleted
        |   |
        |   +-> retryable failure -> NEW/claimable
        +-> lease expiry -> NEW/claimable

Repeated deterministic failures -> FAILED
```

## 3. Logical outbox record

```text
event_id              immutable event identity
aggregate_type        business aggregate type
aggregate_id          partition/order identity
aggregate_sequence    nullable monotonic sequence where aggregate ordering is material
event_type            semantic event type
event_version         semantic contract version
topic                  approved Kafka topic
partition_key          Kafka key
payload                serialized canonical payload or governed JSON staging payload
headers                optional governed metadata
occurred_at            business event timestamp
knowledge_time         when EWS was entitled to know the fact
created_at             outbox insert time
status                 NEW | CLAIMED | PUBLISHED | FAILED
attempt_count          publication attempts
next_attempt_at        retry eligibility
lease_owner            publisher instance
lease_until            lease expiry
published_at           broker acknowledgement time
kafka_partition        broker result
kafka_offset           broker result
last_error_code        normalized category
last_error_message     bounded/sanitized diagnostic
```

Do not store credentials, unrestricted documents or arbitrary sensitive source payloads in the outbox.

## 4. PostgreSQL baseline DDL

```sql
CREATE TABLE ews_outbox_event (
    event_id           UUID PRIMARY KEY,
    aggregate_type     VARCHAR(100) NOT NULL,
    aggregate_id       VARCHAR(200) NOT NULL,
    aggregate_sequence BIGINT,
    event_type         VARCHAR(200) NOT NULL,
    event_version      VARCHAR(30) NOT NULL,
    topic              VARCHAR(200) NOT NULL,
    partition_key      VARCHAR(300) NOT NULL,
    payload            JSONB NOT NULL,
    headers            JSONB,
    occurred_at        TIMESTAMPTZ NOT NULL,
    knowledge_time     TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status             VARCHAR(20) NOT NULL DEFAULT 'NEW',
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner        VARCHAR(200),
    lease_until        TIMESTAMPTZ,
    published_at       TIMESTAMPTZ,
    kafka_partition    INTEGER,
    kafka_offset       BIGINT,
    last_error_code    VARCHAR(100),
    last_error_message VARCHAR(1000),
    CONSTRAINT ck_ews_outbox_status CHECK (status IN ('NEW','CLAIMED','PUBLISHED','FAILED'))
);

CREATE INDEX ix_ews_outbox_claim ON ews_outbox_event (status, next_attempt_at, created_at);
CREATE INDEX ix_ews_outbox_lease ON ews_outbox_event (status, lease_until);
CREATE INDEX ix_ews_outbox_aggregate ON ews_outbox_event (aggregate_type, aggregate_id, aggregate_sequence, created_at);
```

For aggregates requiring strict ordering, the service should allocate `aggregate_sequence` atomically with the business state change. Do not derive it from publisher order or Kafka offset.

## 5. Oracle baseline DDL

```sql
CREATE TABLE EWS_OUTBOX_EVENT (
    EVENT_ID           VARCHAR2(36) PRIMARY KEY,
    AGGREGATE_TYPE     VARCHAR2(100) NOT NULL,
    AGGREGATE_ID       VARCHAR2(200) NOT NULL,
    AGGREGATE_SEQUENCE NUMBER(19),
    EVENT_TYPE         VARCHAR2(200) NOT NULL,
    EVENT_VERSION      VARCHAR2(30) NOT NULL,
    TOPIC              VARCHAR2(200) NOT NULL,
    PARTITION_KEY      VARCHAR2(300) NOT NULL,
    PAYLOAD            CLOB NOT NULL,
    HEADERS            CLOB,
    OCCURRED_AT        TIMESTAMP WITH TIME ZONE NOT NULL,
    KNOWLEDGE_TIME     TIMESTAMP WITH TIME ZONE NOT NULL,
    CREATED_AT         TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    STATUS             VARCHAR2(20) DEFAULT 'NEW' NOT NULL,
    ATTEMPT_COUNT      NUMBER(10) DEFAULT 0 NOT NULL,
    NEXT_ATTEMPT_AT    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    LEASE_OWNER        VARCHAR2(200),
    LEASE_UNTIL        TIMESTAMP WITH TIME ZONE,
    PUBLISHED_AT       TIMESTAMP WITH TIME ZONE,
    KAFKA_PARTITION    NUMBER(10),
    KAFKA_OFFSET       NUMBER(19),
    LAST_ERROR_CODE    VARCHAR2(100),
    LAST_ERROR_MESSAGE VARCHAR2(1000),
    CONSTRAINT CK_EWS_OUTBOX_STATUS CHECK (STATUS IN ('NEW','CLAIMED','PUBLISHED','FAILED')),
    CONSTRAINT CK_EWS_OUTBOX_PAYLOAD_JSON CHECK (PAYLOAD IS JSON),
    CONSTRAINT CK_EWS_OUTBOX_HEADERS_JSON CHECK (HEADERS IS JSON)
);

CREATE INDEX IX_EWS_OUTBOX_CLAIM ON EWS_OUTBOX_EVENT (STATUS, NEXT_ATTEMPT_AT, CREATED_AT);
CREATE INDEX IX_EWS_OUTBOX_LEASE ON EWS_OUTBOX_EVENT (STATUS, LEASE_UNTIL);
CREATE INDEX IX_EWS_OUTBOX_AGGREGATE ON EWS_OUTBOX_EVENT (AGGREGATE_TYPE, AGGREGATE_ID, AGGREGATE_SEQUENCE, CREATED_AT);
```

Validate JSON syntax against the deployed Oracle edition/version.

## 6. Transaction and publication

The service creates the event ID before commit. Business mutation and outbox insert occur in one local transaction. Claim transactions are short and use lease semantics; **never keep the database transaction open while calling Kafka**.

After claim commit, serialize the canonical event, send using the approved topic/key, wait for broker acknowledgement, then mark the row `PUBLISHED` in a short database transaction. Retry transient failures with bounded exponential backoff and jitter; deterministic schema/policy/authorization failures become `FAILED` and alert operations.

## 7. Ordering

`ORDER BY created_at` plus `SKIP LOCKED` does not guarantee strict aggregate ordering across workers. For facility, repayment, signal and case state transitions, persist `aggregate_sequence` and propagate it into `CanonicalEventEnvelopeV1.aggregateSequence`. Consumers detect duplicate, stale and gap sequences. High-volume immutable transaction facts may instead use authoritative source sequence/transaction identity.

## 8. Kafka producer baseline

```text
enable.idempotence = true
acks = all
retries > 0
max.in.flight.requests.per.connection <= 5
```

Kafka transactions are required only when a Kafka processing unit needs atomic multi-record publication and/or consumed-offset commit, not merely because an outbox exists.

## 9. Duplicate publication and consumer idempotency

`publish -> Kafka ACK -> process dies -> DB not marked PUBLISHED` can republish the same `event_id`. Never generate a new event ID for retry. DB-updating consumers use an inbox identity or version-aware idempotent update in the same local transaction as their business projection.

## 10. Outage behaviour and observability

Kafka outage does not block business commits after outbox insertion. Backlog grows and drains after recovery. Capacity planning includes the agreed outage window.

Minimum metrics include backlog rows, oldest unpublished age, publish/claim latency, retry/failed counts, lease expiry and duplicate-risk count. Alerts prioritize oldest unpublished age and sustained growth.

## 11. Operations and retention

Operators can inspect/retry bounded failed events, pause/drain publishers, reconcile event ID to Kafka partition/offset and archive/delete published rows. Every manual retry/requeue is audited.

Outbox rows are operational publication history, not permanent compliance evidence. Long-term event/evidence lineage belongs in the governed archive.