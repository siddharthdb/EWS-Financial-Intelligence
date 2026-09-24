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
- [ADR-003 — Application-Managed Transactional Outbox](docs/architecture/adr/ADR-003-application-managed-transactional-outbox.md)
- [ADR-004 — Kafka Streams First](docs/architecture/adr/ADR-004-kafka-streams-first.md)

### Part IV — assessment
- [Gap Analysis and Implementation Roadmap](docs/architecture/06-gap-analysis-and-implementation-roadmap.md)

## Executable contracts

Machine-readable contracts are under `schemas/` for events, features, signals, classifications, identity/entity resolution and source governance. Contracts are currently **DRAFT / PRE-PUBLICATION**: compatibility/version guarantees become binding when an artifact is first published to the governed schema registry or explicitly marked `PUBLISHED`. After publication, breaking semantic or wire-format changes require a new major contract in accordance with `03h-schema-governance-and-registry.md`.

## Research

`docs/research/` contains supporting research and jurisdiction/source analysis. Research documents inform design decisions but do not override normative architecture contracts.

## Repository evolution

The repository will progressively add implementation POCs, model cards, scoring specifications, diagrams, test fixtures and CI validation around the architecture and executable contracts.

## Status

Parts I–III: **Draft / coherence-reviewed**. Production implementation and schema publication have not yet established backward-compatibility obligations for the draft contracts.