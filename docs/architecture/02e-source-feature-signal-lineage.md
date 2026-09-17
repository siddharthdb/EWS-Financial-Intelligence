# EWS 2.0 — Source → Observation → Feature → Signal Lineage

**Status:** Draft / Part II

## 1. Purpose

This matrix closes the semantic chain between source systems and Phase-1 EWS signals. It is intentionally logical; physical Kafka topics and API contracts are Part III.

## 2. Source domains

| Code | Source domain | Typical authority |
|---|---|---|
| CBS | Core banking / account transactions | T1 |
| LMS | Loan management / repayment schedule | T1 |
| LOS | Sanction/facility/credit decision | T1 |
| TF | Trade finance / LC / BG | T1 |
| DOC | Financial statements / monitoring documents | T1/T2 depending provenance |
| COV | Covenant/monitoring registry | T1 |
| COL | Collateral/valuation registry | T1/T2 |
| RAT | Approved rating source | T2 / official publication |
| REG | Official registry/regulator/exchange/legal source | T1/T2 contextual |
| NEWS | Approved external intelligence | T3 |
| REL | Canonical entity/relationship graph | Derived from T1-T4 evidence |

## 3. Phase-1 lineage matrix

| Source | Observation / canonical fact | Feature(s) | Signal(s) |
|---|---|---|---|
| LMS + CBS | obligation due, payment received, overdue state | current_dpd, max_dpd_30d/90d | DPD_EMERGED, DPD_WORSENING |
| CBS | payment instruction returned + reason | returned_payment_count/value/rate | REPEATED_PAYMENT_RETURN, HIGH_VALUE_PAYMENT_RETURN |
| LOS + CBS | sanctioned limit, outstanding | wc_utilization_ratio, headroom | WC_UTILIZATION_HIGH/SPIKE |
| DOC/stock + LOS + CBS | drawing power, limit, outstanding | applicable_capacity, utilization, excess days | WC_UTILIZATION_HIGH, LIMIT_EXCESS_RECURRING |
| TF | LC devolved | devolved amount/count/age | LC_DEVOLVEMENT |
| TF | BG invoked | invoked amount/status | BG_INVOCATION |
| DOC | financial statement metrics | current_ratio, DSCR, debt_ebitda, EBITDA margin, OCF, receivable/inventory days | financial deterioration signals |
| COV + feature store | covenant definition + measured value | covenant_headroom | COVENANT_BREACH |
| COV/DOC registry | monitoring obligation due/received | monitoring_document_delay_days | FINANCIAL_MONITORING_DATA_DELAY |
| RAT | rating action | notch change, outlook/watch | EXTERNAL_RATING_DOWNGRADE |
| COL + exposure | valuation/security state | collateral_cover_ratio, valuation_age | COLLATERAL_COVER_EROSION |
| CBS + REL + LOS | disbursement/flows + sanctioned purpose + related parties | related_party_transfer_ratio/delta, end-use reconciliation | RELATED_PARTY_TRANSFER_SPIKE, FUND_DIVERSION_SUSPECTED |
| DOC | signed auditor report | qualification categories | AUDITOR_QUALIFICATION_ADVERSE |
| REG + approved NEWS | management/director event | management change count/context | KEY_MANAGEMENT_RESIGNATION |
| REL + risk states | related entity + dependency/guarantee/exposure | distressed_group_exposure_ratio | GROUP_ENTITY_DISTRESS |

## 4. Example — repayment chain

```text
LMS: repayment schedule
CBS: payment receipts
        |
        v
Observations
  obligation.due
  payment.received
        |
        v
Feature
  current_dpd
        |
        +----> Regulatory Classification Adapter
        |           -> applicable SMA/NPA state
        |
        +----> EWS Signal Policy
                    -> DPD_EMERGED
                    -> DPD_WORSENING
```

The regulatory and EWS paths share authoritative facts but do not share semantics.

## 5. Example — working-capital stress

```text
CBS outstanding ----+
LOS limit -----------+
Drawing power -------+
                     v
             wc_utilization_ratio
             wc_available_headroom
             limit_excess_days_30d
                     |
              +------+------+
              |             |
              v             v
        WC_UTIL_HIGH   LIMIT_EXCESS_RECURRING
              |
              +---- correlation ----+
                                    |
Receivable days deterioration ------+
Payment returns --------------------+
                                    v
                         EMERGING_LIQUIDITY_STRESS
```

## 6. Example — financial statement chain

```text
Original financial statement
       |
       +-> document hash/evidence
       +-> extraction + validation
       +-> canonical financial facts
       |
       v
Feature definitions
  current_ratio
  debt_ebitda
  dscr
  ebitda_margin
  operating_cash_flow
  receivable_days
       |
       v
Trend / peer features
       |
       v
Financial signal policies
       |
       v
Proposed EWS signals
```

Every ratio preserves statement period, audited/provisional status, extraction version/confidence, definition version and component lineage.

## 7. Example — suspected diversion chain

```text
Sanctioned purpose --------+
Disbursement --------------+
Transactions --------------+
Relationship graph --------+
Invoice/end-use evidence --+
                           v
                 governed reconciliation
                 + anomaly / graph analysis
                           |
                           v
                FUND_DIVERSION_SUSPECTED
                           |
                           v
                 mandatory human review
                           |
                 +---------+----------+
                 |                    |
              rejected          investigation/case
```

No AI or graph score converts suspicion directly into a confirmed legal/fraud classification.

## 8. Data-quality propagation

Quality does not simply take the minimum numeric input score. Each feature definition specifies material inputs and quality rules. Example: stale drawing power may materially degrade `wc_utilization_ratio`; a stale optional industry tag may not.

A signal policy specifies its minimum evidence/feature quality gate and response:

```text
PASS -> evaluate normally
DEGRADED -> evaluate with reduced confidence / review flag
FAIL -> INSUFFICIENT_EVIDENCE, request data remediation
```

## 9. Source reconciliation

Where two authoritative sources disagree, retain both observations and a conflict state. Reconciliation creates a governed resolution record; ingestion must not silently choose the latest or most convenient value.

Examples include LMS vs CBS repayment state, LOS limit vs CBS limit mirror, document-extracted financial metric vs analyst-validated metric, and relationship registry vs official filing.

## 10. Part-II completion criteria

Part II is considered architecture-complete when:

- canonical ontology is defined;
- signal taxonomy exists;
- priority signal contracts exist;
- severity/confidence/materiality semantics exist;
- feature meta-model and Phase-1 catalogue exist;
- source-to-feature-to-signal lineage exists;
- executable feature/signal schemas exist;
- regulatory classification is separated from EWS interpretation;
- bitemporal/point-in-time semantics are explicit;
- human validation and evidence invariants are explicit.

Physical event envelopes, Kafka topics, partitions, keys, schema compatibility, CDC, replay, DLQ and stream-processing topology belong to Part III.
