# EWS 2.0 — Late Data, Replay and Correction Semantics

**Status:** Draft / Part III

## 1. Why this is a first-class design concern

Financial-risk data is not perfectly ordered. Payments can be posted late, source interfaces can recover after outages, financial statements can be restated, rating feeds can arrive late, relationship data can be corrected and analyst-verified document extraction can supersede machine extraction.

EWS therefore distinguishes **late arrival**, **source correction**, **reversal**, **backfill**, and **counterfactual replay**. They are not the same operation.

## 2. Time axes

Every material fact retains enough temporal information to answer:

1. When did the underlying business event occur?
2. When was the fact effective?
3. When did the institution know or become entitled to use it?
4. When did the platform ingest/process it?

`knowledgeTime` is never moved backwards merely because a historical event has an older `eventTime`.

## 3. Late-arrival classes

### L1 — Normal out-of-order
Within the live processing grace/horizon. Apply to online state and emit normal feature revisions.

### L2 — Late but operationally actionable
Outside normal window grace but still relevant to current risk. Route to correction processor, recalculate affected features, and re-evaluate open signal episodes.

### L3 — Historical correction
Too old to change current operational risk under policy but required for accurate history/audit. Persist corrected historical view without pretending the correction was known earlier.

### L4 — Reconciliation required
Cannot be applied safely because source sequence, identity, conflicting authoritative sources or missing dependencies prevent deterministic interpretation.

## 4. Correction model

Corrections never erase the original event.

A correction carries:

```text
correctionEventId
correctsEventId/sourceRecordId
correctionReason
sourceRevision
original effective/event time
new/corrected fact
knowledgeTime of correction
```

Derived feature revisions reference both the original and correcting evidence where material.

## 5. Reversal model

A reversal means the original business fact occurred but was later reversed/voided. It is distinct from saying the original event was erroneous.

Example:

```text
payment returned
   ↓
feature count increases
   ↓
source posts valid reversal
   ↓
compensating event
   ↓
feature count/value recalculated
```

The audit history still shows the original event and subsequent reversal.

## 6. Feature correction

Affected feature windows are identified by definition/version and temporal scope. Recalculation produces revision N+1 rather than updating revision N in place.

The correction engine records:

```text
recalculationReason
triggerEventId
previousFeatureValueId
newFeatureValueId
featureDefinitionVersion
executionMode
```

## 7. Signal correction

A corrected feature does not delete a prior signal. The signal episode records a new lifecycle event such as severity change, evidence correction, resolution or false-positive disposition as appropriate.

This preserves the distinction between:

```text
what EWS concluded then
```

and

```text
what EWS knows after correction
```

## 8. Replay modes

### RECOVERY
Recover intended production processing after infrastructure failure. No semantic policy/model change.

### REBUILD
Reconstruct a materialized projection or feature state using the same governed definition/version.

### BACKFILL
Introduce data that was historically absent from the platform. Preserve original event/effective time and truthful knowledge/ingestion semantics.

### COUNTERFACTUAL
Evaluate a new feature definition, signal policy or model against historical data. Output is isolated and cannot mutate official historical decisions.

### AS_CORRECTED
Construct an analytical view using later corrections/restatements. Explicitly different from `AS_KNOWN_AT_TIME`.

## 9. Replay isolation

Counterfactual/rebuild runs use:

- separate Streams application IDs;
- separate output topics/namespaces or execution-mode partitioning with strong controls;
- unique run IDs;
- pinned code/config/schema/policy/model versions;
- no notification/workflow side effects;
- explicit promotion/reconciliation if results are later adopted.

Never reset a production consumer group and replay years of data directly into official side-effecting consumers.

## 10. Point-in-time backtesting

For a prediction/evaluation time `T`, predictor inputs satisfy:

```text
knowledgeTime <= T
```

Later restatements, later entity-resolution corrections, subsequent analyst conclusions and future outcomes cannot leak into the predictor set. Future outcomes may be labels for evaluation.

## 11. Operational controls

Monitor:

- late events by source/event family;
- age-at-arrival distribution;
- corrections/reversals by source;
- features recalculated;
- signals revised/resolved due to corrections;
- sequence gaps;
- unresolved reconciliation queue;
- replay run duration and record counts;
- differences between as-known and as-corrected views.

A rising late/correction rate is itself a data-quality/operational signal about the source integration.