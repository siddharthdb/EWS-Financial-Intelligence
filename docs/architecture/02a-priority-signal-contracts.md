# EWS 2.0 — Priority Signal Contracts

**Status:** Draft / Part II  
**Scope:** Global corporate counterparty EWS; initial India/US/UK reference implementations  
**Purpose:** Convert high-value global signal taxonomy entries into implementation-grade semantic contracts.

## 1. Contract rules

A signal contract defines economic-risk meaning and required inputs. Signal policy defines institution/jurisdiction/segment/product thresholds, windows, severity and risk impact. Accounting, prudential and supervisory classifications remain separate governed outputs.

Every signal instance carries entity scope, effective/knowledge time, evidence IDs, feature snapshots, policy/rule/model version, severity, confidence, materiality, quality, lifecycle and validation requirement.

## 2. Source authority and rights

Evidence authority is contextual:

- **T1:** authoritative internal or official legal/regulatory/company/court records for the asserted fact.
- **T2:** verified contracted external sources such as ratings, bureau and market-data providers.
- **T3:** corroborated company/exchange/news/research intelligence.
- **T4:** unverified/open intelligence.

External evidence additionally references source-rights policy, entity/security resolution and jurisdiction. Public availability does not imply unrestricted redistribution/model/LLM use.

## 3. Data-quality assessment

`COMPLETE | PARTIAL | STALE | CONFLICTED | UNVERIFIED | INSUFFICIENT`

Quality considers completeness, freshness, reconciliation, source authority, extraction confidence, entity/security resolution, corroboration and market-liquidity quality where applicable.

## 4. Core operational contracts

### P01 — DPD_EMERGED
A contractual payment obligation became overdue. Inputs: authoritative schedule/receipts; features `current_dpd`, overdue amount/count. Factual DPD and jurisdiction-specific default/classification remain separate.

### P02 — DPD_WORSENING
Repayment delinquency materially increases. Features: DPD velocity, rolling max, cure/relapse. Do not emit unchanged daily duplicates.

### P03 — REPEATED_PAYMENT_RETURN
Multiple borrower-funded payment instructions are returned for liquidity/funding reasons. Technical/network/beneficiary-detail returns are excluded by reason policy.

### P04 — HIGH_VALUE_PAYMENT_RETURN
A single materially significant borrower-funded payment instruction is returned.

### P05 — UTILIZATION_HIGH
Exposure is persistently close to available committed/approved capacity. Product policy defines applicable capacity. `WORKING_CAPITAL_UTILIZATION_HIGH` is a product specialization where drawing power/borrowing-base semantics apply.

### P06 — UTILIZATION_SPIKE
Utilization rises materially relative to recent baseline. `WORKING_CAPITAL_UTILIZATION_SPIKE` is the WC product specialization.

### P07 — LIMIT_EXCESS_RECURRING
Exposure repeatedly/continuously exceeds applicable approved/committed capacity. Regulatory out-of-order/default treatment remains separate.

### P08 — LC_DEVOLVEMENT
A letter-of-credit obligation devolves where the product exists. `GLOBAL_PRODUCT_SPECIFIC`.

### P09 — GUARANTEE_INVOCATION
A guarantee is invoked. Invocation is not automatically borrower default/fraud. `GLOBAL_PRODUCT_SPECIFIC`. Historical alias: `BG_INVOCATION`.

## 5. Financial contracts

### P10 — DSCR_DERIORATION
Debt-service capacity materially weakens. Definition/version pins numerator, debt service, period alignment and actual/projected status.

### P11 — CURRENT_RATIO_DERIORATION
Short-term balance-sheet liquidity materially weakens; interpretation is sector/portfolio contextual.

### P12 — LEVERAGE_DERIORATION
Governed leverage materially worsens. Implementations may use debt/EBITDA, debt/equity, TOL/ATNW or other approved measures. `DEBT_EBITDA_DERIORATION` can remain a specific child policy/signal where required.

### P13 — OPERATING_PROFIT_MATERIAL_DECLINE
Operating performance materially deteriorates against history, plan or peers.

### P14 — OPERATING_CASH_FLOW_NEGATIVE
Operating cash flow is negative for a relevant period, interpreted with seasonality/business-model context.

### P15 — RECEIVABLE_DAYS_DERIORATION
Collection cycle materially lengthens.

### P16 — INVENTORY_DAYS_DERIORATION
Inventory holding period materially increases.

## 6. Covenant, monitoring and collateral

### P17 — COVENANT_BREACH
A contractual covenant is outside permitted terms. Cure/waiver/amendment creates new governed records; original breach remains immutable.

### P18 — REQUIRED_MONITORING_INFORMATION_DELAY
Required monitoring information was not received by governed due date. Product/institution aliases can include stock statement or financial monitoring delays.

### P19 — EXTERNAL_RATING_DOWNGRADE
Approved external rating provider downgrades entity/instrument. Preserve provider scale; normalization is governed/versioned.

### P20 — COLLATERAL_COVER_EROSION
Verified eligible collateral relative to secured exposure materially declines. Legal perfection/enforceability is distinct from value.

## 7. Integrity, governance and contagion

