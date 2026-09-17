# EWS 2.0 — Canonical Risk Information Model

**Status:** Draft / Part II  
**Primary domain:** Global corporate counterparty risk  
**Reference jurisdictions:** India, United States, United Kingdom  
**Design style:** Evidence-first, bitemporal, event-driven, human-governed, jurisdiction-neutral core

## 1. Purpose

The Canonical Risk Information Model (CRIM) defines stable financial-risk semantics independent of databases, event platforms, model vendors, user interfaces and jurisdiction-specific regulatory vocabulary. It is the common language used by ingestion, streaming, feature engineering, rules, ML, GenAI, workflow, audit and reporting.

A key design rule is that **ontology is stable while detection and jurisdiction policy are versioned**. `REPEATED_PAYMENT_RETURN` is a global signal type; a condition such as `count >= N in P30D` is a policy/rule version and may vary by institution, segment, jurisdiction or time.

Regulatory/accounting states are explicit classifications, not overloaded risk signals.

## 2. Risk domains

1. **Credit Deterioration** — weakening ability/capacity to meet obligations.
2. **Fraud / Integrity** — suspected misrepresentation, diversion, fabricated evidence or other integrity concerns.
3. **Operational Conduct** — borrower conduct, covenant/monitoring non-compliance and irregular account behaviour.
4. **External / Contagion** — market, legal, regulatory, sector, macroeconomic and related-entity risk transmission.

A signal may contribute to more than one risk intent but each contribution is explicit and independently weighted.

## 3. Core ontology

```text
PARTY
  +-- COUNTERPARTY
  +-- PERSON
  +-- LEGAL_ENTITY

COUNTERPARTY
  +-- FACILITY
  +-- ACCOUNT
  +-- COLLATERAL
  +-- COVENANT
  +-- FINANCIAL_PROFILE
  +-- RELATIONSHIP
  +-- IDENTIFIER

OBSERVATION
  +-- EVENT
  +-- FINANCIAL_METRIC
  +-- EXTERNAL_FACT
  +-- MARKET_OBSERVATION
  +-- LEGAL_OBSERVATION

EVIDENCE
  +-- SOURCE_RECORD
  +-- DOCUMENT
  +-- TRANSACTION
  +-- EXTERNAL_REFERENCE
  +-- MARKET_RECORD

DERIVATION
  +-- FEATURE
  +-- RULE_EVALUATION
  +-- MODEL_PREDICTION
  +-- ANOMALY
  +-- GRAPH_METRIC

SIGNAL
  +-- SIGNAL_INSTANCE
  +-- SIGNAL_POLICY
  +-- SIGNAL_CORRELATION

RISK
  +-- RISK_DIMENSION
  +-- RISK_ASSESSMENT
  +-- RISK_SCORE

CLASSIFICATION
  +-- CLASSIFICATION_NAMESPACE
  +-- CLASSIFICATION_STATE
  +-- CLASSIFICATION_POLICY

GOVERNANCE
  +-- DECISION
  +-- CASE
  +-- ACTION
  +-- FEEDBACK
```

## 4. Counterparty and identity aggregate

The corporate counterparty is the principal monitoring aggregate. It references facilities, accounts, related parties, collateral, covenants, financial periods, securities and external legal/market identities.

Identity semantics include canonical counterparty ID, legal identifiers, names/aliases, entity type, industry/sector, geography, internal segment, group ID, relationship status and source-system identities.

Global identifier examples include LEI, national/company-registry IDs, SEC CIK, Companies House number, ticker/exchange identifiers, ISIN/CUSIP and licensed-provider IDs. Identifiers retain authority, jurisdiction, effective period and provenance.

Identity resolution confidence is retained. External intelligence must not be attached solely by fuzzy name matching without an auditable resolution decision.

## 5. Relationship model

Relationships are first-class, temporal entities:

```text
(sourceParty)-[relationshipType]->(targetParty)
```

Examples include `DIRECTOR_OF`, `OFFICER_OF`, `BENEFICIAL_OWNER_OF`, `CONTROLS`, `PROMOTER_OF`, `OWNS`, `SUBSIDIARY_OF`, `GUARANTEES`, `SUPPLIES_TO`, `CUSTOMER_OF`, `AUDITED_BY`, `LENDER_TO` and `GROUP_MEMBER_OF`.

Each relationship records effective period, observation/knowledge period, source/evidence, jurisdiction where relevant, confidence and resolution method.

## 6. Bitemporal knowledge model

Every material observation supports:

- **Valid/effective time:** when the fact was economically or legally true.
- **Knowledge/system time:** when the institution learned, stored or revised the fact.

Additional event/source publication and ingestion timestamps are retained where required.

This enables both `state as of T` and `what the institution knew as of T`, preventing look-ahead leakage.

## 7. Evidence and source-rights model

Evidence is immutable/addressable and has a stable `evidenceId`. Metadata includes source/provider, jurisdiction, source record/document identifier, source timestamp, ingestion timestamp, content hash, classification, quality, lineage, retention policy and source-rights reference.

External source rights include permitted use, raw retention, redistribution, model-training permission, LLM-processing permission and cross-border restrictions where applicable.

Derived evidence never replaces original evidence. Corrections create new versions/supersession relationships.

## 8. Observation model

An observation records a fact without asserting its risk meaning. Examples:

- a payment was returned;
- working-capital utilization became 94%;
- current ratio was reported as 0.91;
- a CFO/director resigned;
- a rating outlook changed to negative;
- a US UCC financing statement was filed;
- a UK company charge was created;
- a Chapter 11 case was filed;
- a UK insolvency procedure began;
- a bond spread widened;
- a credit agreement was amended.

Market-specific observations can normalize to common economic facts while retaining source/legal subtype and provenance.

## 9. Feature model

