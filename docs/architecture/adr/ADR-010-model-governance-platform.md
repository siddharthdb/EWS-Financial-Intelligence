# ADR-010 — Model Governance Platform

**Status:** Accepted for Phase-2 design baseline (not yet implemented — see Consequences)
**Decision date:** 2026-09-24

## Context

`01-architecture-blueprint.md` §19 ("Model Lifecycle and Use Governance") already specifies the
governed lifecycle every model in this platform must move through: "governed data/features,
training, validation, backtesting, registry, approval, serving, monitoring and retirement,"
explicitly stating that "Approval is use-case, jurisdiction/market, population and calibration
specific; a model validated for one population is not assumed portable to another," and that
"MLflow is an implementation candidate rather than an architectural dependency." §17 of
`02-canonical-risk-model.md` reinforces this at the data-model level: "Model deployment records
market population, jurisdictional use, segment/product, permitted/prohibited uses,
training/validation populations, calibration version and use-case approvals," and states plainly
that "A model approved for EWS prioritisation is not thereby approved for CECL, IFRS 9, IRB,
regulatory capital or formal default classification. A model calibrated for one country/portfolio is
not assumed portable to another." This non-portability constraint is echoed by the human-decision
list already recorded for roadmap item 2.9 (jurisdiction classification adapters), which cites the
identical principle to justify why classification work needs dedicated sign-off, not autonomous
engineering judgment.

Roadmap item 2.7 ("Model governance platform (ADR-010, not yet written)") names this ADR as its own
explicit prerequisite, mirroring how item 2.5 named ADR-006. Unlike 2.5/ADR-006, however, no
detection method in this platform yet requires a trained model: every signal implemented so far
(`REPEATED_PAYMENT_RETURN`, `DPD_EMERGED`, `DPD_WORSENING`, `UTILIZATION_HIGH`, `UTILIZATION_SPIKE`,
`REQUIRED_MONITORING_INFORMATION_DELAY`) is rule- or statistics-method, not ML-method — and roadmap
item 2.4 (first real ML model) is itself blocked on a product/model-governance decision this session
already declined to resolve autonomously (see the tracker's human-decision list), for lack of both
appropriate training data and a decision on what to build first. This ADR therefore records the
governance *platform's* architecture — the registry/approval/monitoring machinery every future model
must pass through — without pretending a first model exists to register yet.

## Decision

1. **A model registry is mandatory before any ML-method or graph-method signal ships.** Every trained
   model, before it can back a live signal detector, must have a registry entry recording: training
   and validation population, calibration version, the market/jurisdiction/segment/product it is
   approved for, permitted and prohibited uses, and use-case approval status — matching
   `02-canonical-risk-model.md` §17's field list exactly, not a reduced or reinterpreted subset.
2. **Approval is scoped, not global.** A registry entry's approval is use-case, jurisdiction/market,
   population and calibration specific, per §19. A model approved for one use case (e.g., EWS
   signal prioritization) carries no implicit approval for a different use case (e.g., IFRS 9/CECL
   classification, per §17's explicit example) or a different population/jurisdiction. The registry
   schema must make this a structural constraint (multiple scoped approval records per model, never
   one implicit blanket approval field) rather than a documentation convention that can silently
   drift.
3. **The full lifecycle is enforced, not just registration.** Per §19: governed
   data/features -> training -> validation -> backtesting -> registry -> approval -> serving ->
   monitoring -> retirement. A model cannot enter `serving` without having passed `validation` and
   `backtesting` first, and a `serving` model must have an active `monitoring` record — this ADR
   treats lifecycle-stage as a required, ordered field on the registry entry, not an informal process
   note.
4. **MLflow is the implementation candidate, not a fixed dependency.** Per §19's own text, this ADR
   preserves that framing rather than prematurely upgrading it to a hard architectural commitment:
   the registry's *data model* (the fields in points 1-3) is the actual governance contract; MLflow
   (or an equivalent registry/tracking product) is one way to implement it, to be confirmed when a
   real model is actually being built against it.
5. **This ADR governs the registry/approval/monitoring platform only — it does not itself approve,
   train, or select any model.** No model exists in this platform to register yet (see Context); this
   ADR fixes the contract a future model must satisfy, mirroring ADR-006's separation of "gateway
   architecture" from "provider selection."

## Alternatives Considered

1. **Defer this ADR until a real ML model exists to register against, on the theory that designing a
   registry with nothing to register is premature.** Rejected: unlike ADR-006 (where the evidence
   spine the AI Gateway would gate genuinely didn't exist until recently), the model-governance
   contract here is already fully specified in the blueprint (§19) and canonical risk model (§17) —
   there is nothing left to discover by waiting, and the tracker names this ADR as 2.7's own explicit
   prerequisite the same way ADR-006 was 2.5's. Recording the decision now, while both source
   sections are fresh in context, is cheaper than re-deriving it when item 2.4's block eventually
   lifts.
2. **Design a lighter-weight, EWS-specific model registry instead of the general lifecycle in §19,
   since this platform's only anticipated near-term use case is signal detection, not broader model
   risk management.** Rejected: §17's own worked example (EWS approval is not CECL/IFRS9/IRB
   approval) is specifically about avoiding exactly this kind of scope-narrowing — a registry that
   only tracks "is this model approved for signal detection" would silently violate the
   non-portability principle the moment a second use case appears, which the docs already anticipate
   will happen (jurisdiction classification adapters, item 2.9).
3. **Commit to MLflow now as a hard dependency, since it is the most commonly used open-source model
   registry and would let 2.4/2.7 proceed to real code sooner.** Rejected: `01-architecture-blueprint.md`
   §19 explicitly frames MLflow as "an implementation candidate rather than an architectural
   dependency" — overriding that framing without a concrete model/workload to validate against would
   be exactly the kind of premature product commitment ADR-011's own rationale (grounding a choice in
   explicit textual support, not inventing one) argues against making without similar support.
4. **Fold this into ADR-006 (AI Gateway) as a single "AI governance" ADR, since both concern governing
   machine-generated output.** Rejected: the platform's own docs treat these as clearly distinct
   concerns with different scopes — §19/§17 govern trained *models* (registry, approval, lifecycle),
   while §20 governs *inference-time gateway access* (auth, routing, guardrails, prompt registry).
   `docs/adr/README.md`'s planned-ADR list itself keeps them numbered separately (ADR-006 vs.
   ADR-010). A model can be governed by this ADR's registry while never being called through the
   AI Gateway at all (e.g., a batch-scored anomaly model with no GenAI component), so merging them
   would conflate two independently necessary controls.