### P21 — FUND_DIVERSION_SUSPECTED
Evidence suggests financing proceeds may have been deployed outside agreed/approved purpose. Mandatory human investigation; never autonomous legal/fraud confirmation.

### P22 — RELATED_ENTITY_TRANSFER_SPIKE
Transfers to resolved related entities increase materially relative to expected/historical behaviour. Related-party activity is not intrinsically adverse.

### P23 — AUDITOR_QUALIFICATION_ADVERSE
Audited financial statements contain material adverse qualification/matter. NLP-extracted material cases require governed verification.

### P24 — KEY_MANAGEMENT_RESIGNATION
A material key-management/officer departure occurs. Role criticality, succession and surrounding signals determine interpretation.

### P25 — GROUP_ENTITY_DISTRESS
A materially related entity enters adverse state capable of transmitting risk; graph propagation reflects economic dependency, guarantees/exposure/control, not mere group membership.

## 8. International external contracts

### P26 — REFINANCING_RISK_INCREASE
**Meaning:** Evidence indicates increasing difficulty or cost in refinancing material debt/facilities.

- Features: near-term maturity concentration, facility amendment/extension frequency, lender-set change, funding cost, market spreads, rating movement.
- Evidence: internal facilities, authoritative filings, contracted loan/market data, ratings.
- Guardrail: approaching maturity alone is not distress; assess available liquidity/market access/support.

### P27 — COVENANT_WAIVER_FREQUENCY
Repeated waivers/amendments indicate increasing contractual accommodation.

- Preserve original covenant/breach history.
- Distinguish administrative amendments from economically meaningful waivers.

### P28 — NEW_SECURITY_INTEREST_ACTIVITY
New security interests/charges/liens are created at unusual/material frequency or scale.

- Sources can include authorised US UCC data, UK Companies House charges, internal collateral/security systems or equivalent registries.
- Global signal does not claim legal equivalence among regimes.
- Entity/debtor/secured-party resolution mandatory.

### P29 — MARKET_IMPLIED_CREDIT_STRESS
Debt-market behaviour indicates material issuer credit deterioration.

- Features: bond price/yield/spread changes, peer divergence, liquidity quality.
- Security-to-issuer resolution mandatory.
- Avoid interpreting illiquid/stale pricing as high-confidence credit deterioration.

### P30 — CREDIT_SPREAD_DIVERGENCE
Issuer spread materially worsens relative to comparable sector/rating/maturity cohort.

- Benchmark methodology/version and currency/duration normalization are lineage.

### P31 — FINANCIAL_REPORTING_RELIABILITY_CONCERN
Authoritative evidence raises concern about reliability of reported financial information.

- Inputs can include restatement, non-reliance disclosure, material control weakness, adverse audit matter.
- This is not an allegation of fraud absent separate evidence/investigation.

### P32 — MANAGEMENT_TURNOVER_CLUSTER
Multiple material officer/director departures occur within a policy window.

- Role criticality and succession context required.
- Entity registry/filing source preferred to news-only inference.

### P33 — FORMAL_INSOLVENCY_PROCEEDING
An authoritative legal source records commencement of a formal insolvency/restructuring procedure.

- Procedure subtype/jurisdiction preserved: e.g. applicable Indian insolvency process, US bankruptcy chapter, UK administration/liquidation/CVA, etc.
- Legal status is an observation; risk impact is policy-governed.

### P34 — LEGAL_CREDITOR_PRESSURE
Verified creditor/legal actions indicate increasing collection/enforcement pressure.

- Requires authoritative/corroborated evidence and entity match.
- Avoid treating ordinary commercial litigation as equivalent to credit distress.

## 9. Classification adapters

The platform maintains independent namespaces rather than one regulatory state:

```text
IN_SMA_NPA
US_SUPERVISORY_CLASSIFICATION
US_ACCRUAL_STATUS
US_CECL
UK_SICR
UK_IFRS9_STAGE
UK_PRUDENTIAL_DEFAULT
UK_IRB
```

EWS features/signals may inform these adapters where governance permits, but do not mechanically determine them. IFRS 9 SICR is relative to initial-recognition credit risk and incorporates reasonable/supportable forward-looking information; it is not merely a generic EWS threshold.

## 10. Signal episodes

Repeated observations form episodes with opened/last-observed time, peak/current severity, occurrence count, evidence, state, cooldown and resolution. Analyst-visible updates occur on material state/evidence/policy changes rather than every repeated observation.

## 11. Severity, confidence and materiality

Severity = potential consequence; confidence = reliability of assertion; materiality = importance relative to entity/exposure/portfolio. They remain orthogonal.

## 12. Score impact

Signal contracts do not hard-code score points. Separately governed aggregation prevents double counting and permits jurisdiction/portfolio calibration.

## 13. Implementation sequencing

Implementation waves are portfolio/source driven rather than India-first:

- **Operational core:** P01-P09, P17-P20.
- **Financial core:** P10-P16.
- **Integrity/relationship:** P21-P25.
- **Structured external/market:** P26-P34 can run in parallel wherever authoritative sources exist, especially public US/UK corporates.

The ordering objective is evidence quality and business value, not geography.