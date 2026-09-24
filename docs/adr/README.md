# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for consequential EWS Financial Intelligence design decisions.

## ADR format

Each ADR should contain:

- Status
- Context
- Decision
- Alternatives Considered
- Rationale
- Consequences
- Risks
- Review Trigger

## Written ADRs

- [ADR-001 — Event-Driven Architecture as the Platform Backbone](../architecture/adr/ADR-001-event-driven-architecture.md)
- [ADR-002 — Human-Validated Formal EWS](../architecture/adr/ADR-002-human-validated-formal-ews.md)
- [ADR-003 — Application-Managed Transactional Outbox](../architecture/adr/ADR-003-application-managed-transactional-outbox.md)
- [ADR-004 — Kafka Streams First](../architecture/adr/ADR-004-kafka-streams-first.md)
- [ADR-005 — Evidence-First AI Architecture](../architecture/adr/ADR-005-evidence-first-ai-architecture.md)
- [ADR-006 — AI Gateway and Model Access](../architecture/adr/ADR-006-ai-gateway-and-model-access.md) (architecture decision only -- deliberately does not select a foundation-model provider; see the ADR's Consequences)
- [ADR-008 — Graph Intelligence Adoption](../architecture/adr/ADR-008-graph-intelligence-adoption.md) (relationship representation/sequencing decision only -- no relationship data source or graph-method signal exists yet; see the ADR's Consequences)
- [ADR-010 — Model Governance Platform](../architecture/adr/ADR-010-model-governance-platform.md) (architecture/registry contract only -- no model exists yet to register; see the ADR's Consequences)
- [ADR-011 — Schema Registry Baseline: Apicurio Registry](../architecture/adr/ADR-011-schema-registry-baseline-apicurio.md) (not in the original numbered list; added to record the Phase-1 skeleton's concrete registry choice)

## Planned ADRs (not yet written)

- ADR-007 — Hybrid AI deployment
- ADR-009 — Feature-store strategy

These two are Phase-2/3-scoped per `docs/architecture/06-gap-analysis-and-implementation-roadmap.md`
Section 7 and are intentionally not written yet -- ADR-007 depends on ADR-006's gateway shape being
settled first, and ADR-009 (feature-store strategy) has no equivalent already-settled sequencing rule
in the blueprint the way ADR-008's graph-database question did (`01-architecture-blueprint.md` §9),
so it needs its own research pass before a decision can be grounded rather than invented.

Technology-specific ADRs remain open until supported by workload analysis and research.
