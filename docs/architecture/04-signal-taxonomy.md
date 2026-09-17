# EWS 2.0 — Global Corporate Signal Taxonomy

**Status:** Draft / Part II — normalized global taxonomy  
**Reference jurisdictions:** India, United States, United Kingdom  
**Purpose:** Production-oriented catalogue of global economic-risk semantics with product, jurisdiction and institution-policy extensions separated explicitly.

## 1. Taxonomy rules

- Signal semantics describe economic/credit-risk meaning; policies define thresholds, windows and applicability.
- Observations are not automatically signals.
- Severity, confidence, materiality and risk impact are separate.
- Fraud/integrity suspicion is not equivalent to credit deterioration or a legal finding.
- Every machine-proposed signal is evidence-addressable.
- External signals require source authority, entity-resolution confidence and source-rights controls.
- Accounting, prudential and supervisory classifications are not signal types.
- Product-specific signals are reusable internationally where the product exists; they are not forced into portfolios where it does not.
- Jurisdiction-specific regulatory/legal classifications live in adapters, not the global signal ontology.

## 2. Portability classes

| Class | Meaning |
|---|---|
| `GLOBAL_CORE` | Common economic-risk meaning across jurisdictions |
| `GLOBAL_PRODUCT_SPECIFIC` | Globally valid where the relevant lending/trade-finance/product structure exists |
| `JURISDICTION_EXTENSION` | A genuinely jurisdiction-specific risk observation/semantic, not merely a source mapping |
| `INSTITUTION_POLICY_SPECIFIC` | Internal monitoring/process semantics dependent on lender policy |

India/US/UK source records normally map into `GLOBAL_CORE` observations/signals rather than creating country-prefixed duplicates.

## 3. Detection methods

`R` deterministic/rule; `S` statistical/time-series; `ML` predictive ML; `A` anomaly; `NLP` document/text extraction; `G` graph/network; `AI` GenAI correlation/explanation, never sole authoritative detector for material facts.

## 4. Financial performance — GLOBAL_CORE

| Signal type | Typical evidence/features | Method |
|---|---|---|
| REVENUE_MATERIAL_DECLINE | comparable revenue trend | R/S |
| OPERATING_PROFIT_MATERIAL_DECLINE | EBIT/EBITDA/operating profit trend vs history/plan | R/S |
| EBITDA_MARGIN_DERIORATION | margin trend, peer delta | S/A |
| NET_LOSS_EMERGENCE | income statement | R |
| CURRENT_RATIO_DERIORATION | current assets/liabilities | R/S |
| QUICK_RATIO_DERIORATION | quick assets/current liabilities | R/S |
| LEVERAGE_DERIORATION | governed leverage measures | R/S |
| DEBT_EBITDA_DERIORATION | debt/EBITDA | R/S |
| DSCR_DERIORATION | cash available/debt service | R/S |
| INTEREST_COVERAGE_DERIORATION | operating earnings/interest | R/S |
| OPERATING_CASH_FLOW_NEGATIVE | cash-flow statement | R |
| CASH_FLOW_PROFIT_DIVERGENCE | earnings vs operating cash flow | S/A |
| RECEIVABLE_DAYS_DERIORATION | receivables/sales | S/A |
| INVENTORY_DAYS_DERIORATION | inventory/COGS | S/A |
| CASH_CONVERSION_CYCLE_DIVERGENCE | DSO/DIO/DPO | S/A |
| NET_WORTH_EROSION | tangible/adjusted net worth | R/S |
| CONTINGENT_LIABILITY_SPIKE | notes/schedules | R/A |
| RELATED_PARTY_EXPOSURE_SPIKE | financial notes/transactions/graph | R/A/G |
| AUDITOR_QUALIFICATION_ADVERSE | audit report | NLP/R |
| GOING_CONCERN_WARNING | audit report | NLP/R |
| FINANCIAL_STATEMENT_DELAY | expected vs received/filing date | R |
| FINANCIAL_RESTATEMENT_MATERIAL | revisions/non-reliance | R/NLP |
| FINANCIAL_REPORTING_RELIABILITY_CONCERN | restatement/non-reliance/control weakness | R/NLP |
| INTERNAL_CONTROL_WEAKNESS | authoritative filing/audit evidence | R/NLP |

