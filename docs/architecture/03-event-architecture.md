# EWS 2.0 — Event Architecture

**Status:** Draft / Part III  
**Primary backbone:** Apache Kafka  
**Design style:** Event-driven, schema-governed, replayable, evidence-first

## 1. Purpose

Part III defines how authoritative facts and derived financial-risk intelligence move through EWS 2.0. It translates the Part-II semantic chain into event contracts and processing boundaries without confusing Kafka delivery semantics with business correctness.

```text
EWS-owned services                 Legacy/external sources
       |                                   |
transactional outbox              API / file / native event / CDC
       |                                   |
application publisher             source adapter
       +------------------+----------------+
                          v
                Canonical Domain Events
                          v
                        Kafka
                 +--------+--------+
                 |                 |
          Stream Processing   Evidence/History
                 |
          Feature Computation
                 v
             Feature Events
                 v
             Signal Engines
                 v
             Signal Events
                 v
        Correlation/Risk Assessment
                 v
          Human Decision / Case
```

## 2. Event architecture principles

### EA-01 — Events describe facts or governed state transitions
Events are not arbitrary integration messages. Commands and queries are separate concepts.

### EA-02 — Source facts precede risk interpretation
`payment.returned` is a source/domain fact. `REPEATED_PAYMENT_RETURN` is a derived EWS signal. They must not share the same semantic contract.

### EA-03 — Kafka is a durable event log, not the system of record for every domain
Authoritative operational systems remain authoritative for booked transactions/contracts. Kafka enables propagation, replay and stream processing.

### EA-04 — Ordering is scoped, never global
Ordering is guaranteed only within a Kafka partition. Partition keys therefore follow the smallest aggregate requiring ordered processing.

### EA-05 — Idempotency is mandatory
Every material event has a stable `eventId`; consumers with external side effects use event/inbox IDs or equivalent idempotency mechanisms.

### EA-06 — Delivery semantics are documented per boundary
Do not claim platform-wide exactly-once. Kafka producer idempotence, Kafka transactions, stream processors, databases and external APIs have different failure boundaries.

### EA-07 — Event time is first-class
Business event/effective time is distinct from observed/knowledge and ingestion/processing time.

### EA-08 — Schemas are governed contracts
Production domain/derived events use Schema Registry and compatibility checks. Breaking semantic changes create new event/schema versions.

### EA-09 — Replay must be safe by design
Consumers distinguish normal processing from rebuild/backfill where necessary; externally visible side effects are not blindly repeated during replay.

### EA-10 — Sensitive data is minimized
Events contain identifiers and required analytical facts, not entire source records/documents by convenience. Large/sensitive artefacts live in governed stores and are referenced.

### EA-11 — Intentional domain events from systems we own; observation adapters for systems we do not
EWS-owned applications publish intentional domain events through an application-managed transactional outbox. CDC is optional and primarily an anti-corruption/integration mechanism for systems EWS cannot change.

## 3. Event classes

### Source events
Close representation of source-system changes or externally observed facts. Used primarily at ingestion boundaries.

### Canonical domain events
Normalized business facts independent of source-system naming.

Examples:

```text
payment.instruction.returned
obligation.payment.received
facility.limit.changed
facility.drawing_power.changed
trade_finance.lc.devolved
financial.statement.received
rating.action.published
management.director.resigned
```

### Feature events
Versioned feature-state changes derived from observations.

```text
feature.value.updated
feature.quality.degraded
feature.became_stale
feature.recalculated
```

### Signal events
Governed EWS detections and lifecycle transitions.

```text
signal.detected
signal.proposed
signal.accepted
signal.rejected
signal.severity.changed
signal.resolved
signal.reopened
```

### Risk events
Risk-assessment state changes.

```text
risk.assessment.proposed
risk.assessment.approved
risk.score.changed
risk.dimension.changed
```

### Decision/workflow events
Human-governance facts.

```text
decision.recorded
case.opened
case.assigned
case.escalated
case.closed
```

