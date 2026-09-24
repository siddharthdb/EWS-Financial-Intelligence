# EWS 2.0 — Gap Analysis and Implementation Roadmap

**Status:** Draft / Part IV — assessment
**Scope:** Current repository state (Parts I–III architecture docs, ADRs, executable schema contracts) assessed against the Level-0 end-state architecture defined in `01-architecture-blueprint.md`
**Purpose:** Give an honest, file-referenced account of what exists, what is missing, and a phased path from the current documentation-only state to a running platform.

## 1. Executive Summary

This repository is, in its entirety, an **architecture and contract specification** for a jurisdiction-neutral, event-driven Financial Risk Intelligence Platform ("EWS 2.0"). It contains 21 architecture documents, 4 research documents, 2 fully written ADRs (8 more are named but empty), and 8 draft Avro/JSON Schema contracts. It contains **zero lines of application code**: no backend services, no frontend, no database DDL, no ingestion connectors, no stream-processing jobs, no ML/rules/anomaly/graph/NLP engines, no auth/IAM, no tests, and no CI/CD.

The specification itself is unusually mature for a pre-build stage: the canonical ontology, ~150-entry global signal taxonomy, 34 priority signal contracts, Phase-1 feature catalogue with concrete formulas, event envelope, topic strategy, and outbox/streaming decisions have all been through an explicit coherence review (`05-coherence-review-parts-i-iii.md`) with all 12 identified blockers resolved. That review's own conclusion is the right way to frame this analysis: *"No claim is made that production readiness is complete... implementation POCs remain required before production deployment."*

**Bottom line:** every layer of the documented Level-0 architecture (`01-architecture-blueprint.md` §2, layers 1–9, plus cross-cutting concerns) is a gap in the sense that none of it is built. The gap is not one of design quality — the design is coherent and detailed enough to build against today — it is one of **zero implementation**. This document inventories that gap layer by layer and proposes a phased build sequence that reuses the spine the docs already define (`01-architecture-blueprint.md` §25).

## 2. Method

This assessment is based on a direct reading of:

- `README.md`
- `docs/architecture/00-vision-and-principles.md`, `00a-global-jurisdiction-and-market-model.md`
- `docs/architecture/01-architecture-blueprint.md` (Level-0 architecture, Phase-1 spine)
- `docs/architecture/02-canonical-risk-model.md`, `02a-priority-signal-contracts.md`, `02d-phase1-feature-catalogue.md`, and titles/structure of `02b`, `02c`, `02e`, `02f`, `02g`
- `docs/architecture/03-event-architecture.md`, and titles/structure of `03b`–`03i`
- `docs/architecture/04-signal-taxonomy.md`
- `docs/architecture/05-coherence-review-parts-i-iii.md`
- `docs/adr/README.md`, `docs/architecture/adr/ADR-003-application-managed-transactional-outbox.md`, `ADR-004-kafka-streams-first.md`
- `docs/research/*.md` (landscape/comparison research)
- All 13 files under `schemas/` (Avro event contracts, JSON Schema records)
- A full repository file listing (confirmed: no `src/`, `app/`, `api/`, `frontend/`, `backend/`, `infra/`, Dockerfiles, package manifests, or `.github/workflows` exist anywhere)

Gaps are assessed against the Level-0 logical architecture (`01-architecture-blueprint.md` §2, 9 layers + cross-cutting concerns) and the documented Phase-1 spine (§25).

## 3. Current-State Inventory

