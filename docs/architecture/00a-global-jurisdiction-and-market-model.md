# EWS 2.0 — Global Jurisdiction and Market Model

**Status:** Architecture baseline spanning Parts I–III  
**Initial reference jurisdictions:** India, United States, United Kingdom  
**Design intent:** jurisdiction-neutral core with explicit market/regulatory adapters

## 1. Decision

EWS 2.0 is a global Financial Risk Intelligence Platform, not an India EWS generalized later.

The platform separates:

```text
GLOBAL ECONOMIC RISK SEMANTICS
              +
INSTITUTION CREDIT POLICY
              +
JURISDICTION / ACCOUNTING / PRUDENTIAL ADAPTERS
              +
MARKET-SPECIFIC SOURCE ADAPTERS
```

Corporate distress fundamentals such as liquidity pressure, leverage deterioration, repayment weakness, covenant stress, governance disruption, refinancing pressure and market-implied credit stress remain common semantic concepts. Legal classifications, accounting impairment, prudential default and supervisory classifications remain explicit jurisdictional outputs.

## 2. Reference jurisdictions

### India
Examples of jurisdiction-specific integrations/outputs include RBI-regulated classification/policy, SMA/NPA where applicable, MCA/company information, SEBI/exchange disclosures, insolvency/NCLT sources and institution-specific EWS/fraud policy.

### United States
Examples include SEC/EDGAR, federal bankruptcy/PACER, state UCC or licensed lien intelligence, FINRA/TRACE-derived market intelligence, external ratings, internal credit grading/watchlist and applicable US supervisory/accounting adapters such as supervisory asset classification, accrual/nonaccrual and CECL.

### United Kingdom
Examples include Companies House, insolvency/company charges/officers/PSC, regulated issuer disclosures, UK bond-market data, external ratings and applicable UK prudential/accounting adapters such as prudential default, SICR/IFRS 9 and IRB where relevant.

These are reference implementations, not hard-coded assumptions in the canonical model.

## 3. Global versus jurisdictional state

The following are global analytical concepts:

```text
Observation
Evidence
Feature
Signal
Signal Correlation
Analytical Risk Assessment
Human Decision
Watchlist / Investigation workflow
```

The following are namespaced governed classifications rather than one overloaded `riskStatus`:

```text
ANALYTICAL_EWS
INTERNAL_CREDIT_GRADE
WATCHLIST
WORKOUT

IN_SMA_NPA

US_SUPERVISORY_CLASSIFICATION
US_ACCRUAL_STATUS
US_CECL

UK_SICR
UK_IFRS9_STAGE
UK_PRUDENTIAL_DEFAULT
UK_IRB
```

Additional jurisdictions extend this namespace without changing core economic signal semantics.

## 4. Global identity model

A canonical counterparty can resolve multiple identifiers:

```text
internal customer IDs
legal entity identifiers
LEI
country/company-registry identifiers
SEC CIK
ticker / exchange identifiers
Companies House company number
FCA/PRA identifiers where applicable
ISIN / CUSIP / security identifiers
court/case-party identifiers
licensed-provider entity IDs
```

Identifiers have issuer/authority, jurisdiction, effective period, confidence and provenance. Fuzzy names are candidates for resolution, not authoritative identity.

## 5. Market-source model

External acquisition mode is explicit:

```text
PUSH_STREAM
POLL_INCREMENTAL
BULK_SNAPSHOT_PLUS_STREAM
BULK_SCHEDULED
LICENSED_FEED
ON_DEMAND
MANUAL_VERIFICATION
```

The source registry also records rights and operating constraints:

```text
sourceId
provider
jurisdiction
authorityTier
licenceClass
permittedUses
redistributionAllowed
rawRetentionAllowed
modelTrainingAllowed
llmProcessingAllowed
crossBorderTransferAllowed
rateLimit
credentialClass
retentionPolicy
freshnessSlo
```

Public accessibility does not imply unrestricted model-training, LLM-processing or redistribution rights.

## 6. Common external observations

Market-specific facts normalize into common observations where economic semantics match:

```text
RATING_CHANGED
OUTLOOK_CHANGED
DEBT_DEFAULT_DISCLOSED
DEBT_ACCELERATION_DISCLOSED
CREDIT_FACILITY_AMENDED
COVENANT_WAIVER_DISCLOSED
SECURITY_INTEREST_CREATED
INSOLVENCY_PROCEEDING_STARTED
KEY_OFFICER_DEPARTED
AUDITOR_CHANGED
FINANCIAL_STATEMENT_FILED
BOND_PRICE_OBSERVED
BOND_YIELD_OBSERVED
LEGAL_OR_REGULATORY_ACTION_PUBLISHED
```

The source/legal subtype remains attached. A US UCC filing and UK Companies House charge can both contribute to `SECURITY_INTEREST_CREATED` while retaining distinct legal provenance.

## 7. Common economic signals

Examples:

```text
REFINANCING_RISK_INCREASE
FUNDING_COST_INCREASE
NEW_SECURITY_INTEREST_ACTIVITY
COVENANT_WAIVER_FREQUENCY
MARKET_IMPLIED_CREDIT_STRESS
CREDIT_SPREAD_DIVERGENCE
EXTERNAL_RATING_DOWNGRADE
KEY_MANAGEMENT_RESIGNATION
FINANCIAL_REPORTING_RELIABILITY_CONCERN
FORMAL_RESTRUCTURING_EVENT
LEGAL_CREDITOR_PRESSURE
```

A jurisdiction adapter may consume the same evidence/features but produce a different legal/accounting/prudential classification.

## 8. Model portability

Model code may be reusable across markets; model calibration is not assumed portable.

Every model deployment records:

```text
jurisdictionalUse[]
marketPopulation
segment/product
permittedUses[]
prohibitedUses[]
trainingPopulation
validationPopulation
calibrationVersion
accountingUseApproval
prudentialUseApproval
EwsUseApproval
```

Approval for EWS prioritisation is not approval for CECL, IFRS 9, IRB, regulatory capital or formal default classification.

## 9. Technical consequence

The architecture becomes:

```text
Internal Sources             External Market/Jurisdiction Sources
      |                                   |
      |                             Source Adapters
      |                                   |
      +----------------+------------------+
                       v
              Canonical Observations
                       |
                       v
                 Global Features
                       |
                       v
                 Global Signals
                       |
             +---------+----------+
             |                    |
             v                    v
      Analytical EWS       Jurisdiction Adapters
             |                    |
             v            accounting / prudential /
      Human Governance      legal classifications
```

## 10. Guardrails

- No regulator-specific threshold belongs in the canonical signal ontology merely because it exists in one market.
- No jurisdictional accounting stage is inferred directly from a generic EWS score.
- No market-specific source identifier becomes the global counterparty ID.
- No provider-specific rating scale becomes the canonical rating semantics.
- No public-source connector assumes unrestricted data rights.
- No global predictive model is deployed to a new jurisdiction without population validation and calibration.

## 11. Architecture interpretation

All Part I, II and III documents should be read through this model. India, US and UK examples demonstrate adapters and source implementations; they do not define the global core.