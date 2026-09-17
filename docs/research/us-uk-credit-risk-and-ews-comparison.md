# US vs UK Credit Risk Modelling and Early-Warning Systems

**Research date:** 2026-09-17  
**Scope:** Corporate/commercial and retail credit monitoring, EWS/watchlist, impairment, regulatory risk classification, model governance, stress testing and implications for EWS 2.0.

## Executive conclusion

US and UK banks analyse many of the same underlying borrower fundamentals: repayment performance, leverage, liquidity, debt service, cash flow, collateral, covenants, management quality, external ratings, sector/macro conditions and behavioural data. The material differences are not primarily in the raw economics of borrower distress; they are in the **regulatory/accounting state models, supervisory vocabulary, model-governance frameworks and downstream consequences of risk deterioration**.

Therefore EWS 2.0 should not implement `India`, `US` and `UK` as separate end-to-end platforms. It should implement a jurisdiction-neutral evidence/feature/signal core with jurisdiction-specific **classification, impairment, capital, policy and workflow adapters**.

The strongest architectural separation is:

```text
Observed borrower facts
       ↓
Canonical features
       ↓
Early-warning signals / hypotheses
       ↓
Jurisdiction-neutral analytical risk assessment
       |
       +--> India regulatory/classification adapter
       +--> US criticized/classified + CECL adapter
       +--> UK default/IRB + IFRS 9 SICR/ECL adapter
       +--> institution-specific watchlist/workout workflow
```

## 1. United States — supervisory structure and credit-risk semantics

The US banking framework is multi-agency. Relevant federal supervisory expectations may involve the Federal Reserve, OCC and FDIC depending on charter/structure, with interagency guidance used for important cross-industry practices.

### Credit review and risk rating

US supervision places substantial weight on independent, ongoing credit-risk review and internal risk-rating integrity. The 2020 Interagency Guidance on Credit Risk Review Systems describes independent ongoing review and communication to management and boards as important parts of safe and sound lending.

US supervisory classification vocabulary is operationally important:

```text
PASS
SPECIAL MENTION
SUBSTANDARD
DOUBTFUL
LOSS
```

`Special Mention` identifies potential weaknesses deserving management attention. `Substandard`, `Doubtful` and `Loss` are classified assets. These classifications must not be represented as generic EWS severity bands; they are regulatory/supervisory credit classifications with defined semantics.

The 2025 Shared National Credit programme continues to report `non-pass` exposure using Special Mention and classified categories, demonstrating that this vocabulary remains active in large syndicated-credit supervision.

### CECL

US GAAP ASC 326/CECL requires expected credit losses to be estimated over the contractual term for assets within scope using historical experience, current conditions and reasonable/supportable forecasts. Unlike IFRS 9 impairment, CECL does **not** use the same Stage 1 → Stage 2 SICR → Stage 3 architecture as its core allowance model.

EWS implication: a deterioration signal may change CECL assumptions/segmentation/forecast expectations or trigger individual analysis, but the platform must not equate `EWS HIGH` with an IFRS-style accounting stage.

### Model risk management

In April 2026 the Federal Reserve/OCC/FDIC issued revised interagency Model Risk Management guidance, superseding SR 11-7. The revised guidance emphasizes a risk-based approach tailored to the banking organization's model-risk profile, size and complexity. EWS predictive models, ML models, scoring models and material AI-supported models therefore need inventory, governance, validation and monitoring proportional to their use and materiality.

### Stress testing

The Federal Reserve's supervisory stress testing remains a major forward-looking capital tool for large banks. Credit-risk models cover corporate, CRE, mortgages, cards, auto and other retail portfolios. Portfolio stress models and borrower-level EWS should remain separate but interoperable: macro scenarios should be usable as contextual features/scenario overlays without turning the operational EWS into a capital stress-test engine.

### Capital direction as of September 2026

US agencies proposed a revised capital framework in March 2026. For Category I/II banking organizations the proposal would move toward a single expanded risk-based approach and remove internal models from the credit-risk regulatory-capital framework. As of this research date this is a **proposal**, not a basis for hard-coding final capital semantics into EWS.

## 2. United Kingdom — prudential, accounting and credit-risk semantics

UK banking prudential supervision is led by the PRA/Bank of England, with conduct requirements under the FCA where applicable. The post-Brexit UK framework is increasingly expressed through the PRA Rulebook and supervisory statements rather than treating current EU/EBA material as automatically applicable.

