# EWS 2.0 — Global Financial Risk Intelligence Architecture Blueprint

**Status:** Draft / Part I — coherence-normalized  
**Primary Domain:** Corporate Counterparty Risk  
**Future Domain:** Retail Risk using shared infrastructure but independently governed models/policies  
**Reference implementations:** India, United States, United Kingdom  
**Target:** Event-driven Financial Risk Intelligence Platform

## 1. Architectural Objective

Build a jurisdiction-neutral, event-driven Financial Risk Intelligence Platform that continuously observes counterparties, preserves evidence and point-in-time knowledge, detects deterioration, correlates evidence, predicts emerging risk, proposes explainable early-warning signals and allows authorized humans to validate material outcomes before they affect official EWS risk state.

The formal EWS is a governed output of a broader intelligence platform. Accounting, prudential, supervisory and legal classifications are separately governed outputs of jurisdiction/institution adapters; they are not aliases of EWS signals or scores.

Core principle:

> AI does not own the risk state. Evidence does.

## 2. Level-0 Logical Architecture

```text
+-------------------------------------------------------------------+
| 9. EXPERIENCE                                                     |
| Portfolio Cockpit | Counterparty 360 | Analyst Workbench          |
| Investigation | Audit | Explainability | Administration           |
+-------------------------------------------------------------------+
| 8. EWS, DECISION & CLASSIFICATION                                 |
| Signal Validation | Risk Assessment | Cases | Workflow            |
| Human Decisions | Official EWS | Jurisdiction Classification      |
+-------------------------------------------------------------------+
| 7. RISK INTELLIGENCE                                              |
| Rules | Statistics | Predictive ML | Anomaly | Graph | NLP        |
| Peer Analysis | Signal Correlation | Governed GenAI Reasoning      |
+-------------------------------------------------------------------+
| 6. FEATURE & ANALYTICAL STATE                                     |
| Features | Ratios | Trends | Indicators | Model Outputs           |
+-------------------------------------------------------------------+
| 5. EVENT PROCESSING                                               |
| Streaming | Enrichment | Windows | Entity/Security Resolution     |
| Corrections | Replay | Point-in-Time Reconstruction               |
+-------------------------------------------------------------------+
| 4. FINANCIAL DATA PLATFORM                                        |
| Operational | Temporal | Documents | Search | Graph | Analytics   |
| Feature State | Evidence Ledger | Object Storage                  |
+-------------------------------------------------------------------+
| 3. CANONICAL INFORMATION MODEL                                    |
| Entity | Facility | Account | Relationship | Evidence             |
| Observation/Event | Feature | Signal | Risk | Classification      |
| Decision                                                           |
+-------------------------------------------------------------------+
| 2. SOURCE ADAPTERS & INGESTION                                    |
| Streams | Incremental APIs | Snapshot+Change | Licensed Feeds     |
| Bulk/File | CDC where appropriate | Documents | Manual Verification|
+-------------------------------------------------------------------+
| 1. DATA SOURCES                                                   |
| Internal Banking | Financial Statements | Registries | Markets    |
| Ratings | Legal/Insolvency | Regulatory | News | Macro | Bureau   |
+-------------------------------------------------------------------+

Cross-cutting:
Security | IAM | Source Rights | Governance | Lineage | Audit
Observability | Model Risk | Data Quality | AI Gateway | Secrets
Jurisdiction Policy | Entity Resolution | Compliance
```

India, US and UK source systems are adapters to this architecture, not hard-coded layers in the global core. Examples include Indian corporate/regulatory/tax sources where legally applicable, US SEC/UCC/court/market sources, and UK Companies House/insolvency/market sources.

## 3. Source Architecture

### 3.1 Internal counterparty and relationship sources

Customer/legal-entity master, group structure, directors/officers, beneficial owners/controllers, KYC, industry classification, geography, relationship management and internal ratings.

### 3.2 Credit and servicing sources

LOS/LMS or equivalent origination/servicing platforms, limits, facilities, commitments, covenants, collateral, guarantees, restructuring, collections, repayment status, DPD and default information.

### 3.3 Transaction and account sources

Account transactions, cash flows, credits/debits, returned payment instructions, payment failures, utilization, overdraft behaviour, transfers and abnormal movement.

### 3.4 Financial statements and documents

Audited and interim financial statements, balance sheet, income statement, cash flow, schedules, notes, auditor reports and management commentary. Existing financial-statement extraction capability evolves into a governed document-to-financial-data gateway.

### 3.5 External structured intelligence

Approved credit bureaus, rating providers, exchanges/market feeds, bond/reference data, corporate/beneficial-owner registries, regulatory sources, tax sources where lawfully available, courts, insolvency systems, security-interest registries and licensed financing datasets.

### 3.6 External unstructured intelligence

