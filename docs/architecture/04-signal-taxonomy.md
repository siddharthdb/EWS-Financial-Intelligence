# EWS 2.0 — Corporate Signal Taxonomy

**Status:** Draft / Part II  
**Purpose:** Initial production-oriented catalogue of signal semantics. Thresholds shown as examples are policy candidates, not canonical definitions.

## 1. Taxonomy principles

- Signals describe risk meaning; policies describe thresholds/windows.
- Observations are not automatically signals.
- Severity, confidence and risk impact are separate concepts.
- A signal may map to multiple risk dimensions/intents.
- Fraud/integrity suspicion is not equivalent to credit deterioration.
- Every machine-proposed signal requires evidence references.
- External/unstructured signals require source quality and entity-resolution confidence.
- Correlated signals remain independently auditable.

## 2. Detection method codes

- **R** deterministic/rule
- **S** statistical/time-series
- **ML** predictive machine learning
- **A** anomaly detection
- **NLP** document/text extraction
- **G** graph/network analytics
- **AI** GenAI correlation/explanation (not sole authoritative detector for material facts)

## 3. Financial performance signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| REVENUE_MATERIAL_DECLINE | revenue trend, YoY/QoQ | R/S |
| OPERATING_PROFIT_MATERIAL_DECLINE | EBITDA/EBIT trend vs history/plan | R/S |
| EBITDA_MARGIN_DERIORATION | margin trend, peer delta | S/A |
| NET_LOSS_EMERGENCE | P&L | R |
| CURRENT_RATIO_DERIORATION | current assets/liabilities | R/S |
| QUICK_RATIO_DERIORATION | liquid current assets/liabilities | R/S |
| DEBT_EBITDA_DERIORATION | debt, EBITDA | R/S |
| TOL_ATNW_DERIORATION | outside liabilities, adjusted TNW | R/S |
| DSCR_DERIORATION | cash accrual/debt service | R/S |
| INTEREST_COVERAGE_DERIORATION | EBIT/interest | R/S |
| OPERATING_CASH_FLOW_NEGATIVE | cash-flow statement | R |
| CASH_FLOW_PROFIT_DIVERGENCE | EBITDA/PAT vs CFO | S/A |
| RECEIVABLE_DAYS_DERIORATION | receivables, sales | S/A |
| INVENTORY_DAYS_DERIORATION | inventory, COGS | S/A |
| CASH_CONVERSION_CYCLE_DIVERGENCE | DSO/DIO/DPO | S/A |
| NET_WORTH_EROSION | tangible net worth | R/S |
| CONTINGENT_LIABILITY_SPIKE | notes/schedules | R/A |
| RELATED_PARTY_EXPOSURE_SPIKE | financial notes, transactions | R/A/G |
| AUDITOR_QUALIFICATION_ADVERSE | audit report | NLP/R |
| GOING_CONCERN_WARNING | audit report | NLP/R |
| FINANCIAL_STATEMENT_DELAY | expected vs received date | R |
| FINANCIAL_RESTATEMENT_MATERIAL | statement revisions | R/NLP |

## 4. Transactional and liquidity signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| REPEATED_PAYMENT_RETURN | returned cheques/debits in rolling window | R |
| HIGH_VALUE_PAYMENT_RETURN | returned high-value instrument | R |
| WORKING_CAPITAL_UTILIZATION_HIGH | utilization vs limit/DP | R/S |
| WORKING_CAPITAL_UTILIZATION_SPIKE | utilization velocity | S/A |
| OVERDRAWN_FREQUENCY_INCREASE | overdraft episodes | S/A |
| ACCOUNT_BALANCE_DRAIN | balance trend | S/A |
| CREDIT_INFLOW_DECLINE | credits/turnover trend | S/A |
| DEBIT_CREDIT_PATTERN_ANOMALY | transaction profile | A/ML |
| LARGE_UNUSUAL_TRANSFER | amount/beneficiary anomaly | A |
| GROUP_ENTITY_TRANSFER_SPIKE | transfers to related entities | R/A/G |
| OUTSIDE_BANK_ROUTING_ANOMALY | known consortium/account routing | R/G |
| ROUND_TRIPPING_PATTERN | circular flow/network motifs | G/A/ML |
| CASH_FLOW_CONCENTRATION_INCREASE | top payer/customer share | S/A |
| CUSTOMER_RECEIPT_CONCENTRATION | payer concentration | S |
| SUPPLIER_PAYMENT_CONCENTRATION | beneficiary concentration | S |
| TRANSACTION_VOLUME_COLLAPSE | volume/value trend | S/A |
| END_USE_MISMATCH_SUSPECTED | disbursement vs observed deployment | R/A/G |