| Area | Document(s) | Maturity |
|---|---|---|
| Vision & principles | `00-vision-and-principles.md` | Mature — 18 principles, explicit non-goals |
| Jurisdiction/market model | `00a-global-jurisdiction-and-market-model.md` | Mature |
| Architecture blueprint (Level-0) | `01-architecture-blueprint.md` | Mature, coherence-reviewed |
| Canonical risk/ontology model | `02-canonical-risk-model.md` | Mature — full ontology, bitemporal model, audit invariant |
| Priority signal contracts | `02a-priority-signal-contracts.md` | Detailed — P01–P34 (partially reviewed; only P01–P09 read in full here) |
| Signal scoring/confidence | `02b-signal-scoring-and-confidence.md` | Exists, not read in full for this pass |
| Feature model + Phase-1 catalogue | `02c-canonical-feature-model.md`, `02d-phase1-feature-catalogue.md` | Detailed — concrete formulas (e.g. `current_dpd`, `wc_utilization_ratio`, `current_ratio`) |
| Lineage / multi-jurisdiction / external features | `02e`, `02f`, `02g` | Exist, not read in full for this pass |
| Event architecture | `03-event-architecture.md` | Mature — 14 principles, envelope, topics, reliability semantics |
| Outbox / topic / stream-processing / replay / Kafka topology design | `03b`–`03i` | Exist, titles/roles confirmed via README and event architecture cross-references |
| Global signal taxonomy | `04-signal-taxonomy.md` | Very mature — ~150 signals across 11 categories, portability classes, alias/deprecation map |
| Coherence review | `05-coherence-review-parts-i-iii.md` | Complete — 12/12 blockers resolved, 7-item non-blocking backlog remains |
| ADRs | `docs/adr/README.md` + 2 files | **2 of 10 written** (ADR-003 outbox, ADR-004 Kafka Streams-first); ADR-001, 002, 005–010 are titles only |
| Executable schemas | `schemas/**` (13 files) | 8 distinct contracts (event envelope + 5 payloads in Avro; signal/feature/entity-resolution/source-registry/classification-state in JSON Schema). Explicitly **DRAFT / PRE-PUBLICATION** — no compatibility guarantee, no CI validation, no registry |
| Application code | — | **None exists anywhere in the repository** |

## 4. Layer-by-Layer Gap Analysis

Layer numbers follow the Level-0 diagram in `01-architecture-blueprint.md` §2.

### Layer 1–2: Data Sources & Source Adapters/Ingestion
- **Specified:** Source categories (internal banking, financial statements, registries, markets, ratings, legal/insolvency, regulatory, news, macro, bureau — §3.1–3.8); acquisition modes (`PUSH_STREAM`, `POLL_INCREMENTAL`, `BULK_SNAPSHOT_PLUS_STREAM`, `BULK_SCHEDULED`, `LICENSED_FEED`, `ON_DEMAND`, `MANUAL_VERIFICATION`, CDC — §4); a source-rights schema (`schemas/sources/source-registry-v1.schema.json`) covering `llmProcessingAllowed`, `modelTrainingAllowed`, `modelInferenceAllowed`, `crossBorderTransferAllowed`; deep source-landscape research (`docs/research/us-uk-corporate-credit-data-source-landscape.md` — e.g. UK Companies House flagged as unusually API/stream-friendly vs. fragmented US state-level UCC data).
- **Built:** Nothing. No connector code, no source registry data, no checkpoint store.
- **Gap:** 100%. This is the first thing anything downstream depends on.
- **Priority/Effort:** Critical / **L** per adapter (varies hugely by source: a REST-friendly registry like Companies House is much cheaper than fragmented state UCC systems or licensed market-data feeds).

### Layer 3: Canonical Information Model
- **Specified:** Full ontology (PARTY/COUNTERPARTY/OBSERVATION/EVIDENCE/DERIVATION/SIGNAL/RISK/CLASSIFICATION/GOVERNANCE — `02-canonical-risk-model.md` §3), bitemporal semantics, relationship model, identity resolution.
- **Built:** Only as JSON Schema/Avro contracts for a subset of records (events, features, signals, entity resolution, classification state, source registry). No relational/graph persistence schema (DDL), no ORM/entity code.
- **Gap:** Contracts exist; persisted implementation does not.
- **Priority/Effort:** Critical / **M** (translating existing schemas to Postgres DDL is mechanical; the hard part — the model itself — is already done).

### Event Backbone (Kafka + Outbox + Schema Registry)
- **Specified:** In exceptional depth — 14 event-architecture principles (`03-event-architecture.md` §2), canonical envelope (§4, matches `schemas/events/canonical-event-envelope-v1.avsc`), topic strategy (§8, 16 named topics), outbox pattern (ADR-003 + `03b-outbox-reference-design.md`), Kafka Streams-first decision (ADR-004), production sizing/HA/resilience design (`03i`).
- **Built:** No deployed Kafka cluster, no schema registry, no outbox table/publisher, no topic provisioning, nothing.
- **Gap:** 100% infra + 100% application-side outbox code.
- **Priority/Effort:** Critical / **M** (well-trodden pattern; the design already resolved the hard decisions — this is now "build what's specified," not "figure out what to build").