News, issuer announcements, regulatory disclosures, annual reports, press releases, company websites and other approved intelligence sources. NLP/LLM extraction never removes the requirement to retain original evidence, source spans and extraction confidence.

### 3.7 Macroeconomic sources

GDP, interest rates, inflation, FX, commodities, sector indices, property prices and relevant trade/economic statistics.

### 3.8 Derived internal intelligence

Historical signals, analyst decisions, investigation findings, defaults, collections outcomes and credit decisions become governed feedback/evaluation sources. Analyst acceptance is not automatically ground truth for model training.

## 4. Acquisition and Ingestion Architecture

Acquisition mode is a property of the source/connector rather than a global assumption. Supported logical modes include:

```text
PUSH_STREAM
POLL_INCREMENTAL
BULK_SNAPSHOT_PLUS_STREAM
BULK_SCHEDULED
LICENSED_FEED
ON_DEMAND
MANUAL_VERIFICATION
CDC where operationally appropriate
```

All paths converge on governed evidence plus canonical observations/domain events.

```text
Source
  ↓
Source Adapter / Connector
  ↓
Rights + Authentication + Checkpointing
  ↓
Raw Evidence / Document Reference
  ↓
Validation + Normalization + Entity/Security Resolution
  ↓
Canonical Observation / Domain Event
  ↓
Event Backbone
```

Large documents and binary artefacts remain in governed object/evidence storage; events carry references and metadata rather than binary payloads.

External sources carry rights metadata including permitted use, retention, redistribution, model/LLM processing and cross-border constraints where applicable.

## 5. Canonical Information Model

The canonical model is vendor- and jurisdiction-neutral and precedes physical database design. It is a graph/DAG of governed records rather than a single object hierarchy.

```text
SOURCE
  ↓
EVIDENCE ──────────────────────────────┐
  ↓                                   │
OBSERVATION / DOMAIN EVENT             │
  ↓                                   │
FEATURE ← entity / facility / account / relationship context
  ↓
RULE / STATISTICS / ML / GRAPH / NLP
  ↓
SIGNAL
  ↓
CORRELATION / RISK ASSESSMENT
  ├────────→ HUMAN DECISION → OFFICIAL EWS
  │
  └────────→ JURISDICTION / POLICY ADAPTER → CLASSIFICATION STATE
```

Counterparties, legal entities, people, groups, facilities, accounts, collateral, securities and relationships are independently addressable entities connected to this analytical chain.

## 6. Semantic Boundaries

These concepts are intentionally separate.

**Evidence** — immutable/addressable support for a fact or extraction, including source and provenance.

**Observation / Domain Event** — factual statement that something occurred or was observed, e.g. `payment.instruction.returned`.

**Feature** — governed point-in-time derived value, e.g. `returned_payment_count_30d`.

**Signal** — economic-risk interpretation, e.g. `REPEATED_PAYMENT_RETURN`.

**Risk Assessment** — aggregated analytical assessment of one or more governed risk dimensions.

**Classification State** — namespaced institution/accounting/prudential/supervisory/legal state produced under a separately versioned policy.

**Decision** — authorized human/policy action such as accept, reject, modify, escalate, investigate, mitigate or close.

Canonical naming convention:

```text
Domain event:          payment.instruction.returned
Feature:               returned_payment_count_30d
Signal semantic:       REPEATED_PAYMENT_RETURN
Risk dimension:        LIQUIDITY
Classification:        UK_IFRS9_STAGE / US_ACCRUAL_STATUS / IN_SMA_NPA / ...
```

## 7. Canonical Event Envelope

Part I defines the conceptual requirements only. The executable Avro contract in Part III is authoritative for serialization.

The envelope preserves at minimum:

```text
eventId
eventType
eventVersion
producer
entity + optional resolution lineage
aggregateSequence where applicable
partitionKey
eventTime
effectiveTime
knowledgeTime
ingestedAt
jurisdiction / market where applicable
source + authority + evidence + rights reference
correlationId / causationId / traceId
data classification
execution/replay metadata
```

Temporal meanings are distinct:

- `eventTime` — when the source/domain occurrence happened or was recorded as occurring;
- `effectiveTime` — when the fact became economically/legal/domain effective where different;
- `knowledgeTime` — when the institution/platform could legitimately know/use the information;
- `ingestedAt` — when the platform ingested the event.

This separation supports late data, correction, replay, backtesting and the audit question: **what did the institution know at that point in time?**

## 8. Event Domains

Initial domain families include:

```text
counterparty.*
facility.*
account.*
transaction.*
payment.*
financial.*
covenant.*
collateral.*
rating.*
market.*
news.*
regulatory.*
legal.*
management.*
ownership.*
relationship.*
security_interest.*
insolvency.*
sector.*
macro.*
feature.*
signal.*
risk.*
classification.*
case.*
decision.*
model.*
```

