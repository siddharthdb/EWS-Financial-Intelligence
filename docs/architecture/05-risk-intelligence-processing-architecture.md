# EWS 2.0 — Risk Intelligence Processing Architecture

**Status:** Draft / Part IV  
**Scope:** Global corporate risk intelligence; retail is a separately governed future domain  
**Depends on:** Parts I–III canonical model, signal taxonomy, event architecture and executable contracts

## 1. Purpose

Part III established how authoritative facts, observations, features and decisions move through the platform. Part IV defines how those governed inputs become risk intelligence without allowing any single rules engine, model, graph algorithm or foundation model to become the owner of risk state.

The target processing chain is:

```text
CANONICAL EVENTS / OBSERVATIONS
        |
        v
FEATURE COMPUTATION + POINT-IN-TIME SNAPSHOT
        |
        +-------------------+-------------------+-------------------+
        |                   |                   |                   |
        v                   v                   v                   v
DETERMINISTIC RULES   STATISTICS / TS      ML / ANOMALY       GRAPH / NLP
        |                   |                   |                   |
        +-------------------+-------------------+-------------------+
                            |
                            v
                    SIGNAL DETECTION
                            |
                            v
               EPISODE / CORRELATION ENGINE
                            |
                            v
                  PROPOSED RISK ASSESSMENT
                            |
                   +--------+--------+
                   |                 |
                   v                 v
             GENAI REASONING    HUMAN WORKFLOW
             / EXPLANATION           |
                   |                 v
                   +-------> HUMAN VALIDATION
                                      |
                                      v
                              APPROVED EWS STATE
                                      |
                                      +----> JURISDICTION / ACCOUNTING /
                                             PRUDENTIAL CLASSIFICATION
                                             through separately governed adapters
```

The architecture intentionally separates **detection**, **correlation**, **assessment**, **explanation**, **human decision** and **official state**.

## 2. Core invariants

### RI-01 — Evidence owns the conclusion boundary

No engine can make an authoritative EWS, accounting, prudential, supervisory or legal classification solely from an opaque score or generated narrative. Every material conclusion resolves to governed features, observations and evidence.

### RI-02 — Engines are independent producers of analytical assertions

Rules, statistical methods, ML, anomaly detection, graph analytics, NLP and GenAI are independently governed capabilities. They may consume common features/evidence, but they do not call one another through hidden implementation chains that make provenance impossible to reconstruct.

### RI-03 — Signal is the common analytical contract

An engine does not directly mutate counterparty risk state. It produces an evaluation/prediction/observation that may result in a governed `SignalInstance` under an approved `SignalPolicy`.

### RI-04 — Proposed is not approved

```text
Raw Engine Output
    -> Proposed Signal
    -> Correlated Risk Assessment
    -> Proposed Risk Score/State
    -> Human/Governance Decision
    -> Approved Risk Score/State
```

Earlier stages remain immutable and queryable after approval or override.

### RI-05 — Point-in-time reproducibility is mandatory

Every evaluation must be reproducible against what the institution knew at the evaluation point. Processing therefore binds to feature revisions, observation/evidence IDs, knowledge time, policy/rule/model version and execution mode.

### RI-06 — AI is advisory

GenAI may summarize, correlate, explain, identify missing evidence and propose hypotheses. It cannot silently create authoritative facts, overwrite deterministic evidence, approve a signal or directly assign regulatory/accounting state.

### RI-07 — Jurisdiction policy is an adapter

Global risk semantics remain independent of local accounting, prudential, supervisory and legal rules. Classification adapters consume approved analytical state plus jurisdiction-specific evidence/policy; they do not redefine global signal semantics.

### RI-08 — Replay cannot contaminate live state

LIVE, RECOVERY_REPLAY, FEATURE_REBUILD, PROJECTION_REBUILD, COUNTERFACTUAL_BACKTEST and SOURCE_BACKFILL are explicit execution contexts. Non-live runs write to isolated result namespaces unless a separately governed promotion/reconciliation process is invoked.

### RI-09 — Quality gates precede confidence theatre

Missing, stale, conflicted or weakly resolved evidence cannot be converted into apparent certainty merely because a model emits a high probability. Data quality, source authority, entity resolution and extraction confidence remain explicit inputs to signal confidence.

### RI-10 — Human override is additive, not destructive

An analyst adjustment records actor, reason, prior state, evidence context and resulting approved state. It never rewrites the raw model/rule result.

## 3. Processing planes

Risk intelligence uses four processing planes.

### 3.1 Streaming plane

For continuously arriving facts where latency matters:

```text
Kafka canonical event
  -> Kafka Streams feature processor
  -> feature.value.updated
  -> signal policy evaluation
  -> signal.detected / signal.updated
  -> correlation update
```

Typical use cases: DPD movement, payment returns, utilization spikes, rating actions, market deterioration, security-interest events and legal/insolvency observations.

### 3.2 Scheduled analytical plane

For calculations that require portfolio context, expensive joins or periodic recalculation:

```text
point-in-time data snapshot
  -> batch/scheduled feature computation
  -> peer/statistical/model evaluation
  -> governed feature/prediction outputs
  -> signal policy evaluation
```

Typical use cases: financial-statement trends, peer deterioration, refinancing concentration, portfolio-relative anomaly and model recalibration.

### 3.3 On-demand decision plane

Triggered when an analyst, case workflow or upstream credit process requests current intelligence. It reads materialized governed state and may request bounded recalculation; it does not bypass policy/version controls.

### 3.4 Offline governance plane

Used for training, validation, backtesting, challenger evaluation, threshold calibration, rule simulation and historical reconstruction. Offline outputs cannot become live approved state without deployment/promotion governance.

## 4. Logical components

### 4.1 Feature computation service

Responsibilities:
- consume canonical events/observations;
- calculate governed features;
- preserve event/effective/knowledge/calculation time;
- emit immutable feature revisions;
- attach evidence/source-rights/entity-resolution lineage;
- materialize current point-in-time projections separately from history.

Feature computation must not emit official EWS state.

### 4.2 Rule and policy engine

Evaluates deterministic conditions under versioned, effective-dated policy. It supports global core rules plus product, jurisdiction and institution-policy overlays without forking the canonical ontology.

A rule evaluation records at minimum:

```text
ruleEvaluationId
ruleId
ruleVersion
policyId / policyVersion
entity
inputFeatureValueIds
inputObservationIds
condition result
thresholds / parameters
executionTime
knowledgeTime
executionMode
```

Rules that contribute to jurisdiction classification live in the classification adapter/policy domain, not inside generic EWS signal policy.

### 4.3 Statistical and time-series engine

Owns governed trend, change-point, volatility, deterioration-rate, seasonality and peer-relative calculations that do not require a predictive ML model.

Its outputs are features or analytical evaluations. Statistical engines do not receive special authority merely because they are deterministic mathematics.

### 4.4 Predictive ML engine

Owns inference for approved predictive models. Every inference references model/version, feature snapshot, population/applicability, calibration/validation reference and prediction timestamp.

```text
Feature Snapshot -> Model Version -> Raw Prediction
                                  -> Calibrated Prediction where approved
                                  -> Signal Policy
```

The model does not directly write `ApprovedRiskScore`.

### 4.5 Anomaly engine

Detects deviation from entity history, cohort/peer behaviour or expected operating range. An anomaly score is not automatically a risk score. A governed policy determines whether the anomaly becomes a signal and how materiality/confidence are interpreted.

### 4.6 Graph intelligence engine

Consumes temporal relationships and entity-resolution decisions to derive relationship features and contagion hypotheses. Graph propagation is bounded by relationship type, direction, effective time, confidence and policy. Risk must not propagate through arbitrary graph proximity.

### 4.7 NLP/document intelligence engine

Extracts structured candidate observations from filings, financial statements, announcements, legal documents, news and other authorised text sources.

```text
Document Evidence
 -> extraction/span
 -> candidate observation
 -> validation/resolution
 -> canonical observation
 -> feature/signal policy
```

The original evidence span/hash, extraction model/version and confidence remain available.

### 4.8 Signal policy engine

The common boundary between engine output and signal state. It owns:
- applicability;
- evidence/quality requirements;
- detection thresholds;
- confidence policy;
- materiality policy;
- deduplication;
- cooldown/suppression;
- episode membership;
- human-validation requirement.

Signal policy is data/configuration under governance, not hard-coded orchestration logic scattered across services.

### 4.9 Signal episode manager

A signal instance is an analytical assertion. An **episode** groups repeated/revised occurrences representing one continuing risk condition.

Example:

```text
Day 1 utilization spike
Day 3 utilization remains high
Day 6 payment return
Day 8 another utilization spike

individual signal evidence
        -> LIQUIDITY_STRESS episode
        -> correlated hypothesis
```

Episode logic prevents alert storms while preserving every underlying detection and revision.

### 4.10 Correlation engine

Consumes active signals, features, graph context and evidence to form governed risk hypotheses such as:

```text
EMERGING_LIQUIDITY_STRESS
REFINANCING_STRESS
REPAYMENT_DETERIORATION
FINANCIAL_PERFORMANCE_DERIORATION
GOVERNANCE_STRESS
RELATIONSHIP_CONTAGION
```