`TOL_ATNW_DERIORATION` is retained as an institution/market feature-policy specialization under `LEVERAGE_DERIORATION`, rather than defining the global leverage ontology.

## 5. Transactional and liquidity — GLOBAL_CORE / PRODUCT_SPECIFIC

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| REPEATED_PAYMENT_RETURN | GLOBAL_CORE | returned payment instructions | R |
| HIGH_VALUE_PAYMENT_RETURN | GLOBAL_CORE | material returned instrument | R |
| UTILIZATION_HIGH | GLOBAL_CORE | utilization vs available commitment/capacity | R/S |
| UTILIZATION_SPIKE | GLOBAL_CORE | utilization velocity | S/A |
| OVERDRAWN_FREQUENCY_INCREASE | GLOBAL_PRODUCT_SPECIFIC | overdraft episodes | S/A |
| ACCOUNT_BALANCE_DRAIN | GLOBAL_CORE | balance trend | S/A |
| CREDIT_INFLOW_DECLINE | GLOBAL_CORE | account credits/turnover | S/A |
| DEBIT_CREDIT_PATTERN_ANOMALY | GLOBAL_CORE | transaction profile | A/ML |
| LARGE_UNUSUAL_TRANSFER | GLOBAL_CORE | amount/beneficiary anomaly | A |
| RELATED_ENTITY_TRANSFER_SPIKE | GLOBAL_CORE | transfers + relationship graph | R/A/G |
| CASH_FLOW_CONCENTRATION_INCREASE | GLOBAL_CORE | payer/customer concentration | S/A |
| TRANSACTION_VOLUME_COLLAPSE | GLOBAL_CORE | transaction trend | S/A |
| END_USE_MISMATCH_SUSPECTED | GLOBAL_PRODUCT_SPECIFIC | disbursement/purpose vs deployment | R/A/G |
| WORKING_CAPITAL_UTILIZATION_HIGH | GLOBAL_PRODUCT_SPECIFIC | WC facility utilization/capacity | R/S |
| WORKING_CAPITAL_UTILIZATION_SPIKE | GLOBAL_PRODUCT_SPECIFIC | WC utilization velocity | S/A |
| OUTSIDE_AGREED_ROUTING_ANOMALY | GLOBAL_PRODUCT_SPECIFIC | contractual cash-routing structure | R/G |
| ROUND_TRIPPING_PATTERN | GLOBAL_CORE | circular transaction graph | G/A/ML |

## 6. Repayment and credit conduct

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| DPD_EMERGED | GLOBAL_CORE | obligation schedule vs receipt | R |
| DPD_WORSENING | GLOBAL_CORE | DPD trajectory | S |
| REPEATED_LATE_PAYMENT | GLOBAL_CORE | payment history | R/S |
| SCHEDULED_OBLIGATION_MISSED | GLOBAL_CORE | contractual obligation | R |
| INTEREST_SERVICING_DELAY | GLOBAL_CORE | interest due/paid | R |
| LIMIT_EXCESS_RECURRING | GLOBAL_PRODUCT_SPECIFIC | exposure vs committed/approved capacity | R/S |
| TEMPORARY_LIMIT_DEPENDENCY | GLOBAL_PRODUCT_SPECIFIC | repeated temporary/adhoc increases | R/S |
| INTERNAL_RATING_DOWNGRADE | GLOBAL_CORE | internal grade history | R |
| EXTERNAL_RATING_DOWNGRADE | GLOBAL_CORE | rating action | R |
| RATING_OUTLOOK_NEGATIVE | GLOBAL_CORE | rating outlook | R/NLP |
| RESTRUCTURING_REQUESTED | GLOBAL_CORE | borrower/lender/workflow evidence | R |
| MULTIPLE_LENDER_STRESS | GLOBAL_CORE | syndicated/shared-credit/bureau intelligence | R/G |
| LC_DEVOLVEMENT | GLOBAL_PRODUCT_SPECIFIC | letter-of-credit event | R |
| GUARANTEE_INVOCATION | GLOBAL_PRODUCT_SPECIFIC | guarantee event | R |
| DEVOLVED_OR_INVOKED_OBLIGATION_UNPAID | GLOBAL_PRODUCT_SPECIFIC | trade-finance obligation | R |

