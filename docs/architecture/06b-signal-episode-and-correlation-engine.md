# EWS 2.0 — Signal Episode & Correlation Engine

**Status:** Draft / Part IV-B  
**Scope:** Governed signal continuity, deduplication and multi-signal risk correlation  
**Depends on:** `06-risk-intelligence-processing-architecture.md`, Part II signal contracts, Part III event architecture, Part IV-A policy control plane

## 1. Purpose

A signal detection is an immutable analytical assertion at a point in time. Repeated detections must not create alert storms, while materially different signals must not be collapsed into one opaque condition.

Part IV-B therefore introduces two distinct concepts:

```text
SIGNAL DETECTIONS
      |
      v
SIGNAL EPISODE
  same governed condition over time
      |
      +----------------------+
      |                      |
      v                      v
other active episodes   independent signals
      |                      |
      +----------+-----------+
                 |
                 v
        CORRELATION HYPOTHESIS
       multi-signal risk pattern
                 |
                 v
       PROPOSED RISK ASSESSMENT
```

**Episode is continuity. Correlation is interpretation.** They are not interchangeable.

## 2. Core invariants

### EC-01 — Detection history is immutable
Episode membership never deletes, rewrites or hides the underlying signal detections.

### EC-02 — Episode identity is deterministic
An episode is identified from a governed episode policy plus entity scope and a stable condition discriminator. Runtime arrival order must not create a different logical episode.

### EC-03 — Episode does not create new evidence
An episode references signal/evidence lineage. It cannot manufacture evidence or increase source authority merely by grouping detections.

### EC-04 — Correlation is a separately governed analytical output
A correlation hypothesis has its own policy/version, contributors, temporal window, corroboration/contradiction result and confidence. It is not an approved EWS state.

### EC-05 — Shared evidence is not independent corroboration
Two signals derived from the same underlying evidence, feature snapshot or source lineage must not be counted as two independent confirmations.

### EC-06 — Reopen is a transition, not a durable state
An episode can transition from RESOLVED back to OPEN under an approved reopen policy. `REOPENED` is recorded as an event/transition, not stored as a terminal/current status.

### EC-07 — Non-live execution is isolated
Replay/backfill/backtest episodes and hypotheses cannot mutate LIVE episode/correlation state.

### EC-08 — Correlation is bounded
Correlation policies define eligible signal/episode types, entity/relationship scope, temporal window, minimum independent contributors, contradiction handling and materiality/confidence rules.

## 3. Episode semantic boundary

An episode groups **repeated or revised occurrences of the same governed risk condition**.

Correct:

```text
UTILIZATION_HIGH detection Day 1
UTILIZATION_HIGH detection Day 3
UTILIZATION_HIGH detection Day 8
        -> one UTILIZATION_HIGH episode
```

Also correct:

```text
REPEATED_PAYMENT_RETURN detections
        -> one REPEATED_PAYMENT_RETURN episode

DPD_DETERIORATION detections
        -> one DPD_DETERIORATION episode
```

Do **not** create a generic `LIQUIDITY_STRESS` episode by merging unrelated signal types. That is correlation:

```text
UTILIZATION_HIGH episode --------+
REPEATED_PAYMENT_RETURN episode -+--> EMERGING_LIQUIDITY_STRESS hypothesis
DPD_DETERIORATION episode -------+
```

This corrects the simplified example in the Part IV processing spine: multi-signal interpretation belongs to the correlation domain.

## 4. Episode identity

The logical episode key is derived from:

```text
episodePolicyId/version
executionMode
scopeEntityType + scopeEntityId
signalType
conditionDiscriminator
```

`conditionDiscriminator` is policy-defined and stable. Examples include facility ID, account ID, covenant ID, debt instrument ID or a canonical condition key. It must not be a timestamp, event ID or random value.

A physical `episodeId` is immutable once allocated. Repeated detections resolving to the same open episode append membership/revision state.

The key must include `executionMode` or an equivalent isolated namespace so replay/backtest cannot collide with LIVE state.

## 5. Episode lifecycle

Durable episode states:

```text
OPEN -> MONITORING -> RESOLVED
  ^                    |
  +---- reopen --------+
```

`SUPERSEDED` is permitted when a governed identity/policy migration replaces an episode without rewriting history.

Transitions:
- OPEN — first qualifying detection;
- MONITORING — condition remains active but no new adverse transition is required;
- RESOLVED — resolution policy satisfied or governed human disposition closes the continuing condition;
- SUPERSEDED — replaced by an explicitly linked successor;
- reopen — RESOLVED to OPEN, recorded with reason and triggering signal/evidence.

Rejected/false-positive/duplicate remain signal dispositions. They do not become episode lifecycle states.

## 6. Deduplication and membership

Episode policy controls:
- eligible signal type;
- entity grain;
- condition discriminator;
- membership window;
- cooldown;
- resolution criteria;
- reopen window/criteria;
- maximum inactivity;
- whether accepted/proposed/detected signals are eligible;
- handling of rejected/false-positive/duplicate signals.

Membership is append-only. Each member records signal ID, signal revision/snapshot reference, first/last contribution time and membership role.

A duplicate transport/event delivery is removed by event/signal identity before episode logic. Episode deduplication is a business continuity decision, not a substitute for Kafka consumer idempotency.

## 7. Episode state contract

The executable `SignalEpisodeV1` contract records at minimum:

```text
episodeId
episodeKey
episodeType
entity
signalType
status
openedAt
lastActivityAt
resolvedAt
reopenCount
policy { id, version }
executionMode / runId
memberSignals[]
evidenceIds[]
featureSnapshotIds[]
knowledgeTime
revision
previousRevisionRef
resolution
correlationId / traceId
```