### Layer 5: Event/Stream Processing
- **Specified:** Kafka Streams as Phase-1 default (ADR-004); two named applications implied by the architecture (an operational feature processor, a signal policy engine); entity/security resolution before risk interpretation (`03-event-architecture.md` §6); late-data/correction/replay semantics (`03f`).
- **Built:** Nothing.
- **Gap:** 100%.
- **Priority/Effort:** Critical / **L**.

### Layer 6: Feature & Analytical State
- **Specified:** Rich — feature model with definition/version/entity-grain/time-semantics/lineage (`02-canonical-risk-model.md` §9), portability classes (`GLOBAL_CORE`/`GLOBAL_PRODUCT_SPECIFIC`/`JURISDICTION_EXTENSION`/`INSTITUTION_POLICY_SPECIFIC`), and a Phase-1 catalogue with real formulas already written (`02d-phase1-feature-catalogue.md`: `current_dpd`, `max_dpd_30d/90d`, `returned_payment_count_30d`, `wc_utilization_ratio`, `wc_available_headroom`, `current_ratio`, `quick_ratio`, etc., each with explicit missingness/quality rules).
- **Built:** Nothing — no feature computation engine, no feature store (online or offline), no materialized feature values.
- **Gap:** 100% code; the *definitions* are implementation-ready.
- **Priority/Effort:** Critical / **M–L**.

### Layer 7: Risk Intelligence (Rules / Stats / ML / Anomaly / Graph / NLP / GenAI)
- **Specified:** Very thorough — `04-signal-taxonomy.md` catalogues ~150 signal types across 11 categories (financial performance, transactional/liquidity, repayment/conduct, refinancing/funding, covenant/collateral, fraud/integrity, management/governance, legal/insolvency/regulatory, market-implied credit, relationship/contagion, sector/macro), each tagged with a detection method (R/S/ML/A/NLP/G/AI) and portability class. `02a-priority-signal-contracts.md` promotes 34 of these to implementation-grade specs (P01–P34; e.g. P03 `REPEATED_PAYMENT_RETURN` explicitly excludes technical/network returns by reason-code policy). `02b-signal-scoring-and-confidence.md` defines severity/confidence/materiality as independent, decomposed concepts.
- **Built:** Nothing — no rule engine, no statistical/ML models, no anomaly detector, no graph engine, no NLP pipeline, no GenAI integration.
- **Gap:** 100%. This is the largest single body of unbuilt logic in the whole platform, but also the best-specified — the rule-based subset (method `R`) of the priority contracts is directly implementable once features exist.
- **Priority/Effort:** Critical (rule-based slice) / **M** for a first rule-based subset; **XL** for the full multi-engine layer including ML/graph/NLP/GenAI.

### Layer 8: EWS, Decision & Classification
- **Specified:** Signal lifecycle (`DETECTED → PROPOSED → ACCEPTED → ACTIVE → MITIGATED/CLOSED`, with `REJECTED`/reopen paths — `01-architecture-blueprint.md` §15), disposition model (`FALSE_POSITIVE`, `DUPLICATE`, `INSUFFICIENT_EVIDENCE`, `ESCALATE`, etc.), risk assessment lifecycle (raw → policy-adjusted → proposed → human-validated → approved, §17), classification namespace model (`ANALYTICAL_EWS`, `IN_SMA_NPA`, `US_CECL`, `UK_IFRS9_STAGE`, etc., §18, detailed further in `02f-multi-jurisdiction-risk-model.md`), and matching Avro/JSON contracts (`signal-detected-v1.avsc`, `signal-disposition-recorded-v1.avsc`, `signal-instance-v1.schema.json`, `classification-state-v1.schema.json`).
- **Built:** Nothing — no case/workflow engine, no official-EWS state store, no jurisdiction classification adapters (India/US/UK), no human validation UI/API.
- **Gap:** 100%.
- **Priority/Effort:** Critical / **L**.