`EMI_OR_INSTALLMENT_MISSED` becomes a product-specific policy/subtype of `SCHEDULED_OBLIGATION_MISSED`. `BG_INVOCATION` is normalized to `GUARANTEE_INVOCATION`.

## 7. Refinancing and funding — GLOBAL_CORE

| Signal type | Typical evidence/features | Method |
|---|---|---|
| REFINANCING_RISK_INCREASE | maturity wall, market access, facility changes | R/S/ML |
| FUNDING_COST_INCREASE | facility pricing, bond/loan spreads | R/S |
| COVENANT_WAIVER_FREQUENCY | waiver/amendment history | R/S |
| AMEND_AND_EXTEND_ACTIVITY | maturity amendments/extensions | R/S |
| LENDER_SUPPORT_WEAKENING | commitment/lender-set contraction | R/G |
| NEAR_TERM_MATURITY_CONCENTRATION | debt maturity profile | R/S |
| DEBT_ACCELERATION | authoritative acceleration/default disclosure | R/NLP |
| NEW_SECURITY_INTEREST_ACTIVITY | security-interest/charge/lien evidence | R/S |
| CREDITOR_STRUCTURE_CHANGE | secured creditor/lender structure | S/G |

These semantics support US/UK market intelligence without being US/UK-specific.

## 8. Covenant, monitoring and collateral

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| COVENANT_BREACH | GLOBAL_CORE | covenant measurement | R |
| COVENANT_HEADROOM_EROSION | GLOBAL_CORE | distance-to-threshold trend | S |
| REQUIRED_MONITORING_INFORMATION_DELAY | GLOBAL_CORE | obligation schedule | R |
| FACILITY_RENEWAL_OR_REVIEW_DELAY | INSTITUTION_POLICY_SPECIFIC | review/renewal workflow | R |
| SECURITY_CREATION_DELAY | GLOBAL_PRODUCT_SPECIFIC | contractual security requirement | R |
| SECURITY_PERFECTION_DELAY | GLOBAL_PRODUCT_SPECIFIC | perfection/registration status | R |
| COLLATERAL_INSURANCE_LAPSE | GLOBAL_PRODUCT_SPECIFIC | insurance requirement/status | R |
| BORROWER_MONITORING_NON_COOPERATION | INSTITUTION_POLICY_SPECIFIC | information/site/audit requests | R |
| COLLATERAL_VALUE_DECLINE | GLOBAL_CORE | valuation history | S |
| COLLATERAL_COVER_EROSION | GLOBAL_CORE | exposure vs eligible collateral | S |
| DRAWING_POWER_REDUCTION | GLOBAL_PRODUCT_SPECIFIC | borrowing-base/drawing-power history | R/S |

India-style `STOCK_STATEMENT_DELAY`, `STOCK_AUDIT_NON_COOPERATION` and `SITE_VISIT_NON_COOPERATION` become policy/subtypes under the generic monitoring semantics. They remain valid configurations where used.

## 9. Fraud and integrity

