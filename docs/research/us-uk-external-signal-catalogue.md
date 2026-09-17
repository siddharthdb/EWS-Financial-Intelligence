# US / UK External Intelligence Signal Catalogue

**Status:** Research baseline for EWS 2.0 international external intelligence  
**Scope:** Source evidence -> canonical observation -> feature -> proposed EWS signal  
**Jurisdictions:** United States and United Kingdom

## 1. Purpose

This catalogue converts the external-source landscape into implementable risk intelligence. It deliberately separates a source fact from a risk interpretation.

```text
Official/licensed source
        ↓
Raw evidence
        ↓
Canonical observation
        ↓
Entity resolution
        ↓
Governed feature
        ↓
Signal policy
        ↓
Proposed signal
        ↓
Human / credit-policy validation
```

A filing, lien, charge, bankruptcy case, rating action or bond-price move is evidence. It is not automatically a credit conclusion.

## 2. Source authority and corroboration

Suggested authority classes:

- A1 — authoritative legal/regulatory/company filing source;
- A2 — regulated market/transparency source;
- A3 — licensed rating/market/loan data provider;
- A4 — reputable public/news source;
- A5 — inferred/extracted intelligence requiring corroboration.

Signal policies declare whether one source is sufficient or corroboration is required.

## 3. United States signal map

### US-01 SEC periodic financial deterioration

**Sources:** SEC EDGAR 10-K, 10-Q, XBRL company facts.

Canonical observations:

- FINANCIAL_STATEMENT_FILED
- FINANCIAL_FACT_REPORTED
- GOING_CONCERN_DISCLOSED
- MATERIAL_WEAKNESS_DISCLOSED

Features:

- revenue_yoy
- ebitda_margin
- operating_cash_flow
- debt_to_ebitda
- interest_coverage
- current_ratio
- receivable_days
- inventory_days
- cash_balance_change
- debt_maturity_12m
- financial_restated_flag

Candidate signals:

- REVENUE_DERIORATION
- MARGIN_COMPRESSION
- OPERATING_CASH_FLOW_NEGATIVE
- LEVERAGE_DETERIORATION
- LIQUIDITY_BUFFER_EROSION
- WORKING_CAPITAL_STRESS
- FINANCIAL_RESTATEMENT
- GOING_CONCERN_WARNING

Control: XBRL facts require taxonomy/unit/period/context normalization before ratio calculation.

### US-02 SEC 8-K bankruptcy/receivership

**Source:** SEC 8-K Item 1.03 where applicable, corroborated with court source.

Observation:

- BANKRUPTCY_OR_RECEIVERSHIP_DISCLOSED

Features:

- bankruptcy_disclosure_flag
- bankruptcy_case_match_confidence
- days_since_bankruptcy_disclosure

Signals:

- BANKRUPTCY_FILED
- FORMAL_RESTRUCTURING_EVENT

Policy: legal state should be confirmed against authoritative court/case evidence where available; disclosure alone is not used to invent case status.

### US-03 SEC material debt acceleration/default

**Source:** 8-K Item 2.04 and associated exhibits.

Observations:

- DEBT_DEFAULT_DISCLOSED
- DEBT_ACCELERATION_DISCLOSED
- CROSS_DEFAULT_DISCLOSED

Features:

- debt_default_count_12m
- debt_acceleration_count_12m
- affected_debt_amount
- affected_debt_to_total_debt

Signals:

- MATERIAL_DEBT_DEFAULT
- DEBT_ACCELERATION
- REFINANCING_OR_LIQUIDITY_STRESS

### US-04 Credit agreement / amendment extraction

**Sources:** SEC exhibits attached to 8-K/10-Q/10-K; licensed syndicated-loan data where contracted.

Observations:

- CREDIT_FACILITY_EXECUTED
- CREDIT_FACILITY_AMENDED
- COVENANT_WAIVER_DISCLOSED
- MATURITY_EXTENDED
- PRICING_MARGIN_CHANGED
- FACILITY_SIZE_CHANGED
- LENDER_SET_CHANGED

