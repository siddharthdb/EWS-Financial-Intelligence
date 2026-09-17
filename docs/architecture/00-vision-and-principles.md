# EWS 2.0 — Vision and Architecture Principles

**Status:** Draft  
**Scope:** Corporate Counterparty Risk with future Retail Risk extension  
**Architecture Style:** Event-driven, evidence-first, AI-assisted, human-governed

## 1. Vision

EWS 2.0 evolves a conventional rule-driven Early Warning System into a **Financial Risk Intelligence Platform**.

The platform continuously observes counterparties and their environment, detects deterioration, predicts emerging risk, correlates independent evidence, explains the resulting risk hypothesis, and proposes governed early-warning signals to financial-risk analysts.

The formal EWS remains a controlled output of this platform rather than being directly controlled by AI.

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

The initial platform targets corporate counterparties and their associated facilities, accounts, transactions, financial statements, collateral, covenants, management, promoters, directors, guarantors, group entities, external ratings, market information, regulatory/legal events and external intelligence.

### 3.2 Retail — Extension Domain

Retail risk will use common platform infrastructure for ingestion, event processing, evidence, model governance, signal lifecycle, workflow, audit and observability.

Retail will maintain independent domain intelligence including behavioural features, bureau data, repayment patterns, income/cash-flow behaviour, utilization, enquiries, loan stacking, device/fraud signals and retail-specific risk models.

Shared platform does not imply shared models.

## 4. AI Responsibilities

The platform supports four complementary AI capabilities:

1. **Prediction** — estimate future deterioration or distress probability.
2. **Anomaly Detection** — identify behaviour inconsistent with counterparty history, peer groups or expected patterns.
3. **Correlation and Reasoning** — identify relationships between otherwise independent risk observations.
4. **Analyst Intelligence** — explain risk movement, summarize evidence and support investigation.

AI outputs are proposals, predictions and explanations. They are not authoritative financial decisions.

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

## 6. Architecture Principles

### AP-01 — Evidence Before Explanation

AI does not own the risk state. Evidence does.

Every material risk assertion must be traceable to source evidence, derived features, rules/models and transformations. A generated explanation cannot substitute for evidence.

### AP-02 — Event-Driven by Default

Material changes in financial state are represented as immutable business events and distributed through the event backbone.

Batch and document ingestion remain valid where source characteristics require them, but they produce canonical events after processing.

### AP-03 — Separate Event, Evidence, Signal, Risk and Decision

These concepts must remain distinct:

- **Event:** something happened.
- **Evidence:** proof or source information supporting the observation.
- **Signal:** a risk interpretation of one or more observations.
- **Risk:** an aggregated risk assessment.
- **Decision:** an authorized human or policy action concerning that assessment.

### AP-04 — Temporal State Is First-Class

Financial intelligence must preserve when an event occurred, when it was observed, when it was ingested, when information became effective and when it was revised.

The platform must be capable of reconstructing what was known at a historical point in time.

### AP-05 — Multiple Analytical Engines, Not One AI

Risk intelligence combines deterministic rules, statistical methods, predictive ML, anomaly detection, time-series analysis, graph analytics, NLP, peer analysis and GenAI reasoning.

No single model is expected to determine the complete counterparty risk state.

### AP-06 — GenAI Sits Above Evidence

GenAI primarily correlates, summarizes, reasons over and explains evidence produced by governed systems. It does not replace deterministic calculations, financial ratios, source records or independently governed predictive models.

### AP-07 — Human Validation for Formal EWS

AI-generated signals are proposed signals until reviewed under configured policy. Formal EWS signals and official risk-state changes require governed validation where mandated by the operating model.

### AP-08 — Complete Provenance

The platform records lineage from source through event, transformation, feature, model, prediction, signal, AI explanation, analyst decision and risk-score change.

### AP-09 — Hybrid by Design

Core financial records, official risk state, evidence and human decisions remain within the controlled enterprise environment. Approved external/cloud AI services may be used selectively through an AI Gateway enforcing data policy, model routing, redaction, tracing and audit.

### AP-10 — Domain Models over Vendor Models

Canonical financial concepts, event contracts, signal definitions and feature semantics must remain independent of Kafka, Flink, databases, cloud providers and foundation-model vendors.

Technology products implement the architecture; they do not define it.

### AP-11 — Replay and Reproducibility

Where technically feasible, risk computation must be reproducible from retained events, versioned features, model versions, rule versions and configuration.

### AP-12 — Idempotency over Blanket Exactly-Once Claims

Delivery and processing guarantees are specified per pipeline. Idempotency keys, deduplication, transactional boundaries, replay behaviour and external-store consistency are explicitly designed rather than relying on a platform-wide 'exactly once' claim.

### AP-13 — Explainability Is More Than Generated Text

An analyst explanation must expose evidence, feature contribution, model/rule identity and version, temporal context, confidence and decision history. LLM-generated prose is an interface over this information, not the audit record itself.

### AP-14 — Feedback Is Training Data

Accepted, rejected, modified, duplicated, superseded and false-positive signals form governed feedback datasets for evaluation and future model improvement.

### AP-15 — Start Simple, Preserve the North Star

Phase 1 should avoid unnecessary platform proliferation. Kafka, PostgreSQL, Elasticsearch, Redis and object storage can support substantial initial capability. Dedicated stream processing, graph, analytical and feature-store technologies are introduced when justified by workload and domain requirements.

## 7. Architectural Non-Goals

The platform will not:

- delegate formal credit decisions to an LLM;
- derive official risk scores from untraceable generated text;
- use a vector database as the system of record for financial state;
- force all analytical problems into GenAI;
- treat corporate and retail risk as the same model;
- create microservices solely to maximize service count;
- send sensitive financial data directly from applications to arbitrary foundation-model APIs.

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

This forms the foundation for the detailed architecture blueprint and canonical risk model.