Correlation is not simple score addition. It considers temporal proximity, corroboration, source independence, common causal evidence, entity/relationship scope, contradiction and policy-defined interaction.

A correlation result must identify the contributing signals/evidence and policy/version that produced it.

### 4.11 Risk assessment service

Creates the proposed multidimensional counterparty risk assessment.

Canonical dimensions remain those governed in Part II. The assessment may contain:

```text
assessmentId
counterpartyId
asOf / knowledgeTime
riskDimensionAssessments[]
activeSignalIds[]
correlationIds[]
rawEngineContributions[]
proposedRiskScore
proposedRiskBand
confidence
materiality
explanationRef
policy/model/rule versions
```

No arithmetic formula is declared universal. Aggregation policy is portfolio/product/institution governed and separately versioned.

### 4.12 GenAI reasoning service

GenAI operates behind the AI Gateway and consumes a bounded evidence package assembled by deterministic services.

Permitted responsibilities include:
- summarize material evidence;
- explain why signals were produced;
- compare current vs historical state;
- identify corroborating/contradictory evidence;
- propose investigation questions;
- produce analyst-readable risk narratives;
- assist correlation where an approved policy permits it.

Prohibited responsibilities include:
- inventing missing evidence;
- silently changing feature values;
- bypassing source-rights controls;
- approving/rejecting signals;
- directly setting approved EWS or classification state;
- treating model prose as evidence.

Every material AI output records prompt/template version, model/deployment version, retrieved evidence IDs, policy context, response reference, timestamps and applicable safety/rights decision.

### 4.13 Human decision service

The analyst/maker-checker workflow receives proposed signals and assessments with evidence, lineage and explanations. Decisions are immutable events and create new approved-state revisions.

Human actions include accept, reject, request information, escalate, mark false positive/duplicate, adjust proposed assessment under policy and approve official EWS state.

### 4.14 Classification adapters

Separately governed adapters map approved analytical state plus authoritative local facts into namespaced accounting/prudential/supervisory/legal classifications where required.

Examples remain `IN_SMA_NPA`, `US_ACCRUAL_STATUS`, `US_CECL`, `UK_SICR`, `UK_IFRS9_STAGE`, `UK_PRUDENTIAL_DEFAULT`, etc. No generic `HIGH EWS => Stage 2/NPA/default` rule is permitted.

## 5. Synchronous versus asynchronous processing

Default to asynchronous event processing for durable risk intelligence. Synchronous APIs are reserved for bounded reads/evaluations where the caller genuinely requires a response.

```text
DURABLE FACT CHANGE
 -> transaction/outbox
 -> Kafka
 -> asynchronous feature/signal/risk processing

ANALYST READ
 -> query current materialized risk projection

EXPLICIT ON-DEMAND RECALCULATION
 -> submit evaluation request
 -> tracked evaluation/run
 -> durable result
```

Do not build a synchronous chain such as:

```text
API -> feature service -> ML service -> graph service -> LLM -> rule engine -> DB
```

for normal risk-state mutation. It creates latency coupling, ambiguous retry semantics and weak reproducibility.

## 6. State ownership

| State | Authoritative owner |
|---|---|
| canonical fact/observation | source-domain normalizer / evidence pipeline |
| feature revision | feature computation domain |
| rule evaluation | rule/policy engine |
| prediction | model inference domain |
| signal instance | signal policy domain |
| signal episode | episode manager |
| correlation/risk hypothesis | correlation domain |
| proposed risk assessment | risk assessment domain |
| AI narrative/reasoning | AI reasoning domain |
| human decision | decision/workflow domain |
| approved EWS state | governed EWS state service |
| jurisdiction classification | classification adapter/domain |

A materialized dashboard projection may combine these states but is never their system of record.

## 7. Processing identity and reproducibility

Every material analytical output should carry or reference:

```text
outputId
entityId
executionMode
runId
knowledgeTime
input snapshot/revision IDs
policy/rule/model versions
source/evidence lineage
calculation/inference time
correlation/causation/trace IDs
```

This allows the platform to answer:

> What would the approved production logic have concluded using only information the institution knew at time T?

and separately:

> What would a challenger policy/model have concluded against that same historical information?

Those are different queries and must not share mutable state.

## 8. Failure semantics

Risk processing is designed for retry and reconciliation.

