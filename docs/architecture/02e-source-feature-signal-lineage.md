# EWS 2.0 — Source → Evidence → Observation → Feature → Signal Lineage

**Status:** Draft / Part II — coherence-normalized  
**Scope:** Global corporate counterparty risk; India/US/UK are reference implementations

## 1. Purpose

This matrix closes the logical chain between source systems and governed EWS signals. Physical Kafka topics and API contracts are Part III. Jurisdictional accounting, prudential, supervisory and legal classifications branch from common facts through separately governed adapters; they are not signal aliases.

```text
SOURCE
  ↓
EVIDENCE
  ↓
CANONICAL OBSERVATION / DOMAIN EVENT
  ↓
FEATURE
  ↓
SIGNAL POLICY / ANALYTICAL ENGINE
  ↓
SIGNAL
  ├──→ RISK ASSESSMENT → HUMAN DECISION → OFFICIAL EWS
  └──→ JURISDICTION ADAPTER → CLASSIFICATION STATE
```

## 2. Source domains and authority

`T1–T4` is the canonical platform vocabulary for source authority. Extraction confidence, entity-resolution confidence and model confidence are separate quality dimensions and must not be encoded as additional authority tiers.

| Code | Source domain | Typical authority |
|---|---|---|
| CBS | Core banking / account transactions | T1 |
| LMS | Loan management / repayment schedule | T1 |
| LOS | Facility / credit decision system | T1 |
| TF | Trade finance / guarantees / letters of credit | T1 |
| DOC | Financial statements / monitoring documents | T1/T2 depending provenance |
| COV | Covenant / monitoring registry | T1 |
| COL | Collateral / valuation registry | T1/T2 |
| RAT | Approved rating source | T2 / authoritative publication as configured |
| REG | Official registry / regulator / exchange / court / insolvency source | T1/T2 contextually |
| MKT | Approved market/reference-data source | T2/T3 depending authority and licence |
| NEWS | Approved external intelligence | T3 |
| REL | Canonical entity / relationship graph | Derived from T1–T4 evidence |

Source authority never substitutes for source-rights controls. External evidence also carries the applicable source-rights reference and entity/security-resolution lineage.

## 3. Canonical lineage matrix

| Source | Canonical observation / fact | Feature(s) | Signal(s) |
|---|---|---|---|
| LMS + CBS | obligation due, payment received, overdue state | `current_dpd`, `max_dpd_30d/90d` | `DPD_EMERGED`, `DPD_WORSENING` |
| CBS | `payment.instruction.returned` | returned-payment count/value/rate | `REPEATED_PAYMENT_RETURN`, `HIGH_VALUE_PAYMENT_RETURN` |
| LOS + CBS | facility limit/capacity + outstanding | `utilization_ratio`, headroom | `UTILIZATION_HIGH`, `UTILIZATION_SPIKE` |
| borrowing-base/drawing-power source + LOS + CBS | applicable capacity + outstanding | `wc_utilization_ratio`, excess days | `WORKING_CAPITAL_UTILIZATION_HIGH`, `WORKING_CAPITAL_UTILIZATION_SPIKE`, `LIMIT_EXCESS_RECURRING` |
| TF | `trade_finance.lc.devolved` | devolved amount/count/age | `LC_DEVOLVEMENT` |
| TF | `trade_finance.guarantee.invoked` | invoked amount/status | `GUARANTEE_INVOCATION` |
| DOC | validated financial facts | current ratio, DSCR, debt/EBITDA, margin, OCF, receivable/inventory days | governed financial-deterioration signals |
| COV + feature store | covenant definition + measured value | `covenant_headroom` | `COVENANT_BREACH`, `COVENANT_HEADROOM_EROSION` |
| monitoring registry | `monitoring.requirement.status.changed` | monitoring-information delay | `REQUIRED_MONITORING_INFORMATION_DELAY` |
| RAT | `rating.action.published` | notch/outlook/watch features | `EXTERNAL_RATING_DOWNGRADE`, `RATING_OUTLOOK_NEGATIVE` |
| COL + exposure | valuation/security state | collateral cover and valuation age | `COLLATERAL_VALUE_DECLINE`, `COLLATERAL_COVER_EROSION` |
| transactions + REL + financing terms | purpose/flow/related-entity evidence | related-entity transfer and end-use reconciliation | `RELATED_ENTITY_TRANSFER_SPIKE`, `FUND_DIVERSION_SUSPECTED` |
| DOC / filing | audit qualification / going-concern evidence | governed audit/reporting features | `AUDITOR_QUALIFICATION_ADVERSE`, `GOING_CONCERN_WARNING` |
| REG + approved NEWS | `management.position.changed` | management-change count/context | `KEY_MANAGEMENT_RESIGNATION`, `MANAGEMENT_TURNOVER_CLUSTER` |
| REL + governed risk state | related entity + dependency/guarantee/exposure | distressed-related-entity exposure | `GROUP_ENTITY_DISTRESS`, `GUARANTOR_DISTRESS` |
| facility disclosure / lender source | amendment, maturity extension, pricing, waiver | maturity/refinancing/waiver/funding-cost features | `REFINANCING_RISK_INCREASE`, `COVENANT_WAIVER_FREQUENCY`, `FUNDING_COST_INCREASE` |
| authorised security-interest registry | `security.interest.created` | `new_security_interest_count_90d` | `NEW_SECURITY_INTEREST_ACTIVITY` |
| approved market feed | bond price/yield/trade observations | spread, drawdown, peer divergence, liquidity | `MARKET_IMPLIED_CREDIT_STRESS`, `CREDIT_SPREAD_DIVERGENCE`, `BOND_PRICE_DISTRESS` |
| court / insolvency registry | `insolvency.proceeding.started` | procedure/status features | `FORMAL_INSOLVENCY_PROCEEDING`, `FORMAL_RESTRUCTURING_EVENT` where semantically supported |