Detailed event names and schemas are governed in Part III.

## 9. Financial Data Platform

Different workloads require different storage characteristics. The logical platform contains operational state, temporal/history state, object/document storage, search indexes, relationship/graph state, online/offline analytical features, and evidence/provenance.

A pragmatic Phase-1 baseline is:

```text
Kafka
PostgreSQL
Elasticsearch
Redis
S3-compatible object storage
```

Dedicated graph, analytical or feature-store products are introduced only where validated workloads justify them. Kafka is operational event infrastructure, not the 7–10 year evidence archive.

## 10. Temporal Financial Model

Financial observations preserve economic and knowledge time. A financial fact/feature can carry:

```text
metric / feature definition
value + unit/currency
financial period
accounting basis
effective date
reported/published date
knowledge time
source + evidence
revision/supersession
extraction/transformation version
quality/confidence
```

This enables both economic-state reconstruction and information-state reconstruction.

## 11. Stream Intelligence

The stream-processing layer performs validation, deduplication, normalization, enrichment, entity/security resolution, aggregation, event-time windows, trend calculation, feature generation, correction handling and pattern detection.

Example:

```text
payment.instruction.returned
payment.instruction.returned
payment.instruction.returned
        ↓ rolling 30-day feature
returned_payment_count_30d
        ↓ governed signal policy
REPEATED_PAYMENT_RETURN
```

Kafka is the event backbone. Kafka Streams is the Phase-1 default for stateful event processing under ADR-004. Flink remains a future option where validated CEP/event-time/state workloads justify the additional platform complexity.

## 12. Feature Architecture

AI and risk engines consume governed features rather than querying arbitrary production systems. Feature definitions are versioned contracts with semantic scope:

```text
GLOBAL_CORE
GLOBAL_PRODUCT_SPECIFIC
JURISDICTION_EXTENSION
INSTITUTION_POLICY_SPECIFIC
```

Feature examples include financial ratios/trends, repayment conduct, utilization/headroom, transaction behaviour, refinancing/funding, ratings, market-implied credit, legal/insolvency, governance, relationship/contagion and peer-relative measures.

Every feature preserves definition version, entity grain, time semantics, source/evidence lineage, quality, source-rights lineage where relevant, and revision/supersession state.

## 13. Risk Intelligence Architecture

Risk intelligence uses independent governed analytical engines:

```text
FEATURE / OBSERVATION STREAM
          ↓
+---------+---------+---------+---------+---------+---------+
| RULES   | STATS   | ML      | ANOMALY | GRAPH  | NLP     |
+---------+---------+---------+---------+---------+---------+
          ↓
SIGNAL CANDIDATES
          ↓
SIGNAL POLICY + QUALITY GATE
          ↓
SIGNAL INSTANCES
          ↓
CORRELATION / RISK ASSESSMENT
          ↓
GENAI CONTEXT / EXPLANATION where approved
          ↓
HUMAN VALIDATION / DECISION
```

GenAI can correlate and explain governed evidence/features/model outputs but cannot invent authoritative facts, silently modify feature values or own the official risk state.

## 14. Signal Model

A signal is a first-class governed entity containing signal identity/type, semantic scope, entity, lifecycle status, severity, confidence, materiality, temporal fields, policy/version, feature/evidence references, source-rights/entity-resolution lineage where applicable, analytical outputs, proposed risk impact and human disposition history.

Severity, confidence, materiality and risk impact are independent concepts.

## 15. Signal Lifecycle and Dispositions

Canonical lifecycle state describes the operational life of a signal. Human/system dispositions describe why an action was taken; they are not regulatory classifications.

```text
DETECTED
   ↓
PROPOSED
   ├────────→ REJECTED
   ↓
ACCEPTED
   ↓
ACTIVE
   ├────────→ MITIGATED
   ├────────→ CLOSED
   ├────────→ EXPIRED
   └────────→ SUPERSEDED
```

A previously closed/resolved signal may be reopened through an explicit event where policy allows.

Disposition/action metadata can include concepts such as:

```text
FALSE_POSITIVE
DUPLICATE
INSUFFICIENT_EVIDENCE
ESCALATE
INVESTIGATE
REQUEST_INFORMATION
```

`ESCALATE` is an action/disposition, not a competing economic-risk state. `FALSE_POSITIVE`, `DUPLICATE` and `INSUFFICIENT_EVIDENCE` are not accounting/prudential classifications.

Immutable `signal.disposition.recorded` events preserve human decision history.

## 16. Human-in-the-Loop

Analysts drill from conclusion to original evidence:

```text
Explanation / hypothesis
      ↓
Signal
      ↓
Rule / model / correlation output
      ↓
Features
      ↓
Observations / domain events
      ↓
Evidence
      ↓
Original transaction / document / external source
```

