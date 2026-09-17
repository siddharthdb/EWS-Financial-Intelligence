# EWS 2.0 — Event Architecture

**Status:** Draft / Part III  
**Primary backbone:** Apache Kafka  
**Design style:** Event-driven, schema-governed, replayable, evidence-first

## 1. Purpose

Part III defines how authoritative facts and derived financial-risk intelligence move through EWS 2.0. It translates the Part-II semantic chain into event contracts and processing boundaries without confusing Kafka delivery semantics with business correctness.

```text
Source Systems
   |
   +-- API / application events
   +-- transactional outbox
   +-- CDC
   +-- batch/file/document ingestion
   +-- external feeds
   v
Ingestion / Source Adapters
   v
Canonical Domain Events
   v
Kafka
   +--> Stream Processing
   +--> Feature Computation
   +--> Evidence / Historical Stores
   +--> Search / Analytics
   v
Feature Events
   v
Signal Engines
   v
Signal Events
   v
Correlation / Risk Assessment
   v
Decision / Case Workflow
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
Do not claim platform-wide exactly-once. Kafka producer idempotence, Kafka transactions, Flink checkpoints, databases and external APIs have different failure boundaries.

### EA-07 — Event time is first-class
Business event/effective time is distinct from observed/knowledge and ingestion/processing time.

### EA-08 — Schemas are governed contracts
Production domain/derived events use Schema Registry and compatibility checks. Breaking semantic changes create new event/schema versions.

### EA-09 — Replay must be safe by design
Consumers distinguish normal processing from rebuild/backfill where necessary; externally visible side effects are not blindly repeated during replay.

### EA-10 — Sensitive data is minimized
Events contain identifiers and required analytical facts, not entire source records/documents by convenience. Large/sensitive artefacts live in governed stores and are referenced.

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

- `eventId`: globally unique immutable event identity.
- `eventType`: semantic event name.
- `eventVersion`: semantic contract version, independent of Schema Registry numeric ID.
- `producer`: producing bounded context/service/connector and version.
- `entity`: principal business entity affected.
- `partitionKey`: explicit logical key used for ordered processing.
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

Examples:

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

Avoid a universal `counterpartyId` key for high-volume account transactions: one large corporate/retail entity could create a hot partition and unnecessarily serialize unrelated accounts.

## 7. Topic strategy

Use domain/event-family topics rather than one topic per event type or one enterprise mega-topic.

Initial logical families:

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

Environment is preferably a deployment/cluster namespace rather than embedded into semantic event names where platform tooling supports it. Physical naming standards are finalized during implementation design.

Do not create one topic per counterparty, feature or signal type.

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

Rationale:

- compact wire representation;
- mature schema evolution semantics;
- Java/Python ecosystem support;
- strong fit for Kafka/CDC tooling;
- schema IDs avoid carrying full schemas in every record.

Protobuf remains a valid alternative where RPC/message reuse and generated types dominate; do not mix formats arbitrarily inside one event family.

Compatibility recommendation for durable event topics: `BACKWARD_TRANSITIVE` initially, with stronger `FULL_TRANSITIVE` selectively where old consumers must safely process new producer data. Breaking semantic changes require a new event major version rather than disabling compatibility.

Kafka keys should remain simple deterministic scalar/string IDs wherever possible so schema evolution cannot silently alter partitioning.

## 10. Producer reliability

For direct Kafka producers:

- producer idempotence enabled;
- `acks=all`;
- retries managed through supported idempotent-producer semantics;
- stable event IDs generated before publish;
- transactions used only where atomic multi-record/offset-write semantics are actually required.

Kafka producer idempotence protects against duplicate writes caused by producer retry within its defined scope. It does not eliminate application-level duplicate business events or duplicate external side effects.

## 11. Transactional outbox

Services that must atomically persist business state and publish a domain event use the transactional outbox pattern:

```text
DB transaction
  +-- update business tables
  +-- insert immutable outbox row
