# EWS 2.0 — Phase-1 Stream Processing Design

**Status:** Draft / Part III  
**Primary runtime:** Kafka Streams for Phase 1  
**Scope:** Canonical facts → governed features → signal evaluation

## 1. Objective

Define how EWS 2.0 computes low-latency, reproducible features and signals from canonical events while preserving event-time semantics, point-in-time correctness, deduplication, replay safety and audit lineage.

The design deliberately separates:

```text
FACT PROCESSING       FEATURE PROCESSING        RISK INTERPRETATION
canonical event  ->   governed feature     ->  signal policy
```

A stream processor may calculate `returned_payment_count_30d`; it must not decide that the borrower is risky unless a separately versioned signal policy evaluates that feature.

## 2. Phase-1 processing applications

Do not build one giant Kafka Streams topology for the whole platform. Start with bounded processing applications:

```text
repayment-feature-processor
liquidity-feature-processor
financial-feature-processor
conduct-feature-processor
signal-policy-engine
risk-correlation-processor
```

Each has an independent `application.id`, deployment lifecycle, state/changelog namespace and scaling boundary.

## 3. Baseline Kafka Streams configuration

Production baseline:

```properties
processing.guarantee=exactly_once_v2
num.standby.replicas=1
```

Additional producer/consumer/runtime parameters are performance-tested rather than copied blindly from examples.

`exactly_once_v2` is used for Kafka-input → Kafka-state/output boundaries. It does not extend the guarantee to arbitrary databases, HTTP APIs, notifications or human workflow side effects.

State directories are local acceleration/state materialization, not the only durable copy. Kafka Streams changelog topics restore fault-tolerant state after task migration or node failure.

## 4. Timestamp semantics

The Streams record timestamp for analytical windows is extracted from the canonical event's semantic timestamp, normally `eventTime` or a contract-specific effective timestamp.

Do not default financial analytics to broker ingestion time.

Timestamp choice is declared by event family:

| Event | Window timestamp |
|---|---|
| payment.instruction.returned | returnOccurredAt / eventTime |
| obligation.dpd.changed | effectiveTime |
| account transaction | transaction posting/value time per feature definition |
| financial statement received | knowledgeTime for availability; financial period for financial-period calculations |
| rating action | effective/published time plus knowledgeTime gate |

`knowledgeTime` remains available for point-in-time eligibility even where another timestamp drives the event-time window.

## 5. Deduplication

Deduplication occurs before stateful feature calculation.

Primary identity:

```text
eventId
```

For source systems capable of sending semantically duplicate events under different platform event IDs, source-specific uniqueness can additionally use:

```text
sourceSystem + sourceRecordId + sourceVersion/eventSequence
```

A durable/changelogged key-value store retains recently processed identities for the deduplication horizon appropriate to the event family.

Do not use payload hashes as the sole duplicate identity: two legitimate transactions can have identical payload values.

## 6. Returned-payment topology

Input:

```text
ews.canonical.repayment
  payment.instruction.returned
```

Logical topology:

```text
validate envelope
      ↓
deduplicate eventId/source identity
      ↓
classify return reason under versioned reason-code policy
      ↓
exclude non-risk technical returns from analytical count where policy requires
      ↓
re-key by facilityId or governed analytical entity
      ↓
update returned-payment event state
      ↓
compute rolling features
      ├── returned_payment_count_30d
      ├── returned_payment_value_30d
      └── returned_payment_rate_30d (when denominator available)
      ↓
compare against prior emitted feature snapshot
      ↓
feature.value.updated
```

The processor emits features; it does not hard-code `count >= 3` as a universal EWS rule.

## 7. Rolling-window implementation

The business feature `returned_payment_count_30d` is a rolling lookback feature, not merely a calendar/tumbling-window report.

Two implementation options are valid:

### Option A — window-store based rolling calculation

Maintain event-time keyed records for the retained horizon and calculate the active 30-day range.