## 4. Canonical event envelope

Every governed event carries a common envelope conceptually containing:

```text
eventId
eventType
eventVersion
producer
entity / aggregate
partitionKey
aggregateVersion (where ordering/version checks are required)
eventTime
effectiveTime
knowledgeTime
ingestedAt
source
correlationId
causationId
traceId
classification
schemaRef
payload
```

Definitions:

- `eventId`: globally unique immutable event identity and principal idempotency identity.
- `eventType`: semantic event name.
- `eventVersion`: semantic contract version, independent of Schema Registry numeric ID.
- `producer`: producing bounded context/service/connector and version.
- `entity`: principal business entity affected.
- `partitionKey`: explicit logical key used for ordered processing.
- `aggregateVersion`: monotonic version/sequence where consumers must detect gaps/stale transitions.
- `eventTime`: when the originating business event occurred.
- `effectiveTime`: when the asserted fact became economically/legally effective where different.
- `knowledgeTime`: earliest time EWS was entitled to know/use the fact.
- `ingestedAt`: platform ingestion timestamp.
- `correlationId`: links events belonging to a business/process flow.
- `causationId`: identifies the event/command that directly caused this derived event.
- `traceId`: technical distributed trace correlation.
- `classification`: data sensitivity/retention metadata.
- `schemaRef`: logical schema name/version where useful for audit.

## 5. Time model

Kafka record timestamp alone is insufficient for financial-risk semantics. The payload/envelope explicitly retains business time and knowledge time.

Example:

```text
Financial period end       2026-03-31
Statement signed           2026-05-20
Received by institution    2026-05-22
Parsed                     2026-05-22 10:31
Analyst corrected metric   2026-05-24 15:10
```

Historical EWS replay for 2026-05-23 may use the parsed information known by then but cannot use the analyst correction learned on 2026-05-24.

## 6. Event keys and ordering

The default key is the aggregate whose events must be processed in order, not automatically `counterpartyId` for every topic.

| Event family | Preferred key | Reason |
|---|---|---|
| account transactions | accountId | account-local ordering and scale |
| repayment obligations | facilityId | facility repayment state |
| facility changes | facilityId | facility consistency |
| financial statements | counterpartyId | corporate financial sequence |
| counterparty master | counterpartyId | entity state |
| relationship changes | relationshipId or sourcePartyId by topology | graph update semantics |
| feature values | entityId + featureDefinitionId where required | feature-state ordering |
| signals | counterpartyId for counterparty-level signal processing | ordered signal/risk aggregation |
| cases | caseId | workflow ordering |

Avoid a universal `counterpartyId` key for high-volume account transactions: one large entity could create a hot partition and unnecessarily serialize unrelated accounts.

Where concurrent publishers can produce events for the same aggregate, partitioning alone is insufficient to guarantee the producer submits them in business order. Material aggregates use `aggregateVersion`/sequence checks or a publisher strategy that prevents overtaking.

## 7. Topic strategy

Use domain/event-family topics rather than one topic per event type or one enterprise mega-topic.

```text
ews.canonical.counterparty
ews.canonical.facility
ews.canonical.account-transaction
ews.canonical.repayment
ews.canonical.trade-finance
ews.canonical.financial-statement
ews.canonical.collateral
ews.canonical.rating
ews.canonical.relationship
ews.canonical.external-intelligence

ews.derived.feature
ews.derived.signal
ews.derived.risk
ews.derived.decision
ews.derived.case
```

Environment is preferably a deployment/cluster namespace rather than embedded into semantic event names where platform tooling supports it. Do not create one topic per counterparty, feature or signal type.

## 8. Topic retention categories

### Event-history topics
Deletion retention sized for operational replay/recovery and downstream rebuild objectives.

### Current-state/changelog topics
Compaction where the topic intentionally represents latest keyed state. Tombstone semantics must be explicit.

