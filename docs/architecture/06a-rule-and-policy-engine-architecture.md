# EWS 2.0 — Rule and Policy Engine Architecture

**Status:** Draft / Part IV  
**Parent:** `05-risk-intelligence-processing-architecture.md`

## 1. Decision

EWS 2.0 will expose an **institution-controlled, governed Rule & Policy Service** as the canonical rule-management boundary.

Institutions must be able to define and maintain their own risk policies without changing application source code or depending on the platform vendor for ordinary threshold/rule changes.

The platform therefore does **not** make Drools, DRL, DMN or any other engine-specific representation the canonical rule model. The canonical model is an engine-neutral EWS policy contract owned by the institution. A runtime adapter may execute suitable policies using Drools/DMN, but engine choice remains replaceable.

```text
Institution Policy Authoring
        |
        v
Canonical EWS Policy Model
        |
        +--> validation / simulation / maker-checker / approval
        |
        v
Immutable Published Policy Version
        |
        +----------------------+----------------------+
        |                      |                      |
        v                      v                      v
Purpose-built evaluator   Drools/DMN adapter   Future evaluator
        |                      |                      |
        +----------------------+----------------------+
                               |
                               v
                    RuleEvaluationResult
                               |
                               v
                       Signal Policy Engine
```

**Institution control is a product requirement; engine configurability is an implementation detail.**

## 2. Why this boundary matters

A bank or regulated institution needs to change rules such as:

```text
IF dpd >= 30
AND utilization_ratio >= 0.90
AND payment_return_count_30d >= 2
THEN propose LIQUIDITY_STRESS signal severity HIGH
```

without a Java release. But unrestricted scripting inside a production risk platform is equally undesirable.

The architecture therefore separates:

```text
AUTHORED POLICY
  -> VALIDATED POLICY
  -> TESTED/SIMULATED POLICY
  -> APPROVED POLICY
  -> EFFECTIVE POLICY
  -> EXECUTION RESULT
```

from application deployment.

## 3. Policy ownership hierarchy

Policy composition follows explicit scopes:

```text
GLOBAL_CORE
   |
   +-- GLOBAL_PRODUCT_SPECIFIC
   |
   +-- JURISDICTION_EXTENSION
   |
   +-- INSTITUTION_POLICY_SPECIFIC
          |
          +-- portfolio / product / segment overlay
```

The institution-specific layer can tune or extend permitted policy behaviour but cannot silently redefine canonical facts, feature semantics or global signal meaning.

Examples:

- `dpd_days` means the governed DPD feature; a policy cannot redefine its calculation inline.
- `REPEATED_PAYMENT_RETURN` retains its canonical semantic meaning.
- an institution may decide that two returns in 30 days produce MEDIUM while another requires three or produces HIGH.
- local prudential/accounting classification logic remains in the separately governed classification domain.

## 4. Institution Policy Studio

The target product capability is an institution-facing **Policy Studio**, not a developer DRL editor.

Primary authoring surfaces:

1. **Decision table** — preferred for most threshold and segmentation policies.
2. **Guided rule builder** — business-readable AND/OR conditions over governed features/observations.
3. **Advanced expression** — constrained typed expression language for calculations not naturally represented by a table.
4. **Import/export API** — machine-readable canonical policy JSON/YAML for CI/CD and institution integration.
5. **DMN import/export** — optional interoperability capability where an institution already governs DMN assets.

Raw Java, arbitrary SQL, shell, Python, unrestricted SpEL/MVEL or arbitrary DRL execution is not exposed to institutional policy authors.

## 5. Canonical policy model

A policy version contains at minimum:

```text
policyId
policyVersion
name
description
ownerInstitution
semanticScope
jurisdictionContext
portfolio/product/segment applicability
status
validFrom
validTo
priority
rules[]
parameters[]
requiredFeatures[]
requiredEvidence[]
minimumEvidenceTier
qualityRequirements
outputContract
createdBy
approvedBy
approvalDecisionId
createdAt
approvedAt
supersedesVersion
changeReason
```

A rule contains:

```text
ruleId
name
description
enabled
priority
when
then
reasonCode
explanationTemplate
```

Conditions reference only governed inputs:

```text
feature("dpd_days") >= 30
feature("utilization_ratio") >= parameter("high_utilization_threshold")
feature("payment_return_count_30d") >= 2
quality("dpd_days") != "STALE"
```

Actions are declarative and allow-listed:

