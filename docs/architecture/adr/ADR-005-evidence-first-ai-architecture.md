# ADR-005 — Evidence-First AI Architecture

**Status:** Accepted for Part I/II baseline
**Decision date:** 2026-09-24

## Context

The platform uses multiple analytical engines including statistical, ML, anomaly, graph, NLP and GenAI methods (`01-architecture-blueprint.md` §13). GenAI in particular can correlate and explain governed evidence but must never "invent authoritative facts, silently modify feature values or own the official risk state" (`01-architecture-blueprint.md` §13). `02-canonical-risk-model.md` §16 formalizes AI output as a governed artefact (referencing purpose, model/provider/version, prompt/template version, retrieved evidence, structured output, narrative, uncertainty, guardrail results, trace ID and human disposition) and states plainly: "Generated prose is not source evidence." This ADR records the decision to make evidence-addressability a structural requirement enforced by contract, not just a design aspiration, since the Phase-1 skeleton's `signal_instance` table and schema already encode it.

## Decision

Every machine-proposed signal must be **evidence-addressable**: `schemas/signals/signal-instance-v1.schema.json` requires `evidenceIds` with `minItems: 1` — a signal instance with zero evidence references is not a valid record under the governed contract, and the Phase-1 DDL (`db/migration/V1__init_phase1_baseline.sql`, `signal_instance_evidence` table) mirrors this. AI-generated narrative, explanation or correlation output is always a downstream, clearly-labeled derived artefact referencing the evidence/features/signals it explains — never a substitute for them, and never itself treated as evidence in a subsequent audit trail. No AI Gateway or GenAI-reasoning module is included in the Phase-1 skeleton (see Consequences) because the evidence/feature/signal spine — the thing GenAI would explain — does not yet exist to explain.

## Alternatives Considered

1. **Allow signals or risk assessments generated purely from AI narrative/inference without a mandatory evidence link.** Rejected: directly contradicts the platform's foundational principle ("AI does not own the risk state. Evidence does.") and would make the audit invariant (`01-architecture-blueprint.md` §22) unsatisfiable for AI-originated content.
2. **Treat AI explanations as a form of evidence once corroborated by a human reviewer.** Rejected: conflates two distinct roles — evidence establishes what is factually known, while AI explanation interprets it. Retroactively upgrading narrative to evidence status would blur that boundary and make future audits ambiguous about provenance. A human's *decision* based on an AI explanation is captured properly instead, as a `signal.disposition.recorded` event, without needing to reclassify the explanation itself.
3. **Build the AI Gateway (§20) and GenAI reasoning module as part of the Phase-1 skeleton, gated by data classification policy from day one.** Rejected for Phase-1 specifically (not rejected long-term — see ADR-006, not yet written): the gap-analysis roadmap (`06-gap-analysis-and-implementation-roadmap.md` §7) scopes AI Gateway to Phase 2, since it should govern access to a real evidence/feature/signal spine, which doesn't exist yet in this skeleton. Building the gateway first would have nothing authoritative to gate.

## Rationale

Evidence-first is not merely a policy preference but a structural precondition for every other governance claim the architecture makes: the audit invariant, the human-validation gate (ADR-002), and jurisdiction/classification adapters, all depend on being able to trace every material output back to a specific, addressable, original source. Enforcing `evidenceIds minItems:1` at the schema level (rather than only in prose/policy) means this constraint cannot silently regress as the codebase grows — it is testable and CI-enforceable (`test/ews-event-contracts-test`).

## Consequences

- Any future rule/ML/anomaly/graph/NLP/GenAI detector must produce at least one evidence reference before its output can become a valid `signal_instance` row; detector implementations that cannot cite evidence (e.g. a pure black-box classifier with no feature/evidence lineage) cannot ship as-is and must be paired with an evidence-retrieval/lineage step.
- The AI Gateway and GenAI reasoning capability remain explicitly out of scope for the Phase-1 skeleton (no module scaffolded); when built (Phase 2 per the roadmap), it must consume the evidence/feature/signal spine already governed here rather than operating independently of it.
- Downstream classification/case/experience layers can rely on every signal they see having a working evidence drill-down path (`01-architecture-blueprint.md` §16), which is a hard prerequisite for the Analyst Workbench UX described (but not yet designed) for Layer 9.

## Risks

- Enforcing evidence-addressability at the schema level adds friction for genuinely evidence-light statistical/anomaly signals (e.g. a pure time-series anomaly might only have a weak evidentiary trail beyond "the data point itself"); policy will need to define what counts as sufficient evidence for such methods without diluting the requirement into a formality.
- If evidence storage/retrieval (object storage, evidence ledger — Layer 4 of `01-architecture-blueprint.md` §2) is not built with the same rigor as the signal/feature spine, the `evidenceIds` references risk becoming pointers to nothing, an integrity gap that would only surface at audit time.

## Review Trigger

Revisit if: (a) a detection method is found where the evidence-addressability requirement is genuinely unsatisfiable without compromising detection value, requiring a documented, narrowly-scoped exception; or (b) the AI Gateway design (ADR-006, not yet written) surfaces a need to relax or extend this contract for GenAI-specific evidence types (e.g. retrieved document spans) not yet modeled in `signal-instance-v1.schema.json`.