### Layer 9: Experience (Portfolio Cockpit, Counterparty 360, Analyst Workbench, Investigation, Audit, Explainability, Administration)
- **Specified:** Named once as a layer in the Level-0 diagram and referenced as an endpoint of the international processing topology (`03-event-architecture.md` §19, "Counterparty 360"). No dedicated UX/UI spec document exists.
- **Built:** Nothing.
- **Gap:** 100% code **and** largely 100% design — this layer has the least specification depth of any layer and will need its own design pass before being buildable, not just implementation.
- **Priority/Effort:** High (needed to close the human-in-the-loop) / **L**, but blocked on additional design work first.

### Cross-Cutting Concerns (Security/IAM, AI Gateway, Model Governance, Observability, Data Quality, Jurisdiction Policy, Entity Resolution, Compliance)
- **Specified:** AI Gateway control plane is well-described (`01-architecture-blueprint.md` §20: auth, data classification/redaction, source-rights/cross-border policy, prompt registry, model routing, token/rate/cost controls, guardrails, audit/tracing). Model lifecycle governance is narratively defined (§19); MLflow is named as "an implementation candidate rather than an architectural dependency." Entity resolution is specified functionally (`02-canonical-risk-model.md` §4, `03-event-architecture.md` §6) with a dedicated schema (`schemas/identity/entity-resolution-v1.schema.json`).
- **Built:** Nothing. No IAM/RBAC design doc even exists yet (only labeled as a diagram box).
- **Gap:** 100%, and IAM specifically also needs a first design pass, not just implementation.
- **Priority/Effort:** Critical (IAM, entity resolution) / High (AI Gateway, model governance) — all currently **L–XL** with a design step first for IAM.

### Engineering Hygiene: Testing, CI/CD, Config/IaC
- **Specified:** The coherence review explicitly flags this as a known-missing backlog (`05-coherence-review-parts-i-iii.md` §5): schema CI, schema round-trip tests, topic registry generation, terminology checks, etc.
- **Built:** Nothing — no test directories, no CI config of any kind, no `.env`/config management, no IaC.
- **Gap:** 100%.
- **Priority/Effort:** Critical, and cheapest to close — recommended as Phase 0 (see §6) precisely because it's inexpensive and de-risks everything built after it.

## 5. Documentation/Contract-Level Gaps (What the Docs Themselves Flag as Unfinished)

Even setting implementation aside, the specification itself has acknowledged open items:

1. **8 of 10 planned ADRs are unwritten** (`docs/adr/README.md`): ADR-001 (event-driven architecture), ADR-002 (human-validated formal EWS), ADR-005 (evidence-first AI architecture), ADR-006 (AI Gateway/model access), ADR-007 (hybrid AI deployment), ADR-008 (graph intelligence adoption), ADR-009 (feature-store strategy), ADR-010 (model governance platform). Only ADR-003 (outbox) and ADR-004 (Kafka Streams) exist.
2. **Coherence review's 7-item non-blocking cleanup backlog** (`05-coherence-review-parts-i-iii.md` §5): (1) CI parsing every JSON/Avro schema and enforcing contract lifecycle/compatibility, (2) schema fixtures + serialization round-trip tests, (3) a machine-readable topic registry (event type → schema → topic → key → owner → retention/security class), (4) shared schema artifacts for repeated envelope types, (5) automated terminology checks for deprecated aliases, (6) a decision on the permanent owned schema namespace (`org.ewsfi` is currently draft) before first registry publication, (7) architecture diagrams generated from the normalized model.
3. **`03-event-architecture.md` §20 "Part-III remaining specifications"** (7 items): finalize schema subject/reference strategy for heterogeneous topic families; extend the event catalogue with international external observations; add source-registry/checkpoint and rights contracts; add entity/security-resolution event contracts; define market-data aggregation/repartition strategy; finalize Kafka sizing/retention/HA/security/operability; validate Part-III contracts end-to-end before PR.
4. **`04-signal-taxonomy.md` §21 "next design tasks"** (5 items): propagate normalized signal names into priority contracts and event catalogue; add jurisdiction/classification adapter contracts; add external source/entity-resolution quality fields to machine-readable schemas; define migration aliases for persisted signal types; complete Part-III schema/topic strategy and stream-processing topology. Note: some of these may already be partially addressed by the later Part III docs (`03b`–`03i`) — this list should be re-verified against those docs before being treated as still-open, rather than assumed stale or assumed current.