## 5. Repayment and credit-conduct signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| DPD_EMERGED | repayment schedule vs receipt | R |
| DPD_WORSENING | DPD trajectory | S |
| REPEATED_LATE_PAYMENT | payment history | R/S |
| EMI_OR_INSTALLMENT_MISSED | scheduled obligation | R |
| INTEREST_SERVICING_DELAY | interest due/paid | R |
| LC_DEVOLVEMENT | LC event | R |
| BG_INVOCATION | guarantee event | R |
| DEVOLVED_OBLIGATION_UNPAID | devolvement + elapsed time | R |
| ADHOC_LIMIT_DEPENDENCY | repeated temporary limits | R/S |
| LIMIT_EXCESS_RECURRING | outstanding vs sanctioned limit | R/S |
| INTERNAL_RATING_DOWNGRADE | internal rating history | R |
| EXTERNAL_RATING_DOWNGRADE | rating agency event | R |
| RATING_OUTLOOK_NEGATIVE | rating outlook event | R/NLP |
| RESTRUCTURING_REQUESTED | borrower/workflow event | R |
| MULTIPLE_LENDER_STRESS | bureau/consortium intelligence | R/G |

## 6. Monitoring, covenant and documentation signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| STOCK_STATEMENT_DELAY | submission schedule | R |
| FINANCIAL_MONITORING_DATA_DELAY | submission schedule | R |
| COVENANT_BREACH | covenant measurement | R |
| COVENANT_HEADROOM_EROSION | distance-to-threshold trend | S |
| FACILITY_RENEWAL_DELAY | renewal due/status | R |
| SECURITY_CREATION_DELAY | sanction terms vs completion | R |
| SECURITY_PERFECTION_DELAY | charge/perfection status | R |
| INSURANCE_LAPSE | collateral insurance | R |
| STOCK_AUDIT_NON_COOPERATION | audit workflow | R |
| SITE_VISIT_NON_COOPERATION | monitoring workflow | R |
| INFORMATION_REQUEST_NON_COOPERATION | request/response history | R |
| DRAWING_POWER_REDUCTION | stock audit/DP history | R/S |
| COLLATERAL_VALUE_DECLINE | valuation history | S |
| COLLATERAL_COVER_EROSION | exposure vs collateral | S |

## 7. Fraud and integrity signals

These are investigation triggers, not automatic fraud classifications.

| Signal type | Typical evidence/features | Method |
|---|---|---|
| FUND_DIVERSION_SUSPECTED | sanctioned purpose vs flow | R/A/G |
| FUND_SIPHONING_SUSPECTED | unrelated use detrimental to borrower/lender | R/A/G |
| FINANCIAL_DOCUMENT_INCONSISTENCY | cross-document reconciliation | R/A/NLP |
| INVOICE_FABRICATION_SUSPECTED | invoice/vendor validation | A/NLP/G |
| DEBTOR_LIST_ANOMALY | debtor reconciliation/existence | A/G |
| STOCK_STATEMENT_INCONSISTENCY | stock audit vs submissions | R/A |
| TURNOVER_OVERSTATEMENT_SUSPECTED | GST/bank/accounting reconciliation | R/A |
| RELATED_PARTY_DIVERSION_PATTERN | flows + relationship graph | G/A |
| SHELL_ENTITY_EXPOSURE_SUSPECTED | entity/graph/external intelligence | G/NLP |
| ROUND_TRIPPING_SUSPECTED | transaction network | G/ML |
| SANCTION_TERM_MISUSE_SUSPECTED | end-use evidence | R/A |
| FALSE_REPRESENTATION_SUSPECTED | contradictory verified records | R/NLP |

## 8. Management and governance signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| KEY_MANAGEMENT_RESIGNATION | filings/news | NLP/R |
| PROMOTER_EXIT_OR_STAKE_REDUCTION | ownership filings | R/NLP |
| DIRECTOR_FREQUENT_CHANGE | corporate registry history | S |
| AUDITOR_RESIGNATION | filing | R/NLP |
| AUDITOR_FREQUENT_CHANGE | filing history | S |
| CFO_RESIGNATION | filing/news | NLP/R |
| GOVERNANCE_ADVERSE_EVENT | regulatory/company disclosure | NLP |
| PROMOTER_PLEDGE_INCREASE | market filing | R/S |
| MANAGEMENT_LITIGATION_MATERIAL | legal intelligence | NLP/G |
| DIRECTOR_DEFAULT_ASSOCIATION | verified external/credit data | G/R |

## 9. Legal, regulatory and external signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| INSOLVENCY_PROCEEDING_FILED | NCLT/legal source | R/NLP |
| MATERIAL_LITIGATION_FILED | court/legal source | NLP |
| REGULATORY_ENFORCEMENT_ACTION | regulator source | R/NLP |
| STATUTORY_DUES_DEFAULT_DISCLOSED | annual report/verified source | NLP/R |
| TAX_OR_STATUTORY_ATTACHMENT | official/legal source | NLP/R |
| ADVERSE_NEWS_MATERIAL | approved news sources | NLP/AI |
| ADVERSE_NEWS_VELOCITY_SPIKE | news-event time series | S/NLP |
| REPUTATIONAL_CONTROVERSY | multi-source intelligence | NLP/AI |
| MARKET_PRICE_SHOCK | market feed | S/A |
| BOND_YIELD_OR_SPREAD_STRESS | market feed | S/A |
| MARKET_LIQUIDITY_STRESS | market feed | S/A |