### Audit evidence
Kafka retention is not the compliance archive. Long-term immutable evidence/event history is persisted in governed historical/object storage with hashes/lineage.

### Quarantine topics
Invalid/unprocessable records retained separately with error metadata and access controls.

## 9. Serialization and schema governance

Recommended production default: **Avro + Schema Registry** for high-volume canonical/derived Kafka events, while retaining JSON Schema for API/document-facing contracts where appropriate.

Protobuf remains a valid alternative where RPC/message reuse and generated types dominate; do not mix formats arbitrarily inside one event family.

Compatibility recommendation for durable event topics: `BACKWARD_TRANSITIVE` initially, with stronger `FULL_TRANSITIVE` selectively where old consumers must safely process new producer data. Breaking semantic changes require a new event major version rather than disabling compatibility.

Kafka keys should remain simple deterministic scalar/string IDs wherever possible so schema evolution cannot silently alter partitioning.

## 10. Producer reliability

For direct Kafka producers and the shared outbox publisher:

- producer idempotence enabled;
- `acks=all`;
- retries managed through supported idempotent-producer semantics;
- stable event IDs generated before publication;
- Kafka transactions used only where atomic Kafka multi-record/offset-write semantics are actually required.

Kafka producer idempotence protects against duplicate writes caused by retry within Kafka producer protocol semantics. It does **not** remove the database-outbox/Kafka acknowledgement ambiguity. The stable `eventId` remains the cross-boundary business idempotency identity.

## 11. Application-managed transactional outbox

EWS-owned services that must atomically persist business state and create an event use:

```text
DB transaction
  +-- update business tables
  +-- insert immutable OUTBOX_EVENT containing committed event intent
COMMIT
       |
       v
short claim/lease transaction
       |
       v
Application Outbox Publisher
       |
       v
Kafka
       |
       v
mark PUBLISHED / reconciliation metadata
```

The publisher does not reconstruct the event from mutable business tables after commit. The outbox retains the immutable event payload/envelope or immutable references needed to reproduce it.

### Claiming

Multiple publisher workers claim small batches using tested database queue semantics, e.g. `FOR UPDATE SKIP LOCKED` where supported. The preferred Phase-1 implementation uses a **short persisted claim/lease**, commits the DB transaction, then performs Kafka network I/O outside the lock-holding transaction.

Expired claims are recoverable. Kafka acknowledgement followed by publisher failure may cause the row to be sent again; this is expected and handled through `eventId` idempotency.

### Backpressure

Kafka unavailability creates an outbox backlog, not a synchronous dependency for the business transaction. Monitor:

- oldest unpublished event age;
- NEW/claimed/failed counts;
- publication throughput;
- retry/error rate;
- claim expiry rate;
- database capacity consumed by retained outbox rows.

See `ADR-003-application-managed-transactional-outbox.md`.

## 12. Legacy/source integration and CDC

Integration preference for systems EWS does not own:

```text
1. native governed event
2. supported API/integration feed
3. scheduled/file integration where latency permits
4. CDC where database change observation is the practical mechanism
```

CDC is therefore optional, not a mandatory EWS platform dependency.

Where CDC is used, raw table changes remain source events. An anti-corruption/normalization adapter converts them into governed canonical observations/domain events. Physical table schemas must not become long-lived enterprise contracts.

## 13. Consumer idempotency

### Pure deterministic Kafka-to-Kafka transformations
Use Kafka/stream-processing transactional semantics where supported and justified.

### Database-writing consumers
Use a transactional inbox or version-aware idempotent upsert.

```text
BEGIN
  INSERT processed_event(event_id) -- unique
  apply state/projection change
COMMIT
```

### External side-effect consumers
Notifications, external APIs and workflow actions require explicit idempotency keys and replay suppression/reconciliation policies.

An event redelivery must not create a second analyst/customer notification merely because processing restarted.

## 14. Processing guarantees

