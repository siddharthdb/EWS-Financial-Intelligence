# EWS 2.0 — Signal Severity, Confidence and Risk Aggregation

**Status:** Draft / Part II

## 1. Why three independent measures are required

EWS 2.0 does not use one overloaded `score` field. Every signal separates:

1. **Severity** — how serious the risk consequence could be.
2. **Confidence** — how reliable the signal assertion is.
3. **Materiality** — how significant it is relative to the monitored exposure/entity/portfolio.

Risk impact is a fourth governed output produced by aggregation policy, not a synonym for any of these measures.

## 2. Severity

Canonical bands:

```text
INFO | LOW | MEDIUM | HIGH | CRITICAL
```

The semantic bands are stable; policy determines mappings. Severity can use absolute and relative measures such as overdue amount, DPD, covenant criticality, utilization duration, exposure-relative amount, collateral shortfall or legal event type.

## 3. Confidence

Confidence is decomposed rather than invented by an LLM:

```text
confidence = f(
  sourceAuthority,
  dataQuality,
  entityResolution,
  detectionReliability,
  corroboration,
  extractionConfidence
)
```

Not every component applies to every signal. Deterministic CBS DPD may have near-certain detection reliability but still be degraded by unreconciled source data. NLP-derived management news may have high extraction confidence but weak entity-resolution confidence.

Recommended output includes both a normalized confidence value and the component assessment so the number remains explainable.

## 4. Data quality

Data-quality dimensions:

- accuracy/reconciliation;
- completeness;
- freshness/timeliness;
- consistency;
- source authority;
- lineage availability;
- extraction quality;
- entity-resolution quality.

A quality gate can result in `INSUFFICIENT_EVIDENCE` without discarding the underlying observation.

## 5. Materiality

Materiality should be evaluated against appropriate denominators, for example:

- event amount / total exposure;
- event amount / sanctioned limit;
- collateral shortfall / secured exposure;
- related-party transfer / total debit flow;
- affected facility / total borrower exposure;
- affected group entity / dependency or guarantee exposure.

Absolute thresholds remain available for legally/regulatorily significant events.

## 6. Risk dimensions

Signals contribute to one or more dimensions:

```text
LIQUIDITY
LEVERAGE_SOLVENCY
PROFITABILITY_OPERATIONS
CASH_FLOW_DEBT_SERVICE
REPAYMENT_CONDUCT
COVENANT_DOCUMENTATION
COLLATERAL_SECURITY
MANAGEMENT_GOVERNANCE
FRAUD_INTEGRITY
EXTERNAL_REPUTATION
LEGAL_REGULATORY
RELATIONSHIP_CONTAGION
SECTOR_MACRO
```

The contribution vector is versioned by policy.

## 7. Avoiding double counting

Correlated signals must not be naively summed. Example:

```text
RECEIVABLE_DAYS_DERIORATION
       -> WORKING_CAPITAL_UTILIZATION_SPIKE
       -> REPEATED_PAYMENT_RETURN
```

These may be manifestations of one liquidity-stress process. Aggregation therefore records correlation groups/causal families and applies caps, diminishing contribution, or a calibrated meta-model.

Phase 1 should use transparent policy-based aggregation with explicit caps. Learned fusion/meta-models can be introduced only after sufficient labelled history exists.

## 8. Proposed aggregation pipeline

```text
Signal Instances
      |
      v
Quality / Confidence Gate
      |
      v
Deduplication + Episode State
      |
      v
Correlation / Causal-Family Grouping
      |
      v
Dimension Contributions
      |
      v
Policy Caps / Materiality Adjustment
      |
      v
Raw Analytical Risk Assessment
      |
      v
Human / Policy Governance
      |
      v
Approved Risk Assessment
```

## 9. Raw versus approved state

Persist separately:

```text
rawAnalyticalScore
proposedScore
approvedScore
analystAdjustment
adjustmentReason
```

Never overwrite raw model/rule outputs when an analyst changes the approved assessment.

## 10. Prediction outputs

Predictive models should expose an explicit target and horizon, for example:

```text
target: MATERIAL_CREDIT_DETERIORATION
horizon: P90D
probability: 0.68
modelVersion: 4.2
calibrationVersion: 2
featureSnapshot: FS-...
topDrivers: [...]
```

A prediction is not itself an approved EWS state. Policy determines whether it creates a proposed signal, changes monitoring intensity, or contributes to a risk dimension.

## 11. AI analysis outputs

AI narrative is stored separately:

```text
analysisId
purpose
model/provider/version
promptVersion
predictionIds[]
signalIds[]
evidenceIds[]
retrievalSnapshot
structuredFindings[]
narrative
citations[]
traceId
guardrailResults
```

It can contextualise and correlate governed outputs but cannot silently alter feature values, source evidence or approved scores.

## 12. Human override

Overrides require role authorization, reason code, free-text rationale where appropriate, timestamp and before/after values. Override expiry/review dates are supported so temporary judgement does not become permanent undocumented state.

Analyst decisions also become feedback labels, but human acceptance is not automatically treated as ground truth for model training. Training datasets require independent outcome definitions and label-governance rules.

## 13. Evaluation metrics

Per signal/policy/segment track:

- precision / false-positive rate;
- recall / false-negative rate where outcomes permit;
- lead time before target adverse event;
- alert count per 1,000 monitored entities;
- alerts per analyst;
- duplicate/suppression rate;
- analyst acceptance/rejection/insufficient-evidence rates;
- median disposition time;
- recurrence after closure;
- stability/drift by segment;
- data-quality failure rate;
- downstream case/escalation/outcome rates.

For predictive models additionally track discrimination, calibration and stability; model metrics must be selected for the actual target and class imbalance rather than relying on raw accuracy.

## 14. Portfolio aggregation

Counterparty risk and portfolio risk are separate views. Portfolio aggregation must support industry, group, geography, product, rating band and other governed dimensions. It should preserve drill-down from aggregate movement to counterparties, signals, features and evidence.

This design aligns with the requirement that the risk-data architecture support both routine and ad-hoc aggregation without destroying provenance.
