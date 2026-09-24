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
- [ADR-011 — Schema Registry Baseline: Apicurio Registry](../architecture/adr/ADR-011-schema-registry-baseline-apicurio.md) (not in the original numbered list; added to record the Phase-1 skeleton's concrete registry choice)

## Planned ADRs (not yet written)

- ADR-006 — AI Gateway and model access
- ADR-007 — Hybrid AI deployment
- ADR-008 — Graph intelligence adoption
- ADR-009 — Feature-store strategy
- ADR-010 — Model governance platform

These five are Phase-2/3-scoped per `docs/architecture/06-gap-analysis-and-implementation-roadmap.md`
Section 7 and are intentionally not written yet -- they govern capabilities (AI Gateway, graph
intelligence, model governance) not present in the Phase-1 skeleton.

Technology-specific ADRs remain open until supported by workload analysis and research.