Features:

- amendment_count_12m
- waiver_count_12m
- maturity_extension_count_24m
- pricing_spread_change_bps
- committed_facility_change_pct
- lender_count_change
- nearest_material_maturity_days

Signals:

- COVENANT_WAIVER_FREQUENCY
- AMEND_AND_EXTEND_ACTIVITY
- REFINANCING_RISK_INCREASE
- FUNDING_COST_INCREASE
- LENDER_SUPPORT_WEAKENING

Guardrail: amendment activity is not inherently adverse; refinancing undertaken from strength must not be mislabeled distress.

### US-05 Auditor/accounting events

**Sources:** SEC 8-K accounting/auditor disclosures and periodic reports.

Observations:

- AUDITOR_CHANGED
- AUDITOR_RESIGNED_OR_DISMISSED
- FINANCIALS_NON_RELIANCE_DISCLOSED
- MATERIAL_WEAKNESS_DISCLOSED

Features:

- auditor_change_count_24m
- non_reliance_flag
- material_weakness_count

Signals:

- AUDITOR_CHANGE
- FINANCIAL_REPORTING_RELIABILITY_CONCERN
- INTERNAL_CONTROL_WEAKNESS

### US-06 Management/governance disruption

**Source:** SEC 8-K executive/director disclosures.

Observations:

- CEO_DEPARTURE
- CFO_DEPARTURE
- DIRECTOR_DEPARTURE
- KEY_OFFICER_APPOINTED

Features:

- key_management_departures_180d
- cfo_turnover_24m
- clustered_departures_90d

Signals:

- KEY_MANAGEMENT_RESIGNATION
- MANAGEMENT_TURNOVER_CLUSTER

Guardrail: ordinary succession/retirement must be distinguishable from unexpected departure.

### US-07 Chapter 11 / federal bankruptcy

**Sources:** PACER/Case Locator or authorised court-data provider; SEC corroboration for reporting issuers.

Observations:

- BANKRUPTCY_CASE_FILED
- CHAPTER_11_CASE_FILED
- BANKRUPTCY_CASE_STATUS_CHANGED

Features:

- active_bankruptcy_case_flag
- chapter_11_flag
- filing_age_days
- entity_match_confidence

Signals:

- BANKRUPTCY_FILED
- CHAPTER_11_RESTRUCTURING

Entity resolution is mandatory; party-name similarity alone is insufficient.

### US-08 UCC financing / lien activity

**Sources:** authorised state bulk/API services or licensed nationwide UCC provider.

Observations:

- UCC_FINANCING_STATEMENT_FILED
- UCC_AMENDMENT_FILED
- UCC_CONTINUATION_FILED
- UCC_TERMINATION_FILED

Features:

- new_ucc_filings_90d
- active_secured_party_count
- ucc_amendments_12m
- ucc_terminations_12m
- new_secured_creditor_count_90d

Candidate signals:

- NEW_SECURITY_INTEREST_ACTIVITY
- SECURED_BORROWING_INCREASE
- CREDITOR_STRUCTURE_CHANGE

Guardrail: a UCC filing is not proof of financial distress. It becomes meaningful through change, concentration, frequency, financing context and corroboration.

### US-09 Rating deterioration

**Sources:** licensed S&P/Moody's/Fitch or other approved provider.

Observations:

- RATING_CHANGED
- OUTLOOK_CHANGED
- CREDITWATCH_CHANGED

Features:

- rating_notches_change_90d
- rating_notches_change_12m
- negative_outlook_flag
- negative_watch_flag
- days_since_downgrade

Signals:

- EXTERNAL_RATING_DOWNGRADE
- NEGATIVE_RATING_OUTLOOK
- RATING_WATCH_NEGATIVE
- RATING_MIGRATION_ACCELERATION