```text
PROPOSE_SIGNAL
SET_SEVERITY
SET_MATERIALITY
ADD_REASON_CODE
REQUEST_REVIEW
SUPPRESS_SIGNAL
ADD_DISPOSITION_TAG
```

A policy cannot directly execute arbitrary code or update an application table.

## 6. Parameters versus rules

Institutions should not need to clone a complete rule merely to alter thresholds.

Example:

```text
Rule:
  utilization_ratio >= HIGH_UTILIZATION_THRESHOLD

Institution A:
  HIGH_UTILIZATION_THRESHOLD = 0.85

Institution B:
  HIGH_UTILIZATION_THRESHOLD = 0.90
```

Parameters are typed, effective-dated and versioned:

```text
name
type
value
unit
allowedRange
scope
validFrom
validTo
```

Changing a parameter creates a new governed policy/parameter revision and follows the configured approval workflow.

## 7. Applicability and precedence

The engine resolves applicable policy before evaluating rules.

```text
entity
 -> jurisdiction
 -> institution
 -> product
 -> portfolio
 -> segment
 -> policy effective time
 -> approved policy version
```

Precedence must be deterministic. Recommended order:

```text
INSTITUTION/SEGMENT OVERRIDE
        > INSTITUTION PRODUCT POLICY
        > INSTITUTION BASE POLICY
        > JURISDICTION EXTENSION
        > GLOBAL PRODUCT DEFAULT
        > GLOBAL CORE
```

Overrides are explicit records; they do not mutate their parent policy.

Conflicting same-precedence policies fail validation/publishing unless a deterministic conflict strategy is explicitly declared.

## 8. Policy lifecycle

```text
DRAFT
  -> VALIDATION
  -> TESTING
  -> PENDING_APPROVAL
  -> APPROVED
  -> SCHEDULED
  -> ACTIVE
  -> SUPERSEDED
  -> RETIRED
```

Emergency suspension is an operational state/action with audit evidence; it must not delete the active version.

Maker-checker should be configurable by institution, with production policy publication normally requiring a distinct approver from the author.

## 9. Validation gates

A policy cannot be published merely because it parses.

Required gates:

### Structural
- schema valid;
- all feature/signal references exist;
- types/operators are compatible;
- parameter ranges are valid;
- no forbidden action exists.

### Semantic
- canonical signal/risk semantics are respected;
- classification rules are not inserted into analytical EWS policy accidentally;
- evidence/source-rights requirements are valid;
- jurisdiction applicability is explicit where necessary.

### Conflict
- overlapping rules identified;
- unreachable/shadowed rules reported;
- contradictory actions detected;
- precedence deterministic.

### Historical simulation
Run the candidate policy against a point-in-time historical population and compare with the current production version.

```text
current policy vs candidate policy
signals created
signals suppressed
severity movement
portfolio distribution
false-positive/analyst-rejection history
cases generated
population affected
```

Simulation results become part of the approval package.

## 10. Execution contract

Runtime evaluation is stateless from the caller's perspective:

```text
PolicyEvaluationRequest
  evaluationId
  entity
  policyContext
  knowledgeTime
  executionMode
  featureSnapshotIds
  observationIds
  evidenceIds

PolicyEvaluationResult
  evaluationId
  policyId
  policyVersion
  matchedRuleIds[]
  evaluatedRuleResults[]
  proposedActions[]
  reasonCodes[]
  inputRefs[]
  evaluatedAt
  executionMode
```

The rule engine does not directly persist an approved signal. The Signal Policy domain consumes the evaluation result and applies signal lifecycle/idempotency/episode semantics.

## 11. Drools versus purpose-built decision service

Apache KIE/Drools is a mature inference/decision engine with DRL, DMN, decision tables and CEP capabilities. Modern Drools supports rule units and DMN, while DRL exposes forward/backward chaining, agenda controls and temporal/event constructs. These are substantial capabilities, but they are broader than the core EWS institution-policy requirement.

