# ADR-003 — Application-Managed Transactional Outbox

**Status:** Accepted for Part III baseline  
**Decision date:** 2026-09-17

## Context

EWS-owned services must atomically persist business state and create the intent to publish a domain event. A direct `database commit -> Kafka send` sequence has a failure gap: the database may commit while the process dies before Kafka accepts the event.

CDC/Debezium can relay an outbox, but making database-log capture a mandatory dependency for every EWS-owned service adds Kafka Connect/connector lifecycle, database log configuration/permissions and another operational failure domain. EWS already controls its Spring Boot services and their database transactions.

## Decision

For EWS-owned transactional services, use an **application-managed transactional outbox**.

```text
Application transaction
  +-- mutate business state
  +-- insert immutable OUTBOX_EVENT
COMMIT
       |
       v
Application Outbox Publisher
       |
       v
Kafka
```

CDC/Debezium is not a required platform component. CDC remains an optional integration adapter for legacy/external systems that EWS cannot modify and where API/file/native-event integration is not adequate.

## Required guarantees

1. Business mutation and outbox insert occur in the same local database transaction.
2. `event_id` is generated once before the transaction commits and never changes.
3. Publisher delivery is **at least once**; duplicate publication is allowed after ambiguous failures.
4. Consumers must provide idempotent/effectively-once business handling using `event_id`, versioned upsert or an equivalent mechanism.
5. Outbox rows are retained long enough for reconciliation and operational audit.
6. Publisher lag, retry count, oldest unpublished age and terminal failures are monitored.
7. Replay/backfill cannot silently trigger external side effects.

## Outbox state model

Minimum logical states:

```text
NEW -> CLAIMED -> PUBLISHED
          |
          +-> NEW        (retryable failure / lease expiry)
          |
          +-> FAILED     (terminal after governed retry policy)
```

`CLAIMED` is logical; an implementation may use row locks without persisting this state. If a persisted claim/lease is used, it must have an expiry/recovery mechanism so a dead publisher cannot strand events.

## Row claiming

Multiple publisher workers may claim independent batches using database-supported queue semantics such as `SELECT ... FOR UPDATE SKIP LOCKED` where supported and tested.

The query must use deterministic ordering, normally `(created_at, event_id)`, and an index that allows finding unpublished rows without scanning the entire outbox.

`SKIP LOCKED` is appropriate here because the outbox is intentionally queue-like; it must not be generalized to ordinary business queries.

## Critical failure window

The publisher cannot atomically commit Kafka and the service database without a distributed transaction. Therefore this sequence is possible:

```text
Kafka accepts event
      |
publisher crashes before DB marks PUBLISHED
      |
row becomes eligible again
      |
Kafka receives duplicate event_id
```

This is an expected architecture condition, not an exceptional bug. Kafka producer idempotence handles producer retry semantics within Kafka's producer protocol; it does not make a later application-level resend of the same outbox row globally unique.

The stable `event_id` is therefore the business idempotency identity.

## Recommended outbox columns

```text
event_id                 UUID / canonical ID, PK
aggregate_type           VARCHAR
aggregate_id             VARCHAR
event_type               VARCHAR
event_version            VARCHAR
partition_key            VARCHAR
payload                   JSON/CLOB/BLOB or serialized envelope
headers                   optional metadata
created_at                timestamp
available_at              timestamp
status                    NEW/PUBLISHED/FAILED
publish_attempts          integer
last_attempt_at           timestamp nullable
published_at              timestamp nullable
last_error_code           nullable
last_error_message        bounded/sanitized nullable
kafka_topic               nullable
kafka_partition           nullable
kafka_offset              nullable
```

Kafka topic/partition/offset are operational reconciliation metadata, not domain truth.

## Publisher transaction strategy

Do **not** hold a database row lock while waiting indefinitely on Kafka network I/O.

Two implementation variants are acceptable:

### Variant A — short lock + lease/claim
1. Lock/select eligible rows with `SKIP LOCKED`.
2. Persist a bounded claim/lease and commit quickly.
3. Publish outside the DB transaction.
4. On Kafka acknowledgement, mark `PUBLISHED`.
5. Expired claims become retryable.

