# EWS 2.0 — Completion Plan

**Status:** Living plan, written 2026-10-12
**Replaces as current-state truth:** `06-gap-analysis-and-implementation-roadmap.md` (that document still describes a repository with no application code)
**Queue:** `08-roadmap-progress-tracker.md`

## 1. What "end state" means

Two different end states are easy to mix up.

**Phase-1 spine** (`01-architecture-blueprint.md` §25–§26): one institution can ingest governed facts, compute a small set of features, propose rule-based signals, and have a person accept or reject them with an audit trail. That is the buildable near-term product. It is not finished.

**Platform end state** (§26, two-to-three-year objective): the full Level-0 stack. External adapters across reference jurisdictions, statistical and ML detection, an AI gateway that cannot own official risk state, model governance, graph contagion, a portfolio cockpit, document intelligence, and retail as a separate domain on the same spine. That end state is not a single implementation pass. Several items in it are blocked on decisions this repository cannot make by itself.

## 2. What is actually built

| Slice | Path | Limit |
|---|---|---|
| Contracts and CI | Avro and JSON Schema parse in CI; JVM round-trip parse; topic registry; deprecated-alias scan; diagram generated from the registry | Namespace `org.ewsfi` is still draft. Wire format is JSON, not Avro + Apicurio |
| Outbox | `SKIP LOCKED` claim, Kafka publish, status update | JSON payload, not Avro |
| Payment return | `POST /api/v1/internal/payment-returns` → `returned_payment_count_30d` → `REPEATED_PAYMENT_RETURN` → accept/reject → `GET /api/v1/proposed-signals/{id}` | Trigger is a stand-in REST endpoint, not a core-banking feed. Drill-down stops at feature values |
| DPD | `POST /api/v1/internal/obligation-dpd` → `current_dpd` and `max_dpd_30d` → `DPD_EMERGED` / `DPD_WORSENING` | Same stand-in pattern. Bands 1/30/60/90 are institution placeholders, not a regulatory classification |
| Case / registry shells | Controllers exist with no handlers | Open/assign/escalate/close and counterparty master are unbuilt |
| Experience | Read API only | No UI |

Official EWS state is still not a stored, human-approved risk record. Disposition updates `signal_instance.status` and writes `signal.disposition.recorded`. It does not approve a risk assessment.

## 3. Gaps that can be built without a new decision

In tracker order, skipping rows that need a person:

1. **1.17 Avro + Schema Registry.** ADR-011 already chose Apicurio. The payment-return and DPD slices publish JSON on purpose so they can be tested without a registry. Replacing that path is the next platform correction, not a new product feature.
2. **1.14 Utilization.** `wc_utilization_ratio`, `wc_available_headroom`, `UTILIZATION_HIGH`, `UTILIZATION_SPIKE`. Needs `facility.limit.changed` and `facility.outstanding.changed` adapters, which do not have Avro payloads yet. Write those contracts first, then copy the DPD slice.
3. **1.15 Case model.** `case.opened` / `assigned` / `escalated` / `closed` on `ews.derived.case`, plus a link from an accepted signal to a case. The case controller is an empty shell.
4. **Evidence chain past the feature.** Experience drill-down does not read `feature_value_lineage_ref` or the original canonical event. `01-architecture-blueprint.md` §16 requires that chain.
5. **Persisted signal policies.** `signal_policy` is specified and has DDL. Both live policies are Java constants.
6. **Event-time windows.** Feature topologies use the Kafka record timestamp, not the envelope `eventTime`.
7. **Remaining Phase-1 features** in `02d` once a source event exists: ratios, cash-flow, covenant, monitoring. Each one is another vertical slice, not a framework rewrite.
8. **Phase 2 and 3 rows in the tracker** after the ADRs they depend on are written: ADR-006, ADR-008, ADR-009, ADR-010. ADR-007 (hybrid AI deployment) is also unwritten and should land before any cloud-model integration.

## 4. Decisions that block the rest

These stay `NOT_STARTED` until a person answers them. Guessing would freeze the wrong contract or invent a system that is not in this repository.

| Tracker | Question |
|---|---|
| 0.11 | What permanent schema namespace replaces draft `org.ewsfi` before the first production registry publication? Changing it later is a major contract migration. |
| 0.13 | Where is the existing Spring Boot EWS this blueprint says can be reused? It is not in this repo. Financial-statement extraction and the current case system cannot be assessed from here. |
| 1.13 | Is a UK Companies House API key available, and do the source-rights flags allow storing and scoring that data? Public API access is not the same as a modeled source right. |
| 1.16 | Which identity provider and which roles gate disposition and the experience API? Today `actorId` is the literal `unauthenticated-analyst`. |

A workable default, if you want implementation to continue without waiting, is: keep `org.ewsfi` until publication; treat the legacy EWS as unavailable and build the spine fresh; skip Companies House until a key and a rights decision exist; use an in-process API key or HTTP Basic with two roles (`ANALYST`, `SUPERVISOR`) only as a local-dev gate, and replace it when the real provider is chosen. Say if those defaults are acceptable.

## 5. Recommended sequence

1. Answer or explicitly defer the four decisions above.
2. Switch the existing two slices to Avro + Apicurio (1.17) before adding more event families, so new slices are not born on the interim JSON wire.
3. Add utilization (1.14) and the case lifecycle (1.15). That finishes the operational core called out in `02a` §4 for the features whose inputs are already internal servicing facts, except trade finance.
4. Extend evidence drill-down one hop, from feature value to the canonical event id.
5. Only then open Phase 2: one external adapter that has a real credential, then one statistical detector, then the AI gateway ADR and a read-only explanation that cannot change official EWS state.

Phase 3 (graph, cockpit UI, NLP, retail, production DR) stays behind that spine. Building it first would skip the human-validation gate the architecture treats as mandatory.
