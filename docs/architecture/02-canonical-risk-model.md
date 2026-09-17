# EWS 2.0 — Canonical Risk Information Model

**Status:** Draft / Part II  
**Primary domain:** Corporate counterparty risk  
**Design style:** Evidence-first, bitemporal, event-driven, human-governed

## 1. Purpose

The Canonical Risk Information Model (CRIM) defines stable financial-risk semantics independent of databases, event platforms, model vendors and user interfaces. It is the common language used by ingestion, streaming, feature engineering, rules, ML, GenAI, workflow, audit and reporting.

A key design rule is that **ontology is stable while detection policy is versioned**. `REPEATED_PAYMENT_RETURN` is a signal type; a condition such as `count >= 3 in 30 days` is a policy/rule version and may vary by institution, segment or time.

## 2. Risk domains

EWS 2.0 separates four top-level risk intents so that signals are not semantically conflated:

1. **Credit Deterioration** — weakening ability/capacity to meet obligations.
2. **Fraud / Integrity** — suspected misrepresentation, diversion, siphoning, fabricated evidence or other integrity concerns.
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

OBSERVATION
  +-- EVENT
  +-- FINANCIAL_METRIC
  +-- EXTERNAL_FACT

EVIDENCE
  +-- SOURCE_RECORD
  +-- DOCUMENT
  +-- TRANSACTION
  +-- EXTERNAL_REFERENCE

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

GOVERNANCE
  +-- DECISION
  +-- CASE
  +-- ACTION
  +-- FEEDBACK
```

## 4. Counterparty aggregate

The corporate counterparty is the principal monitoring aggregate. It references, rather than physically owns, facilities, accounts, related parties, collateral, covenants and financial periods.

Minimum identity semantics include canonical counterparty ID, legal identifiers, names/aliases, entity type, industry/sector, geography, internal segment, group ID, relationship status and source-system identities.

Identity resolution confidence and provenance must be retained. External intelligence must not be attached to a counterparty solely by fuzzy name matching without an auditable entity-resolution decision.

## 5. Relationship model

Relationships are first-class, temporal entities:

```text
(sourceParty)-[relationshipType]->(targetParty)
```

Examples include `DIRECTOR_OF`, `PROMOTER_OF`, `OWNS`, `SUBSIDIARY_OF`, `GUARANTEES`, `SUPPLIES_TO`, `CUSTOMER_OF`, `AUDITED_BY`, `LENDER_TO` and `GROUP_MEMBER_OF`.

Each relationship records effective period, observation period, source/evidence, confidence and resolution method. This allows historical graph reconstruction and prevents current corporate structure from being incorrectly projected into the past.

## 6. Bitemporal knowledge model

Every material observation supports two time axes:

- **Valid/effective time:** when the fact was economically or legally true.
- **Knowledge/system time:** when the institution learned, stored or revised the fact.

Additional event timestamps such as source event time and ingestion time are retained where required.

This enables two distinct queries:

- `What was the borrower's state as of 31-Mar-2026?`
- `What did the institution know about that state on 15-Jun-2026?`

Backtesting and audit must use the second question when reconstructing historical decisions to prevent look-ahead leakage.

## 7. Evidence model

Evidence is immutable/addressable and has a stable `evidenceId`. Evidence metadata includes source system/provider, source record/document identifier, source timestamp, ingestion timestamp, content hash where applicable, classification, quality status, lineage and retention policy.

Derived evidence never replaces original evidence. Corrections create a new version and supersession relationship.

## 8. Observation model

An observation records a fact without asserting its risk meaning. Examples:

- a payment was returned;
- working-capital utilization became 94%;
- current ratio was reported as 0.91;
- a director resigned;
- a rating outlook changed to negative;
- an insolvency proceeding was filed against a related entity.

Observations reference evidence and carry confidence/quality where extraction or entity resolution is probabilistic.

## 9. Feature model

A feature is a governed, versioned derivation from observations or other features. Required metadata:

```text
featureId / name
version
entity scope
definition
calculation or transformation
input lineage
event/effective time
knowledge time
window
value + unit
quality
owner
materiality
```

Feature groups initially include liquidity, leverage, profitability, cash flow, working capital, repayment/conduct, utilization, covenant, collateral, external intelligence, management/governance, relationship/group, sector and macroeconomic features.

Online and offline representations must have equivalent semantics even when physically stored differently.

## 10. Signal model

A **Signal Type** defines semantic meaning. A **Signal Policy** defines how it is detected. A **Signal Instance** records a particular detection.

