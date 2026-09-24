# ADR-006 — AI Gateway and Model Access

**Status:** Accepted for Phase-2 design baseline (not yet implemented — see Consequences)
**Decision date:** 2026-09-24

## Context

`01-architecture-blueprint.md` §20 already specifies an AI Gateway control plane between any
application and a foundation model: "Applications do not directly call arbitrary foundation-model
APIs," routed instead through authentication/authorization, data classification/redaction,
source-rights and cross-border policy, a prompt registry, model routing, token/rate/cost controls,
guardrails, and audit/tracing, sitting in front of "Private Model and/or Approved Cloud AI." §21
separately establishes hybrid deployment as "a deployment capability, not a requirement to move
sensitive data outside institutional control." `02-canonical-risk-model.md` §16 requires every AI
output to be a governed artefact referencing "purpose, model/provider/version, prompt/template
version, retrieved evidence, structured output, narrative, uncertainty, policy/guardrail results,
trace ID and human disposition," and states plainly that "Generated prose is not source evidence."
ADR-005 (evidence-first AI architecture) built on this to require every signal to be
evidence-addressable, and explicitly deferred building the gateway itself: "No AI Gateway or
GenAI-reasoning module is included in the Phase-1 skeleton... because the evidence/feature/signal
spine — the thing GenAI would explain — does not yet exist to explain," naming this ADR-006 as the
place to pick the decision back up once that spine exists.

That spine now exists for a meaningful slice of the platform: `feature_value`, `signal_instance`
(with `signal_instance_evidence`), and the disposition workflow are real, tested, and populated by
five real signal families (`REPEATED_PAYMENT_RETURN`, `DPD_EMERGED`, `DPD_WORSENING`,
`UTILIZATION_HIGH`, `UTILIZATION_SPIKE`, `REQUIRED_MONITORING_INFORMATION_DELAY` — see
`08-roadmap-progress-tracker.md` Phase 1/2). Roadmap item 2.5 ("AI Gateway (ADR-006, not yet
written)") is next in phase order and named this ADR as its own prerequisite. This ADR records the
gateway's architecture decision; it deliberately does **not** select a foundation-model provider
(see Decision and Consequences) — the platform's own research/vision docs never name one, and
committing to a specific vendor is a procurement/security-review decision this session has no basis
to make on the platform owner's behalf, the same class of decision item 2.4 (first real ML model)
was already deferred for.

## Decision

Adopt the AI Gateway as a single mandatory choke point for every GenAI call in the platform,
matching `01-architecture-blueprint.md` §20 exactly rather than inventing a different shape:

1. **No direct foundation-model calls.** No service (`ews-experience-api`,
   `ews-case-workflow-service`, or any future module) may hold a foundation-model API key or call a
   provider SDK directly. Every GenAI invocation goes through the gateway.
2. **The gateway is a logical control-plane responsibility, not a product choice yet.** This ADR
   fixes its required responsibilities (per §20: authn/authz, data classification/redaction,
   source-rights/cross-border policy enforcement, a prompt registry, model routing, token/rate/cost
   controls, guardrails, audit/tracing) and its position in the data flow (§22's audit invariant:
   `... -> Signal -> Risk Assessment -> AI Explanation where used -> Human Decision -> ...`). It does
   **not** fix an implementation technology, a specific foundation-model provider, or whether the
   gateway is built in-house versus adopting an existing open-source AI gateway product — those are
   Phase-2 implementation decisions for whoever picks this ADR back up to build against, informed by
   the platform owner's actual procurement/security constraints at that time.
3. **Every gateway call must be evidence-addressable per ADR-005 before it can run.** The gateway's
   prompt registry only accepts prompts parameterized by a `signal_instance` (or `feature_value`/
   `canonical_event_envelope`) ID already present in governed storage; it must reject any request
   whose context cannot be traced back to `evidenceIds`. This makes ADR-005's schema-level
   requirement (`evidenceIds minItems: 1`) enforceable at the gateway boundary too, not only at the
   `signal_instance` write path.
4. **Gateway output is always advisory, never authoritative.** Matching `01-architecture-blueprint.md`
   line 355 ("AI does not own the risk state. Evidence does.") and the gap-analysis roadmap's own
   framing (§7: "explicitly advisory only, never the authoritative risk-state source"), a gateway
   response can only ever produce a new, clearly-labeled AI-output artefact per
   `02-canonical-risk-model.md` §16 (referencing model/provider/version, prompt/template version,
   retrieved evidence, structured output, narrative, uncertainty, guardrail results, trace ID) — it
   cannot write to `signal_instance`, `classification_state`, or any other governed authoritative
   table directly.
5. **No gateway implementation ships in this ADR.** Consistent with how ADR-001/002/005/011 recorded
   architecture decisions before or alongside their corresponding code, and how ADR-011 recorded a
   concrete registry choice only because the docs already called it a "preferred baseline" — this
   ADR fixes the gateway's contract and constraints so a future increment can implement it against a
   concrete, human-chosen provider without re-litigating the architecture.

## Alternatives Considered