A feature is a governed, versioned derivation from observations or other features. Required metadata includes definition/version, entity scope, input lineage, effective/knowledge time, window, value/unit, quality, owner and materiality.

Feature groups include liquidity, leverage, profitability, cash flow, working capital, repayment/conduct, utilization, covenant, collateral, external ratings, market-implied credit, refinancing, management/governance, relationship/group, legal/insolvency, sector and macroeconomic features.

External features additionally carry source authority, entity-match confidence, extraction confidence, corroboration and market-liquidity quality where relevant.

## 10. Signal model

A **Signal Type** defines global economic-risk meaning. A **Signal Policy** defines how it is detected. A **Signal Instance** records a particular detection.

Signal instance fields include signal/entity/scope, risk intents/dimensions, status, severity, confidence, materiality, detection/effective time, policy/version, feature snapshots, evidence, model/rule outputs, correlation, proposed impact, explanation and human decision history.

Examples of global signals include `DPD_WORSENING`, `REFINANCING_RISK_INCREASE`, `MARKET_IMPLIED_CREDIT_STRESS`, `EXTERNAL_RATING_DOWNGRADE`, `NEW_SECURITY_INTEREST_ACTIVITY`, `KEY_MANAGEMENT_RESIGNATION` and `LEGAL_CREDITOR_PRESSURE`.

A US UCC filing and UK registered charge can contribute to the same global signal while retaining different legal semantics.

## 11. Signal policy model

Thresholds, windows, peer definitions, market normalization and suppression logic are configuration/policy, not hard-coded signal semantics.

Policy applicability can include institution, jurisdiction, legal entity, portfolio, segment, product, currency/market and effective period.

Historical replay uses the policy effective at the relevant knowledge time unless explicitly counterfactual.

## 12. Signal lifecycle

```text
DETECTED -> PROPOSED -> ACCEPTED -> ACTIVE -> MITIGATED/CLOSED
                  \-> REJECTED
```

Classifications include `FALSE_POSITIVE`, `DUPLICATE`, `SUPERSEDED`, `EXPIRED` and `INSUFFICIENT_EVIDENCE`.

Human decisions never mutate original machine output.

## 13. Risk assessment model

Risk is multidimensional. Initial dimensions include Liquidity, Leverage/Solvency, Profitability/Operating Performance, Cash Flow/Debt Service, Repayment/Conduct, Covenant/Documentation, Collateral/Security, Refinancing/Funding, Management/Governance, Fraud/Integrity, External/Market/Reputation, Legal/Regulatory, Relationship/Group Contagion and Sector/Macro.

A risk assessment records raw analytical score, policy adjustments, proposed score, human adjustment where permitted, approved score, confidence, horizon, model/policy versions and supporting signals.

GenAI-generated text is never the authoritative score source.

## 14. Classification model — global core, local namespace

Accounting, prudential, legal and supervisory states are separate from analytical EWS risk.

Canonical namespaces initially include:

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

A classification state records namespace, value, jurisdiction, governing policy/rule/version, effective/knowledge time, evidence/features, decision/approval and supersession history.

No direct mapping such as `EWS HIGH => IFRS9 Stage 2`, `EWS HIGH => CECL state` or `DPD => NPA` exists in the canonical model. Dedicated governed adapters perform applicable classifications.

## 15. Correlation model

Correlation groups independent signals into a risk hypothesis without destroying provenance.

```text
REPEATED_PAYMENT_RETURN
+ WC_UTILIZATION_SPIKE
+ RECEIVABLE_DAYS_DERIORATION
+ RATING_OUTLOOK_NEGATIVE
       |
       v
EMERGING_LIQUIDITY_STRESS
```

International examples include refinancing stress from market-spread widening + rating deterioration + covenant amendments + near-term maturity, or governance/reporting stress from CFO departure + auditor change + restatement/material-control weakness.

## 16. AI output model

AI output is a governed artefact referencing purpose, model/provider/version, prompt/template version, retrieved evidence, structured output, narrative, uncertainty, policy/guardrail results, trace ID and human disposition.

Generated prose is not source evidence.

## 17. Model-use governance

Model deployment records market population, jurisdictional use, segment/product, permitted/prohibited uses, training/validation populations, calibration version and use-case approvals.

A model approved for EWS prioritisation is not thereby approved for CECL, IFRS 9, IRB, regulatory capital or formal default classification. A model calibrated for one country/portfolio is not assumed portable to another.

## 18. Audit invariant

For any approved material risk or classification change:

```text
Approved Risk / Classification State
 <- Human / Policy Decision
 <- Proposed Assessment
 <- Signal(s) / Classification Rule
 <- Rule / Model / Correlation Output
 <- Feature Snapshot(s)
 <- Observation(s)
 <- Evidence
 <- Original Source
```

Each node is versioned and time-addressable.

## 19. Retail extension

Retail uses the same meta-model but separate domain features, policies and models. Jurisdiction-specific retail regulation/classification remains adapter-driven in the same manner.

## 20. Design consequences

1. Canonical semantics are vendor- and jurisdiction-neutral.
2. Thresholds are policy and can change without renaming signal types.
3. Regulatory/accounting classifications cannot pollute global signal semantics.
4. Every material signal/classification is evidence-addressable.
5. Bitemporal state prevents look-ahead leakage.
6. Fraud suspicion and credit deterioration remain distinguishable.
7. AI explanations are derived artefacts, not evidence.
8. Human validation is append-only governance.
9. Graph relationships and external identities are temporal/provenance-bearing.
10. Feature definitions are versioned contracts.
11. External data rights and entity-match confidence are first-class metadata.
12. Models require market/population/use-case validation.
13. The model supports live event processing, historical replay and jurisdiction-specific downstream classification without forking the core ontology.