These are cheap to close relative to any implementation work and materially reduce risk for everything built afterward (see Phase 0, §6).

## 6. Legacy System Consideration

`01-architecture-blueprint.md` §24 ("Evolution from Existing EWS") states that "existing Spring Boot services, financial-statement extraction, case/workflow capability and institutional data sources can be reused where they fit the target contracts," and that "migration should establish the event/evidence spine first, then governed features/signals, then risk/correlation/AI capabilities. Existing EWS remains operational during transition until official risk-state ownership is deliberately migrated."

**This legacy system's code is not present in this repository.** Its actual state — API surface, data model, financial-statement extraction quality, case/workflow maturity — is unknown from what's available here and materially changes the real implementation roadmap (reuse vs. rebuild for financial-statement extraction and case/workflow in particular). This should be scoped as a dedicated discovery task before Phase 1 begins in earnest, ideally producing its own short "existing-system capability assessment" that this roadmap can then be revised against.

## 7. Prioritized Implementation Roadmap

Consistent with the docs' own framing (`01-architecture-blueprint.md` §25 Phase-1 spine, §26 strategic direction: 3–6 month spine objective, 2–3 year platform objective).

### Phase 0 — Foundation Hygiene (weeks, not months)
Close the documentation-level backlog from §5 above before writing implementation code against it:
- Write the 8 missing ADRs (at minimum ADR-001, 002, 005 — the ones most likely to shape Phase 1 code decisions).
- Stand up schema CI (parse/validate every `.avsc`/`.schema.json`, enforce lifecycle rules) and add round-trip serialization fixtures.
- Generate the machine-readable topic registry.
- Decide the permanent schema namespace (replacing draft `org.ewsfi`) before any registry publication.
- Kick off the legacy-system discovery task from §6.

This phase is cheap, fully within the existing "docs + schemas" skillset already demonstrated in this repo, and de-risks every phase after it.

### Phase 1 — The Spine (3–6 months, per §26)
Build one true end-to-end vertical slice, not a broad shallow build:
- **Event/evidence backbone:** deploy Kafka + schema registry; implement the application-managed transactional outbox per ADR-003/`03b-outbox-reference-design.md`; provision the topic catalogue from `03-event-architecture.md` §8.
- **Canonical model persistence:** translate the existing `schemas/` contracts into Postgres DDL (Phase-1 storage baseline per `01-architecture-blueprint.md` §9: Kafka, PostgreSQL, Elasticsearch, Redis, S3-compatible object storage).
- **One or two source adapters:** pick the highest-value, lowest-friction source first — the research in `docs/research/us-uk-corporate-credit-data-source-landscape.md` already flags UK Companies House as unusually API/stream-friendly, making it a strong first external adapter; pair with one internal source (e.g. payment-instruction-returned, matching the existing `schemas/events/payloads/payment-instruction-returned-v1.avsc` contract).
- **Feature computation:** implement a Kafka Streams app (per ADR-004) computing a handful of the already-defined Phase-1 features — `current_dpd`, `returned_payment_count_30d`, `wc_utilization_ratio` are good first candidates since their formulas and missingness rules are already fully specified in `02d-phase1-feature-catalogue.md`.
- **Rule-based signal detection:** implement the rule-based (`R`) subset of a small number of priority signal contracts — `DPD_EMERGED` (P01), `DPD_WORSENING` (P02), `REPEATED_PAYMENT_RETURN` (P03) are natural first targets since they're both fully specified (`02a-priority-signal-contracts.md`) and match the feature set above.
- **Minimal human validation workflow:** enough case/decision model to move a signal through `DETECTED → PROPOSED → ACCEPTED/REJECTED` with an audit trail (`signal-disposition-recorded-v1.avsc` already defines the event contract).
- **Thin Experience API/UI:** just enough to close the loop for one signal family end-to-end (view proposed signals, evidence drill-down, accept/reject) — this will require a short UX design pass first, since Layer 9 has no existing spec to build against.

