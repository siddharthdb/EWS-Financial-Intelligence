# Policy Schema and Semantic Validation

**Status:** Draft / Part IV  
**Related:** `06a-rule-and-policy-engine-architecture.md`, `06a1-phase1-policy-pack-and-fixtures.md`, `schemas/policies/*`

## 1. Purpose

A syntactically valid policy is not necessarily safe, coherent or executable. Policy publication therefore requires two independent gates:

```text
Policy JSON
   |
   v
SCHEMA VALIDATION
   |
   v
SEMANTIC VALIDATION
   |
   v
SIMULATION / MAKER-CHECKER
   |
   v
PUBLISHED POLICY VERSION
```

Schema validation answers **is this structurally a policy?** Semantic validation answers **does this policy make sense against the governed EWS domain?**

## 2. Validation severity

Semantic findings use:

- `ERROR` — blocks transition to APPROVED/ACTIVE;
- `WARNING` — does not block by default but must be visible in approval evidence;
- `INFO` — advisory/governance metadata.

Institutions may configure stricter publication gates, but may not downgrade platform-defined ERROR rules.

## 3. Core semantic rules

### PSV-001 Referenced parameter exists
Every `parameter` operand must resolve to exactly one parameter in the same effective policy.

### PSV-002 Referenced feature exists
Every `feature` operand must resolve to a governed feature definition/catalogue entry valid for the policy scope.

### PSV-003 Signal outcome exists
Every `SIGNAL_CANDIDATE` outcome code must resolve to the canonical signal taxonomy or an approved institution extension namespace.

### PSV-004 Risk dimension is canonical
Every risk dimension must resolve to the canonical Part-II dimension registry.

### PSV-005 Parameter value satisfies declared type and bounds
Runtime/configured values must match type, allowed range and allowed values. Institution configuration cannot exceed platform/policy-template guardrails.

### PSV-006 Threshold ordering is coherent
Related thresholds declare constraints such as warning < high or high < critical. The validator must not infer these relationships from parameter names; constraints are explicit governed metadata.

### PSV-007 PRIORITY is deterministic
For `PRIORITY` policies, enabled rules must have unique priority values unless the contract explicitly defines tie behaviour. Phase 1 prohibits ties.

### PSV-008 FIRST_MATCH/PRIORITY rule ordering is stable
Rule array order is not authoritative. Evaluation ordering derives from explicit priority and policy mode.

### PSV-009 Effective period is valid
`effectiveTo`, where present, must be later than `effectiveFrom`.

### PSV-010 Effective policy versions do not overlap
Two ACTIVE versions for the same institution/policy/scope must not overlap unless the policy family explicitly supports layered composition.

### PSV-011 Activation governance is complete
An ACTIVE policy requiring maker-checker must have approver identity/time and an accepted simulation reference. Maker and checker identity must differ when institutional governance requires separation of duties.

### PSV-012 Engine artifact is resolvable
DMN/DROOLS policies require an approved external artifact reference/version. NATIVE policies must not depend on an undeclared external executable artifact.

### PSV-013 Classification boundary
A `CLASSIFICATION_CANDIDATE` outcome is valid only for a classification-policy namespace/adapter. Generic analytical EWS policy packs cannot use it to infer accounting/prudential/supervisory/legal state.

### PSV-014 Feature semantic scope is compatible
A policy cannot silently apply a jurisdiction/product-specific feature outside its governed applicability.

### PSV-015 Entity grain is compatible
Feature grain and policy target entity must be compatible or an explicit aggregation/resolution contract must exist.

### PSV-016 Missing-value semantics are executable
Required operands whose feature state is unusable must resolve according to the declared missing-value policy; null must never be coerced to zero.

### PSV-017 Policy cannot redefine feature semantics
A policy parameter may change a threshold but cannot change the lookback/unit/denominator/observation semantics of an existing feature.

### PSV-018 Outcome severity is permitted
Institution-configurable severity must remain within any platform/template guardrails and cannot silently weaken mandatory classification or governance controls.

### PSV-019 Duplicate rule identity prohibited
Rule IDs are unique within a policy version and stable enough for audit/replay comparison.

### PSV-020 Unknown/deprecated taxonomy reference
Deprecated aliases produce WARNING or ERROR according to migration policy; unknown references are ERROR.

## 4. Validation context

Semantic validation is not a pure JSON function. It consumes governed registries:

```text
Policy
 + Feature Definition Registry
 + Signal Taxonomy Registry
 + Risk Dimension Registry
 + Institution/Portfolio Configuration
 + Policy Version Registry
 + Engine Artifact Registry
 + Jurisdiction Adapter Registry
        |
        v
Policy Semantic Validator
```