Provider scales must map through a canonical ordinal/semantic layer without losing original rating values.

### US-10 TRACE/bond market deterioration

**Source:** FINRA TRACE-derived licensed/authorised datasets and market/reference data.

Observations:

- BOND_TRADE_OBSERVED
- BOND_PRICE_OBSERVED
- BOND_YIELD_OBSERVED

Features:

- bond_price_drawdown_5d/30d
- yield_change_bps_5d/30d
- spread_change_bps_5d/30d
- issuer_vs_sector_spread_zscore
- issuer_vs_rating_bucket_spread_zscore
- abnormal_trade_volume
- liquidity_proxy_change

Signals:

- MARKET_IMPLIED_CREDIT_STRESS
- CREDIT_SPREAD_DIVERGENCE
- BOND_PRICE_DISTRESS
- MARKET_LIQUIDITY_DETERIORATION

Guardrail: market moves require benchmark/sector/rating/maturity normalization and minimum-liquidity gates.

## 4. United Kingdom signal map

### UK-01 Companies House accounts deterioration

**Sources:** Companies House filing data and XBRL/iXBRL accounts where available.

Observations/features/signals broadly mirror US financial-statement analytics but use UK accounting/company identifiers and filing semantics.

Additional features:

- accounts_filing_delay_days
- accounts_overdue_flag
- filing_frequency_change

Signals:

- FINANCIAL_REPORTING_DELAY
- ACCOUNTS_OVERDUE
- plus financial deterioration signals shared with the core catalogue.

### UK-02 Company status changes

**Source:** Companies House company-information stream.

Observations:

- COMPANY_STATUS_CHANGED
- REGISTERED_OFFICE_CHANGED
- COMPANY_NAME_CHANGED

Features:

- company_status
- status_change_count_12m
- registered_office_change_count_12m

Signals:

- MATERIAL_COMPANY_STATUS_CHANGE
- CORPORATE_ADMINISTRATION_CHANGE_CLUSTER

Address/name changes are contextual indicators, not standalone distress conclusions.

### UK-03 Officer/director changes

**Source:** Companies House officers stream.

Observations:

- DIRECTOR_APPOINTED
- DIRECTOR_RESIGNED
- OFFICER_CHANGED

Features:

- director_resignations_90d
- director_turnover_12m
- clustered_director_resignations_90d

Signals:

- DIRECTOR_RESIGNATION_CLUSTER
- GOVERNANCE_TURNOVER

### UK-04 Persons with significant control

**Source:** Companies House PSC stream.

Observations:

- PSC_ADDED
- PSC_REMOVED
- CONTROL_NATURE_CHANGED

Features:

- psc_change_count_12m
- beneficial_control_change_flag

Signals:

- MATERIAL_OWNERSHIP_OR_CONTROL_CHANGE

This is primarily governance/relationship intelligence and should feed the entity graph as well as EWS.

### UK-05 Charges/security interests

**Source:** Companies House charges stream/filings.

Observations:

- CHARGE_CREATED
- CHARGE_SATISFIED
- CHARGE_RELEASED
- CHARGE_UPDATED

Features:

- new_charge_count_90d
- outstanding_charge_count
- charge_creation_velocity
- charge_satisfaction_rate
- secured_creditor_count_change

Signals:

- NEW_SECURITY_INTEREST_ACTIVITY
- SECURED_BORROWING_INCREASE
- CREDITOR_STRUCTURE_CHANGE

Guardrail: new security can accompany healthy acquisition/growth financing; policy requires context/corroboration.

### UK-06 Insolvency

**Sources:** Companies House insolvency stream, The Gazette and authoritative insolvency/court sources as appropriate.

Observations:

- INSOLVENCY_CASE_OPENED
- ADMINISTRATION_STARTED
- LIQUIDATION_STARTED
- CVA_STARTED
- RECEIVERSHIP_STARTED
- MORATORIUM_STARTED
- INSOLVENCY_CASE_STATUS_CHANGED