| Criterion | Drools / KIE | Purpose-built EWS Decision Service |
|---|---|---|
| Complex inference | Excellent | Must be implemented deliberately |
| Large interacting rule sets | Strong Rete-style engine | Good for bounded deterministic policies; custom optimization needed at scale |
| DMN | Native/strong | Requires adapter/library |
| Decision tables | Native | Straightforward to implement over canonical model |
| CEP/temporal rules | Native capability | Keep primarily in Kafka Streams/features rather than duplicate streaming state |
| Business authoring | DMN/tables available, but engine concepts can leak | UI can be designed exactly around EWS vocabulary |
| Institution self-service | Possible with governance layer | First-class design goal |
| Canonical policy portability | Risk of DRL/DMN coupling | High if engine-neutral contract is canonical |
| Explainability | Rule match is inspectable; complex agenda interactions can be harder | Deterministic trace can be designed as a core contract |
| Effective dating/versioning | Requires surrounding governance | Native product capability |
| Maker-checker/approval | Requires surrounding governance | Native product capability |
| Historical simulation | Requires platform integration | Native product capability |
| Multi-tenant institution isolation | Requires design around engine | Native architecture concern |
| Operational complexity | Additional KIE/runtime/tooling expertise | Smaller runtime but platform owns more code |
| Developer learning curve | DRL/DMN/KIE concepts | Standard service + constrained DSL/AST |
| Vendor/project lifecycle exposure | Tied to KIE evolution | Internal contract stable; evaluator replaceable |
| Lock-in | DRL assets can create engine coupling; DMN is more portable | Low if canonical AST/schema is maintained |
| Advanced future rules | Strong headroom | Add capabilities deliberately or route selected policies to Drools |

## 12. Current Drools landscape

As of 2026, Apache KIE 10.2 is the current major line and the project is under Apache incubation. KIE 10.2 modernized tooling and supports contemporary Java/Spring Boot baselines. Drools supports DRL, DMN and CEP; DMN provides a standardized portable decision notation, while DRL provides deeper engine-specific expressiveness.

This makes Drools technically credible. It does **not** imply that EWS should expose Drools as its policy system of record.

## 13. Option A — Drools as the platform rule engine

```text
Policy Studio
 -> generate DMN/DRL
 -> KIE/Drools runtime
 -> evaluation result
```

### Strengths
- proven rule/inference engine;
- sophisticated matching and conflict/agenda behaviour;
- DMN interoperability;
- decision tables;
- temporal/CEP capabilities if ever required;
- avoids building a general-purpose inference engine.

### Weaknesses
- DRL can become a second programming language inside the platform;
- institution-facing governance still has to be built by us;
- version/effective-date/maker-checker/simulation/source-rights semantics are EWS concerns, not solved simply by choosing Drools;
- complex agenda/rule interaction can reduce predictability for business-authored EWS policies;
- overlap with Kafka Streams if Drools CEP is used for event-window logic;
- engine-specific assets can become architectural lock-in.

## 14. Option B — Purpose-built EWS decision service

```text
Policy Studio
 -> canonical policy JSON/AST
 -> deterministic evaluator
 -> evaluation result
```

### Strengths
- exact fit for institution-controlled EWS policy;
- constrained safe authoring;
- deterministic evaluation/explanation;
- straightforward multi-institution isolation;
- first-class effective dating, approval, simulation and audit;
- engine-neutral canonical representation;
- simpler operational footprint;
- no need to teach business users DRL.

### Weaknesses
- we own evaluator correctness and performance;
- temptation to grow a home-built general-purpose rules language;
- complex inference becomes expensive to recreate;
- DMN interoperability must be implemented separately;
- requires rigorous AST/schema/versioning/test infrastructure.

## 15. Option C — Hybrid engine-neutral policy service

```text
                    +--> native deterministic evaluator
Canonical Policy ---|
                    +--> DMN/Drools compiler/adapter
```

Most EWS policies execute in the purpose-built deterministic evaluator. Policies genuinely requiring complex inference or externally supplied DMN can be routed through an approved Drools adapter.

Both engines must return the same `PolicyEvaluationResult` contract and consume the same governed feature/evidence vocabulary.

Drools is therefore a **runtime capability**, not the product's policy ownership model.

## 16. Recommendation

Adopt **Option C architecturally**, but implement **Option B first**.

Phase 1 should build the institution-controlled canonical policy model, Policy Studio, deterministic evaluator, versioning, maker-checker, effective dating, simulation and evaluation trace. This covers the majority of EWS rules: thresholds, combinations, segmentation, evidence gates, severity/materiality mapping and signal proposal.

Add a Drools/DMN adapter only when a demonstrated rule set requires capabilities that would otherwise force the native evaluator to become a general-purpose inference engine.

The architectural rule is:

> **Do not build Drools ourselves, and do not make Drools our product model.**

