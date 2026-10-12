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
| Contracts and CI | Avro and JSON Schema parse in CI; JVM round-trip parse; topic registry; deprecated-alias scan; diagram generated from the registry | Namespace `org.ewsfi` is still draft |
| Outbox | `SKIP LOCKED` claim, Kafka publish, status update | The outbox row stays JSON. The Kafka value is Confluent-framed Avro |
| Payment return | `POST /api/v1/internal/payment-returns` → `returned_payment_count_30d` → `REPEATED_PAYMENT_RETURN` → accept/reject → `GET /api/v1/proposed-signals/{id}` | Trigger is a stand-in REST endpoint, not a core-banking feed. Drill-down stops at feature values |
| DPD | `POST /api/v1/internal/obligation-dpd` → `current_dpd` and `max_dpd_30d` → `DPD_EMERGED` / `DPD_WORSENING` | Same stand-in pattern. Bands 1/30/60/90 are institution placeholders, not a regulatory classification |
| Case / registry shells | Controllers exist with no handlers | Open/assign/escalate/close and counterparty master are unbuilt |
| Experience | Read API only | No UI |

Official EWS state is still not a stored, human-approved risk record. Disposition updates `signal_instance.status` and writes `signal.disposition.recorded`. It does not approve a risk assessment.

## 3. Gaps that can be built without a new decision

In tracker order, skipping rows that need a person:

1. **Evidence chain past the feature.** Experience drill-down does not read `feature_value_lineage_ref` or the original canonical event. `01-architecture-blueprint.md` §16 requires that chain.
2. **Persisted signal policies.** `signal_policy` is specified and has DDL. Both live policies are Java constants.
3. **Event-time windows.** Feature topologies use the Kafka record timestamp, not the envelope `eventTime`.
4. **Remaining Phase-1 features** in `02d` once a source event exists: ratios, cash-flow, covenant, monitoring. Each one is another vertical slice, not a framework rewrite.
5. **Phase 2 and 3 rows in the tracker** after the ADRs they depend on are written: ADR-006, ADR-008, ADR-009, ADR-010. ADR-007 (hybrid AI deployment) is also unwritten and should land before any cloud-model integration.

## 4. Decisions recorded 2026-10-12

| Tracker | Decision |
|---|---|
| 0.11 | Keep draft namespace `org.ewsfi` until the first production schema-registry publication. |
| 0.13 | The legacy Spring Boot EWS is not available. The spine is built in this repository. |
| 1.13 | Do not call Companies House until an API key and a source-rights decision exist. |
| 1.16 | Local development uses HTTP Basic. Users `analyst` / `supervisor`, roles `ANALYST` / `SUPERVISOR`. Closing and escalating a case require `SUPERVISOR`. Replace this gate when a real identity provider is chosen. |

## 5. Recommended sequence

1. Extend evidence drill-down one hop, from feature value to the canonical event id.
2. Only then open Phase 2: one external adapter that has a real credential, then one statistical detector, then the AI gateway ADR and a read-only explanation that cannot change official EWS state.

Phase 3 (graph, cockpit UI, NLP, retail, production DR) stays behind that spine. Building it first would skip the human-validation gate the architecture treats as mandatory.
