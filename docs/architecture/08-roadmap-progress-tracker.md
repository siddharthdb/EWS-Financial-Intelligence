# EWS 2.0 — Roadmap Progress Tracker

**Status:** Living document — updated by every implementation iteration
**Purpose:** The single queue every work session (manual or autonomous) reads to decide what to
build next, and the reference a periodic user check-in is measured against. Mirrors the phases in
`docs/architecture/06-gap-analysis-and-implementation-roadmap.md` §7.

## How to use this file

1. Before starting work, find the first `NOT_STARTED` row, in phase order (finish Phase 0 rows
   before Phase 1, Phase 1 before Phase 2, etc.).
2. Set it to `IN_PROGRESS` before starting.
3. When done and verified (see each phase's definition of done), set it to `DONE` and fill in the
   **Build log entry** column with the dated entry in `07-build-log.md` that covers it.
4. Never mark a row `DONE` on the basis of skeleton/stub code alone — "done" means real logic,
   tested, `mvn -B -ntp verify` green, committed and pushed.

## Phase 0 — Foundation hygiene

| # | Item | Status | Build log entry |
|---|---|---|---|
| 0.1 | Write ADR-001 (event-driven architecture) | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 0.2 | Write ADR-002 (human-validated formal EWS) | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 0.3 | Write ADR-005 (evidence-first AI architecture) | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 0.4 | Write ADR-011 (schema registry baseline: Apicurio) | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 0.5 | Schema CI (parse every Avro/JSON Schema contract) | DONE | 2026-09-24 (session: ADRs + skeleton) — `.github/workflows/ci.yml` |
| 0.6 | JVM-native schema round-trip tests | DONE | 2026-09-24 (session: ADRs + skeleton) — `ews-event-contracts-test` |
| 0.7 | Fix pre-existing malformed JSON in `source-registry-v1.schema.json` | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 0.8 | Machine-readable topic registry (event type → schema → topic → key → owner → retention) | DONE | 2026-09-24 — machine-readable topic registry |
| 0.9 | Shared schema artifacts/references for repeated envelope types | DONE (partial) | 2026-09-24 — shared semantic-scope schema artifact. Only the `semanticScope` enum extracted so far (4 identical occurrences); other candidate fields (quality-state, source-authority-tier) identified but deferred — see build log for why. |
| 0.10 | Automated terminology checks for deprecated signal aliases | DONE | 2026-09-24 — automated deprecated-terminology check |
| 0.11 | Decide permanent owned schema namespace (replace draft `org.ewsfi`) | NOT_STARTED | needs a human decision — do not resolve autonomously |
| 0.12 | Architecture diagrams generated from the normalized model | DONE | 2026-09-24 — generated topic flow diagram |
| 0.13 | Legacy Spring Boot EWS discovery/capability assessment | NOT_STARTED | needs access to a system not in this repo — flag to user, do not fabricate |

## Phase 1 — The spine

| # | Item | Status | Build log entry |
|---|---|---|---|
| 1.1 | Maven multi-module skeleton (all service shells, buildable) | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 1.2 | Postgres DDL from `schemas/` contracts | DONE | 2026-09-24 (session: ADRs + skeleton) — `db/migration/V1__init_phase1_baseline.sql` |
| 1.3 | docker-compose local dev stack | DONE | 2026-09-24 (session: ADRs + skeleton) |
| 1.4 | `ews-persistence-core` shared JPA module | DONE | 2026-09-24 — `ews-persistence-core` shared JPA module |
| 1.5 | Outbox claim strategy (`SKIP LOCKED`) + publisher worker (real Kafka publish) | DONE | 2026-09-24 — outbox claim + publish |
| 1.17 | Switch Kafka wire format from JSON to Avro + Schema Registry (per 03-event-architecture.md §10 and ADR-011) | DONE (partial — Avro binary codec only; registry integration + live pipeline cutover deferred) | 2026-09-24 — Avro binary wire-format codec |
| 1.6 | `PaymentInstructionReturnedAdapter` real logic + trigger endpoint | DONE | 2026-09-24 — payment-return ingestion adapter |
| 1.7 | Feature processor: `returned_payment_count_30d` real Kafka Streams topology | DONE | 2026-09-24 — feature processor topology |
| 1.8 | Signal policy engine: `REPEATED_PAYMENT_RETURN` (P03) real topology | DONE | 2026-09-24 — signal policy engine |
| 1.9 | Case workflow: real disposition endpoints (accept/reject) | DONE | 2026-09-24 — case-workflow disposition endpoints |
| 1.10 | Experience API: real proposed-signal query + evidence drill-down | DONE | 2026-09-24 — experience-api evidence drill-down |
| 1.11 | Integration tests for the full payment-return slice (embedded Kafka + Postgres) | DONE | covered incrementally by each step's own tests (19 tests total across the slice) rather than one combined end-to-end test — see 2026-09-24 experience-api entry |
| 1.12 | `current_dpd` feature + `DPD_EMERGED` (P01) signal | DONE (partial — see 1.18 for `max_dpd_30d`/`DPD_WORSENING`) | 2026-09-24 — DPD feature/signal family: current_dpd + DPD_EMERGED |
| 1.13 | UK Companies House source adapter (real HTTP integration) | NOT_STARTED | needs an API key/credential decision — flag to user |
| 1.14 | `wc_utilization_ratio` feature + `UTILIZATION_HIGH` (P05) signal | DONE (partial — `wc_available_headroom`/`wc_utilization_delta_30d` and `UTILIZATION_SPIKE` (P06) not yet implemented) | 2026-09-24 — Working-capital utilization: wc_utilization_ratio + UTILIZATION_HIGH |
| 1.15 | Minimal case/decision model beyond single disposition (case open/assign/escalate/close) | DONE | 2026-09-24 — Minimal investigation-case model (open/assign/escalate/close) |
| 1.16 | Basic auth/IAM for the Experience API and disposition endpoints | NOT_STARTED | needs a design decision (auth provider, RBAC model) — flag to user |
| 1.18 | `max_dpd_30d` (windowed) feature + `DPD_WORSENING` (P02) signal | DONE | 2026-09-24 — max_dpd_30d + DPD_WORSENING (P02) — closes out roadmap item 1.12 |

## Phase 2 — Breadth

| # | Item | Status | Build log entry |
|---|---|---|---|
| 2.1 | US SEC/EDGAR source adapter | DONE (partial — most-recent 10-K/10-Q detection only; 8-K item classification, XBRL extraction, CIK watch-list, and entity resolution not yet implemented) | 2026-09-24 — SEC EDGAR connector: the platform's first genuine external-source integration |
| 2.2 | India regulatory source adapter(s) | NOT_STARTED | blocked — no keyless public API found; see human-decision list |
| 2.3 | Statistical/anomaly detection engine (method `S`/`A` signals) | DONE (partial — first method-S signal only: `wc_utilization_delta_30d` + `UTILIZATION_SPIKE` (P06); true anomaly-method (A) detection and the financial-statement-based signals in 04-signal-taxonomy.md §4 remain unimplemented) | 2026-09-24 — wc_utilization_delta_30d + UTILIZATION_SPIKE (P06): first statistical (method S) signal |
| 2.4 | First real ML model (method `ML` signals) + model registry integration | NOT_STARTED | blocked — see human-decision list |
| 2.5 | AI Gateway (ADR-006, not yet written) | NOT_STARTED | needs ADR-006 first |
| 2.6 | Governed GenAI explanation capability | NOT_STARTED | depends on 2.5 |
| 2.7 | Model governance platform (ADR-010, not yet written) | NOT_STARTED | needs ADR-010 first |
| 2.8 | Expand priority signal contracts P10–P34 implementations | DONE (partial — P18 REQUIRED_MONITORING_INFORMATION_DELAY only; every other P10–P34 contract needs financial-statement XBRL contents this platform doesn't parse yet) | 2026-09-24 — financial_statement_filing_delay_days + REQUIRED_MONITORING_INFORMATION_DELAY (P18) |
| 2.9 | Jurisdiction classification adapters (US CECL/supervisory, UK IFRS9/SICR, IN SMA/NPA) | NOT_STARTED | |

## Phase 3 — Platform maturity

| # | Item | Status | Build log entry |
|---|---|---|---|
| 3.1 | Graph intelligence adoption (ADR-008, not yet written) + graph-method signals | NOT_STARTED | |
| 3.2 | Portfolio Cockpit (Layer 9 Experience UI) | NOT_STARTED | needs its own design pass first — Layer 9 has the least spec depth of any layer |
| 3.3 | NLP/document intelligence for filings/news/audit reports | NOT_STARTED | |
| 3.4 | Retail domain extension | NOT_STARTED | |
| 3.5 | Full multi-jurisdiction richness beyond India/US/UK reference implementations | NOT_STARTED | |
| 3.6 | Production hardening: performance benchmarks, failure tests, DR rehearsal, security integration | NOT_STARTED | explicitly called out as still-required in `05-coherence-review-parts-i-iii.md` |

## Items requiring a human decision (never resolve autonomously)

- 0.11 — permanent schema namespace
- 0.13 — legacy system discovery (needs access this session doesn't have)
- 1.13 — external API credentials/licensing for source adapters
- 1.16 — auth/IAM provider and RBAC model choice
- 2.2 — India regulatory source adapter(s): checked during the 2026-09-24 firing that added the
  SEC EDGAR connector (roadmap 2.1) for a keyless-public-API equivalent — MCA21 (India's company
  registry) returned HTTP 403 on an unauthenticated request (login/session-gated), and India's
  open-government data portal (`api.data.gov.in`) requires a registered API key. Unlike SEC EDGAR,
  no genuinely keyless official source was found reachable from this environment; which India
  source to target and how to obtain access is a licensing/credential decision, same class as 1.13
- 2.4 — First real ML model + model registry integration: checked every method-`ML`-tagged signal
  in `docs/architecture/04-signal-taxonomy.md` (`DEBIT_CREDIT_PATTERN_ANOMALY`,
  `ROUND_TRIPPING_PATTERN`/`_SUSPECTED`, `REFINANCING_RISK_INCREASE`,
  `MARKET_IMPLIED_CREDIT_STRESS`, `CONTAGION_SCORE_SPIKE`) against everything this platform
  currently ingests (payment returns, DPD, facility limit/outstanding, SEC filing metadata only,
  not filing contents) — none is honestly implementable without either new data ingestion (full
  transaction ledgers, graph/relationship data, market pricing) this platform doesn't have, or
  inventing an ungoverned signal type outside the taxonomy. Separately, no historical labeled
  outcome data exists anywhere in this platform yet to train against. Which composite/ML capability
  to build first, and what to train it on (synthetic vs. deferred until real data exists), is a
  product/model-governance decision, not one to resolve autonomously by fabricating training data
  or a training-data proxy for a real business outcome
- Any item where the "right" answer depends on real analyst throughput data, regulatory sign-off,
  or a source's actual licensing terms (per `docs/architecture/06-gap-analysis-and-implementation-roadmap.md`
  §8 "Risks & Open Questions")