1. **Let each service call a foundation-model API directly, with data classification/redaction
   enforced by convention in each caller.** Rejected: `01-architecture-blueprint.md` §20 already
   rejects this explicitly ("Applications do not directly call arbitrary foundation-model APIs"),
   and duplicating classification/redaction/guardrail logic per service is exactly the kind of
   drift-prone pattern a single control plane exists to prevent — a single missed redaction rule in
   one of N callers is a data-leak risk the gateway pattern eliminates by construction.
2. **Defer this ADR further, since roadmap items 2.1–2.3/2.8 (the actual signal detection work) are
   arguably higher-value than gateway architecture with nothing yet consuming it.** Rejected: the
   gap-analysis roadmap (§7) and the tracker both name ADR-006 as the explicit prerequisite for item
   2.5, and — unlike ADR-005's Phase-1 deferral, where the evidence/feature/signal spine genuinely
   didn't exist yet — that spine now does exist (five real signal families with evidence chains).
   Recording the architecture decision now, while the evidence model it must integrate with is fresh
   in context, is cheaper than re-deriving it later; it also unblocks 2.6 (governed GenAI explanation),
   which explicitly depends on 2.5 in the tracker.
3. **Select a specific foundation-model provider now, so 2.5/2.6 can proceed to real code in the same
   firing.** Rejected: no document in this repository (vision, research, or architecture) names a
   preferred or default GenAI provider — unlike ADR-011's schema-registry choice, which had explicit
   textual support ("preferred baseline") to build on. Selecting one here would be fabricating a
   procurement/vendor decision with real cost, data-residency, and security-review implications that
   belongs to the platform owner, not an unstated inference from architecture docs. This mirrors why
   roadmap item 2.4 (first real ML model) is on the human-decision list — model/provider selection
   with real operational consequences is out of scope for autonomous resolution.
4. **Build a minimal working gateway service now with a stubbed/no-op model backend, to avoid
   shipping "only documentation."** Rejected: a gateway with no real model behind it and no real
   provider integration would be exactly the skeleton/stub pattern this project has consistently
   avoided (see `07-build-log.md`'s repeated "no business logic in skeletons" discipline) — it would
   add code with no genuine behavior to test against, while creating false signal that 2.5 is further
   along than it is. Recording the real architecture decision honestly, without pretending to have
   also solved the vendor question, is more useful than a hollow service.

## Rationale

The gateway's necessity is not new — it was already fully specified in the blueprint before any code
existed. What this ADR adds is the linkage to the concrete governed artefacts this platform has since
built: the gateway's prompt registry is defined in terms of real `signal_instance`/`feature_value`/
`canonical_event_envelope` IDs rather than an abstract "evidence" concept, and its advisory-only
output requirement is tied to the real `signal_instance` write path ADR-002 and ADR-005 already
govern. Recording this now — while that linkage is concrete and testable in principle — is more
durable than leaving the gateway as a purely narrative concept in the blueprint indefinitely.
Deliberately not selecting a provider keeps this ADR honest about what is and is not an engineering
decision: the control-plane shape is architecture; the vendor is procurement.

## Consequences

- Item 2.5 in `08-roadmap-progress-tracker.md` can move from `NOT_STARTED` to `DONE (partial)`: the
  architecture decision this row named as its prerequisite is now written, but no gateway service,
  module, or code exists yet — that remains explicitly future work, gated on a provider decision.
- Item 2.6 (governed GenAI explanation capability) remains blocked on 2.5's actual implementation,
  not just this ADR; the tracker's "depends on 2.5" note should be read as "depends on 2.5's
  eventual code, not merely this ADR."
- Any future implementation of item 2.5 must be checked against this ADR's five decision points
  (no direct calls, gateway responsibilities per §20, evidence-addressable prompt registry,
  advisory-only output, provider left open) rather than free-designed from scratch.
- The provider selection this ADR deliberately leaves open should be added to
  `08-roadmap-progress-tracker.md`'s "Items requiring a human decision" list, alongside 2.4, as the
  next thing genuinely blocking 2.5's code from proceeding.

## Risks

- Leaving the provider choice open means 2.5 cannot be fully implemented autonomously even after
  this ADR — a future firing that reaches 2.5 again will hit the same block this ADR describes,
  unless a human has since made that decision. This is intentional (see Alternatives #3) but worth
  flagging as a recurring, not one-time, blocker until resolved.
- The gateway's guardrail/redaction requirements (§20) are currently narrative only; without a
  concrete provider and a concrete data-classification policy (itself dependent on jurisdiction
  policy work not yet built — `01-architecture-blueprint.md` §23 lists "jurisdiction adapters" as a
  separate logical domain), "data classification/redaction" and "source-rights and cross-border
  policy" remain aspirational requirements this ADR cannot yet make testable. A future
  implementation ADR/increment must define these concretely, not merely reference this one.

## Review Trigger

Revisit if: (a) the platform owner selects a foundation-model provider, at which point a follow-up
ADR or an update to this one should record the concrete choice (mirroring how ADR-011 recorded
Apicurio) and unblock item 2.5's actual implementation; (b) ADR-007 (hybrid AI deployment, not yet
written) surfaces a data-residency or cross-border requirement that changes the gateway's required
responsibilities beyond what §20 already lists; or (c) a jurisdiction-policy adapter (item 2.9,
itself human-decision-gated) is eventually built and reveals concrete source-rights/cross-border
rules this ADR's "data classification/redaction" and "source-rights and cross-border policy" points
need to be made specific against.
