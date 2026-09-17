# EWS 2.0 — Event Architecture

**Status:** Draft / Part III  
**Primary backbone:** Apache Kafka  
**Reference jurisdictions:** India, United States, United Kingdom  
**Design style:** Event-driven, schema-governed, replayable, evidence-first, jurisdiction-neutral core

## 1. Purpose

Part III defines how authoritative internal facts and market/jurisdiction-specific external intelligence become governed global financial-risk events. It translates the Part-II semantic chain into event contracts without confusing transport delivery with business correctness or source-specific legal semantics with global economic-risk semantics.

```text
EWS-owned services                 External / legacy sources
       |                                   |
transactional outbox        stream/API/bulk/feed/file/CDC
       |                                   |
application publisher               source adapter
       +------------------+----------------+
                          v
             Canonical Observations / Events
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
                 |
          +------+-------+
          |              |
     Human EWS      Jurisdiction adapters
```

## 2. Event architecture principles

### EA-01 — Events describe facts or governed state transitions
Events are not arbitrary integration messages. Commands and queries are separate.

### EA-02 — Source facts precede risk interpretation
`payment.instruction.returned`, `security.interest.created` or `rating.action.published` are observations/facts. EWS signals are downstream interpretations.

### EA-03 — Kafka is a durable event log, not the system of record for every domain
Authoritative operational, registry, court, market and licensed-provider systems retain their authority. Kafka enables propagation/replay/processing.

### EA-04 — Ordering is scoped, never global
Ordering is guaranteed within a partition. Keys follow the smallest aggregate requiring ordered processing.

### EA-05 — Idempotency is mandatory
Every material event has a stable `eventId`; consumers use inbox/version/idempotency semantics as appropriate.

### EA-06 — Delivery semantics are documented per boundary
No platform-wide exactly-once claim.

### EA-07 — Event and knowledge time are first-class
Business/effective time is distinct from publication/observation, knowledge and ingestion time.

### EA-08 — Schemas are governed contracts
Production events use governed schema compatibility; breaking semantics create new contract versions.

### EA-09 — Replay must be safe
Recovery, backfill, reconstruction and counterfactual replay are distinct.

### EA-10 — Sensitive/licensed data is minimized
Events contain required facts/identifiers/references, not full source documents or licensed datasets by convenience.

### EA-11 — Intentional domain events from systems we own; observation adapters for systems we do not
EWS-owned apps use application-managed transactional outbox. External systems use the most appropriate authorised acquisition mechanism.

### EA-12 — Acquisition mode is a source property
External connectors declare `PUSH_STREAM`, `POLL_INCREMENTAL`, `BULK_SNAPSHOT_PLUS_STREAM`, `BULK_SCHEDULED`, `LICENSED_FEED`, `ON_DEMAND` or `MANUAL_VERIFICATION`.

### EA-13 — Global semantics do not erase local provenance
A US UCC record and UK company charge may normalize to a common security-interest observation, but the original legal type, jurisdiction and evidence remain attached.

### EA-14 — Data rights are enforceable metadata
Source rights control retention, redistribution, model-training/LLM use and cross-border processing.

## 3. Event classes

### Source events
Close representation of source-system/market changes.

### Canonical observations/domain events
Normalized facts independent of source naming, e.g.:

```text
payment.instruction.returned
obligation.dpd.changed
facility.limit.changed
financial.statement.received
rating.action.published
management.position.changed
security.interest.created
credit.facility.amended
debt.default.disclosed
insolvency.proceeding.started
market.bond.trade.observed
ownership.control.changed
```

### Feature events
`feature.value.updated`, `feature.quality.degraded`, `feature.recalculated`.

### Signal events
`signal.detected`, `signal.proposed`, `signal.accepted`, `signal.rejected`, `signal.resolved`.

### Risk events
`risk.assessment.proposed`, `risk.assessment.approved`, `risk.score.changed`.

### Classification events
Jurisdiction/institution adapters emit namespaced events such as:

```text
classification.state.proposed
classification.state.approved
classification.state.changed
```

The payload namespace identifies `IN_SMA_NPA`, `US_CECL`, `US_SUPERVISORY_CLASSIFICATION`, `UK_SICR`, `UK_IFRS9_STAGE`, `UK_PRUDENTIAL_DEFAULT`, etc.

### Decision/workflow events
`decision.recorded`, `case.opened`, `case.assigned`, `case.escalated`, `case.closed`.

## 4. Canonical event envelope

Every governed event conceptually carries:

```text
eventId
eventType
eventVersion
producer
entity / aggregate
partitionKey
aggregateVersion
eventTime
effectiveTime
knowledgeTime
ingestedAt
source
jurisdiction (where applicable)
correlationId
causationId
traceId
classification
sourceRightsRef (external where applicable)
schemaRef
payload
```

`eventId` is the principal idempotency identity. `eventTime` is occurrence time; `effectiveTime` legal/economic effectiveness where different; `knowledgeTime` earliest time the institution was entitled to use the fact; `ingestedAt` platform arrival.

## 5. External source checkpoints

Connectors persist source-specific checkpoint state separately from business events:

```text
sourceId
connectorId
cursor/timepoint/sequence
lastSuccessfulPoll
lastObservedSourceTimestamp
lastIngestedEventId
status
recoveryMetadata
```

A connector restart resumes from the authoritative checkpoint and relies on event/source identity deduplication.

Examples include stream timepoints, API pagination/cursors, filing timestamps, bulk snapshot versions and licensed-feed sequences.

## 6. Identity and security resolution before risk interpretation

External observations pass through entity/security resolution before feature/signal processing:

```text
source entity/security ID
        ↓
Identifier Graph
        ↓
canonical counterparty / security
        ↓
match confidence + method
```

Low-confidence matches are quarantined/reviewed or confidence-gated. Name similarity alone cannot trigger a material legal/credit signal.

## 7. Event keys and ordering

| Event family | Preferred key |
|---|---|
| account transactions | accountId |
| repayment obligations | facilityId |
| facility changes | facilityId |
| financial statements | counterpartyId |
| counterparty master | counterpartyId |
| relationship/ownership | relationshipId or sourcePartyId |
| external company/legal events | resolved entityId; source identity before resolution |
| security/market observations | securityId before issuer aggregation |
| feature values | entityId + featureDefinitionId where required |
| signals | counterpartyId for counterparty aggregation |
| classifications | entity/exposure ID + namespace as required |
| cases | caseId |

Market trades should not be keyed directly by counterparty if security-level ordering/scale is required; deliberate repartitioning produces issuer/counterparty aggregates.

## 8. Topic strategy

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
ews.canonical.market

ews.derived.feature
ews.derived.signal
ews.derived.risk
ews.derived.classification
ews.derived.decision
ews.derived.case
```

Broad topic families require a schema-subject strategy that safely supports heterogeneous typed records; this remains a Part-III schema-governance decision.

## 9. Topic retention

Kafka is not the permanent compliance/evidence archive. Event-history retention supports operational replay/recovery; compacted topics support intentional latest-state projections; immutable evidence/documents/history live in governed storage. Licensed-source retention follows source rights.

## 10. Serialization and schema governance

Recommended production default remains Avro + Schema Registry for high-volume Kafka events, with JSON Schema for suitable API/document contracts. Breaking semantic changes create a new event major version.

Market/jurisdiction-specific source payloads do not leak directly into canonical schemas. Source adapters preserve raw evidence/reference and map only governed facts.

## 11. Producer reliability and transactional outbox

EWS-owned services atomically persist business state + immutable outbox event intent in a local DB transaction. A short claim/lease transaction claims rows, Kafka publication occurs outside DB row-lock holding, and acknowledgement/reconciliation updates publication metadata.

Kafka acknowledgement followed by publisher failure can produce a duplicate with the same `eventId`; consumers are idempotent.

External connectors use source checkpoints plus stable source/event identity rather than an application outbox when they do not own the external transaction.

## 12. External/legacy integration preference

For systems EWS does not own:

```text
1. native authorised realtime stream/event
2. supported incremental API
3. snapshot + change stream
4. licensed feed
5. scheduled bulk/file/API
6. CDC where DB observation is the practical authorised mechanism
7. manual verification where automation is not authoritative/allowed
```

The choice is source-specific. CDC is not a platform mandate.

## 13. Consumer idempotency

Kafka-to-Kafka deterministic processing uses stream transactional semantics where justified. DB writers use inbox/version-aware upsert. External side effects use explicit idempotency/reconciliation.

## 14. Processing guarantees

| Boundary | Target semantic |
|---|---|
| service business state + outbox | atomic local DB transaction |
| outbox -> Kafka | at-least-once with stable identity |
| external source -> adapter | source-specific checkpoint + dedup/reconciliation |
| Kafka -> deterministic stream -> Kafka | exactly-once processing where configured/supported |
| Kafka -> DB | effectively-once business outcome |
| Kafka -> external side effect | at-least-once attempt + idempotency/reconciliation |

## 15. Event-time, corrections and late data

External intelligence is frequently late/revised. A company filing may describe an earlier effective event; court/registry status can be corrected; financial statements can be restated; market reference data can be corrected.

Late/corrected facts never manufacture earlier knowledge. Historical EWS replay uses information with `knowledgeTime <= replayTime`.

## 16. Replay/backfill

Recovery replay, projection/feature rebuild, counterfactual backtest and source backfill remain explicit execution modes. Source backfill preserves truthful knowledge/ingestion semantics and source snapshot/version.

## 17. Quarantine and quality

Failure classes include schema invalidity, business validation, unknown/ambiguous entity, security resolution failure, reference data missing, rights-policy violation, transformation failure and exhausted transient retry.

External records also carry `sourceAuthority`, `entityMatchConfidence`, `extractionConfidence`, `corroborationCount` and market-liquidity quality where relevant.

## 18. Stream-processing boundary

Kafka Streams remains Phase-1 default for moderate Kafka-centric stateful processing. Flink is introduced for workloads that justify more sophisticated event-time/watermark/CEP/large-state semantics. Runtime selection does not alter canonical contracts.

## 19. International processing topology

```text
Internal services                    US / UK / India / other sources
      |                                         |
    OUTBOX                           stream/API/feed/bulk/document
      |                                         |
 publisher                                source adapters
      |                                         |
      +-------------------+---------------------+
                          v
                ENTITY / SECURITY RESOLUTION
                          |
                          v
                CANONICAL OBSERVATIONS
                          |
                         Kafka
                          |
               FEATURE COMPUTATION
                          |
                     GLOBAL SIGNALS
                          |
              +-----------+-----------+
              |                       |
       Correlation/EWS        Jurisdiction adapters
              |                       |
        Human validation       accounting/prudential/
              |                 legal classifications
              +-----------+-----------+
                          v
                  Counterparty 360
```

## 20. Part-III remaining specifications

1. finalize schema subject/reference strategy for heterogeneous topic families;
2. extend event catalogue with international external observations;
3. add source-registry/checkpoint and rights contracts;
4. add entity/security-resolution event contracts;
5. define market-data aggregation/repartition strategy;
6. finalize Kafka sizing/retention/HA/security/operability;
7. validate the Part-III contracts end-to-end before PR.