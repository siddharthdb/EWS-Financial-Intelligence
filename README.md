# EWS Financial Intelligence Platform

Architecture and engineering specification for a jurisdiction-neutral, event-driven, AI-assisted Early Warning System (EWS) and Financial Risk Intelligence Platform.

## Objective

The platform continuously observes financial and non-financial events, derives governed risk features, detects and predicts deterioration, correlates evidence, proposes explainable early-warning signals, and routes material outcomes to authorized analysts before they affect official EWS risk state.

The initial domain is corporate counterparty risk. Retail risk is a future domain using shared platform capabilities but independently governed features, rules, policies and models.

## Core operating model

```text
Evidence -> Observation/Event -> Feature -> Signal -> Risk Assessment
                                                |
                                                v
                                     Human Validation / Decision
                                                |
                              +-----------------+-----------------+
                              |                                   |
                        Official EWS                    Namespaced Classification
```

**AI does not own the risk state. Evidence does.** AI is advisory and governed; official EWS and jurisdiction/accounting/prudential classifications remain separately controlled outputs.

## Architecture documentation

### Part I — foundation
- [Vision and Architecture Principles](docs/architecture/00-vision-and-principles.md)
- [Global Jurisdiction and Market Model](docs/architecture/00a-global-jurisdiction-and-market-model.md)
- [Architecture Blueprint](docs/architecture/01-architecture-blueprint.md)

### Part II — canonical risk, feature and signal model
- [Canonical Risk Information Model](docs/architecture/02-canonical-risk-model.md)
- [Priority Signal Contracts](docs/architecture/02a-priority-signal-contracts.md)
- [Signal Scoring and Confidence](docs/architecture/02b-signal-scoring-and-confidence.md)
- [Canonical Feature Model](docs/architecture/02c-canonical-feature-model.md)
- [Phase-1 Feature Catalogue](docs/architecture/02d-phase1-feature-catalogue.md)
- [Source-Feature-Signal Lineage](docs/architecture/02e-source-feature-signal-lineage.md)
- [Multi-Jurisdiction Risk Model](docs/architecture/02f-multi-jurisdiction-risk-model.md)
- [Global External Feature Catalogue](docs/architecture/02g-global-external-feature-catalogue.md)

### Part III — event and streaming architecture
- [Event Architecture](docs/architecture/03-event-architecture.md)
- [Application-Managed Outbox Reference Design](docs/architecture/03b-outbox-reference-design.md)
- [Kafka Topic and Partition Strategy](docs/architecture/03c-topic-and-partition-strategy.md)
- [Phase-1 Event Catalogue](docs/architecture/03d-phase1-event-catalogue.md)
- [Stream Processing Design](docs/architecture/03e-stream-processing-design.md)
- [Late Data, Replay and Correction](docs/architecture/03f-late-data-replay-and-correction.md)
- [International External Event Catalogue](docs/architecture/03g-international-external-event-catalogue.md)
- [Schema Governance and Registry](docs/architecture/03h-schema-governance-and-registry.md)
- [Production Kafka Topology, Sizing and Resilience](docs/architecture/03i-production-kafka-topology-and-resilience.md)

### Cross-part taxonomy and decisions
- [Global Corporate Signal Taxonomy](docs/architecture/04-signal-taxonomy.md)
- Architecture Decision Records: see [docs/adr/README.md](docs/adr/README.md) for the full index (ADR-001, 002, 003, 004, 005, 011 written; ADR-006–010 planned)

### Part IV — assessment and ongoing build tracking
- [Gap Analysis and Implementation Roadmap](docs/architecture/06-gap-analysis-and-implementation-roadmap.md) — historical baseline, written before implementation
- [Build Log](docs/architecture/07-build-log.md) — dated record of every implementation session
- [Roadmap Progress Tracker](docs/architecture/08-roadmap-progress-tracker.md) — what's done, what's next
- [Completion Plan](docs/architecture/09-completion-plan.md) — current gaps and the path that remains

## Phase-1 spine

A Java 21 / Spring Boot 3 Maven multi-module project implements the first vertical slices of the
Phase-1 spine in `01-architecture-blueprint.md` §25:

- `platform/ews-schemas` generates Java classes from the contracts under `schemas/`.
- `platform/ews-platform-outbox-starter` claims outbox rows with `SKIP LOCKED` and publishes them to Kafka.
- `db/migration/V1__init_phase1_baseline.sql` — Postgres DDL derived from the `schemas/` contracts.
- `docker-compose.yml` — local dev stack matching the Phase-1 storage baseline (`01-architecture-blueprint.md` §9).
- `.github/workflows/ci.yml` — validates every schema contract, the topic registry, and the Maven build.
- Kafka values use Confluent-framed Avro. Schema ids are registered with Apicurio's Confluent-compatible API (ADR-011). The outbox row itself stays JSON.
- Payment-return slice: ingestion → `returned_payment_count_30d` → `REPEATED_PAYMENT_RETURN` → human disposition → experience-api evidence drill-down.
- DPD slice: `obligation.dpd.changed` → `current_dpd` / `max_dpd_30d` → `DPD_EMERGED` / `DPD_WORSENING`.
- Utilization slice: facility limit and outstanding → `wc_utilization_ratio` / `wc_available_headroom` → `UTILIZATION_HIGH` / `UTILIZATION_SPIKE`.
- Cases open from an accepted signal, then assign, escalate, and close. The experience API and case workflow use local HTTP Basic (`analyst`, `supervisor`).

What is still open, including decisions that need a person, is tracked in
`docs/architecture/08-roadmap-progress-tracker.md` and planned in
`docs/architecture/09-completion-plan.md`.

## Executable contracts

Machine-readable contracts are under `schemas/` for events, features, signals, classifications, identity/entity resolution and source governance. Contracts are currently **DRAFT / PRE-PUBLICATION**: compatibility/version guarantees become binding when an artifact is first published to the governed schema registry or explicitly marked `PUBLISHED`. After publication, breaking semantic or wire-format changes require a new major contract in accordance with `03h-schema-governance-and-registry.md`.

## Research

`docs/research/` contains supporting research and jurisdiction/source analysis. Research documents inform design decisions but do not override normative architecture contracts.

## Repository evolution

The repository will progressively add implementation POCs, model cards, scoring specifications, diagrams, test fixtures and CI validation around the architecture and executable contracts.

## Status

Parts I–III: **Draft / coherence-reviewed**. Contracts remain **DRAFT / PRE-PUBLICATION**. The Phase-1 spine has two working signal families (payment return and DPD). The full platform described in Parts I–III is not built; see `docs/architecture/09-completion-plan.md`.