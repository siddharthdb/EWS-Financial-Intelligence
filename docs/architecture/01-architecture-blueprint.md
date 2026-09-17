# EWS 2.0 — Architecture Blueprint

**Status:** Draft / Part I  
**Primary Domain:** Corporate Counterparty Risk  
**Future Domain:** Retail Risk  
**Target:** Event-driven Financial Risk Intelligence Platform

## 1. Architectural Objective

Build an event-driven Financial Risk Intelligence Platform that continuously observes counterparties, detects deterioration, correlates evidence, predicts emerging risk, proposes explainable early-warning signals and allows authorized analysts to validate those signals before they affect the official EWS risk state.

The formal EWS is therefore a governed output of a broader intelligence platform.

## 2. Level-0 Logical Architecture

```text
+---------------------------------------------------------------+
| 9. EXPERIENCE                                                 |
| Portfolio Cockpit | Counterparty 360 | Analyst Workbench      |
| Investigation | Audit | Explainability | Administration       |
+---------------------------------------------------------------+
| 8. EWS & DECISION                                             |
| Signal Validation | Risk Scoring | Cases | Workflow           |
| Notifications | Escalation | Human Decisions                  |
+---------------------------------------------------------------+
| 7. AI INTELLIGENCE                                            |
| GenAI | Predictive ML | Anomaly | NLP | Graph | Time Series   |
| Peer Analysis | Signal Correlation | AI Agents                |
+---------------------------------------------------------------+
| 6. RISK & FEATURE                                             |
| Features | Rules | Ratios | Trends | Indicators | Scores      |
+---------------------------------------------------------------+
| 5. EVENT PROCESSING                                           |
| Streaming | Enrichment | Windows | CEP | Entity Resolution    |
+---------------------------------------------------------------+
| 4. FINANCIAL DATA PLATFORM                                    |
| Operational | Temporal | Documents | Search | Graph | Lake    |
| Feature Store | Evidence Store                                |
+---------------------------------------------------------------+
| 3. CANONICAL INFORMATION MODEL                                |
| Counterparty | Facility | Financial | Event | Evidence        |
| Relationship | Signal | Risk | Decision                       |
+---------------------------------------------------------------+
| 2. INGESTION                                                  |
| APIs | CDC | Events | Files | Documents | External Feeds      |
+---------------------------------------------------------------+
| 1. DATA SOURCES                                               |
| CBS | LOS | LMS | Financials | Markets | News | Bureau        |
| MCA | SEBI | RBI | Legal | Ratings | GST | External Intel     |
+---------------------------------------------------------------+

Cross-cutting:
Security | IAM | Governance | Lineage | Audit | Observability
Model Risk | Data Quality | AI Gateway | Secrets | Compliance
```

## 3. Source Architecture

### 3.1 Counterparty Sources

Customer master, group structure, directors, promoters, beneficial owners, KYC, industry classification, geography, relationship management and internal ratings.

### 3.2 Credit Sources

LOS, LMS, limits, facilities, sanctions, covenants, collateral, guarantees, restructuring, collections, DPD and default information.

### 3.3 Transaction Sources

Account transactions, cash flows, credits/debits, cheque returns, payment failures, utilization, overdraft behaviour, fund transfers and abnormal movement.

### 3.4 Financial Statements

Audited and interim financial statements, balance sheet, P&L, cash flow, schedules, notes, auditor reports and management commentary.

Existing financial-statement extraction capability can evolve into the document-to-financial-data gateway.

### 3.5 External Structured Intelligence

Credit bureau, rating agencies, stock exchanges, market prices, bond information, FX, commodities, corporate registries, regulatory sources, tax/GST where legally and operationally available, courts and insolvency sources.

### 3.6 External Unstructured Intelligence

News, company announcements, regulatory disclosures, annual reports, press releases, company websites and other approved intelligence sources.

### 3.7 Macroeconomic Sources

GDP, interest rates, inflation, FX, commodity prices, sector indices, property prices and relevant trade/economic statistics.

### 3.8 Derived Internal Intelligence

Historical EWS signals, analyst comments, investigation findings, accepted/rejected signals, historical defaults, collections outcomes and credit decisions.

These datasets become important governed feedback and evaluation sources.

## 4. Ingestion Architecture

Four primary ingestion patterns are expected:

1. Real-time API/event ingestion.
2. Change Data Capture for suitable operational sources.
3. Batch/file/API ingestion for systems that cannot publish events.
4. Document ingestion and extraction.

All paths converge on governed canonical events.