Every validation result records the registry/catalogue versions used so that approval evidence remains reproducible.

## 5. Publication gate

Recommended publication transition:

```text
DRAFT
  -> schema valid
VALIDATION
  -> semantic errors = 0
  -> fixtures pass
  -> simulation complete
PENDING_APPROVAL
  -> maker/checker complete
APPROVED
  -> effective-date scheduler / controlled activation
ACTIVE
```

Runtime services consume only approved/published immutable versions. Editing creates a new draft version.

## 6. CI versus runtime validation

CI validates repository-managed reference policies and fixtures. The Policy Management Service performs the same schema/semantic checks for institution-authored policies before publication.

The validator rules must therefore be packaged as reusable application code/library, not duplicated as CI-only shell logic.

## 7. Phase-1 implementation recommendation

Implement the validator in the Java/Spring policy service because policy publication, versioning and runtime evaluation are enterprise control-plane concerns. Keep JSON Schema validation standards-based. Implement semantic rules as explicit validator classes with stable rule IDs (`PSV-001`, etc.), deterministic outputs and unit fixtures.

Do not implement semantic validation as Drools rules in Phase 1. Using the policy engine to validate the policy engine creates avoidable bootstrap, debugging and governance complexity.


## 8. Unified publication validator

The executable control plane now composes structural, semantic and governance validation through a single `PolicyPublicationValidator`.

```text
risk-policy-v1.schema.json
        |
        v
JSON Schema validation
        |
        +--> governed feature registry
        +--> governed signal registry
        +--> canonical risk-dimension registry
        +--> explicit semantic constraints
        +--> existing policy-version registry
        |
        v
PolicySemanticValidator
        +
PolicyGovernanceValidator
        |
        v
PolicyPublicationValidator
        |
        +--> ERROR => publication blocked
        +--> WARNING => approval evidence
        +--> zero ERROR => publishable candidate
```

The repository CLI validates the actual Phase-1 policy documents rather than only synthetic unit-test objects. CI executes this gate whenever policy contracts, packs, fixtures or validator code change.

## 9. Reproducible validation evidence

Every publication validation produces a `policy-validation-result-v1` evidence artifact containing:

- policy ID/version;
- SHA-256 policy artifact hash;
- structural validation outcome;
- semantic/governance findings;
- exact feature/signal/risk-dimension registry versions;
- validator version;
- publication decision;
- validation timestamp.

The hash binds approval evidence to the evaluated policy artifact. Runtime activation must reference the same immutable published artifact; a changed artifact requires a new validation/approval cycle.

## 10. Completion boundary for Part IV-A

The Rule & Policy Engine architecture is considered ready to close when:

1. all repository policy JSON validates against its declared schema;
2. the actual Phase-1 policies pass the unified publication gate;
3. negative fixtures prove publication-blocking controls;
4. validation evidence itself conforms to its schema;
5. CI fails on any ERROR;
6. simulation and maker-checker references are required before ACTIVE lifecycle transition;
7. policy artifact/configuration identity is immutable and replayable.

Runtime rule evaluation remains a separate concern from publication governance.


## 8. Phase-1 execution-loop verification

The Phase-1 publication loop was executed in GitHub Actions on 2026-09-18 after repairing structural schema defects exposed by the gate.

Verified pipeline:

```text
JSON parse
 -> Maven compile
 -> 20 validator tests
 -> repository policy publication tests
 -> actual Phase-1 publication CLI
 -> validation-result schema validation
 -> evidence artifact upload
```

Verified results:

- all policy JSON/schema artifacts parse successfully;
- Java 17 validator build succeeds;
- 20 tests execute with zero failures/errors/skips;
- `REPEATED_PAYMENT_RETURN` is schema-valid, semantic-valid and publishable;
- `DPD_DERIORATION` is schema-valid, semantic-valid and publishable;
- `HIGH_UTILIZATION` is schema-valid, semantic-valid and publishable;
- negative fixtures prove PSV-006 threshold-order and PSV-010 version-overlap rejection;
- hard-control tests cover PSV-008 stable FIRST_MATCH priority, PSV-009 effective-time validation and PSV-013 classification boundary;
- publication evidence is emitted and uploaded as the `policy-validation-results` CI artifact;
- the evidence contract includes the SHA-256 hash of the exact policy file bytes.

Two non-blocking PSV-014 warnings remain intentionally visible: `current_dpd` and `utilization_ratio` are registered as product-specific features while the reference policies do not yet constrain `productTypes`. This is acceptable for the reference pack but must be resolved or explicitly accepted by institution policy governance before production activation where product applicability is narrower.

**Phase-1 publication gate status: GREEN.**
