# EWS 2.0 — Risk Assessment and Score Aggregation

**Status:** Draft / Part IV-C  
**Scope:** Proposed analytical risk assessment; approved EWS state remains separately governed  
**Depends on:** `06-risk-intelligence-processing-architecture.md`, `06b-signal-episode-and-correlation-engine.md`, Part II scoring/confidence semantics

## 1. Purpose

Part IV-B turns repeated detections into episodes and multiple independent conditions into governed correlation hypotheses. Part IV-C defines the next boundary:

```text
SIGNALS + EPISODES + CORRELATION HYPOTHESES
                    |
                    v
         QUALITY / ELIGIBILITY GATE
                    |
                    v
          DIMENSION CONTRIBUTIONS
                    |
                    v
       GOVERNED AGGREGATION POLICY
                    |
                    v
       RAW ANALYTICAL ASSESSMENT
                    |
                    v
       PROPOSED RISK ASSESSMENT
                    |
          human governance later
                    v
       APPROVED EWS STATE (06g)
```

The assessment service does not create accounting, prudential, supervisory or legal classification.

## 2. Non-negotiable invariants

### RA-01 — Assessment is multidimensional before it is scalar

The canonical top-level risk dimensions remain governed registry values. A single overall score is a projection for prioritisation and workflow, not a replacement for dimension state.

### RA-02 — No universal score formula

The platform contract does not declare that every institution must use the same weights, score range or bands. Aggregation is an effective-dated institution policy. The Phase-1 reference pack uses a transparent 0–100 scale and `MAX_DIMENSION` overall aggregation only as an executable baseline.

### RA-03 — Raw, proposed, adjusted and approved are different records

```text
raw analytical contribution
        -> raw dimension assessment
        -> proposed assessment
        -> analyst adjustment / decision
        -> approved EWS state
```

No later stage overwrites an earlier stage.

### RA-04 — Correlation prevents double counting; aggregation must respect it

Signals/episodes that have already been collapsed into a governed hypothesis cannot be independently re-added as if they were unrelated evidence when the aggregation policy marks the hypothesis as the authoritative contribution for that causal family.

### RA-05 — Confidence does not multiply away missing evidence

Data-quality and eligibility gates run before scoring. A high model probability or high-severity signal cannot turn stale, conflicted or insufficient evidence into high-confidence assessment state.

### RA-06 — Contradiction is retained

Contradictory evidence and hypotheses remain attached to the assessment. Policy may reduce confidence, require human review or block a proposed band transition. Contradiction is never silently discarded.

### RA-07 — Institution control is bounded configuration

Institutions may configure score bands, severity-to-contribution mappings, dimension weights where the selected method uses weights, caps, minimum evidence requirements and escalation thresholds within platform/template guardrails. They cannot redefine canonical risk dimensions, evidence lineage, feature semantics, signal semantics or the separation between analytical EWS and jurisdiction classification.

### RA-08 — Point-in-time and replay isolation remain mandatory

Every assessment binds to `knowledgeTime`, input revisions, policy version, execution mode and run ID. Non-live assessments cannot mutate live projections.

## 3. Canonical assessment contract

A proposed assessment contains:

```text
assessmentId
entity
assessmentType = PROPOSED
asOf
knowledgeTime
executionMode / runId
aggregationPolicy id/version/hash
dimensionAssessments[]
overallAssessment
signalIds[]
episodeIds[]
correlationHypothesisIds[]
evidenceIds[]
featureSnapshotIds[]
contradictions[]
qualitySummary
revision / previousRevisionRef
createdAt
correlationId / traceId
```

Each dimension assessment preserves:

```text
riskDimension
rawScore? + scoreScaleId?
proposedBand
confidence
materiality
contributors[]
capApplied
rationaleRef?
```

Numeric scores are optional at the canonical level. A policy may operate on bands only.

## 4. Contributor semantics

An assessment contribution references a governed analytical object rather than copying its hidden calculation:

- `SIGNAL`
- `EPISODE`
- `CORRELATION_HYPOTHESIS`
- future governed `MODEL_PREDICTION` or `STATISTICAL_ASSERTION`

Every contribution identifies its risk dimension, contribution family, lineage groups, score/band contribution where applicable, confidence and materiality.