## Rationale

Recording the registry's data contract now — while §19 and §17's text is directly in front of this
session rather than re-derived later — costs little and removes ambiguity for whoever eventually
builds item 2.4's first real model: they will not need to reverse-engineer governance requirements
from prose scattered across two documents, because this ADR consolidates them into a single decision
record with the same structure every other ADR in this platform uses. Deliberately not fixing MLflow
as a hard dependency, and deliberately not inventing a first model to register, keeps this ADR
honest about what is actually settled (the governance contract) versus what remains open
(implementation product, and the underlying model/training-data decision item 2.4 already flagged as
human-decision-gated).

## Consequences

- Item 2.7 in `08-roadmap-progress-tracker.md` can move from `NOT_STARTED` to `DONE (partial)`: the
  architecture decision this row named as its prerequisite is now written, but no registry
  service/schema/code exists yet, and there is still no model in the platform to register.
- Any future implementation of item 2.7 (the actual registry service/schema) must satisfy this ADR's
  four structural points (mandatory registry before ML/graph signals ship, scoped not global
  approval, ordered lifecycle enforcement, MLflow-as-candidate-not-dependency) rather than being
  free-designed from scratch.
- Item 2.4 (first real ML model) remains blocked on the same human decision already recorded in the
  tracker (what to build first, what to train it on) — this ADR does not unblock 2.4, since it
  governs the platform a model would register into, not the decision of which model to build.
- Item 2.9 (jurisdiction classification adapters) — itself human-decision-gated for compliance
  reasons — will, whenever it eventually proceeds, need to register any classification-supporting
  model through this same registry with its own distinct scoped approval, per point 2 above; this
  ADR does not itself authorize building 2.9.

## Risks

- A governance contract designed before any real model exists risks missing a requirement that only
  becomes apparent once a concrete training/validation/serving workflow is actually built (e.g.,
  feature-drift monitoring thresholds, a specific backtesting cadence). This ADR's lifecycle points
  should be treated as a floor, not a ceiling, and revisited once item 2.4 actually produces a model
  to register.
- Because no model exists yet, none of this ADR's constraints are currently enforced by any running
  code or schema — unlike ADR-005's `evidenceIds minItems: 1`, which is CI-enforceable today, this
  ADR's registry contract is presently narrative only until a real implementation exists to hold it
  to account.

## Review Trigger

Revisit if: (a) item 2.4's human-decision block lifts and a first real ML model is actually built,
at which point this ADR's registry contract must be implemented as real schema/code and checked
against the concrete workflow that results (mirroring how ADR-011 recorded a concrete registry
product only once there was a concrete workload to validate it against); (b) a graph-method signal
(item 3.1, itself gated on ADR-008, not yet written) is built and needs registration, which may
surface graph-model-specific governance needs (e.g., relationship-data provenance) not covered by
§19/§17's model-centric framing; or (c) item 2.9's jurisdiction classification work proceeds and
needs a concrete example of this ADR's "scoped, not global" approval in practice.