Signal instance fields include:

```text
signalId
signalType
entityId / scope
riskIntent[]
riskDimension[]
status
severity
confidence
detectedAt
effectiveAt
policyId + policyVersion
featureSnapshotIds[]
evidenceIds[]
rule/model outputs[]
correlationId
proposedRiskImpact
explanationRef
caseId
humanDecisionHistory[]
```

Severity and confidence are independent. A severe event may have low confidence; a highly certain observation may have low materiality.

## 11. Signal policy model

Thresholds, windows, peer definitions and suppression logic are configuration/policy, not hard-coded signal semantics.

A policy contains:

```text
policyId
signalType
version
applicable segment
inputs
condition/window
minimum evidence quality
severity mapping
confidence mapping
suppression/deduplication
cooldown/decay
risk impact mapping
human-validation requirement
effectiveFrom/effectiveTo
approval metadata
```

Policy changes are auditable and historical replays must use the policy version effective at the relevant knowledge time unless performing an explicitly labelled counterfactual backtest.

## 12. Signal lifecycle

```text
DETECTED -> PROPOSED -> ACCEPTED -> ACTIVE -> MITIGATED/CLOSED
                  \-> REJECTED
```

Classifications include `FALSE_POSITIVE`, `DUPLICATE`, `SUPERSEDED`, `EXPIRED` and `INSUFFICIENT_EVIDENCE`.

Human decisions never mutate the original machine output. The proposal and decision are separate records.

## 13. Risk assessment model

Risk is multidimensional. Initial dimensions are:

- Liquidity
- Leverage / Solvency
- Profitability / Operating Performance
- Cash Flow / Debt Service
- Repayment / Conduct
- Covenant / Documentation
- Collateral / Security
- Management / Governance
- Fraud / Integrity
- External / Reputation
- Legal / Regulatory
- Relationship / Group Contagion
- Sector / Macro

A risk assessment records raw analytical score, policy adjustments, proposed score, human adjustment where permitted, approved score, confidence, assessment horizon, model/policy versions and supporting signals.

GenAI-generated text is never the authoritative score source.

## 14. Correlation model

Correlation groups independent signals into a risk hypothesis without destroying their individual provenance.

Example:

```text
REPEATED_PAYMENT_RETURN
+ WC_UTILIZATION_SPIKE
+ RECEIVABLE_DAYS_DETERIORATION
+ RATING_OUTLOOK_NEGATIVE
       |
       v
HYPOTHESIS: EMERGING_LIQUIDITY_STRESS
```

A correlation records constituent signal IDs, temporal relationship, correlation method/version, confidence, hypothesis, evidence coverage and analyst disposition.

## 15. AI output model

AI output is stored as a governed artefact referencing its inputs:

- purpose/use case;
- model/provider/version;
- prompt/template version;
- retrieved evidence IDs;
- structured model output;
- generated narrative;
- confidence/uncertainty where meaningful;
- policy/guardrail results;
- trace ID;
- human disposition.

The platform must be able to render an explanation without treating the generated prose as source evidence.

## 16. Audit invariant

For any approved material risk-state change the platform must be able to traverse:

```text
Approved Risk State
 <- Human Decision
 <- Proposed Risk Assessment
 <- Signal(s)
 <- Rule / Model / Correlation Output
 <- Feature Snapshot(s)
 <- Observation(s)
 <- Evidence
 <- Original Source
```

Each node is versioned and time-addressable.

## 17. Retail extension

Retail uses the same meta-model — Party, Account, Observation, Evidence, Feature, Signal, Risk Assessment, Decision — but separate domain features, policies and models. Retail examples include bureau changes, DPD, utilization, income/cash-flow stability, EMI behaviour, enquiry velocity, loan stacking and fraud/device signals.

This allows shared infrastructure without falsely treating corporate and retail credit behaviour as one analytical problem.

## 18. Design consequences

1. Canonical semantics are vendor-neutral.
2. Thresholds are policy and can change without renaming signal types.
3. Every material signal is evidence-addressable.
4. Bitemporal state prevents look-ahead leakage in audit/backtesting.
5. Fraud suspicion and credit deterioration remain distinguishable.
6. AI explanations are derived artefacts, not evidence.
7. Human validation is append-only governance, not mutation of model output.
8. Graph relationships are temporal and provenance-bearing.
9. Feature definitions are versioned contracts.
10. The model can support both event-driven live analysis and historical replay.