These are investigation triggers, not automatic fraud/legal classifications.

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| FUND_DIVERSION_SUSPECTED | GLOBAL_PRODUCT_SPECIFIC | agreed purpose vs flow | R/A/G |
| FUND_SIPHONING_SUSPECTED | GLOBAL_PRODUCT_SPECIFIC | flow/relationship evidence | R/A/G |
| FINANCIAL_DOCUMENT_INCONSISTENCY | GLOBAL_CORE | cross-document reconciliation | R/A/NLP |
| INVOICE_FABRICATION_SUSPECTED | GLOBAL_CORE | invoice/vendor validation | A/NLP/G |
| RECEIVABLE_OR_DEBTOR_DATA_ANOMALY | GLOBAL_CORE | receivable reconciliation | A/G |
| INVENTORY_OR_BORROWING_BASE_INCONSISTENCY | GLOBAL_PRODUCT_SPECIFIC | collateral/borrowing-base submissions | R/A |
| TURNOVER_OVERSTATEMENT_SUSPECTED | GLOBAL_CORE | tax/bank/accounting reconciliation | R/A |
| RELATED_PARTY_DIVERSION_PATTERN | GLOBAL_CORE | flows + graph | G/A |
| SHELL_ENTITY_EXPOSURE_SUSPECTED | GLOBAL_CORE | entity/graph intelligence | G/NLP |
| ROUND_TRIPPING_SUSPECTED | GLOBAL_CORE | transaction network | G/ML |
| FINANCING_TERM_MISUSE_SUSPECTED | GLOBAL_PRODUCT_SPECIFIC | financing terms/end use | R/A |
| FALSE_REPRESENTATION_SUSPECTED | GLOBAL_CORE | contradictory verified records | R/NLP |

`GST` is a possible India source for turnover reconciliation, not part of the global signal name.

## 10. Management, ownership and governance

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| KEY_MANAGEMENT_RESIGNATION | GLOBAL_CORE | authoritative filing/announcement | R/NLP |
| MANAGEMENT_TURNOVER_CLUSTER | GLOBAL_CORE | officer/director changes | S |
| MATERIAL_OWNERSHIP_OR_CONTROL_CHANGE | GLOBAL_CORE | ownership/control registry/filing | R/G |
| MATERIAL_OWNER_STAKE_REDUCTION | GLOBAL_CORE | ownership filings | R/S |
| DIRECTOR_OR_OFFICER_FREQUENT_CHANGE | GLOBAL_CORE | registry history | S |
| AUDITOR_RESIGNATION | GLOBAL_CORE | authoritative filing | R/NLP |
| AUDITOR_FREQUENT_CHANGE | GLOBAL_CORE | filing history | S |
| CFO_RESIGNATION | GLOBAL_CORE | filing/announcement | R/NLP |
| GOVERNANCE_ADVERSE_EVENT | GLOBAL_CORE | regulator/company disclosure | NLP/R |
| MANAGEMENT_LITIGATION_MATERIAL | GLOBAL_CORE | legal intelligence | NLP/G |
| MANAGEMENT_DEFAULT_ASSOCIATION | GLOBAL_CORE | verified credit/entity data | G/R |
| CONTROLLING_OWNER_PLEDGE_INCREASE | GLOBAL_PRODUCT_SPECIFIC | pledge/security filing | R/S |

`PROMOTER_*` names become India policy/source aliases of globally portable owner/controller semantics rather than canonical global signal names.

## 11. Legal, insolvency, regulatory and external

| Signal type | Class | Typical evidence/features | Method |
|---|---|---|---|
| FORMAL_INSOLVENCY_PROCEEDING | GLOBAL_CORE | authoritative court/registry | R/NLP |
| FORMAL_RESTRUCTURING_EVENT | GLOBAL_CORE | court/borrower/lender disclosure | R/NLP |
| MATERIAL_LITIGATION_FILED | GLOBAL_CORE | authoritative legal source | NLP |
| LEGAL_CREDITOR_PRESSURE | GLOBAL_CORE | creditor/legal actions | R/NLP |
| REGULATORY_ENFORCEMENT_ACTION | GLOBAL_CORE | regulator source | R/NLP |
| STATUTORY_OR_TAX_DEFAULT_DISCLOSED | GLOBAL_CORE | authoritative disclosure | R/NLP |
| TAX_OR_STATUTORY_ATTACHMENT | GLOBAL_CORE | official/legal source | R/NLP |
| ADVERSE_NEWS_MATERIAL | GLOBAL_CORE | approved news sources | NLP/AI |
| ADVERSE_NEWS_VELOCITY_SPIKE | GLOBAL_CORE | news-event time series | S/NLP |
| REPUTATIONAL_CONTROVERSY | GLOBAL_CORE | multi-source intelligence | NLP/AI |