Features:

- active_insolvency_case_flag
- insolvency_procedure_type
- days_since_procedure_start

Signals:

- FORMAL_INSOLVENCY_PROCEEDING
- ADMINISTRATION_STARTED
- LIQUIDATION_STARTED
- CVA_STARTED
- MORATORIUM_STARTED

These legal-state signals should not be inferred from news if authoritative registry evidence is available.

### UK-07 Gazette notices

**Source:** The Gazette under applicable access/licensing terms.

Observations may include:

- WINDING_UP_NOTICE_PUBLISHED
- INSOLVENCY_NOTICE_PUBLISHED
- CREDITOR_NOTICE_PUBLISHED

Features:

- adverse_legal_notices_90d
- notice_type_count

Signals:

- WINDING_UP_OR_INSOLVENCY_NOTICE
- LEGAL_CREDITOR_PRESSURE

Correlate against Companies House/court/insolvency evidence before strong legal conclusions.

### UK-08 FCA/RNS issuer disclosures

**Sources:** approved regulated-information/RNS/licensed market feeds, issuer announcements, FCA data where applicable.

Observations:

- PROFIT_WARNING_PUBLISHED
- TRADING_UPDATE_PUBLISHED
- DEBT_RESTRUCTURING_DISCLOSED
- COVENANT_EVENT_DISCLOSED
- MATERIAL_FINANCING_DISCLOSED
- MANAGEMENT_CHANGE_DISCLOSED

Features:

- profit_warning_count_12m
- guidance_revision_count
- debt_restructuring_disclosure_flag
- covenant_event_count

Signals:

- PROFIT_WARNING
- GUIDANCE_DETERIORATION
- REFINANCING_RISK_INCREASE
- COVENANT_STRESS
- KEY_MANAGEMENT_RESIGNATION

Natural-language extraction is proposed evidence until grounded in the original announcement/document.

### UK-09 Rating deterioration

Same provider-neutral canonical model and core signals as US-09.

### UK-10 UK bond consolidated tape / market data

**Sources:** authorised/licensed UK bond consolidated-tape data and security/reference data.

Features/signals mirror US market-implied analytics where sufficient trade coverage/liquidity exists:

- spread_change_bps
- price_drawdown
- abnormal_volume
- issuer_vs_peer spread divergence
- liquidity deterioration

Signals:

- MARKET_IMPLIED_CREDIT_STRESS
- CREDIT_SPREAD_DIVERGENCE
- BOND_PRICE_DISTRESS

## 5. Cross-jurisdiction canonical signal families

The source mechanics differ, but many semantic signals should remain global:

### Financial
- REVENUE_DERIORATION
- MARGIN_COMPRESSION
- OPERATING_CASH_FLOW_NEGATIVE
- LEVERAGE_DETERIORATION
- LIQUIDITY_BUFFER_EROSION
- WORKING_CAPITAL_STRESS
- GOING_CONCERN_WARNING
- FINANCIAL_RESTATEMENT

### Financing / refinancing
- REFINANCING_RISK_INCREASE
- FUNDING_COST_INCREASE
- COVENANT_WAIVER_FREQUENCY
- AMEND_AND_EXTEND_ACTIVITY
- NEW_SECURITY_INTEREST_ACTIVITY
- SECURED_BORROWING_INCREASE
- LENDER_SUPPORT_WEAKENING

### External rating / market
- EXTERNAL_RATING_DOWNGRADE
- NEGATIVE_RATING_OUTLOOK
- RATING_WATCH_NEGATIVE
- MARKET_IMPLIED_CREDIT_STRESS
- CREDIT_SPREAD_DIVERGENCE
- MARKET_LIQUIDITY_DETERIORATION