Material overrides require role authorization, reason, timestamp, before/after values and review/expiry where appropriate.

## 17. Risk Assessment Architecture

Risk assessment is multidimensional. The canonical dimension vocabulary is governed in Part II and includes financial/operating, liquidity, leverage/solvency, cash-flow/debt-service, repayment conduct, covenant/documentation, collateral/security, refinancing/funding, management/governance, fraud/integrity, external/market/reputation, legal/regulatory, relationship/contagion and sector/macro dimensions.

The lifecycle is:

```text
Raw Analytical Assessment
        ↓
Policy Adjustment
        ↓
Proposed Risk Assessment
        ↓
Human Validation / Authorized Adjustment
        ↓
Approved Risk Assessment
        ↓
Official EWS State
```

Raw, proposed and approved states are persisted separately and never overwritten by later judgement.

## 18. Classification Architecture

Classification is a separate namespaced state model. Reference namespaces include:

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

The detailed multi-jurisdiction classification/adaptation model is normative in `02f-multi-jurisdiction-risk-model.md`. No one-to-one equivalence between an EWS signal/score and an accounting/prudential classification is assumed.

## 19. Model Lifecycle and Use Governance

Models move through governed data/features, training, validation, backtesting, registry, approval, serving, monitoring and retirement. Approval is use-case, jurisdiction/market, population and calibration specific; a model validated for one population is not assumed portable to another.

MLflow is an implementation candidate rather than an architectural dependency.

## 20. AI Gateway

Applications do not directly call arbitrary foundation-model APIs.

```text
Application
    ↓
AI Gateway
  authentication / authorization
  data classification / redaction
  source-rights and cross-border policy
  prompt registry
  model routing
  token/rate/cost controls
  guardrails
  audit/tracing
    ↓
Private Model and/or Approved Cloud AI
```

The gateway is the control plane for hybrid AI consumption and provider portability.

## 21. Hybrid Deployment Direction

Core financial records, evidence, official risk state, feature state and analyst decisions remain within the institution's approved trust boundary. Approved cloud capabilities can be consumed selectively through controlled interfaces when data classification, source rights, jurisdiction policy and cross-border rules permit.

Hybrid is therefore a deployment capability, not a requirement to move sensitive data outside institutional control.

## 22. Audit and Evidence Invariant

The platform must reconstruct:

```text
Raw Source / Document
   ↓
Evidence
   ↓
Observation / Event
   ↓
Transformation + Feature Definition
   ↓
Feature Snapshot
   ↓
Rule / Model / Correlation Version
   ↓
Signal
   ↓
Risk Assessment and/or Classification Adapter
   ↓
AI Explanation where used
   ↓
Human Decision
   ↓
Approved EWS / Classification State
```

The reconstruction must preserve knowledge time, revisions/corrections, model/policy versions and the evidence available at the historical decision point.

## 23. Logical Service Boundaries

Candidate logical domains include ingestion/connectors, counterparty/facility/relationship core, stream processing, financial-statement intelligence, feature computation, rules/signals/risk, anomaly/prediction/graph/NLP/correlation, AI gateway/reasoning, workflow/case/notification, evidence/audit/model governance, jurisdiction adapters and experience APIs.

These are logical boundaries, not a mandate for one microservice/deployment unit per item.

## 24. Evolution from Existing EWS

The target architecture is defined independently of current implementation constraints, then reached incrementally. Existing Spring Boot services, financial-statement extraction, case/workflow capability and institutional data sources can be reused where they fit the target contracts.

Migration should establish the event/evidence spine first, then governed features/signals, then risk/correlation/AI capabilities. Existing EWS remains operational during transition until official risk-state ownership is deliberately migrated.

## 25. Phase-1 Architecture Spine

```text
Internal + approved external sources
        ↓
Adapters / Outbox / APIs / Files / Feeds
        ↓
Evidence + Canonical Events
        ↓
Kafka
        ↓
Kafka Streams feature processing
        ↓
Governed features
        ↓
Rules / anomaly / initial ML
        ↓
Signal policy + correlation
        ↓
Proposed risk assessment + explanation
        ↓
Human validation
        ↓
Official EWS + case/workflow/dashboard
```

Structured external intelligence can be introduced in parallel where portfolio value, source authority, source rights and entity-resolution quality justify it.

## 26. Strategic Direction

The 3–6 month objective is a pragmatic EWS 2.0 event/evidence/feature/signal spine with human-controlled official risk state. The 2–3 year objective is a broader Financial Intelligence Platform with richer external intelligence, graph analytics, predictive models, portfolio intelligence and governed AI reasoning.

The architecture remains stable by keeping global economic-risk semantics separate from institution policy, jurisdiction/accounting/prudential adapters, market/source adapters and technology implementation choices.
