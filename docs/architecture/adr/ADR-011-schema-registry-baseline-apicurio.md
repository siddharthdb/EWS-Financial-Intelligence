# ADR-011 — Schema Registry Baseline: Apicurio Registry

**Status:** Accepted for Phase-1 skeleton
**Decision date:** 2026-09-24

**Note on numbering:** ADR-011 was not in the original planned list in `docs/adr/README.md` (which runs ADR-001 through ADR-010). It is added here because the Phase-1 skeleton's `docker-compose.yml` commits to a concrete schema registry product, and `docs/architecture/03h-schema-governance-and-registry.md` only states a "preferred baseline" without formally deciding it. `docs/adr/README.md` should be updated to list ADR-011 alongside the existing planned ADRs.

## Context

`03-event-architecture.md` §10 and `03h-schema-governance-and-registry.md` establish Avro + Schema Registry as the recommended production default for Kafka wire-format governance, with breaking semantic changes requiring a new event major version. `03h` names Apicurio Registry as a "preferred open/on-prem baseline" but does not commit to it as a binding decision. The Phase-1 skeleton's `docker-compose.yml` needs a concrete, runnable registry service, so this ADR converts that soft preference into an explicit, recorded choice — consistent with ADR-003's precedent of recording concrete infrastructure choices, not just abstract patterns.

## Decision

Adopt **Apicurio Registry** (Confluent-compatible API surface enabled) as the schema registry for local development and as the Phase-1 target production candidate. The Phase-1 skeleton runs it via `apicurio/apicurio-registry:3.0.6` in `docker-compose.yml`, using its bundled in-memory/H2 storage profile for local dev only. Moving to Postgres-backed Apicurio storage (or an equivalent durable backend) is required before anything resembling production and is explicitly called out as a follow-up, not addressed in this skeleton.

## Alternatives Considered

1. **Confluent Schema Registry.** Rejected as the default: licensing terms couple the platform to Confluent's ecosystem and commercial terms in a way that conflicts with the architecture's general preference for open/on-prem-portable components (implicit in `03h`'s own framing of Apicurio as "open/on-prem baseline" versus not naming Confluent's registry at all). Confluent-compatible API mode is still enabled on Apicurio specifically to preserve tooling portability if this decision is revisited.
2. **AWS Glue Schema Registry / a cloud-managed registry.** Rejected for Phase-1: the architecture's hybrid deployment principle (`01-architecture-blueprint.md` §21) keeps core financial records and control-plane infrastructure within the institution's approved trust boundary by default; a cloud-managed registry is a deployment option to revisit later, not a Phase-1 default, especially before any cloud deployment target is chosen.
3. **No schema registry; embed schema IDs/versions directly in event payloads without centralized governance.** Rejected: directly undermines `EA-08` ("Schemas are governed contracts") and the entire schema-governance design in `03h-schema-governance-and-registry.md`, which assumes a registry as the mechanism for compatibility enforcement.

## Rationale

Apicurio Registry is open-source, self-hostable (matching the hybrid deployment principle), supports the Confluent-compatible API that most Kafka tooling (including `kafka-ui` in the local dev stack) expects, and was already the document's own stated preference — this ADR simply converts that preference into a binding, buildable decision so the Phase-1 skeleton's docker-compose stack has a concrete service rather than a placeholder.

## Consequences

- `docker-compose.yml`'s `schema-registry` service and any future schema-registration tooling in `ews-schemas` or CI should target Apicurio's API (Confluent-compatible surface) specifically.
- The in-memory/H2 storage profile used in local dev is explicitly not production-durable; any registered schema in a dev environment is lost on container restart. This is acceptable for the skeleton stage but must be revisited before Phase 1 implementation work relies on registry state persisting.
- The permanent owned schema namespace decision (currently draft `org.ewsfi`, flagged as still open in `05-coherence-review-parts-i-iii.md` §4 and the gap-analysis roadmap) is a separate, still-unresolved decision from the registry *product* choice made here — this ADR does not resolve schema namespace ownership.

## Risks

- Apicurio Registry has a smaller ecosystem/community than Confluent's registry; some third-party Kafka tooling may assume Confluent-specific registry behavior beyond the compatible API surface, requiring workarounds discovered only during real integration.
- If the platform later needs a fully managed, high-availability registry without operating the infrastructure itself, this decision would need revisiting alongside the broader hybrid-deployment/cloud strategy.

## Review Trigger

Revisit if: (a) Phase-1 implementation work surfaces concrete Apicurio limitations (missing compatibility rules, API gaps) that block schema governance requirements in `03h-schema-governance-and-registry.md`; or (b) the platform's deployment target moves toward a managed cloud environment where a cloud-native registry becomes the pragmatic default.