- duplicate events are handled by event identity and state/version semantics;
- stale aggregate transitions are rejected/ignored according to sequence policy;
- model unavailability does not invalidate deterministic signals;
- GenAI unavailability does not block authoritative evidence processing;
- graph/NLP enrichment may arrive later and revise the proposed assessment;
- low-quality or unresolved external evidence remains quarantined/qualified rather than silently discarded;
- failed evaluations retain enough context for controlled replay.

The system should degrade by **losing optional analytical enrichment**, not by losing authoritative evidence or corrupting official state.

## 9. Service decomposition recommendation

Do not create one microservice for every engine on day one. Phase-1 physical deployment should minimize operational fragmentation while preserving logical boundaries.

Recommended initial physical services:

```text
ews-feature-processor
  - streaming feature computation
  - scheduled feature jobs where lightweight

ews-signal-policy-engine
  - deterministic rule evaluation
  - signal policy
  - episode/deduplication

ews-risk-intelligence-service
  - correlation
  - risk assessment
  - risk projections

ews-model-service (Python)
  - ML/anomaly inference
  - approved statistical/model packages

ews-document-intelligence-service (Python)
  - document/NLP extraction

ews-ai-gateway / reasoning-service
  - model routing
  - evidence-bounded GenAI reasoning
  - prompt/model audit

ews-decision-service
  - analyst decisions
  - approved EWS state
  - workflow integration
```

Graph capability can initially be a module/data capability behind the risk-intelligence service and split only when graph scale/technology justifies independent lifecycle.

## 10. Technology fit

The architecture deliberately permits workload-fit implementation:

- **Java / Spring Boot / Kafka Streams** — event processing, feature/stateful streaming, signal policy, correlation/risk services and enterprise integration;
- **Python** — statistical/ML/anomaly/NLP workloads, model serving and experimentation-to-production packaging;
- **PostgreSQL** — governed operational/risk state and policy metadata;
- **Kafka** — operational event backbone and replay source within configured retention;
- **Redis** — bounded low-latency cache/state where loss/rebuild semantics are acceptable;
- **Elasticsearch/OpenSearch-compatible search capability** — analyst search and evidence discovery where justified;
- **object storage** — immutable documents/evidence/model artifacts/history;
- **graph database** — introduced only when relationship query/algorithm requirements exceed relational/materialized graph approaches;
- **MLflow or equivalent** — model lifecycle/registry where ML is deployed;
- **Camunda/workflow engine** — human decision/maker-checker orchestration where process visibility is required;
- **Vault** — runtime secrets and credential rotation;
- **OpenTelemetry + metrics/logging stack** — distributed observability.

No technology choice changes the canonical contracts or governance boundaries.

## 11. Phase-1 execution path

The first production slice should prove the complete evidence-to-human-decision chain rather than implement every intelligence engine.

Recommended slice:

```text
payment.instruction.returned
obligation.dpd.changed
facility.outstanding.changed
facility.limit.changed
        |
        v
streaming features
        |
        v
REPEATED_PAYMENT_RETURN
DPD_DERIORATION
UTILIZATION_HIGH / SPIKE
        |
        v
EMERGING_LIQUIDITY_STRESS correlation
        |
        v
proposed risk assessment
        |
        v
analyst validation
        |
        v
approved EWS state revision
```

Add financial-statement/document intelligence next, then predictive/anomaly models, external intelligence and graph contagion.

## 12. Part IV decomposition

This chapter establishes the processing spine. Subsequent specifications should be:

```text
05a-rule-and-policy-engine-architecture.md
05b-signal-episode-and-correlation-engine.md
05c-risk-assessment-and-score-aggregation.md
05d-predictive-ml-and-anomaly-architecture.md
05e-graph-risk-intelligence.md
05f-genai-reasoning-and-ai-gateway.md
05g-human-decision-and-ews-state-machine.md
05h-explainability-backtesting-and-model-governance.md
```

Executable schemas should be added alongside each specification rather than deferred to the end of Part IV.

## 13. Architectural recommendation

Keep **Signal Policy + Correlation + Risk Assessment** as the center of the platform. Rules, ML, graph, NLP and GenAI are replaceable analytical engines around that center.

This avoids two common failure modes:

```text
RULE ENGINE AS THE ENTIRE EWS
```

and

```text
LLM / MODEL AS THE ENTIRE EWS
```

The durable architecture is:

```text
GOVERNED EVIDENCE
 -> GOVERNED FEATURES
 -> MULTIPLE INDEPENDENT ANALYTICAL ENGINES
 -> GOVERNED SIGNAL CONTRACT
 -> CORRELATION / RISK ASSESSMENT
 -> HUMAN GOVERNANCE
 -> OFFICIAL EWS STATE
```

That is the Part IV processing spine.