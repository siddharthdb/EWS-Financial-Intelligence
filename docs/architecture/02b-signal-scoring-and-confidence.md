# EWS 2.0 — Signal Severity, Confidence and Risk Aggregation

**Status:** Draft / Part II — coherence-normalized

## 1. Independent measures

EWS 2.0 does not use one overloaded `score`. Every signal separates:

1. **Severity** — potential seriousness of the risk consequence.
2. **Confidence** — reliability of the signal assertion.
3. **Materiality** — significance relative to the monitored exposure/entity/portfolio.
4. **Risk impact** — governed contribution produced by aggregation policy; not a synonym for the first three.

## 2. Severity

Canonical bands:

```text
INFO | LOW | MEDIUM | HIGH | CRITICAL
```

The bands are stable; policy determines mappings using absolute/relative measures such as overdue amount, DPD, covenant criticality, utilization duration, exposure-relative amount, collateral shortfall, refinancing concentration or legal event type.

## 3. Confidence

Confidence is decomposed rather than invented by an LLM:

```text
confidence = f(
  sourceAuthority,
  dataQuality,
  entityResolution,
  detectionReliability,
  corroboration,
  extractionConfidence,
  marketLiquidityQuality where applicable
)
```

Not every component applies to every signal. Deterministic servicing DPD may have near-certain detection reliability but still be degraded by unreconciled source data. NLP-derived management intelligence may have high extraction confidence but weak entity-resolution confidence. Market-derived credit signals may require an explicit liquidity-quality component.

The normalized confidence value and material component assessments are retained together.

## 4. Data quality

Data-quality dimensions include accuracy/reconciliation, completeness, freshness/timeliness, consistency, source authority, lineage availability, extraction quality, entity-resolution quality, corroboration and market-liquidity quality where applicable.

`T1–T4` is the canonical source-authority vocabulary. Extraction/inference confidence is not a source-authority tier.

A failed quality gate can produce an `INSUFFICIENT_EVIDENCE` disposition/outcome without discarding the underlying observation. `INSUFFICIENT_EVIDENCE` is not an economic-risk or regulatory classification.

## 5. Materiality

Materiality is evaluated against appropriate denominators, for example event amount / total exposure, event amount / approved capacity, collateral shortfall / secured exposure, related-entity transfer / debit flow, affected facility / borrower exposure, affected group entity / dependency exposure, or debt maturing / available refinancing capacity.

Absolute thresholds remain available for legally/regulatorily significant events where the applicable policy requires them.

## 6. Canonical risk dimensions

Signals contribute to one or more governed dimensions:

```text
LIQUIDITY
LEVERAGE_SOLVENCY
PROFITABILITY_OPERATIONS
CASH_FLOW_DEBT_SERVICE
REPAYMENT_CONDUCT
COVENANT_DOCUMENTATION
COLLATERAL_SECURITY
REFINANCING_FUNDING
MANAGEMENT_GOVERNANCE
FRAUD_INTEGRITY
EXTERNAL_MARKET_REPUTATION
LEGAL_REGULATORY
RELATIONSHIP_CONTAGION
SECTOR_MACRO
```

This vocabulary is canonical across Part I, signal policy and risk aggregation. Policies may define subdimensions but must not silently introduce conflicting top-level meanings.

## 7. Avoiding double counting

Correlated signals must not be naively summed. Example:

```text
RECEIVABLE_DAYS_DERIORATION
       → WORKING_CAPITAL_UTILIZATION_SPIKE
       → REPEATED_PAYMENT_RETURN
```

These can be manifestations of one liquidity-stress process. Aggregation records correlation groups/causal families and applies caps, diminishing contribution or a validated meta-model.

Phase 1 uses transparent policy-based aggregation with explicit caps. Learned fusion/meta-models require sufficient labelled history and independent validation.

## 8. Aggregation pipeline

```text
Signal Instances
      ↓
Quality / Confidence Gate
      ↓
Deduplication + Episode State
      ↓
Correlation / Causal-Family Grouping
      ↓
Dimension Contributions
      ↓
Policy Caps / Materiality Adjustment
      ↓
Raw Analytical Risk Assessment
      ↓
Proposed Risk Assessment
      ↓
Human / Policy Governance
      ↓
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

Accounting/prudential/supervisory classification state is also persisted separately from these EWS analytical scores.

## 10. Prediction outputs

Predictive models expose an explicit target and horizon, for example:

```text
target: MATERIAL_CREDIT_DETERIORATION
horizon: P90D
probability: 0.68
modelVersion: 4.2
calibrationVersion: 2
featureSnapshot: FS-...
topDrivers: [...]
```

A prediction is not an approved EWS state or a jurisdiction classification. Policy determines whether it creates a proposed signal, changes monitoring intensity or contributes to a risk dimension.

## 11. AI analysis outputs

AI narrative is stored separately with purpose, model/provider/version, prompt version, prediction/signal/evidence references, retrieval snapshot, structured findings, narrative/citations, trace and guardrail results.

It can contextualize and correlate governed outputs but cannot silently alter feature values, evidence, risk assessments or classification state.

## 12. Human override and disposition

Overrides require role authorization, reason code, rationale where appropriate, timestamp and before/after values. Override expiry/review dates are supported.

Signal lifecycle state and disposition are distinct. `FALSE_POSITIVE`, `DUPLICATE`, `INSUFFICIENT_EVIDENCE`, escalation/investigation and information-request actions belong to disposition/action history rather than accounting/prudential classification.

Analyst decisions become governed feedback labels, but human acceptance is not automatically ground truth for model training.

## 13. Evaluation metrics

Per signal/policy/segment/market where relevant track precision/false-positive rate, recall/false-negative rate where outcomes permit, lead time, alert volume, analyst workload, duplicate/suppression rate, disposition rates, median disposition time, recurrence, drift/stability, data-quality failures and downstream outcomes.

For predictive models additionally track discrimination, calibration and stability for the actual target/population. Model evaluation and calibration are not assumed portable across jurisdictions or materially different market populations.

## 14. Portfolio aggregation

Counterparty and portfolio risk are separate views. Portfolio aggregation supports industry, group, geography/jurisdiction, product, currency/market, rating band and other governed dimensions while preserving drill-down to counterparties, signals, features and evidence.