COMMIT
       |
       v
Debezium CDC
       |
       v
Outbox Event Router
       |
       v
Kafka
```

Outbox rows include at least event ID, aggregate type/ID, event type/version, event timestamp and payload/reference metadata.

The aggregate ID normally becomes the Kafka message key to preserve aggregate ordering.

Do not implement `DB commit -> application calls Kafka` as the default for state-changing services where loss between the two operations would be material.

## 12. CDC strategy

Use CDC for:

1. legacy/source systems that cannot natively publish governed events;
2. transactional outbox relay;
3. selected state synchronization where change-log semantics are explicitly understood.

Raw table CDC is not automatically a canonical business event. A normalization layer converts source changes into governed observations/domain events.

Avoid exposing physical source table schemas as long-lived enterprise contracts.

## 13. Consumer idempotency

Consumers are categorized:

### Pure deterministic Kafka-to-Kafka transformations
Can use Kafka/Flink transactional/checkpoint capabilities where supported.

### Database-writing consumers
Persist processed `eventId`/inbox identity in the same transaction as the materialized state change, or use an equivalent idempotent upsert/version strategy.

### External side-effect consumers
Notifications, external APIs and human workflow actions require explicit idempotency keys and replay suppression policies.

An event being redelivered must not send a second customer/analyst notification merely because Kafka processing restarted.

## 14. Processing guarantees

Guarantees are stated per pipeline:

| Boundary | Target semantic |
|---|---|
| source DB + outbox row | atomic local DB transaction |
| Debezium outbox -> Kafka | at-least-once delivery with stable event identity; downstream idempotency |
| direct Kafka producer | idempotent production; transactions where justified |
| Kafka -> deterministic stream -> Kafka | exactly-once processing where configured/supported |
| Kafka -> operational DB | effectively-once business outcome via transactional inbox/upsert |
| Kafka -> external API/notification | at-least-once attempt + idempotency/reconciliation |

The architecture deliberately uses **effectively-once business outcomes** where a global distributed transaction would be brittle or impossible.

## 15. Event-time and late events

Financial data is frequently late and out of order. Stream processing therefore uses event time for time-sensitive features/windows and configurable watermarks/allowed lateness.

Late events are not globally discarded. Policy determines whether they:

- update an open window;
- create a correction/retraction;
- trigger feature recalculation;
- produce a revised signal;
- are stored for historical accuracy only;
- require manual reconciliation.

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
Preferred for Phase-1 service-local/stateful transformations where:

- topology is moderate;
- processing is tightly integrated with Spring Boot/Java;
- Kafka is the principal source/sink;
- simple joins, windows and aggregations dominate.

### Apache Flink
Introduce where requirements justify it:

- complex event-time processing;
- multiple heterogeneous streams/sources;
- sophisticated watermarks/late-data handling;
- large stateful joins/patterns;
- advanced CEP;
- independent streaming platform/team.

Recommendation: **Kafka Streams first for the Phase-1 EWS spine; validate Flink against specific workloads rather than introducing it merely because the platform is event-driven.**

## 19. Initial processing topology

```text
SOURCE / OUTBOX / CDC
       |
       v
source.* / connector boundary
       |
       v
Normalization + validation + entity resolution
       |
       v
CANONICAL DOMAIN TOPICS
       |
       +--> Evidence/history sink
       |
       +--> Feature processors
                 |
                 v
          FEATURE EVENTS/STORE
                 |
          +------+-------+
          |              |
       Rules         Anomaly/ML
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

1. Canonical event envelope schema.
2. Topic catalogue and key/partition matrix.
3. Event-type catalogue for the 25 Phase-1 signals/features.
4. Outbox contract.
5. Schema compatibility/versioning rules.
6. Replay/quarantine operating model.
7. Kafka sizing and retention model based on assumed scale.
8. Kafka Streams versus Flink workload decision matrix/ADR.