### IFRS 9 and significant increase in credit risk

For UK banks reporting under IFRS, IFRS 9 expected-credit-loss architecture makes **Significant Increase in Credit Risk (SICR)** particularly important.

Conceptually:

```text
Stage 1 -> 12-month ECL
Stage 2 -> lifetime ECL after SICR
Stage 3 -> credit-impaired
```

This creates a strong linkage between forward-looking borrower monitoring and accounting impairment. EWS indicators can be valuable inputs to SICR governance, but `EWS signal = Stage 2` would be an unsafe architectural shortcut. SICR is a governed accounting determination considering change in default risk since initial recognition and other relevant information.

UK supervisory/stress-test practice explicitly models IFRS 9 SICR and ECL behaviour under stress scenarios.

### Default and IRB

The PRA maintains explicit supervisory expectations for definition of default, including past-due criteria, unlikeliness-to-pay indicators, return to non-defaulted status, retail application and documentation. Basel 3.1 final UK rules take effect from 1 January 2027, including replacement/transition of parts of the existing IRB supervisory framework.

EWS therefore needs separate concepts for:

```text
EWS deterioration
SICR / IFRS 9 stage
prudential default
IRB PD/LGD/EAD state
watchlist/workout state
```

These concepts interact but are not synonyms.

### Model risk management

PRA SS1/23, updated effective April 2026, establishes five broad MRM principles: model identification/classification, governance, development/implementation/use, independent validation and model-risk mitigants. It also explicitly addresses AI/ML model risk within the general model-risk framework.

The UK architecture should therefore retain named accountable ownership, model inventory/classification, independent validation, limitations/mitigants and governance evidence as first-class metadata.

### Forward-looking supervision and data

PRA material emphasizes forward-looking supervision, business-model analysis, peer/outlier analysis, timely granular data and stress testing. The PRA's 2026 Future Banking Data discussion paper describes credit-risk evaluations used to benchmark asset quality, identify outliers and provide early-warning signals using loan-book, mortgage, arrears, forbearance and management information.

### EBA material: use carefully

EBA Guidelines remain highly useful design references, particularly their detailed treatment of loan monitoring and early-warning indicators. However, post-Brexit applicability must be checked guideline-by-guideline against PRA policy. The PRA has explicitly stated that some EBA credit-risk guidelines continue to be expected while others do not apply in the UK. EWS documentation must therefore distinguish:

```text
UK legal/regulatory requirement
PRA supervisory expectation
retained/onshored EU-derived requirement
EBA design reference only
```

Do not label an EBA EWI list automatically as a current binding UK requirement.

## 3. Business-fundamental comparison

The borrower economics are substantially common.

### Common corporate risk drivers

Both markets require monitoring of:

- repayment/default behaviour;
- leverage and debt-service capacity;
- liquidity and working-capital stress;
- revenue/profitability/cash-flow deterioration;
- covenant compliance/headroom;
- collateral coverage and valuation;
- management/governance changes;
- rating changes;
- sector and macroeconomic stress;
- concentration/dependency risk;
- refinancing risk;
- legal/regulatory events;
- external intelligence and material adverse developments.

The canonical feature catalogue can therefore remain largely jurisdiction-neutral.

### Market-specific emphasis

US commercial banking commonly embeds internal loan grades and supervisory criticized/classified mappings deeply in portfolio management and credit review. Large syndicated exposures also have the Shared National Credit supervisory context.

UK risk monitoring has a particularly visible connection to IFRS 9 SICR/Stage 2, PRA default/IRB rules, ICAAP/SREP and PRA stress-testing practices. For retail, FCA conduct/Consumer Duty considerations also mean automated risk interventions must be assessed not only for credit risk but for customer outcomes where relevant.

## 4. EWS semantic comparison