The purpose-built service owns governance and the canonical contract. Drools remains an optional execution engine behind that boundary.

## 17. Phase-1 supported expression set

Keep the native evaluator deliberately bounded:

### Data
- governed feature values;
- feature quality/state;
- selected canonical observation metadata;
- entity/product/portfolio attributes;
- approved parameters;
- current signal state where explicitly allowed.

### Operators

```text
= != > >= < <=
IN / NOT_IN
BETWEEN
IS_NULL / IS_NOT_NULL
AND / OR / NOT
```

### Bounded functions

```text
age_days(value)
count(feature/observation, window)
exists(...)
changed_by(...)
percentage_change(...)
```

Windowed calculations should normally already exist as governed features. Policy expressions should not become an alternative feature-engineering framework.

## 18. Example institution-authored policy

```yaml
policyId: LIQUIDITY-STRESS-001
version: 3.2
scope: INSTITUTION_POLICY_SPECIFIC
appliesTo:
  product: CORPORATE_WORKING_CAPITAL
parameters:
  highUtilization: 0.90
  paymentReturns: 2
rules:
  - ruleId: LS-01
    when:
      all:
        - feature: utilization_ratio
          operator: GTE
          parameter: highUtilization
        - feature: payment_return_count_30d
          operator: GTE
          parameter: paymentReturns
        - feature: payment_return_count_30d
          qualityNotIn: [STALE, INSUFFICIENT]
    then:
      action: PROPOSE_SIGNAL
      signalType: EMERGING_LIQUIDITY_STRESS
      severity: HIGH
      reasonCode: HIGH_UTILIZATION_WITH_REPEATED_RETURNS
```

The UI renders this as a decision table/guided rule; YAML/JSON is the transport/storage representation, not necessarily what an analyst edits directly.

## 19. Governance and security

Institution self-service requires stronger controls, not weaker ones.

- tenant/institution boundary on every policy object;
- RBAC for author, reviewer, approver, publisher and auditor;
- optional maker-checker and higher approval thresholds for material rules;
- immutable version history;
- no edit-in-place for ACTIVE versions;
- complete audit trail;
- policy signing/hash at publication;
- deterministic runtime artifact generated from approved version;
- rollback activates a prior immutable version through a new audited action;
- secrets unavailable to rule expressions;
- CPU/time/expression complexity limits;
- policy publication events emitted to the audit/event platform.

## 20. Deployment and caching

The policy control plane persists canonical versions in PostgreSQL. Runtime services maintain read-only compiled policy caches keyed by institution, policy, version and applicability.

```text
Policy Studio/API
 -> PostgreSQL policy repository
 -> approval/publication
 -> policy.published event
 -> runtime compile/validate
 -> immutable cache
```

An evaluation records the exact version; cache refresh never changes the semantics of an evaluation already in flight.

## 21. Failure semantics

- invalid policy never reaches ACTIVE;
- compilation failure prevents publication/activation;
- runtime policy lookup failure does not fall back to an unversioned/default rule silently;
- unavailable optional Drools adapter does not affect policies assigned to the native evaluator;
- duplicate evaluation requests are idempotent by evaluation identity;
- policy activation is atomic per applicability scope;
- conflicting active versions are a control-plane error, not resolved nondeterministically at runtime.

## 22. Observability

Capture:

```text
policy evaluation count/latency
matched/no-match rate
rule match distribution
policy version distribution
signal proposal rate
candidate-vs-production simulation delta
rejection/false-positive rate by rule
policy compile/publication failures
stale policy-cache age
```

This enables the institution to govern not merely rule configuration but rule effectiveness.

## 23. Next executable contracts

The architecture should now add:

```text
schemas/policies/risk-policy-v1.schema.json
schemas/policies/policy-evaluation-request-v1.schema.json
schemas/policies/policy-evaluation-result-v1.schema.json
schemas/policies/policy-simulation-result-v1.schema.json
```

and events:

```text
policy.created
policy.submitted
policy.approved
policy.published
policy.activated
policy.superseded
policy.suspended
policy.retired
policy.simulation.completed
```

## 24. Architectural conclusion

The differentiating requirement is not "which rule engine do we use?" It is:

> **Can an institution safely own, test, approve, schedule, audit and evolve its risk policy without application releases while the platform preserves canonical semantics and reproducibility?**

EWS 2.0 answers that through an institution-controlled canonical policy service. Drools is retained as an optional sophisticated execution capability, not allowed to become the canonical architecture.