`INSOLVENCY_PROCEEDING_FILED` no longer references NCLT in its canonical definition. Procedure subtype and jurisdiction distinguish NCLT/CIRP, Chapter 11, UK administration/liquidation/CVA and other regimes.

## 12. Market-implied credit — GLOBAL_CORE

| Signal type | Typical evidence/features | Method |
|---|---|---|
| MARKET_PRICE_SHOCK | equity/debt market feed | S/A |
| MARKET_IMPLIED_CREDIT_STRESS | bond yield/spread/price/liquidity features | S/A/ML |
| CREDIT_SPREAD_DIVERGENCE | issuer vs sector/rating peers | S/A |
| BOND_PRICE_DISTRESS | price drawdown | S/A |
| MARKET_LIQUIDITY_DETERIORATION | volume/bid-ask/liquidity proxies | S/A |
| BOND_YIELD_OR_SPREAD_STRESS | governed yield/spread feature | S/A |

Security/issuer resolution, benchmark methodology and liquidity quality are mandatory lineage.

## 13. Relationship and contagion

| Signal type | Typical evidence/features | Method |
|---|---|---|
| GROUP_ENTITY_DISTRESS | related entity state | G/R |
| CONTROLLING_OWNER_LINKED_DISTRESS | owner/controller relationship + distress | G |
| GUARANTOR_DISTRESS | guarantor state | G/R |
| MAJOR_CUSTOMER_DISTRESS | concentration + entity state | G/S |
| MAJOR_SUPPLIER_DISTRESS | concentration + entity state | G/S |
| GROUP_CROSS_DEFAULT | linked obligations/defaults | G/R |
| RELATIONSHIP_RISK_CONCENTRATION | graph exposure concentration | G/S |
| CONTAGION_SCORE_SPIKE | graph-derived propagation | G/ML |

`PROMOTER_LINKED_DISTRESS` becomes an India-facing alias/policy mapping to `CONTROLLING_OWNER_LINKED_DISTRESS`.

## 14. Sector and macro

`SECTOR_STRESS_RISING`, `SECTOR_DEMAND_SHOCK`, `COMMODITY_INPUT_PRICE_SHOCK`, `FX_EXPOSURE_STRESS`, `INTEREST_RATE_SENSITIVITY_STRESS`, `REGIONAL_ECONOMIC_STRESS` and `PEER_PERFORMANCE_DIVERGENCE` remain `GLOBAL_CORE`. Data sources and calibration are market-specific.

## 15. Correlated risk hypotheses

Global hypotheses include:

```text
EMERGING_LIQUIDITY_STRESS
DEBT_SERVICE_STRESS
OPERATING_DETERIORATION
WORKING_CAPITAL_STRESS
REFINANCING_STRESS
MARKET_CREDIT_STRESS
POTENTIAL_FUND_DIVERSION
GOVERNANCE_STRESS
FINANCIAL_REPORTING_STRESS
LEGAL_CREDITOR_STRESS
GROUP_CONTAGION_RISK
EXTERNAL_SHOCK_EXPOSURE
DOCUMENT_INTEGRITY_CONCERN
```

Example:

```text
NEAR_TERM_MATURITY_CONCENTRATION
+ CREDIT_SPREAD_DIVERGENCE
+ EXTERNAL_RATING_DOWNGRADE
+ COVENANT_WAIVER_FREQUENCY
        |
        v
REFINANCING_STRESS
```

## 16. Jurisdiction classification boundary

The following are **not signal types**:

```text
SMA / NPA
US Special Mention / Substandard / Doubtful / Loss
US nonaccrual
CECL allowance/classification outputs
UK/IFRS9 Stage 1 / Stage 2 / Stage 3
UK SICR
UK prudential default
IRB regulatory classifications
```

They belong to governed classification adapters. EWS signals/features can be inputs, but no one-to-one inference is assumed.

## 17. Alias/deprecation mapping

