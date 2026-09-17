# EWS 2.0 — Vision and Architecture Principles

**Status:** Draft  
**Scope:** Global Corporate Counterparty Risk with future Retail Risk extension  
**Reference jurisdictions:** India, United States, United Kingdom  
**Architecture Style:** Event-driven, evidence-first, AI-assisted, human-governed

## 1. Vision

EWS 2.0 evolves a conventional rule-driven Early Warning System into a **global Financial Risk Intelligence Platform**.

The platform continuously observes counterparties and their environment, detects deterioration, predicts emerging risk, correlates independent evidence, explains the resulting risk hypothesis, and proposes governed early-warning signals to financial-risk analysts.

The formal EWS remains a controlled output of this platform rather than being directly controlled by AI.

The platform is not built around one country's regulatory vocabulary. Economic risk semantics are global; institution policy, accounting treatment, prudential/default classification and legal/regulatory consequences are explicit adapters.

```text
Global evidence / observations / features / signals
                       |
            +----------+----------+
            |                     |
     Analytical EWS       Jurisdiction adapters
            |                     |
      Human governance     India / US / UK / ...
```

See `00a-global-jurisdiction-and-market-model.md`.

## 2. Target Operating Loop

```text
OBSERVE
   |
UNDERSTAND
   |
DETECT
   |
CORRELATE
   |
PREDICT
   |
PROPOSE
   |
HUMAN VALIDATE
   |
SCORE
   |
ACT
   |
LEARN
```

## 3. Scope

### 3.1 Corporate Counterparty — Primary Domain

The initial platform targets corporate counterparties and their associated facilities, accounts, transactions, financial statements, collateral, covenants, management, directors/officers, beneficial owners/controllers, guarantors, group entities, external ratings, market information, regulatory/legal events and external intelligence.

The canonical core is jurisdiction-neutral. Market-specific source adapters support sources such as company registries, securities/regulatory filings, insolvency/court systems, security-interest/lien registries, rating providers and bond/market feeds.

### 3.2 Initial Jurisdiction Reference Implementations

- **India:** internal banking sources plus RBI/MCA/SEBI/exchange/insolvency/legal and other approved sources.
- **United States:** internal banking sources plus SEC/EDGAR, federal bankruptcy/court sources, UCC/lien intelligence, ratings and bond/market intelligence.
- **United Kingdom:** internal banking sources plus Companies House, insolvency/charges/officer/PSC data, regulated issuer disclosures, ratings and bond/market intelligence.

These sources are examples of adapters. They do not define the canonical ontology.

### 3.3 Retail — Extension Domain

Retail risk will use common platform infrastructure for ingestion, event processing, evidence, model governance, signal lifecycle, workflow, audit and observability.

Retail will maintain independent domain intelligence including behavioural features, bureau data, repayment patterns, income/cash-flow behaviour, utilization, enquiries, loan stacking, device/fraud signals and retail-specific risk models.

Shared platform does not imply shared models.

## 4. AI Responsibilities

The platform supports four complementary AI capabilities:

1. **Prediction** — estimate future deterioration or distress probability.
2. **Anomaly Detection** — identify behaviour inconsistent with counterparty history, peer groups or expected patterns.
3. **Correlation and Reasoning** — identify relationships between otherwise independent risk observations.
4. **Analyst Intelligence** — explain risk movement, summarize evidence and support investigation.

AI outputs are proposals, predictions and explanations. They are not authoritative financial decisions, accounting classifications, prudential default determinations or legal findings.

## 5. Human-Governed EWS

The primary control flow is:

```text
Data
  -> Analytical / AI Engines
  -> Proposed Signal
  -> Evidence Package
  -> Human Validation
  -> Approved EWS Signal
  -> Risk Score / Case / Action
```

Authorized analysts can accept, reject, modify or escalate proposed signals. Their decisions and reasons are retained as first-class auditable records.

Credit-review challenge, watchlist, workout and jurisdiction-specific approval workflows can extend this base governance model.

## 6. Architecture Principles

### AP-01 — Evidence Before Explanation
AI does not own the risk state. Evidence does.

Every material risk assertion must be traceable to source evidence, derived features, rules/models and transformations. A generated explanation cannot substitute for evidence.

### AP-02 — Event-Driven by Default
Material changes in financial state are represented as immutable business events and distributed through the event backbone.

Batch, polling, licensed feeds and document ingestion remain valid where source characteristics require them, but they produce canonical observations/events after processing.

### AP-03 — Separate Event, Evidence, Signal, Risk, Classification and Decision
These concepts must remain distinct:

- **Event/Observation:** something happened or was observed.
- **Evidence:** proof or source information supporting the observation.
- **Signal:** a risk interpretation of one or more observations.
- **Risk:** an aggregated analytical assessment.
- **Classification:** an institution/accounting/prudential/legal state under a governed namespace.
- **Decision:** an authorized human or policy action concerning that assessment/classification.

