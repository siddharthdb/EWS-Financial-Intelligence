# EWS 2.0 — Application-Managed Outbox Reference Design

**Status:** Draft / Part III  
**Applies to:** EWS-owned transactional services

## 1. Objective

Persist business state and the intent to publish a domain event atomically in the service database, then publish asynchronously to Kafka without holding database locks during broker I/O.

Target guarantee: **effectively-once business outcome**, not cross-system distributed exactly-once.

## 2. State machine

```text
NEW
 |
 | claim
 v
CLAIMED
 | \
 |  \ lease expiry / worker crash
 |   +------------------> NEW/claimable
 |
 | Kafka ACK
 v
PUBLISHED
 |
 | retention/archive
 v
ARCHIVED / deleted

Repeated failures:
NEW/CLAIMED -> RETRY -> ... -> FAILED
```

`CLAIMED` is a lease, not ownership forever. A worker that dies cannot strand an event.

## 3. Logical outbox record

```text
event_id              UUID/string, immutable, unique
aggregate_type        business aggregate type
aggregate_id          partition/order identity
event_type            semantic event type
event_version         semantic contract version
topic                  resolved/approved Kafka topic
partition_key          Kafka key
payload                serialized canonical payload or safe JSON staging payload
headers                optional governed metadata
occurred_at            business event timestamp
knowledge_time         when EWS was entitled to know the fact
created_at             outbox insert time
status                 NEW | CLAIMED | PUBLISHED | FAILED
attempt_count          publication attempts
next_attempt_at        retry eligibility
lease_owner            publisher instance
lease_until            lease expiry
published_at           successful broker acknowledgement time
kafka_partition        broker result
kafka_offset           broker result
last_error_code        normalized error category
last_error_message     bounded/sanitized diagnostic
```

Do not store credentials, unrestricted documents, or arbitrary sensitive source payloads in the outbox.

## 4. PostgreSQL baseline DDL

```sql
CREATE TABLE ews_outbox_event (
    event_id           UUID PRIMARY KEY,
    aggregate_type     VARCHAR(100) NOT NULL,
    aggregate_id       VARCHAR(200) NOT NULL,
    event_type         VARCHAR(200) NOT NULL,
    event_version      VARCHAR(30)  NOT NULL,
    topic              VARCHAR(200) NOT NULL,
    partition_key      VARCHAR(300) NOT NULL,
    payload            JSONB        NOT NULL,
    headers            JSONB,
    occurred_at        TIMESTAMPTZ  NOT NULL,
    knowledge_time     TIMESTAMPTZ  NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status             VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    attempt_count      INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner        VARCHAR(200),
    lease_until        TIMESTAMPTZ,
    published_at       TIMESTAMPTZ,
    kafka_partition    INTEGER,
    kafka_offset       BIGINT,
    last_error_code    VARCHAR(100),
    last_error_message VARCHAR(1000),
    CONSTRAINT ck_ews_outbox_status
      CHECK (status IN ('NEW','CLAIMED','PUBLISHED','FAILED'))
);

CREATE INDEX ix_ews_outbox_claim
ON ews_outbox_event (status, next_attempt_at, created_at);

CREATE INDEX ix_ews_outbox_lease
ON ews_outbox_event (status, lease_until);

CREATE INDEX ix_ews_outbox_aggregate
ON ews_outbox_event (aggregate_type, aggregate_id, created_at);
```

Production may partition/archive this table if volume warrants it. Do not add indexes casually: every business transaction also inserts this row.

## 5. Oracle baseline DDL

Use application-generated UUID strings for portability unless RAW UUID storage is standardized across the platform.