This is preferred when Kafka latency/outages must not hold DB locks.

### Variant B — lock during small publish batch
Acceptable only after load testing proves Kafka acknowledgement latency and batch size keep lock duration safely bounded. Simpler, but less resilient during broker/network degradation.

**Phase-1 recommendation: Variant A.**

## Ordering

Database claim order does not by itself establish Kafka ordering across concurrent publisher workers.

Ordering requirements are scoped by aggregate. `aggregate_id`/`partition_key` maps related events to the same Kafka partition. If strict per-aggregate production order is required, publisher concurrency must prevent later sequence numbers for the same aggregate from overtaking earlier unpublished events.

Where ordering is material, include:

```text
aggregate_version / sequence_number
```

Consumers detect gaps, duplicates and stale transitions instead of trusting arrival order blindly.

## Payload strategy

For normal domain events, persist the immutable event envelope/payload in the outbox so the exact committed event intent is recoverable.

Do not have the publisher reconstruct event payload from mutable business tables after commit; doing so can publish a representation of later state rather than the state associated with the original transaction.

Large documents/binaries are stored externally and referenced by immutable evidence/document IDs and hashes.

## Cleanup and retention

Published rows are not deleted immediately. Retain according to operational reconciliation requirements, then archive/purge in bounded batches.

Partitioning by creation date may be introduced when volume justifies it. Cleanup must never contend materially with transactional inserts or publisher scans.

## Backpressure

Kafka outage must not cause business transactions to wait for Kafka. The outbox absorbs temporary publication failure subject to database capacity.

Controls:

- alert on oldest NEW/claimed-expired age;
- alert on unpublished count/growth rate;
- retry with exponential backoff and jitter;
- circuit-break publishing during systemic broker failures;
- capacity threshold and runbook before the outbox threatens the operational DB;
- explicit recovery drain strategy after Kafka returns.

## Consumer idempotency

Database-writing consumers use a transactional inbox where practical:

```text
BEGIN
  INSERT processed_event(event_id, processed_at)
  -- unique constraint on event_id
  apply business/materialized-state change
COMMIT
```

If the unique insert indicates prior processing, the consumer acknowledges/skips the duplicate. For naturally idempotent projections, a version-aware upsert may replace the inbox, but this must be demonstrated per consumer.

## Alternatives considered

### Debezium + outbox router
**Not baseline.** Strong option for CDC-centric estates and legacy integration, but unnecessary mandatory infrastructure for services EWS owns.

### Direct DB commit followed by Kafka publish
**Rejected for material state changes.** Can lose events after successful DB commit.

### Kafka-first/event sourcing
**Deferred.** Can provide a stronger event-native model but would make Kafka/event log the authoritative write model and materially increase migration complexity.

### XA / distributed transaction
**Rejected as baseline.** Couples availability/failure handling across database and Kafka and is not justified for the EWS architecture.

### Database-native queue/event mechanism
**Allowed as a specialized adapter, not platform default.** May be useful for specific source databases, but would couple event transport semantics to database vendor capabilities.

## Consequences

### Positive
- fewer mandatory infrastructure components;
- explicit application ownership of domain-event intent;
- easy reconciliation from service DB to Kafka;
- works naturally with Spring transactional boundaries;
- CDC remains optional rather than architectural dependency.

### Costs
- an outbox publisher component/library must be engineered and standardized;
- publisher lag and table growth become operational concerns;
- duplicate publication remains possible and consumers must be idempotent;
- ordering across concurrent workers requires deliberate design;
- every supported database needs tested claiming/locking SQL semantics.

## Standardization requirement

Do not let every microservice invent its own outbox implementation. EWS should provide a shared Spring Boot starter/library containing:

- outbox persistence API;
- canonical event-envelope serialization;
- publisher worker;
- claim/lease implementation;
- Kafka producer configuration;
- metrics/tracing;
- retry/error policy;
- reconciliation hooks;
- database dialect support;
- integration/chaos tests for failure windows.

Application teams create domain events; the platform library owns reliable publication mechanics.