The assessment service rejects duplicate contributor identity and collapses shared causal/lineage families according to aggregation policy.

## 5. Aggregation policy

The policy is engine-neutral and institution controlled:

```text
Institution Risk Policy Studio
       -> risk-assessment-aggregation-policy-v1
       -> schema validation
       -> semantic/governance validation
       -> simulation
       -> maker/checker
       -> immutable ACTIVE version
       -> Risk Assessment Service
```

Governed configuration includes:

- score scale and band boundaries;
- aggregation method;
- severity-to-score mapping where numeric scoring is enabled;
- dimension caps;
- optional dimension weights;
- hypothesis substitution/suppression rules;
- contradiction treatment;
- minimum confidence/quality eligibility;
- human-review thresholds;
- effective scope and dates.

Phase 1 supports `MAX_DIMENSION`, `WEIGHTED_DIMENSIONS` and `BAND_ONLY` as explicit methods. `WEIGHTED_DIMENSIONS` requires weights to sum to 1.0. Learned fusion is not a Phase-1 aggregation method.

## 6. Phase-1 reference aggregation

The reference pack intentionally remains explainable:

```text
severity contribution:
LOW      -> 20
MEDIUM   -> 40
HIGH     -> 70
CRITICAL -> 90

correlation hypothesis:
EMERGING_LIQUIDITY_STRESS -> LIQUIDITY contribution 80

dimension aggregation:
maximum eligible independent contribution, subject to configured cap

overall aggregation:
MAX_DIMENSION

reference bands:
0–24   LOW
25–49  MEDIUM
50–74  HIGH
75–100 CRITICAL
```

These values are reference defaults, not global risk truth. Institutions may change configurable values inside approved guardrails and must simulate/approve a new immutable policy version.

For the Phase-1 fixture, `EMERGING_LIQUIDITY_STRESS` is the authoritative liquidity-family contribution. Its underlying `UTILIZATION_HIGH` and `REPEATED_PAYMENT_RETURN` episode contributions remain lineage but are not added again to the liquidity score.

## 7. Overall score versus official EWS state

```text
ProposedRiskAssessment.overallAssessment
          !=
Approved EWS state
          !=
IFRS9 / CECL / NPA / supervisory classification
```

The proposed assessment is an analytical recommendation to human/governed workflow. Part IV-G owns human decisions and approved EWS state. Jurisdiction adapters remain separate.

## 8. Revision semantics

Assessment revisions are immutable. Re-evaluation occurs when:

- a contributing signal/episode/hypothesis changes;
- new evidence materially changes confidence/materiality;
- an aggregation policy version changes;
- a scheduled/on-demand recomputation is requested;
- a controlled replay/backtest runs.

A revision references the previous revision and exact input IDs. Historical assessments remain reproducible.

## 9. Failure and degradation

- unavailable optional ML/GenAI enrichment does not block deterministic assessment;
- unknown risk dimensions are rejected;
- unknown contributor types/IDs are rejected by governed registries/projections;
- stale/insufficient contributors are excluded or force review according to policy;
- invalid policy configuration is not publishable;
- aggregation failure emits no synthetic score;
- non-live results remain isolated by `executionMode/runId`.

## 10. Executable artifacts

Part IV-C is implemented through:

- `schemas/risk-intelligence/proposed-risk-assessment-v1.schema.json`
- `schemas/risk-intelligence/risk-assessment-aggregation-policy-v1.schema.json`
- `policy-packs/phase1/assessments/corporate-risk-assessment-v1.json`
- `tests/fixtures/risk-intelligence/phase1-proposed-risk-assessment-v1.json`
- `schemas/events/payloads/risk-assessment-proposed-v1.avsc`
- semantic/publication validation in the Java risk-intelligence validator.

## 11. Closure criteria

Part IV-C is complete when:

1. proposed assessment schema is executable;
2. aggregation policy is institution-configurable but guardrailed;
3. canonical risk dimensions are registry validated;
4. double-counting/correlation substitution is tested;
5. weighted methods enforce coherent weights;
6. replay isolation is tested;
7. aggregation policy publication emits immutable validation evidence;
8. proposed assessment Kafka payload parses under Avro CI;
9. the Phase-1 reference assessment passes the complete CI loop.

The next architecture section is `06d-predictive-ml-and-anomaly-architecture.md`.