```text
Sources
  |
  +--> Real-time APIs / Events ----+
  |                                |
  +--> CDC ------------------------+--> Ingestion Gateway --> Event Backbone
  |                                |
  +--> Batch / SFTP / APIs --------+
  |
  +--> Documents --> Validation --> Object Store
                       |
                       +--> Document Processing --> Canonical Events
```

The ingestion gateway is responsible for schema validation, source authentication, metadata enrichment, classification, deduplication and policy enforcement.

Large documents and binary artefacts are stored in object storage. Events carry references and metadata rather than large binary payloads.

## 5. Canonical Information Model

The canonical model is vendor-neutral and precedes physical database design.

```text
COUNTERPARTY
   |
   +-- FACILITY
   |      |
   |      +-- COLLATERAL
   |
   +-- ACCOUNT
   |      |
   |      +-- TRANSACTION
   |
   +-- RELATIONSHIP
   |      |
   |      +-- PERSON / ENTITY
   |
   +-- FINANCIAL PROFILE
          |
          +-- FINANCIAL PERIOD
                 |
                 +-- METRIC
                        |
                        +-- FEATURE
                               |
                               +-- EVENT
                                      |
                                      +-- EVIDENCE
                                             |
                                             +-- SIGNAL
                                                    |
                                                    +-- RISK ASSESSMENT
                                                           |
                                                           +-- DECISION
                                                                  |
                                                                  +-- CASE
```

This representation is conceptual; actual relationships will not necessarily form a single hierarchy.

## 6. Event, Evidence, Signal, Risk and Decision

These concepts are intentionally separate.

### Event

A fact or observation that something occurred, e.g. `PAYMENT_RETURNED`.

### Evidence

The source record supporting an event, e.g. a CBS transaction and return code.

### Signal

A risk interpretation derived from one or more events/features, e.g. `REPEATED_PAYMENT_FAILURE`.

### Risk Assessment

An aggregated assessment of a risk dimension or overall counterparty state.

### Decision

An authorized human or policy decision, including accept, reject, modify, escalate, mitigate or close.

## 7. Canonical Event Envelope

The initial event contract will include at least:

```json
{
  "eventId": "01993fa7-c4...",
  "eventType": "PAYMENT_RETURNED",
  "eventVersion": "1.0",
  "entity": {
    "type": "COUNTERPARTY",
    "id": "CP-918271"
  },
  "eventTime": "2026-09-17T09:32:18Z",
  "observedTime": "2026-09-17T09:32:20Z",
  "ingestedTime": "2026-09-17T09:32:21Z",
  "source": {
    "system": "CBS",
    "recordId": "TXN-98271882"
  },
  "classification": {
    "domain": "TRANSACTION",
    "sensitivity": "CONFIDENTIAL"
  },
  "correlationId": "...",
  "causationId": "...",
  "payload": {}
}
```

Event time, observed time and ingestion time are intentionally distinct to support late-arriving data, temporal reconstruction and audit.

## 8. Event Taxonomy — Initial Domains

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
relationship.*
sector.*
macro.*
risk.*
signal.*
case.*
decision.*
model.*
```

Detailed event and signal catalogues are defined in later blueprint stages.

## 9. Financial Data Platform

Different workloads require different storage characteristics. The logical data platform contains:

- operational state;
- historical/analytical state;
- object/document storage;
- search indexes;
- relationship/graph state;
- online/offline features;
- evidence and provenance.

The initial implementation should avoid unnecessary technology proliferation. A practical Phase-1 baseline is:

```text
Kafka
PostgreSQL
Elasticsearch
Redis
S3-compatible object storage
```

Dedicated graph, stream-processing, analytical and feature-store technologies are introduced based on validated workload requirements.

## 10. Temporal Financial Model

Financial observations require temporal context. A financial metric should preserve fields such as:

```text
Metric
Value
Financial Period
Effective Date
Reported Date
Received Date
Source
Revision
Extraction Version
Confidence
```

This permits reconstruction of both economic state and information state: what was true for a financial period and what the institution knew at a specific historical point.

## 11. Stream Intelligence

The stream-processing layer performs:

- validation;
- deduplication;
- normalization;
- enrichment;
- entity resolution;
- aggregation;
- event-time windows;
- trend calculation;
- feature generation;
- complex pattern detection.

Example:

```text
PAYMENT_RETURNED
PAYMENT_RETURNED
PAYMENT_RETURNED
        |
        | rolling 30-day window
        v