### AP-04 — Temporal State Is First-Class
Financial intelligence must preserve when an event occurred, when information became effective, when the institution was entitled to know/use it, when it was ingested and when it was revised.

The platform must reconstruct both economic history and `as-known-at-time` history.

### AP-05 — Multiple Analytical Engines, Not One AI
Risk intelligence combines deterministic rules, statistical methods, predictive ML, anomaly detection, time-series analysis, graph analytics, NLP, peer analysis and GenAI reasoning.

No single model determines the complete counterparty risk state.

### AP-06 — GenAI Sits Above Evidence
GenAI primarily correlates, summarizes, reasons over and explains evidence produced by governed systems. It does not replace deterministic calculations, financial ratios, source records or independently governed predictive models.

### AP-07 — Human Validation for Formal EWS
AI-generated signals are proposed signals until reviewed under configured policy. Formal EWS signals and official risk-state changes require governed validation where mandated by the operating model.

### AP-08 — Complete Provenance
The platform records lineage from source through observation/event, transformation, feature, model, prediction, signal, AI explanation, analyst decision and risk/classification change.

### AP-09 — Hybrid by Design
Core financial records, official risk state, evidence and human decisions remain within the controlled enterprise environment. Approved external/cloud AI services may be used selectively through an AI Gateway enforcing data policy, model routing, redaction, tracing and audit.

### AP-10 — Domain Models over Vendor and Jurisdiction Models
Canonical financial concepts, event contracts, signal definitions and feature semantics remain independent of Kafka, databases, cloud/model vendors **and any single jurisdiction's regulatory vocabulary**.

Technology products and jurisdiction adapters implement the architecture; they do not define the global economic-risk ontology.

### AP-11 — Replay and Reproducibility
Where technically feasible, risk computation must be reproducible from retained events, versioned features, model versions, rule versions and configuration.

### AP-12 — Idempotency over Blanket Exactly-Once Claims
Delivery and processing guarantees are specified per pipeline. Idempotency keys, deduplication, transactional boundaries, replay behaviour and external-store consistency are explicitly designed rather than relying on a platform-wide 'exactly once' claim.

### AP-13 — Explainability Is More Than Generated Text
An analyst explanation must expose evidence, feature contribution, model/rule identity and version, temporal context, confidence and decision history. LLM-generated prose is an interface over this information, not the audit record itself.

### AP-14 — Feedback Is Training Data
Accepted, rejected, modified, duplicated, superseded and false-positive signals form governed feedback datasets for evaluation and future model improvement.

### AP-15 — Start Simple, Preserve the North Star
Phase 1 should avoid unnecessary platform proliferation. Introduce dedicated stream processing, graph, analytical and feature-store technologies when justified by workload and domain requirements.

### AP-16 — Global Semantics, Local Policy
Common economic signals remain globally meaningful while thresholds, accounting treatment, supervisory classifications and legal consequences are versioned by institution, jurisdiction, segment and effective date.

`DPD_WORSENING` can be global. SMA/NPA, US supervisory classification, CECL, UK SICR/IFRS 9 and UK prudential default are separate governed outputs.

### AP-17 — Model Approval Is Use-Case and Population Specific
A model validated for EWS prioritisation in one market is not automatically valid in another market and is not automatically approved for accounting impairment, prudential default, IRB or regulatory-capital use.

### AP-18 — External Data Rights Are Architecture Metadata
Source licensing, permitted use, retention, redistribution, LLM processing, model training and cross-border constraints are enforceable metadata in the source/evidence control plane.

## 7. Architectural Non-Goals

The platform will not:

- delegate formal credit decisions to an LLM;
- derive official risk scores from untraceable generated text;
- equate an EWS score with an accounting or prudential classification;
- hard-code one jurisdiction's regulatory thresholds into global signal semantics;
- assume a model calibrated in one market is portable without validation;
- use a vector database as the system of record for financial state;
- force all analytical problems into GenAI;
- treat corporate and retail risk as the same model;
- create microservices solely to maximize service count;
- send sensitive financial data directly from applications to arbitrary foundation-model APIs;
- treat publicly accessible external data as automatically unrestricted for storage, redistribution or AI/model use.

## 8. Strategic Outcome

The intended outcome is a platform capable of answering not merely **'What is the counterparty's current risk score?'**, but:

- What changed?
- When did it change?
- What evidence supports it?
- Is the change anomalous?
- How does it compare with the counterparty's history and peers?
- Which related entities contribute risk?
- What deterioration is predicted?
- Why was a signal proposed?
- Which model/rule produced it?
- What did the analyst decide?
- What did the institution know at the time of that decision?
- Which jurisdiction/institution policy applies?
- What accounting, prudential or legal classifications exist independently of the analytical EWS state?
- Is the model approved and calibrated for this market and use case?

This forms the foundation for the detailed architecture blueprint and canonical risk model.