Advantages:
- semantically direct;
- supports correction/removal where source events are revised;
- useful when multiple rolling horizons are required.

### Option B — bucketed aggregate

Maintain daily/hourly buckets and combine the last N buckets.

Advantages:
- bounded state;
- efficient for very high event volume.

Phase-1 recommendation: daily bucketed aggregates for count/value features plus retained event identities/evidence references needed for audit. Validate precision at day boundaries against credit-policy requirements.

Do not model a rolling 30-day feature as a fixed 30-day tumbling window; the semantics are different.

## 8. Grace and late data

Kafka Streams window grace controls how long out-of-order records are accepted before a window is considered closed. Grace is therefore a processing policy, not a business definition.

For EWS, do not globally drop every event arriving after a grace period. Records outside the normal online correction horizon are routed to a late-event path:

```text
normal event-time horizon
       ↓
update online state/features

beyond online grace/horizon
       ↓
ews.processing.late-event
       ↓
late-data policy
       ├── historical correction only
       ├── feature recalculation
       ├── revised signal evaluation
       └── manual reconciliation
```

The original `eventTime` and `knowledgeTime` remain unchanged.

## 9. Feature emission semantics

A processor does not emit a noisy update for every input event unless the feature materially changed.

Feature definition controls emission policy:

```text
ON_CHANGE
ON_MATERIAL_CHANGE
ON_WINDOW_CLOSE
ON_SCHEDULE
ON_QUALITY_CHANGE
```

For `returned_payment_count_30d`, `ON_CHANGE` is appropriate because each qualifying returned payment changes the feature.

For high-volume utilization data, `ON_MATERIAL_CHANGE` or scheduled snapshots may be more appropriate.

Every emitted feature value includes:

- feature definition/version;
- entity/grain;
- effective/window times;
- knowledge time;
- value state;
- value/unit;
- evidence/observation lineage;
- quality state;
- transformation version.

## 10. Signal policy topology

Input:

```text
ews.derived.feature
```

Policy definitions are loaded from a governed policy store/configuration source and versioned/effective-dated.

Logical flow:

```text
feature.value.updated
       ↓
resolve policies applicable to
  feature + segment + product + jurisdiction + effective date
       ↓
quality gate
       ↓
evaluate condition/window/context
       ↓
load existing signal episode
       ↓
new / unchanged / severity change / resolution / recurrence
       ↓
produce signal lifecycle event
```

A policy evaluation result records the exact policy ID/version and feature snapshot IDs used.

## 11. Signal episode state

State key conceptually:

```text
entityId + signalType + policyId
```

State includes:

```text
episodeId
state
openedAt
lastObservedAt
peakSeverity
currentSeverity
occurrenceCount
lastFeatureSnapshotIds
lastEvidenceIds
cooldownUntil
resolvedAt
lastPolicyVersion
```

This prevents an analyst from receiving a new `REPEATED_PAYMENT_RETURN` alert every time another return arrives while the same episode is already active.

A new analyst-visible event is normally emitted only for:

- initial detection;
- material severity/materiality/confidence change;
- new material evidence;
- escalation;
- resolution;
- recurrence after the governed reset/cooldown semantics.

## 12. DPD topology

`obligation.dpd.changed` is treated differently from a rolling aggregation because the source event already contains an authoritative delinquency state transition.

```text
obligation.dpd.changed
       ↓
deduplicate
       ↓
validate sequence / stale transition
       ↓
materialize current DPD state
       ↓
derive
  current_dpd
  max_dpd_30d
  max_dpd_90d
       ↓
feature.value.updated
       ├── EWS policy evaluation
       └── Regulatory Classification Adapter
```

The regulatory adapter and EWS policy engine are separate consumers. Neither rewrites the canonical DPD fact.

## 13. Out-of-order aggregate transitions

Where events carry `aggregateSequence`, stateful processors retain the latest accepted sequence.