| Boundary | Target semantic |
|---|---|
| service business state + outbox row | atomic local DB transaction |
| application outbox -> Kafka | at-least-once publication with stable event identity |
| direct Kafka producer | idempotent production; Kafka transactions where justified |
| Kafka -> deterministic stream -> Kafka | exactly-once processing where configured/supported |
| Kafka -> operational DB | effectively-once business outcome via inbox/versioned upsert |
| Kafka -> external API/notification | at-least-once attempt + external/business idempotency and reconciliation |
| optional legacy CDC -> normalization | source-specific; canonical output remains idempotent/versioned |

The architecture deliberately uses **effectively-once business outcomes** where a global distributed transaction would be brittle or impossible.

## 15. Event-time and late events

Financial data is frequently late and out of order. Stream processing therefore uses event time for time-sensitive features/windows and configurable watermarks/allowed lateness.

Late events are not globally discarded. Policy determines whether they update an open window, create a correction/retraction, trigger feature recalculation, produce a revised signal, are stored only for historical accuracy, or require manual reconciliation.

Example: a payment-return event arriving two days late may change `returned_payment_count_30d`; a six-month-late restated financial statement may create revised historical features without pretending the bank knew them six months earlier.

## 16. Replay and backfill

Replay modes are explicit:

- **recovery replay:** reproduce missed processing after failure;
- **projection rebuild:** rebuild a materialized view/search index;
- **feature rebuild:** recompute feature history under the same definition;
- **counterfactual backtest:** recompute under a new rule/model and write to isolated outputs;
- **source backfill:** introduce historical records not previously ingested.

Replays carry execution metadata and must not overwrite `knowledgeTime` or trigger uncontrolled external side effects.

## 17. Invalid events and quarantine

Do not use a DLQ as a generic graveyard. Separate failure classes:

```text
SCHEMA_INVALID
BUSINESS_VALIDATION_FAILED
UNKNOWN_ENTITY
REFERENCE_DATA_MISSING
TRANSFORMATION_FAILED
CONSUMER_RETRY_EXHAUSTED
SECURITY_POLICY_FAILED
```

Invalid source records go to quarantine with source identity, error code, timestamps and trace IDs. Recoverable downstream failures use bounded retry/retry topics or platform-native retry mechanisms. Remediation/replay is auditable.

## 18. Stream-processing engine boundary

### Kafka Streams
Preferred for Phase-1 service-local/stateful transformations where topology is moderate, processing is tightly integrated with Spring Boot/Java, Kafka is the principal source/sink, and simple joins/windows/aggregations dominate.

### Apache Flink
Introduce where requirements justify complex event-time processing, heterogeneous streams/sources, sophisticated watermarks/late-data handling, large stateful joins/patterns, advanced CEP, or an independent streaming platform.

**Recommendation:** Kafka Streams first for the Phase-1 EWS spine; validate Flink against specific workloads rather than introducing it merely because the platform is event-driven.

## 19. Initial processing topology

```text
EWS services             Legacy/external
     |                         |
  OUTBOX                 API/file/CDC
     |                         |
 publisher                 adapters
     +-----------+-------------+
                 v
       CANONICAL DOMAIN TOPICS
                 |
       +---------+----------+
       |                    |
 Evidence/history     Feature processors
                            |
                            v
                     FEATURE EVENTS/STORE
                            |
                     +------+-------+
                     |              |
                   Rules        Anomaly/ML
                     |              |
                     +------+-------+
                            v
                         SIGNALS
                            |
                    Correlation engine
                            |
                            v
                     RISK ASSESSMENT
                            |
                            v
                    HUMAN VALIDATION
```

## 20. Part-III next specifications

1. Canonical event envelope Avro schema.
2. Application outbox SQL/logical contract and Spring publisher reference design.
3. Topic catalogue and key/partition matrix.
4. Event-type catalogue for Phase-1 signals/features.
5. Schema compatibility/versioning rules.
6. Replay/quarantine operating model.
7. Kafka sizing and retention model based on assumed scale.
8. Kafka Streams versus Flink workload decision ADR.