Every mutation creates a new episode revision/projection update; the evidence/audit store retains prior revisions.

## 8. Correlation hypothesis

A correlation hypothesis is a governed analytical assertion that multiple conditions together support a broader risk interpretation.

Initial Phase-1 hypothesis:

```text
EMERGING_LIQUIDITY_STRESS
```

Potential later hypotheses include `REFINANCING_STRESS`, `REPAYMENT_DETERIORATION`, `FINANCIAL_PERFORMANCE_DERIORATION`, `GOVERNANCE_STRESS` and `RELATIONSHIP_CONTAGION`.

A hypothesis is not a signal alias and not an official risk classification.

## 9. Contributor model

A correlation contributor references either a signal or an episode, never an untraceable score.

Each contributor records:
- contributor type and ID;
- signal/hypothesis-relevant type;
- entity;
- role: SUPPORTING, CONTRADICTING or CONTEXTUAL;
- effective/knowledge time;
- evidence IDs;
- feature snapshot IDs;
- source lineage group IDs where available;
- contribution weight/strength where the approved policy uses one.

The engine calculates **independent contributor count** after lineage collapse. Shared evidence/source lineage cannot inflate corroboration.

## 10. Correlation policy

Correlation policy is institution-configurable under the same governance philosophy as Part IV-A, but its semantic contract is distinct from deterministic signal policy.

A correlation policy defines:
- hypothesis type;
- eligible contributor signal/episode types;
- required/minimum contributor combinations;
- temporal proximity/window;
- entity and relationship scope;
- source-independence requirements;
- contradiction rules;
- confidence/materiality calculation;
- suppression/cooldown;
- expiry/re-evaluation;
- human-validation requirement.

Institution configuration may tune governed thresholds/windows/combinations within platform guardrails. It may not redefine canonical signal meanings or convert a hypothesis directly into regulatory/accounting state.

## 11. Correlation lifecycle

Durable hypothesis states:

```text
PROPOSED -> ACTIVE -> RESOLVED
    |
    +-> REJECTED
```

A new materially different contributor set or policy version produces a new hypothesis revision. Historical revisions remain queryable.

## 12. Phase-1 liquidity correlation

Reference rule:

```text
Eligible contributors:
  REPEATED_PAYMENT_RETURN
  DPD_DERIORATION
  UTILIZATION_HIGH / UTILIZATION_SPIKE

Window:
  institution-configurable within governed bounds

Minimum:
  at least 2 independent adverse contributor families

Guardrails:
  shared evidence lineage counts once
  NOT_APPLICABLE / rejected / false-positive signals do not support
  stale/insufficient contributors are quality-gated
  contradictory evidence is retained
```

The result is a proposed `EMERGING_LIQUIDITY_STRESS` hypothesis with complete contributor/evidence lineage.

## 13. Processing algorithm

```text
signal.detected / signal.updated / disposition
        |
        v
resolve episode key
        |
        +--> existing eligible episode? -- yes --> append member + revise
        |                              |
        |                              no
        +------------------------------+--> open episode
                                               |
                                               v
                                  emit episode revision event
                                               |
                                               v
                                  select affected correlations
                                               |
                                               v
                                  load point-in-time contributors
                                               |
                                               v
                                  collapse shared lineage
                                               |
                                               v
                                  apply quality + temporal gates
                                               |
                                               v
                                  evaluate correlation policy
                                               |
                                               v
                                  proposed/revised/resolved hypothesis
```

Processing is deterministic for a fixed input snapshot, policy version and knowledge time.

## 14. Ordering and late data

Episode and correlation processing use the Part III aggregate/event sequencing rules. Late data is evaluated using effective time and knowledge time.

A late event may revise a historical/backtest result. It does not silently rewrite what production knew earlier. LIVE correction emits a new revision with causation and prior-revision reference.

## 15. Persistence and projections

Recommended Phase-1:
- PostgreSQL authoritative operational episode/hypothesis revisions and policy metadata;
- Kafka durable change events;
- Kafka Streams/local state for bounded processing where useful;
- Elasticsearch/OpenSearch-compatible projection for analyst search;
- evidence/object store for immutable source artifacts.

Dashboard/search projections are rebuildable and not systems of record.

## 16. Service boundary

Phase-1 keeps episode management inside `ews-signal-policy-engine` and correlation inside `ews-risk-intelligence-service`.

Do not create separate microservices until scale/team/technology lifecycle requires it.

## 17. Executable contracts

Part IV-B introduces:

```text
schemas/risk-intelligence/signal-episode-v1.schema.json
schemas/risk-intelligence/correlation-hypothesis-v1.schema.json
tests/fixtures/risk-intelligence/phase1-signal-episode-v1.json
tests/fixtures/risk-intelligence/phase1-liquidity-correlation-v1.json
```

The contracts are DRAFT/PRE-PUBLICATION until explicitly published under the Part III schema-governance rules.

## 18. Next implementation slice

1. Episode identity/lifecycle contract — implemented and executable.
2. Correlation contributor/evidence-lineage contract — implemented and executable.
3. Institution-configurable correlation policy + platform guardrails — implemented for Phase 1.
4. Positive/negative schema and semantic fixtures — implemented, including shared-lineage and same-family anti-double-counting.
5. CI contract validation — implemented.
6. Episode/hypothesis Kafka revision payloads — implemented and Avro-parser validated in CI.
7. Remaining 06b closure: transition-table tests and correlation-policy publication evidence/version-overlap governance; then move to `06c-risk-assessment-and-score-aggregation.md`.