REPEATED_PAYMENT_RETURNS_DETECTED
```

Kafka is the initial event backbone. Kafka Streams can support early stateful/event-driven processing. Apache Flink is a North-Star candidate for complex stateful streaming, event-time processing and larger-scale CEP requirements. The final choice is workload-driven and will be captured through an ADR.

## 12. Feature Architecture

AI and risk engines consume governed features rather than querying arbitrary production systems.

Example feature groups:

```text
Counterparty CP001
|
+-- liquidity
|   +-- current_ratio
|   +-- quick_ratio
|   +-- wc_utilization_30d_avg
|   +-- cash_balance_trend_90d
|
+-- leverage
|   +-- debt_equity
|   +-- debt_ebitda
|
+-- behaviour
|   +-- payment_returns_30d
|   +-- max_dpd_90d
|   +-- utilization_change_30d
|
+-- external
|   +-- adverse_news_30d
|   +-- rating_change_90d
|
+-- relationships
    +-- distressed_entities
    +-- director_risk_score
```

Each feature requires a versioned definition, calculation, source, owner, timestamp, quality state and lineage.

## 13. Risk Intelligence Architecture

Risk intelligence uses multiple analytical engines.

```text
                         FEATURE STREAM
                              |
       +--------------+-------+--------+---------------+
       |              |       |        |               |
       v              v       v        v               v
     RULES          ANOMALY   ML     GRAPH            NLP
       |              |       |        |               |
       +--------------+-------+--------+---------------+
                              |
                              v
                       SIGNAL CANDIDATES
                              |
                              v
                      SIGNAL CORRELATION
                              |
                              v
                        GenAI REASONING
                              |
                              v
                       PROPOSED SIGNAL
```

This supports prediction, anomaly detection, correlation/reasoning and analyst explanation without making a single AI model authoritative.

## 14. Proposed Signal Model

A signal is a first-class governed entity containing at minimum:

- signal ID;
- counterparty/entity ID;
- signal type;
- lifecycle status;
- severity;
- confidence;
- detection timestamp;
- drivers/features;
- evidence references;
- rule/model identities and versions;
- proposed risk impact;
- generated explanation where applicable;
- analyst decisions and history.

AI explanation is an attribute of the signal, not the source of truth for the signal.

## 15. Signal Lifecycle

Initial lifecycle:

```text
DETECTED
   |
PROPOSED
   |
   +-----------> REJECTED
   |
ACCEPTED
   |
ACTIVE
   |
   +--> ESCALATED
   +--> MITIGATED
   +--> CLOSED
```

Additional states/classifications include `FALSE_POSITIVE`, `DUPLICATE`, `SUPERSEDED` and `EXPIRED`.

These outcomes form important governed feedback labels.

## 16. Human-in-the-Loop

Analysts must be able to inspect a proposed signal and drill from conclusion to original evidence:

```text
AI Explanation
      |
Signal
      |
Model / Rule Output
      |
Features
      |
Events
      |
Original Transaction / Document / External Source
```

Analysts can accept, reject, modify, escalate or request investigation subject to role and policy.

## 17. Risk-Score Architecture

Risk scoring is multidimensional rather than an arbitrary score produced by an LLM.

Candidate dimensions include:

- Financial;
- Behavioural;
- Credit;
- External;
- Management/Governance;
- Relationship/Group;
- Sector/Macroeconomic.

The lifecycle is:

```text
Raw Analytical Score
        |
Policy Adjustments
        |
Proposed Risk Score
        |
Human Validation
        |
Approved Risk Score
        |
Official EWS State
```

Each transition is retained for audit.

## 18. Model Lifecycle

```text
Data -> Features -> Training -> Validation -> Backtest
                                      |
                                      v
                                Model Registry
                                      |
                                Approval Gate
                                      |
                                      v
                                Model Serving
                                      |
                                      v
                                 Monitoring
                                      |
                           Drift / Performance
                                      |
                                      v
                                  Retraining
```

MLflow is an initial candidate for model registry, lineage and AI/ML tracing. Product selection remains an ADR rather than a locked architectural requirement.

## 19. AI Gateway

Applications do not directly call arbitrary foundation-model APIs.

```text
Application
    |
    v
+-----------------------+
| AI Gateway            |
| Authentication        |
| Authorization         |
| Data policy           |
| Redaction             |
| Prompt registry       |
| Model routing         |
| Rate limiting         |
| Guardrails            |
| Audit / tracing       |
| Token/cost accounting |
+-----------+-----------+
            |
     +------+------+ 
     |             |
     v             v
 Private LLM   Approved Cloud AI
```

The gateway provides the primary control plane for hybrid AI consumption and vendor portability.

## 20. Hybrid Deployment Direction

Core financial records, evidence, official risk state, feature state and analyst decisions remain inside the enterprise trust boundary.

Approved cloud capabilities can be consumed selectively through controlled interfaces.

```text
Public / External Sources
          |
          v
External Ingestion
          |
          v
================================================
        ENTERPRISE TRUST BOUNDARY