```sql
CREATE TABLE EWS_OUTBOX_EVENT (
    EVENT_ID           VARCHAR2(36)   PRIMARY KEY,
    AGGREGATE_TYPE     VARCHAR2(100)  NOT NULL,
    AGGREGATE_ID       VARCHAR2(200)  NOT NULL,
    EVENT_TYPE         VARCHAR2(200)  NOT NULL,
    EVENT_VERSION      VARCHAR2(30)   NOT NULL,
    TOPIC              VARCHAR2(200)  NOT NULL,
    PARTITION_KEY      VARCHAR2(300)  NOT NULL,
    PAYLOAD            CLOB           NOT NULL,
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
    CONSTRAINT CK_EWS_OUTBOX_STATUS
      CHECK (STATUS IN ('NEW','CLAIMED','PUBLISHED','FAILED')),
    CONSTRAINT CK_EWS_OUTBOX_PAYLOAD_JSON CHECK (PAYLOAD IS JSON),
    CONSTRAINT CK_EWS_OUTBOX_HEADERS_JSON CHECK (HEADERS IS JSON)
);

CREATE INDEX IX_EWS_OUTBOX_CLAIM
ON EWS_OUTBOX_EVENT (STATUS, NEXT_ATTEMPT_AT, CREATED_AT);

CREATE INDEX IX_EWS_OUTBOX_LEASE
ON EWS_OUTBOX_EVENT (STATUS, LEASE_UNTIL);

CREATE INDEX IX_EWS_OUTBOX_AGGREGATE
ON EWS_OUTBOX_EVENT (AGGREGATE_TYPE, AGGREGATE_ID, CREATED_AT);
```

Validate JSON constraint/storage syntax against the deployed Oracle edition/version before migration rollout.

## 6. Business transaction

The service creates the event ID before the transaction commits.

```text
@Transactional
business operation
  -> mutate aggregate
  -> insert outbox row with same transaction
commit
```

If commit fails, neither business change nor publication intent exists. If commit succeeds, publication intent survives service/Kafka failure.

## 7. Claim algorithm

The database transaction used to claim work must be short.

Conceptually:

```sql
BEGIN;

SELECT event_id
FROM ews_outbox_event
WHERE (
        status = 'NEW'
        OR (status = 'CLAIMED' AND lease_until < CURRENT_TIMESTAMP)
      )
  AND next_attempt_at <= CURRENT_TIMESTAMP
ORDER BY created_at
FOR UPDATE SKIP LOCKED
FETCH FIRST :batch_size ROWS ONLY;

UPDATE selected_rows
SET status = 'CLAIMED',
    lease_owner = :instance_id,
    lease_until = :lease_until,
    attempt_count = attempt_count + 1;

COMMIT;
```

The exact SQL differs between PostgreSQL and Oracle. Implement the dialect in repository adapters and integration-test it against both databases.

**Never keep this database transaction open while calling Kafka.**

## 8. Publish algorithm

After the claim transaction commits:

```text
for claimed event:
    validate lease ownership
    serialize canonical event
    kafka.send(topic, partitionKey, event)
    await broker result

    if ACK:
        short DB transaction:
            UPDATE ...
            SET status=PUBLISHED,
                published_at=now,
                kafka_partition=?,
                kafka_offset=?,
                lease_owner=null,
                lease_until=null
            WHERE event_id=?
              AND lease_owner=?
              AND status=CLAIMED

    if failure:
        classify error
        calculate bounded exponential backoff + jitter
        release/retry or mark FAILED
```

## 9. Ordering caveat

Multiple publisher workers can claim rows for the same aggregate and Kafka acknowledgements can complete in a different order. If strict aggregate ordering is required, the implementation must preserve it explicitly.

Preferred strategies:

1. Kafka key = aggregate ID and publisher ensures events for one aggregate are dispatched in database sequence order; or
2. persist `aggregate_sequence` and make consumers reject/buffer out-of-sequence state transitions; or
3. shard claims by deterministic aggregate hash so one publisher lane owns an aggregate at a time.

Do not assume `ORDER BY created_at` plus `SKIP LOCKED` alone provides strict aggregate ordering across workers.

For EWS, use an `aggregate_sequence` for domains where transition order materially changes state (facility, repayment, signal lifecycle, case lifecycle). High-volume immutable transaction facts may use source sequence/transaction identity instead.

## 10. Kafka producer baseline