| Existing name | Global treatment |
|---|---|
| `TOL_ATNW_DERIORATION` | specialization under `LEVERAGE_DERIORATION` |
| `EMI_OR_INSTALLMENT_MISSED` | subtype of `SCHEDULED_OBLIGATION_MISSED` |
| `BG_INVOCATION` | rename `GUARANTEE_INVOCATION` |
| `ADHOC_LIMIT_DEPENDENCY` | rename `TEMPORARY_LIMIT_DEPENDENCY` |
| `GROUP_ENTITY_TRANSFER_SPIKE` | rename `RELATED_ENTITY_TRANSFER_SPIKE` |
| `OUTSIDE_BANK_ROUTING_ANOMALY` | rename `OUTSIDE_AGREED_ROUTING_ANOMALY` |
| `STOCK_STATEMENT_DELAY` | policy subtype of `REQUIRED_MONITORING_INFORMATION_DELAY` |
| `STOCK_AUDIT_NON_COOPERATION` | subtype of `BORROWER_MONITORING_NON_COOPERATION` |
| `SITE_VISIT_NON_COOPERATION` | subtype of `BORROWER_MONITORING_NON_COOPERATION` |
| `DEBTOR_LIST_ANOMALY` | rename `RECEIVABLE_OR_DEBTOR_DATA_ANOMALY` |
| `STOCK_STATEMENT_INCONSISTENCY` | normalize to `INVENTORY_OR_BORROWING_BASE_INCONSISTENCY` |
| `SANCTION_TERM_MISUSE_SUSPECTED` | rename `FINANCING_TERM_MISUSE_SUSPECTED` |
| `PROMOTER_EXIT_OR_STAKE_REDUCTION` | map to `MATERIAL_OWNER_STAKE_REDUCTION` / control change |
| `PROMOTER_PLEDGE_INCREASE` | map to `CONTROLLING_OWNER_PLEDGE_INCREASE` |
| `PROMOTER_LINKED_DISTRESS` | map to `CONTROLLING_OWNER_LINKED_DISTRESS` |
| `DIRECTOR_DEFAULT_ASSOCIATION` | normalize `MANAGEMENT_DEFAULT_ASSOCIATION` |
| `INSOLVENCY_PROCEEDING_FILED` | normalize `FORMAL_INSOLVENCY_PROCEEDING` |

Existing event/history data can retain old semantic version aliases. Do not silently reinterpret historical signal instances.

## 18. Policy example — jurisdiction neutral

```yaml
policyId: POL-PAYMENT-RETURN-CORP-001
signalType: REPEATED_PAYMENT_RETURN
version: 2.0
applicability:
  portfolio: CORPORATE
  jurisdictions: ["*"]
window: P30D
condition:
  feature: returned_payment_count_30d
  operator: GTE
  value: ${institutionPolicy.threshold}
minimumEvidenceQuality: VERIFIED
humanValidationRequired: true
```

The threshold is institution policy, not a universal regulatory constant.

## 19. Implementation priorities

Priority is evidence availability and predictive/operational value, not a fixed country-centric sequence.

**Core internal spine:** DPD/repayment, payment returns, utilization/limit behaviour, financial ratios/cash flow, covenant/monitoring, ratings, collateral and transaction anomalies.

**External structured spine where available:** rating actions, company/ownership/officer changes, financing amendments/default disclosures, security-interest activity, insolvency/legal events and market-implied credit features.

**Advanced layer:** graph contagion, peer/sector analytics, validated predictive models, document intelligence and governed correlation.

For public US/UK corporates, structured external intelligence can enter the first implementation wave rather than waiting for a generic 'Phase 2'.

## 20. Evaluation requirements

Evaluate precision/false-positive rate, recall/false-negative rate where labels exist, lead time, alert volume, duplication, analyst disposition, stability by jurisdiction/segment, evidence quality, source/entity-resolution failure, and downstream risk/case outcome.

Cross-market model/policy evaluation must test calibration and performance separately by jurisdiction/population. Aggregate global accuracy cannot hide poor local calibration.

## 21. Next design tasks

- propagate normalized signal names into priority signal contracts and event catalogue;
- add jurisdiction/classification adapter contracts;
- add external source/entity-resolution quality fields to machine-readable schemas;
- define migration aliases for already-persisted signal types;
- complete Part-III schema/topic strategy and stream-processing topology.