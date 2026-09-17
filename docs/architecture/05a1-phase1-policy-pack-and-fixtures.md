# Phase-1 Policy Pack and Test Fixtures

**Status:** Draft / Part IV  
**Related:** `05a-rule-and-policy-engine-architecture.md`, `schemas/policies/*`

## 1. Objective

The first policy pack proves that institution-owned configuration can change risk-policy behaviour without changing application code or canonical signal semantics.

Reference thresholds in this pack are examples for development/simulation. They are not universal credit-policy recommendations and must not become production defaults merely because they are shipped with the platform.

## 2. Phase-1 policies

### REPEATED_PAYMENT_RETURN

Canonical feature: `returned_payment_count_30d`.

Reference configuration:

```text
PAYMENT_RETURN_COUNT_THRESHOLD = 3
PAYMENT_RETURN_LOOKBACK_DAYS = 30
```

The 30-day lookback is represented by the governed feature definition; the rule evaluates the feature and threshold. A future institution configuration requiring a different window must bind to a corresponding governed feature (for example `returned_payment_count_60d`) or a parameterized feature-definition capability. The rule evaluator must not silently reinterpret a 30-day feature as 60 days.

### DPD_DERIORATION

Canonical feature: `current_dpd`.

Reference configuration:

```text
DPD_WARNING_THRESHOLD = 15
DPD_HIGH_THRESHOLD = 30
```

Priority semantics ensure that 30+ DPD produces the HIGH outcome rather than also surfacing a second MEDIUM outcome under `FIRST_MATCH`/priority evaluation.

These are analytical EWS thresholds only. Jurisdiction-specific delinquency, non-performing, default, accounting or supervisory classifications remain separate classification-policy contracts.

### HIGH_UTILIZATION

Canonical feature: `utilization_ratio`.

Reference configuration:

```text
HIGH_UTILIZATION_THRESHOLD = 0.85
CRITICAL_UTILIZATION_THRESHOLD = 0.95
```

This produces analytical liquidity/refinancing signals. It does not itself conclude default or accounting deterioration.

## 3. Institution configuration model

The intended deployment model is:

```text
Platform policy template
       |
       v
Institution policy configuration
  - thresholds
  - enabled rules
  - severity within allowed policy bounds
  - portfolio/product applicability
  - effective dates
       |
       v
Schema + semantic validation
       |
       v
Historical simulation
       |
       v
Maker -> Checker
       |
       v
Immutable ACTIVE policy version
```

An institution change creates a new version. It never mutates the historical policy version that produced an earlier signal.

## 4. Fixture strategy

The fixture set covers four classes of behaviour:

1. normal match/non-match;
2. exact threshold boundaries;
3. institution-specific threshold overrides;
4. missing/stale/not-available evidence.

This is intentionally more important than testing only happy paths. Threshold boundaries and data-quality semantics are common sources of production divergence between a policy UI, runtime evaluator and historical replay.

## 5. Expected quality semantics

For these Phase-1 policies:

```text
VALUE / ZERO -> eligible for deterministic evaluation
NULL_UNKNOWN -> INSUFFICIENT_EVIDENCE
NOT_AVAILABLE -> INSUFFICIENT_EVIDENCE
STALE -> INSUFFICIENT_EVIDENCE
INVALID -> evaluation failure or insufficient evidence according to policy
NOT_APPLICABLE -> rule not applicable, not an adverse signal
```

The executable `risk-policy-v1` contract owns the precise missing-value policy. The test fixtures currently assume `INSUFFICIENT_EVIDENCE` for required-but-unusable inputs.

## 6. Important contract finding

The first fixture pass exposes a deliberate architectural boundary: a policy parameter such as `PAYMENT_RETURN_LOOKBACK_DAYS` cannot change the semantics of the already materialized feature `returned_payment_count_30d`.

Therefore configurable **thresholds** and configurable **feature windows** are different capabilities.

Phase 1 should support institution-controlled thresholds immediately. Institution-controlled windows should be introduced only through governed feature definitions / approved parameterized feature templates so that feature lineage and replay remain deterministic.

## 7. Next implementation test

The next step is to materialize each policy as a full `risk-policy-v1` document rather than the compact pack manifest, and create matching `policy-evaluation-request-v1` and `policy-evaluation-result-v1` fixtures. Those artifacts should then be validated automatically against JSON Schema in CI.

That pass will also determine whether the current expression AST needs first-class support for rule priority and reusable parameter/value types before runtime implementation begins.