Historical aliases such as `BG_INVOCATION` remain readable for migration/audit but new policies and events use normalized names.

## 4. Repayment chain and classification boundary

```text
LMS repayment schedule
CBS payment receipts
        ↓
Evidence + canonical observations
        ↓
current_dpd
        ├──────────────→ EWS signal policy
        │                  ├─ DPD_EMERGED
        │                  └─ DPD_WORSENING
        │
        └──────────────→ jurisdiction / accounting / prudential adapter
                           ├─ IN_SMA_NPA where applicable
                           ├─ US_ACCRUAL_STATUS / other US policy where applicable
                           ├─ UK_SICR / UK_IFRS9_STAGE where applicable
                           └─ other governed classification namespace
```

The branches share authoritative facts but not semantics. No generic rule such as `DPD_WORSENING => IFRS9_STAGE_2`, `DPD_WORSENING => CECL`, or `EWS_HIGH => NPA` exists in the canonical model.

## 5. Utilization and working-capital specialization

```text
facility.outstanding.changed
facility.limit.changed
        ↓
utilization_ratio
        ↓
UTILIZATION_HIGH / UTILIZATION_SPIKE

where product structure requires borrowing-base/drawing-power mechanics:

drawing/borrowing-base capacity
        + outstanding
        ↓
wc_utilization_ratio
        ↓
WORKING_CAPITAL_UTILIZATION_HIGH
WORKING_CAPITAL_UTILIZATION_SPIKE
```

Working-capital mechanics are a product specialization and do not define the global utilization ontology.

## 6. Financial-statement chain

```text
Original financial statement
       ↓
immutable evidence + document hash
       ↓
extraction + validation
       ↓
canonical financial facts
       ↓
versioned feature definitions
       ↓
trend / peer features
       ↓
financial signal policies
       ↓
proposed EWS signals
```

Every derived ratio preserves statement period, accounting basis, audited/provisional status, extraction version/confidence, feature-definition version, component lineage and knowledge time.

## 7. External refinancing / market chain

```text
credit.facility.amended
facility.maturity.extended
facility.pricing.changed
covenant.waiver.disclosed
rating.action.published
market.bond.trade.observed
        ↓
source rights + entity/security resolution
        ↓
refinancing / funding / market features
        ↓
REFINANCING_RISK_INCREASE
FUNDING_COST_INCREASE
COVENANT_WAIVER_FREQUENCY
MARKET_IMPLIED_CREDIT_STRESS
        ↓
correlation
        ↓
REFINANCING_STRESS hypothesis
```

A connector emits source facts/observations, not analytical signals.

## 8. Suspected diversion chain

```text
Financing purpose ----------+
Disbursement ---------------+
Transactions ---------------+
Relationship graph ---------+
Invoice/end-use evidence ---+
                            ↓
                  governed reconciliation
                  + anomaly / graph analysis
                            ↓
                 FUND_DIVERSION_SUSPECTED
                            ↓
                  mandatory human review
                            ↓
                    investigation / case
```

No AI, graph score or signal converts suspicion directly into a confirmed legal/fraud classification.

## 9. Data-quality propagation

Quality does not simply take the minimum numeric input score. Each feature definition identifies material inputs and quality rules. A stale borrowing-base input can materially degrade a working-capital feature while a stale optional industry tag may not.

A signal policy specifies the evidence/feature quality gate:

```text
PASS      → evaluate normally
DEGRADED  → evaluate with reduced confidence / review flag
FAIL      → do not assert a new material signal; record insufficient-evidence outcome and request remediation
```

`INSUFFICIENT_EVIDENCE` is primarily a quality/disposition outcome. It must not be treated as an economic-risk classification.

## 10. Source reconciliation

Where authoritative sources disagree, retain both observations and a conflict state. Reconciliation creates a governed resolution record; ingestion must not silently select the latest or most convenient value.

Examples include LMS vs CBS repayment state, LOS facility terms vs servicing-system mirror, document-extracted financial metric vs analyst-validated metric, and relationship registry vs authoritative filing.

## 11. Part-II completion criteria

Part II is architecture-complete when:

- canonical ontology and global portability rules are defined;
- normalized signal taxonomy and priority signal contracts exist;
- severity/confidence/materiality semantics exist;
- feature meta-model and catalogues exist;
- source → evidence → observation → feature → signal lineage exists;
- executable feature/signal/classification schemas exist;
- accounting/prudential/supervisory/legal classification is separated from EWS interpretation;
- bitemporal/point-in-time semantics are explicit;
- human validation and evidence invariants are explicit.

Physical event envelopes, Kafka topics, partitions, keys, schema compatibility, CDC, replay, DLQ and stream-processing topology belong to Part III.
