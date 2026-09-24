# ADR-002 — Human-Validated Formal EWS

**Status:** Accepted for Part I/II baseline
**Decision date:** 2026-09-24

## Context

The platform's core principle is "AI does not own the risk state. Evidence does." (`README.md`, `00-vision-and-principles.md`, `01-architecture-blueprint.md` §1). Machine-detected signals and analytical risk assessments are proposals, not authoritative outcomes: the signal lifecycle explicitly routes every material signal through `DETECTED → PROPOSED → ACCEPTED/REJECTED → ACTIVE` (`02-canonical-risk-model.md` §12, `01-architecture-blueprint.md` §15), and risk assessments separately track raw/proposed/approved states that are never overwritten by later judgement (`01-architecture-blueprint.md` §17). This ADR records the decision to make human validation a structural, non-optional gate before any machine output can affect the official EWS state, since the Phase-1 skeleton now includes a dedicated `services/ews-case-workflow-service` module built specifically to host this gate.

## Decision

The **official EWS risk state and jurisdiction/accounting/prudential classification state are only ever set or changed through an explicit, authorized human (or governed policy) decision**, recorded as an immutable event (`schemas/events/payloads/signal-disposition-recorded-v1.avsc`) and never inferred automatically from a machine-detected signal or model prediction, however high its confidence score. `signal_policy.human_validation_required` (`schemas/signals/signal-policy-v1.schema.json`) defaults to `true`, and any policy that sets it `false` must be an explicit, reviewable exception, not a default posture. `ews-case-workflow-service` is a mandatory module in every phase of the build, not an optional add-on — there is no supported configuration of the platform where signals reach `ACTIVE` state without passing through this gate (except a deliberately configured, auditable low-risk-tolerance exception, which itself requires the same governance rigor as any other policy).

## Alternatives Considered

1. **Fully automated EWS with human review only on escalations.** Rejected: contradicts the platform's core stated principle and the explicit non-goal in `00-vision-and-principles.md` §7 ("delegate formal credit decisions to an LLM"/analytical engine). Also undermines regulatory defensibility — supervisory/accounting classifications (CECL, IFRS9, SMA/NPA) generally expect a governed, explainable decision trail with human accountability.
2. **Human validation as a configurable feature, off by default for high-confidence signals.** Rejected: confidence, severity and materiality are explicitly modeled as independent, imperfect concepts (`02-canonical-risk-model.md` §10, `02b-signal-scoring-and-confidence.md`) precisely because no single score can be trusted as a sufficient proxy for "safe to auto-approve." Making the gate optional by default would erode the platform's core trust model over time as thresholds drift.
3. **Human validation only at the risk-assessment level, not the signal level.** Rejected: the signal lifecycle itself (not just downstream risk aggregation) needs a human disposition step (`ACCEPTED`/`REJECTED`/`FALSE_POSITIVE`/`DUPLICATE`) to prevent noisy or incorrect signals from ever reaching correlation/risk-assessment logic and silently biasing it.

## Rationale

Financial risk decisions carry legal, regulatory and reputational consequences that the platform's own design principles (evidence-first, explainable, human-accountable) require to remain under human authority. Structurally guaranteeing this — via a dedicated service and a schema-enforced disposition event — is more durable than relying on policy discipline alone, and it gives the audit invariant (`01-architecture-blueprint.md` §22) a concrete, always-present link in the chain from evidence to approved state.

## Consequences

- `services/ews-case-workflow-service` and its `SignalDispositionController`/`CaseController` are mandatory Phase-1 deliverables, not deferrable to a later phase.
- Every signal-detection improvement (better rules, new ML models) increases proposal quality but never removes the human decision step from the critical path — this is a deliberate throughput ceiling the platform accepts in exchange for accountability.
- Downstream classification adapters (`02f-multi-jurisdiction-risk-model.md`) must also route through governed human/policy decisions, not directly from EWS signal state, per the "no direct mapping" rule in `02-canonical-risk-model.md` §14.
- Analyst capacity becomes a real operational constraint on how many proposed signals the platform can process; this must be sized for once real signal volume is known, likely requiring the signal-policy engine's severity/materiality gates to actively suppress low-value proposals rather than surfacing everything.

## Risks

- If analyst throughput cannot keep pace with proposed-signal volume, backlogs could delay time-sensitive early-warning value, partially offsetting the platform's core benefit; suppression/prioritization policy in `signal_policy` becomes operationally critical, not just a nice-to-have.
- A future, well-validated model with strong track record might reasonably deserve narrower human oversight for specific low-materiality signal types; the "default true" posture must remain revisable per-policy rather than hard-coded platform-wide, or the platform risks becoming unnecessarily rigid.

## Review Trigger

Revisit if: (a) real analyst throughput data shows the default human-gate posture is unsustainable at production signal volume, prompting a need for narrowly-scoped, rigorously governed auto-approval policies for specific low-materiality/high-confidence signal types; or (b) a regulator/compliance function formally requires a different governance model than the one assumed here.