| Concept | United States | United Kingdom | EWS 2.0 treatment |
|---|---|---|---|
| Early warning/watchlist | Institution-specific monitoring plus strong credit-review/risk-rating discipline | Institution monitoring, forward-looking prudential supervision, IFRS 9/SICR relevance | Core Signal + institution policy |
| Regulatory credit classification | Pass/Special Mention/Substandard/Doubtful/Loss supervisory vocabulary | Default/non-default and PRA/IRB prudential constructs; internal watchlist states | Jurisdiction adapter |
| Accounting impairment | CECL / ASC 326 lifetime expected-loss framework | IFRS 9 12-month/lifetime ECL with SICR staging | Separate impairment adapter |
| Default | Bank/regulatory definitions depending purpose; nonaccrual/classification concepts also relevant | Explicit PRA definition-of-default framework incl. UTP/past due | Dedicated default adapter |
| Regulatory capital | US capital rules; 2026 reform proposal still evolving | PRA Basel 3.1 implementation from 2027 | Capital adapter, not EWS ontology |
| Model governance | 2026 interagency revised MRM guidance | PRA SS1/23 | Shared Model Governance domain + jurisdiction profiles |
| Stress testing | Federal Reserve supervisory stress tests / SCB for covered firms | BoE/PRA stress testing, ICAAP/SREP | Scenario service feeding contextual features |

## 5. Technical architecture implications

### 5.1 Introduce `JurisdictionProfile`

Do not fork the event model. Add governed context:

```text
jurisdiction = US | UK | IN | ...
regulatoryRegime
accountingFramework = US_GAAP | IFRS
legalEntity
bookingEntity
portfolio
product
policyEffectiveDate
```

A multinational exposure may involve borrower domicile, booking jurisdiction and reporting entity jurisdiction that differ. Therefore a single `country` field is insufficient.

### 5.2 Separate analytical and regulatory state machines

Core:

```text
Evidence -> Observation -> Feature -> Signal -> Analytical Risk Assessment
```

Adapters:

```text
US:
Analytical state + authoritative facts
   -> Internal Risk Grade
   -> Criticized/Classified mapping where applicable
   -> CECL inputs/segmentation
   -> Watchlist/workout

UK:
Analytical state + authoritative facts
   -> Internal Risk Grade
   -> SICR assessment inputs
   -> IFRS 9 Stage/ECL workflow
   -> PRA default/IRB inputs
   -> Watchlist/workout
```

No adapter is permitted to overwrite the analytical evidence chain.

### 5.3 Add classification namespaces

Avoid one field named `riskStatus`.

Use typed classifications such as:

```text
ANALYTICAL_EWS
INTERNAL_CREDIT_GRADE
US_SUPERVISORY_CLASSIFICATION
US_CECL
UK_IFRS9_STAGE
UK_SICR
UK_PRUDENTIAL_DEFAULT
IRB_DEFAULT
WATCHLIST
WORKOUT
```

Each record carries policy/version, effective time, knowledge time, authority, evidence/input references and approval provenance.

### 5.4 Policy effective dating is mandatory

Regulatory regimes change. The US 2026 capital proposal and UK Basel 3.1 transition demonstrate why capital/default logic must be effective-dated configuration/adapter logic rather than embedded in canonical event schemas.

### 5.5 Model registry needs `purpose` and `jurisdictionalUse`

A model may be valid for EWS prioritisation but not approved for regulatory capital, accounting impairment or automated customer decisions.

Minimum metadata:

```text
modelPurpose
permittedUses
prohibitedUses
jurisdictions
legalEntities
portfolios
materiality/model-risk tier
validationStatus
validationDate
owner
independentValidator
limitations
monitoringThresholds
trainingDataPeriod
featureDefinitionVersions
```

### 5.6 Explainability requirements differ by use, not just country

A deterministic watchlist rule, an ML deterioration model, a CECL model, an IRB PD model and an LLM analyst narrative have different validation/explainability needs. The architecture should classify by `modelPurpose + materiality + decisionImpact + jurisdiction`, not simply `US model` versus `UK model`.

### 5.7 Scenario architecture

Create a reusable scenario context:

```text
Scenario
  macro variables
  market variables
  sector shocks
  effective horizon
  source/version
  probability/weight where applicable
```

Operational EWS may consume current/base/adverse scenario features. CECL/IFRS9/stress-test engines can consume the same governed macro data while applying their own methodology.

## 6. Signal taxonomy implications

Most Phase-1 signal semantics survive across markets:

```text
DPD_EMERGED
DPD_WORSENING
REPEATED_PAYMENT_RETURN
WORKING_CAPITAL_UTILIZATION_HIGH
LIMIT_EXCESS_RECURRING
DSCR_DERIORATION
CURRENT_RATIO_DERIORATION
OPERATING_CASH_FLOW_NEGATIVE
RECEIVABLE_DAYS_DERIORATION
COVENANT_BREACH
EXTERNAL_RATING_DOWNGRADE
COLLATERAL_COVER_EROSION
KEY_MANAGEMENT_RESIGNATION
GROUP_ENTITY_DISTRESS
```