## 10. Relationship and contagion signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| GROUP_ENTITY_DISTRESS | related entity risk state | G/R |
| PROMOTER_LINKED_DISTRESS | promoter relationship + external event | G |
| GUARANTOR_DISTRESS | guarantor risk state | G/R |
| MAJOR_CUSTOMER_DISTRESS | customer concentration + entity risk | G/S |
| MAJOR_SUPPLIER_DISTRESS | supplier concentration + entity risk | G/S |
| GROUP_CROSS_DEFAULT | group obligations/defaults | G/R |
| RELATIONSHIP_RISK_CONCENTRATION | graph exposure concentration | G/S |
| CONTAGION_SCORE_SPIKE | graph-derived risk propagation | G/ML |

## 11. Sector and macro signals

| Signal type | Typical evidence/features | Method |
|---|---|---|
| SECTOR_STRESS_RISING | sector default/market/operating indicators | S/ML |
| SECTOR_DEMAND_SHOCK | sector production/sales data | S |
| COMMODITY_INPUT_PRICE_SHOCK | commodity exposure + price | S |
| FX_EXPOSURE_STRESS | currency exposure + FX move | S |
| INTEREST_RATE_SENSITIVITY_STRESS | floating debt + rates | S |
| REGIONAL_ECONOMIC_STRESS | geography + macro data | S/ML |
| PEER_PERFORMANCE_DIVERGENCE | counterparty vs peer cohort | A/S |

## 12. Correlated risk hypotheses

Correlation is a second-order analytical layer. Initial hypothesis types include:

- `EMERGING_LIQUIDITY_STRESS`
- `DEBT_SERVICE_STRESS`
- `OPERATING_DETERIORATION`
- `WORKING_CAPITAL_STRESS`
- `POTENTIAL_FUND_DIVERSION`
- `GOVERNANCE_STRESS`
- `GROUP_CONTAGION_RISK`
- `EXTERNAL_SHOCK_EXPOSURE`
- `DOCUMENT_INTEGRITY_CONCERN`

Example:

```text
WORKING_CAPITAL_UTILIZATION_SPIKE
+ RECEIVABLE_DAYS_DERIORATION
+ REPEATED_PAYMENT_RETURN
+ RATING_OUTLOOK_NEGATIVE
       |
       v
EMERGING_LIQUIDITY_STRESS
```

GenAI may explain/correlate these governed inputs, but material hypotheses should have structured drivers and evidence coverage independent of generated prose.

## 13. Signal policy example

```yaml
policyId: POL-PAYMENT-RETURN-001
signalType: REPEATED_PAYMENT_RETURN
version: 1.0
segment: CORPORATE
window: P30D
condition:
  metric: returned_payment_count
  operator: GTE
  value: 3
severity:
  medium: 3
  high: 5
minimumEvidenceQuality: VERIFIED
humanValidationRequired: true
cooldown: P7D
```

The example illustrates policy separation; values are not universal regulatory thresholds.

## 14. Phase-1 priority set

The first implementation should prioritize high-quality internal evidence before broad unstructured intelligence:

1. DPD and repayment deterioration.
2. Returned payments.
3. Working-capital utilization and limit excess.
4. LC/BG devolvement and unpaid obligations.
5. Financial-statement ratios/trends: current ratio, DSCR, leverage, profitability and cash flow.
6. Receivable/inventory/working-capital deterioration.
7. Covenant and monitoring-document breaches/delays.
8. Internal/external rating deterioration.
9. Collateral cover deterioration.
10. Selected fund-flow anomalies involving related parties/end use.

Phase 2 adds stronger external intelligence, entity graph, news/legal/regulatory extraction and peer/sector analytics. Phase 3 adds validated predictive and graph models with portfolio-level correlation.

## 15. Evaluation requirements

Each signal policy must be evaluated using more than accuracy. At minimum track precision/false-positive rate, recall/false-negative rate where labels exist, lead time before adverse outcome, alert volume per analyst, duplication rate, analyst acceptance/rejection, time-to-disposition, stability by segment, evidence quality and downstream risk/case outcome.

Threshold selection should explicitly model the asymmetric cost of missed deterioration versus excessive false alerts.

## 16. Next design tasks

- Convert priority signal types into machine-readable definitions.
- Define feature contracts and source-to-feature lineage.
- Define severity/confidence/risk-impact semantics.
- Define deduplication, correlation, decay and suppression rules.
- Map signal types to canonical events and Kafka topic strategy.
- Define labelled outcomes for backtesting: default, SMA/NPA migration, restructuring, rating deterioration, fraud/investigation outcome and analyst disposition.