```text
incoming < current       -> STALE/DUPLICATE path
incoming = current       -> duplicate/idempotency check
incoming = current + 1   -> normal apply
incoming > current + 1   -> sequence gap
```

A sequence gap does not automatically mean data loss: source systems can have event-family-specific sequencing. The contract declares whether the sequence is contiguous. For contiguous sources, a gap triggers reconciliation before authoritative state replacement.

Immutable transaction facts may not require aggregate sequence ordering if their business semantics are additive and identity-based.

## 14. Corrections and reversals

Never silently mutate a prior fact.

Source corrections use an explicit semantic pattern:

```text
original event
      ↓
correction/reversal event
      ↓
state processor applies compensating change
      ↓
feature recalculated
      ↓
new feature snapshot
      ↓
signal policy re-evaluated
```

The prior feature/signal remains in historical lineage.

## 15. State stores

Phase-1 logical stores include:

```text
processed-event-store
returned-payment-bucket-store
returned-payment-evidence-store
dpd-current-state-store
dpd-history-store
feature-last-emitted-store
signal-episode-store
policy-cache-store
```

Persistent fault-tolerant stores use Kafka Streams changelogging. Local RocksDB is implementation state, not the compliance archive.

## 16. State restoration and availability

When a stateful task moves or restarts, Kafka Streams restores its state from changelog topics before active processing resumes. Standby replicas reduce recovery time by maintaining replicated local state.

For Phase 1:

```properties
num.standby.replicas=1
```

is the baseline for material stateful processors, subject to cluster sizing and availability testing.

Operational SLOs must measure:

- restore lag;
- restoration duration;
- standby lag;
- processing lag;
- state-store size;
- rebalance duration.

## 17. Replay modes

### Recovery replay
Same application ID/state lineage; recover after failure using committed offsets and changelogs.

### Projection/feature rebuild
Use a controlled rebuild deployment with isolated application ID/output namespace. Do not reset production offsets and hope for the best.

### Counterfactual policy replay
Historical canonical/feature events + a different policy version write to a backtest namespace, never the official signal topic.

### Corrected-history rebuild
May use information learned later only when explicitly producing an `AS_CORRECTED` analytical view. It must not overwrite the `AS_KNOWN_AT_TIME` history.

## 18. Processing failures

Separate record failure from infrastructure failure.

Record-level failures:

```text
SCHEMA_INVALID
BUSINESS_VALIDATION_FAILED
REFERENCE_DATA_MISSING
ENTITY_RESOLUTION_FAILED
SEQUENCE_GAP
FEATURE_CALCULATION_INVALID
```

Infrastructure failures:

```text
KAFKA_UNAVAILABLE
STATE_STORE_FAILURE
SCHEMA_REGISTRY_UNAVAILABLE
DEPENDENCY_TIMEOUT
```

Record failures go to governed quarantine/reconciliation flows. Infrastructure failures should normally stop/retry processing rather than mark valid financial events as bad data.

## 19. Exactly-once boundary

For Kafka Streams configured with `exactly_once_v2`, input-offset commits, Kafka output records and Kafka Streams state-store updates participate in the Streams transactional processing boundary.

This is valuable for:

```text
canonical Kafka event
      ↓
stateful feature calculation
      ↓
feature Kafka event
```

It does not make the following globally exactly once:

```text
Kafka -> Oracle/PostgreSQL
Kafka -> HTTP API
Kafka -> email/SMS
Kafka -> analyst workflow
```

Those boundaries retain their own inbox/idempotency/reconciliation controls.

## 20. Phase-1 deployment recommendation

Start with two processing applications rather than six physical deployments:

### `ews-operational-feature-processor`
Owns repayment, returned-payment, utilization and conduct features initially.

### `ews-signal-policy-engine`
Owns deterministic Phase-1 signal policies and signal episode state.

Split processors later when throughput, team ownership or deployment independence justifies it.

This preserves logical bounded contexts without creating unnecessary operational fragmentation on day one.