Kafka -> Stream Processing -> Data Platform
                    |
          +---------+---------+
          |         |         |
        Rules       ML       Graph
          |         |         |
          +---------+---------+
                    |
                Risk Engine
                    |
                AI Gateway
                 /      \
                /        \
        Private Model   Approved Cloud Model
```

Hybrid is therefore an architectural capability, not a requirement that sensitive data leave the controlled environment.

## 21. Audit and Lineage

The required provenance chain is:

```text
Raw Source
   |
Event
   |
Transformation Version
   |
Feature Version
   |
Rule / Model Version
   |
Prediction
   |
Signal
   |
AI Explanation / Correlation
   |
Analyst Decision
   |
Risk Score Change
```

The system must preserve enough information to reconstruct why a material signal or risk-state change occurred and what information was available at the relevant point in time.

## 22. Logical Service Boundaries

Candidate domains/services include:

```text
ingestion/
  connector-service
  document-ingestion-service

core/
  counterparty-service
  facility-service
  relationship-service

streaming/
  event-normalization
  financial-feature-stream
  behavioural-feature-stream

financial/
  financial-statement-service
  ratio-engine
  trend-engine
  peer-analysis-service

risk/
  rules-engine
  feature-service
  signal-service
  risk-scoring-service

intelligence/
  anomaly-service
  prediction-service
  graph-intelligence-service
  nlp-intelligence-service
  correlation-service

ai/
  ai-gateway
  reasoning-service
  explanation-service

workflow/
  validation-service
  case-management-service
  notification-service

governance/
  evidence-service
  audit-service
  model-governance-service

experience/
  portfolio-api
  counterparty-360-api
```

These are logical boundaries, not a mandate for one deployment unit per item.

## 23. Evolution from Existing EWS

The target architecture is designed to evolve existing capabilities rather than require a big-bang rewrite.

```text
Existing EWS                 Target Domain
-----------------------------------------------------
customer-service          -> Counterparty Domain
analytics-services        -> Risk / Feature Domain
case-mgmt-service         -> Validation / Case Domain
notification-service      -> Notification Domain
auth-service              -> IAM integration
api-gateway               -> API / Experience Gateway
```

New platform capabilities are introduced around these domains: canonical events, event streaming, feature computation, evidence/provenance, ML, signal correlation, AI Gateway and relationship intelligence.

## 24. Initial Architecture Decisions

| Concern | Direction |
|---|---|
| Primary paradigm | Event-driven |
| Primary entity | Corporate Counterparty |
| AI role | Advisory / intelligence |
| Formal EWS | Human validated |
| AI risk score | Proposed, not authoritative |
| Evidence | Immutable/provenance-driven |
| Event backbone | Kafka |
| Stream processing | Kafka Streams initially; evaluate Flink |
| Core backend | Spring Boot |
| AI/ML implementation | Python |
| Operational data | PostgreSQL candidate |
| Search | Elasticsearch candidate |
| Cache/state | Redis candidate |
| Documents | S3-compatible object storage |
| Graph | Introduce based on relationship use cases |
| Model governance | MLflow candidate |
| GenAI access | AI Gateway only |
| Deployment | Hybrid-capable |
| Retail | Shared platform, separate domain intelligence |
| Audit | End-to-end lineage |

## 25. Scale Assumptions for Architecture Stress Testing

Until actual sizing is available, architecture exercises may use the following non-contractual assumptions:

| Metric | Working Assumption |
|---|---:|
| Corporate counterparties | 100,000 |
| Retail customers | 10 million |
| Corporate facilities | 1 million |
| Financial/business events | 10–50 million/day |
| External intelligence events | up to 1 million/day |
| Documents | 10 million+ |
| Relationships | 100 million+ |
| Peak event rate | 5,000–10,000/sec |
| Analysts | 500–2,000 |
| Risk history | 7–10 years |
| Critical-event processing target | <5 sec |
| Normal signal-generation target | <30 sec |

These numbers are design stress assumptions, not capacity commitments. They will be replaced by measured workload and NFRs.

## 26. Next Blueprint Stage

Part II will define the **Canonical Risk Information Model and Corporate Signal Taxonomy**.

Initial signal domains:

```text
Financial
Transactional
Repayment / Conduct
Credit
Covenant
Collateral
Management / Governance
Rating
Market
Legal / Regulatory
News / Reputation
Relationship / Group
Sector
Macroeconomic
Fraud / Integrity
```

For each signal the catalogue will define source, detection logic, analytical method, severity, confidence, evidence, feature impact, risk dimension, AI involvement, human-validation policy, score impact/weighting strategy, decay/expiry behaviour, false-positive feedback and audit requirements.