### Phase 2 — Breadth (6–18 months)
- Expand source adapters to additional US/India sources.
- Add statistical/ML/anomaly detection engines beyond rule-based (`S`/`ML`/`A` methods in the taxonomy).
- Build the AI Gateway (§20) and introduce governed GenAI explanation — explicitly advisory only, never the authoritative risk-state source per the "AI does not own the risk state" principle.
- Stand up model governance (ADR-010, MLflow or equivalent).
- Expand feature and signal coverage toward the remaining ~115+ taxonomy entries and 25 remaining priority contracts (P10–P34).
- Build jurisdiction classification adapters (US CECL/supervisory, UK IFRS9/SICR, India SMA/NPA) per `02f-multi-jurisdiction-risk-model.md`.

### Phase 3 — Platform Maturity (18–36 months, per §26)
- Graph analytics/contagion detection (`G` method signals: `GROUP_ENTITY_DISTRESS`, `ROUND_TRIPPING_PATTERN`, etc.).
- Portfolio-level intelligence (Portfolio Cockpit).
- NLP/document intelligence for auditor reports, news, filings.
- Retail domain extension using shared infrastructure with independently governed models/policies (`02-canonical-risk-model.md` §19).
- Full richness across all reference jurisdictions and additional markets.

## 8. Risks & Open Questions

- **Legacy system unknown** (§6): the real Phase-1 cost depends heavily on what's actually reusable from the existing Spring Boot EWS, which isn't visible from this repository.
- **Data-source licensing/rights uncertainty:** the source-rights schema (`schemas/sources/source-registry-v1.schema.json`) and research docs flag that public availability does not imply unrestricted redistribution/model/LLM use — several candidate sources (market data, ratings, some registries) likely require paid licensing before Phase 1 adapters can legally be built.
- **Multi-jurisdiction compliance complexity:** classification adapters (CECL, IFRS9, SMA/NPA) are regulatory-grade work with compliance/legal review needs beyond engineering effort alone.
- **No production-readiness claim exists yet:** the coherence review is explicit that "performance benchmarks, failure tests, schema compatibility CI, DR rehearsal, security integration and implementation POCs remain required" — this roadmap does not shortcut that requirement.
- **Team capacity/scope:** this is, in full, a multi-year enterprise platform build (Kafka infrastructure, multiple ML/analytics engines, a full workflow/case system, multi-jurisdiction compliance adapters, and a rich analyst UX). Phase 1 as scoped above is achievable by a small team in the stated 3–6 month window; Phases 2–3 require materially more investment and should be re-scoped with actual team size once Phase 1 delivers real learnings.

## 9. Summary Table

| Layer | Spec Maturity | Build Status | Priority | Est. Effort |
|---|---|---|---|---|
| 1–2. Sources & Ingestion | High | None | Critical | L per adapter |
| 3. Canonical Information Model | High | Contracts only, no persistence | Critical | M |
| Event Backbone (Kafka/Outbox/Registry) | Very High | None | Critical | M |
| 5. Event/Stream Processing | High | None | Critical | L |
| 6. Feature & Analytical State | High | Definitions only, no engine | Critical | M–L |
| 7. Risk Intelligence (rules/ML/anomaly/graph/NLP/GenAI) | Very High | None | Critical (rules) / High (rest) | M (rules slice) → XL (full) |
| 8. EWS, Decision & Classification | High | None | Critical | L |
| 9. Experience (Cockpit/360/Workbench) | Low (needs design) | None | High | L (+ design pass) |
| Cross-cutting (IAM, AI Gateway, Model Governance, Entity Resolution) | Mixed (IAM low) | None | Critical (IAM, entity resolution) / High (rest) | L–XL |
| Engineering hygiene (tests/CI/IaC) | Flagged as backlog | None | Critical, cheapest to close | S–M |
| Documentation backlog (ADRs, schema CI, topic registry) | Self-identified | Partially open | High, cheap | S |