But signal policies differ by product, market, accounting/regulatory purpose and institution. Therefore thresholds must never be globally encoded in the signal type.

Additional international signal families worth adding:

- refinancing/maturity-wall stress;
- market-implied credit spread/CDS deterioration where available;
- forbearance/modification activity;
- nonaccrual/accrual-status changes (US context);
- SICR indicator changes (UK accounting context, as derived governed state rather than raw risk signal);
- unlikeliness-to-pay indicators;
- criticized/classified grade migration;
- covenant waiver/amend-and-extend frequency;
- sponsor support deterioration for leveraged/private-equity-backed borrowers;
- CRE occupancy/NOI/debt-yield/refinancing metrics;
- consumer vulnerability/forbearance/customer-outcome indicators where applicable to UK retail workflows.

## 7. Data architecture implications

The common platform should support:

### Corporate/commercial

- borrower and group financial statements;
- facility/obligation/payment data;
- covenant and collateral data;
- internal ratings;
- external ratings/market data;
- legal/entity/ownership data;
- sector/macro data;
- syndicated-credit/agent-bank data where relevant;
- amendments, waivers and restructuring/forbearance events.

### Retail

- delinquency roll rates;
- utilization;
- bureau score/attributes where legally/permissibly available;
- payment behaviour;
- modifications/forbearance;
- affordability/income indicators where applicable;
- collateral/LTV for secured lending;
- product/customer outcome data where required.

Jurisdiction-specific privacy, permissible-purpose, retention and consumer-decision controls must be layered above this data model and researched separately before production implementation.

## 8. Governance implication for GenAI

Neither US nor UK model-risk frameworks support treating an opaque LLM narrative as the authoritative credit state merely because it cites sources.

For EWS 2.0:

```text
structured facts/features
       ↓
validated rule/statistical/ML outputs
       ↓
correlated hypothesis
       ↓
LLM explanation/analyst assistance
       ↓
human/governed decision
```

The LLM artefact records model/provider/version, prompt/template, retrieved evidence, output, guardrail result and human disposition. It does not become evidence of the borrower's financial condition by itself.

## 9. Architecture decision

EWS 2.0 should become **multi-regime by design**, but not multi-codebase by jurisdiction.

Stable core:

```text
Evidence
Observation
Feature
Signal
Signal Episode
Analytical Risk Assessment
Decision
Case
Model/Policy Governance
Temporal/Lineage semantics
```

Pluggable regimes:

```text
Regulatory Classification Adapter
Accounting Impairment Adapter
Default Adapter
Capital/IRB Adapter
Watchlist/Workout Policy
Consumer/Conduct Controls
```

This allows India, US and UK deployments to share the event and intelligence platform while preserving legally and economically distinct downstream meanings.

## 10. Primary sources consulted

- Federal Reserve/OCC/FDIC, Revised Guidance on Model Risk Management (2026), superseding SR 11-7.
- OCC, Lending and Loan Portfolio Risk Management, Comptroller's Handbook (2026).
- OCC, Rating Credit Risk, Comptroller's Handbook.
- Federal banking agencies, Interagency Guidance on Credit Risk Review Systems (2020).
- Federal Reserve/OCC/FDIC, Shared National Credit programme/reporting.
- FASB ASC 326 / Credit Losses transition material.
- Federal Reserve supervisory stress-test documentation, including 2026 credit-risk models.
- US agencies' March 2026 regulatory-capital proposals (proposal status as of research date).
- PRA SS1/23 Model Risk Management Principles for Banks, current April 2026 version.
- PRA SS3/24 Credit Risk Definition of Default and January 2026 future version effective 1 January 2027.
- PRA PS1/26 Basel 3.1 final rules.
- Bank of England 2025 stress-test guidance (credit risk and IFRS 9).
- Bank of England/PRA Future Banking Data DP1/26.
- PRA statements on interpretation/applicability of EBA credit-risk guidelines after EU withdrawal.
- EBA Guidelines on Loan Origination and Monitoring, used as a detailed design reference with UK applicability caveat.