```text
enable.idempotence = true
acks = all
retries > 0
max.in.flight.requests.per.connection <= 5
```

Use supported client defaults where they already satisfy these invariants, but validate effective configuration at startup/operations.

A Kafka transaction is not required merely because an outbox exists. Kafka transactions are introduced when one Kafka processing unit must atomically publish multiple Kafka records and/or commit consumed offsets.

## 11. Retry policy

Retry only transient classes automatically:

```text
BROKER_UNAVAILABLE
TIMEOUT
NETWORK_ERROR
TEMPORARY_AUTH_INFRA_FAILURE
```

Do not endlessly retry deterministic failures:

```text
SCHEMA_INVALID
TOPIC_NOT_ALLOWED
PAYLOAD_POLICY_VIOLATION
UNSUPPORTED_EVENT_VERSION
```

Suggested policy is configurable, e.g. exponential backoff with jitter, bounded maximum delay, and a maximum attempt/age threshold before `FAILED` plus operator alert.

## 12. Kafka outage behaviour

Kafka outage must not block business DB transactions after the outbox insert. The expected behaviour is:

```text
business commits continue
       |
outbox backlog grows
       |
monitor oldest unpublished age + row count
       |
Kafka recovers
       |
publishers drain backlog under controlled rate
```

Capacity planning therefore includes outbox storage for the agreed Kafka recovery window.

## 13. Duplicate publication

This is expected:

```text
publish -> Kafka ACK -> process dies -> DB not marked PUBLISHED
```

After lease expiry the same `event_id` is sent again. Downstream systems therefore deduplicate by stable event ID or apply idempotent versioned updates.

Never generate a new event ID when retrying the same committed business event.

## 14. Consumer inbox pattern

For consumers that update an operational DB:

```sql
BEGIN;

INSERT INTO ews_processed_event(event_id, processed_at)
VALUES (:event_id, CURRENT_TIMESTAMP);

-- uniqueness failure => event already applied; safely acknowledge

-- materialized-state update(s)

COMMIT;
```

The inbox identity and business update belong to the same local transaction.

For high-volume projections, a version-aware idempotent upsert may be more efficient than a permanent inbox table; this is consumer-specific.

## 15. Spring Boot module boundary

Recommended shared components:

```text
ews-event-core
  CanonicalEvent metadata
  EventId generator
  EventSerializer abstraction
  validation

ews-outbox-core
  OutboxEvent
  OutboxWriter
  OutboxRepository SPI
  Publisher state machine
  RetryPolicy
  Metrics

ews-outbox-postgres
  PostgreSQL claim repository

ews-outbox-oracle
  Oracle claim repository

ews-kafka-publisher
  Kafka adapter
  topic resolver
  producer configuration validation
```

Business services depend on the writer/API, not the polling implementation internals.

Avoid a shared library that couples every service to one ORM entity model. The stable boundary is an interface/contract plus database migration templates.

## 16. Observability

Minimum metrics:

```text
outbox_new_total
outbox_claimed_total
outbox_published_total
outbox_failed_total
outbox_retry_total
outbox_backlog_rows
outbox_oldest_unpublished_age_seconds
outbox_publish_latency_seconds
outbox_claim_latency_seconds
outbox_lease_expired_total
outbox_publish_duplicate_risk_total
```

Alerts should emphasize oldest unpublished age and sustained backlog growth, not merely row count.

## 17. Operations

Provide operator actions for:

- inspect failed event without exposing sensitive payload unnecessarily;
- retry selected failed event;
- replay a bounded event range;
- pause publisher;
- drain backlog;
- identify events by aggregate/event ID;
- reconcile DB published metadata with Kafka topic/partition/offset;
- archive/delete published rows after retention.

Every manual retry/requeue action is audited.

## 18. Retention

Published rows are operational publication history, not the permanent compliance evidence store. Retain them for an operational reconciliation window, then archive/delete according to policy. Long-term event/evidence lineage belongs in the governed event/evidence archive defined elsewhere in EWS.