### Governance / reporting
- KEY_MANAGEMENT_RESIGNATION
- MANAGEMENT_TURNOVER_CLUSTER
- MATERIAL_OWNERSHIP_OR_CONTROL_CHANGE
- AUDITOR_CHANGE
- FINANCIAL_REPORTING_RELIABILITY_CONCERN
- INTERNAL_CONTROL_WEAKNESS
- FINANCIAL_REPORTING_DELAY

### Legal / insolvency
- BANKRUPTCY_FILED
- FORMAL_RESTRUCTURING_EVENT
- FORMAL_INSOLVENCY_PROCEEDING
- MATERIAL_DEBT_DEFAULT
- DEBT_ACCELERATION
- LEGAL_CREDITOR_PRESSURE

Jurisdiction-specific legal labels remain attributes/subtypes, not replacements for the global economic-risk semantics.

## 6. Correlated external hypotheses

Single-source external signals should normally be weaker than multi-domain corroboration. Candidate correlated hypotheses include:

### EXT-LIQ-01 Emerging refinancing/liquidity stress

```text
bond spread widening
+ rating outlook negative
+ credit agreement amendment/waiver
+ near-term maturity
+ internal utilization increase
```

### EXT-GOV-01 Governance/reporting stress

```text
CFO/director departure
+ auditor change
+ delayed/restated accounts
+ material weakness/non-reliance disclosure
```

### EXT-CRED-01 Creditor pressure

```text
new UCC/charge activity
+ debt default disclosure
+ covenant waiver
+ legal/insolvency notice
```

### EXT-MKT-01 Market leads fundamentals

```text
issuer spread divergence
+ abnormal bond price decline
+ negative rating action
while latest reported financial ratios remain within policy
```

This is an investigation hypothesis, not permission for the LLM to override deterministic evidence.

## 7. Feature quality controls

External features carry at minimum:

```text
sourceAuthority
entityMatchConfidence
sourceCompleteness
observationFreshness
marketLiquidityQuality
extractionConfidence
corroborationCount
knowledgeTime
```

A perfect NLP extraction with poor entity resolution is still poor evidence.

## 8. Document/NLP extraction pattern

For filings, announcements and credit agreements:

```text
Original document
   ↓
immutable evidence + hash
   ↓
deterministic metadata/parser
   ↓
structured extraction
   ↓
LLM/NLP extraction where needed
   ↓
validation against document spans
   ↓
canonical observation
```

The generated summary is not the evidence. The source document and grounded extracted facts are evidence.

## 9. Phase-1 international connector priority

Recommended order based on authority, automation potential and EWS value:

1. SEC EDGAR submissions/XBRL and material 8-K events;
2. Companies House company/officer/PSC/charge/insolvency streams;
3. provider-neutral external ratings adapter;
4. US/UK bond-market adapter;
5. PACER/US bankruptcy adapter;
6. UK Gazette/regulated announcement adapter;
7. UCC adapter through licensed/authorised aggregation;
8. syndicated-loan/credit-agreement intelligence.

The ordering is implementation priority, not a ranking of regulatory importance.

## 10. Required platform extensions

This catalogue requires the external-intelligence architecture to support:

- `ExternalObservation` as a first-class canonical type;
- legal/company/security identifiers and entity-resolution confidence;
- provider/source rights metadata;
- source checkpoint/cursor management;
- document evidence hashes and grounded extraction spans;
- market security-to-issuer mapping;
- provider-neutral rating normalization;
- correction/retraction semantics;
- source-specific freshness SLOs;
- corroboration policies;
- global semantic signal types with jurisdiction-specific legal attributes.

## 11. Key principle

Do not internationalise EWS by copying jurisdiction-specific alert labels into the ontology.

Internationalisation is achieved by:

```text
different source systems
        ↓
source-specific observations
        ↓
common economic features/signals
        +
explicit jurisdictional legal/regulatory states
```

This allows a US UCC filing and a UK Companies House charge to contribute to the common economic concept `NEW_SECURITY_INTEREST_ACTIVITY` while retaining their different legal meaning and provenance.