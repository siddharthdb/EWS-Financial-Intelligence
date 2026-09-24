# ADR-001 — Event-Driven Architecture as the Platform Backbone

**Status:** Accepted for Part I/III baseline
**Decision date:** 2026-09-24

## Context

The platform must continuously observe counterparties across many internal and external sources (`docs/architecture/01-architecture-blueprint.md` §3), preserve point-in-time evidence and knowledge, and propagate facts through feature computation, signal detection, correlation and human validation without those stages being tightly coupled request/response services. `01-architecture-blueprint.md` §25 (the Phase-1 spine) and `03-event-architecture.md` both already assume a Kafka-centered event backbone; this ADR records that assumption as a deliberate, load-bearing decision rather than an implicit default, since the Phase-1 skeleton (`platform/`, `services/`) is now being scaffolded directly against it.

## Decision

Adopt an **event-driven architecture** as the platform's structural backbone: canonical observations/domain events flow through Kafka (`03-event-architecture.md` §3, §8) from source adapters and EWS-owned services, are processed by stateful stream applications into governed features and signals, and are consumed by downstream risk/case/experience services. Services communicate primarily through governed, versioned events rather than synchronous service-to-service calls for cross-domain propagation. Synchronous APIs remain appropriate for read paths and command/query interactions within a bounded context (e.g. the Experience API), per `EA-01` ("Commands and queries are separate").

## Alternatives Considered

1. **Synchronous request/response integration (service mesh of REST/RPC calls).** Rejected: creates tight temporal coupling between ingestion, feature computation and signal detection, makes replay/backfill/point-in-time reconstruction (a hard architectural requirement, `02-canonical-risk-model.md` §6) nearly impossible, and does not naturally support the "what did the institution know at time T" audit invariant (`01-architecture-blueprint.md` §22).
2. **Batch ETL / scheduled pipelines only.** Rejected as the primary mechanism: too slow for early-warning use cases where lead time matters (`04-signal-taxonomy.md` §20 explicitly lists lead time as an evaluation metric), though scheduled/bulk acquisition modes remain valid for specific sources (`01-architecture-blueprint.md` §4).
3. **CDC/Debezium as the default propagation mechanism for EWS-owned services.** Rejected as the default; ADR-003 already decided this in favor of an application-managed transactional outbox, with CDC retained only as an optional adapter for systems EWS does not own.

## Rationale

An event backbone is the only mechanism among the alternatives that simultaneously supports: (a) decoupled, independently scalable stages of the OBSERVE→UNDERSTAND→DETECT→CORRELATE→PREDICT→PROPOSE loop (`00-vision-and-principles.md`); (b) durable replay for recovery, feature rebuild and counterfactual backtest (`03-event-architecture.md` §16); (c) bitemporal point-in-time reconstruction; and (d) the audit invariant chaining evidence through to approved risk/classification state. This is also the design every subsequent architecture document already assumes, so treating it as anything other than foundational would require rewriting Parts II and III.

## Consequences

- Every EWS-owned service that mutates business state and needs to propagate a fact must implement the outbox pattern (ADR-003) rather than calling downstream services directly.
- Ordering guarantees are scoped to a partition, never global (`EA-04`); consumers and stream topologies must be designed accordingly.
- The Phase-1 skeleton's module boundaries (`services/ews-feature-processor`, `services/ews-signal-policy-engine`) are Kafka Streams applications by construction, not incidental technology choices.
- Operational complexity increases relative to a simpler synchronous system: the team now owns Kafka cluster operations, schema registry governance, and consumer idempotency across every service.

## Risks

- Kafka operational maturity (sizing, HA, security) is still a target/benchmark per `03i-production-kafka-topology-and-resilience.md`, not a validated production guarantee; under-provisioning or misconfiguration could bottleneck the whole platform.
- Event-driven systems are harder to debug end-to-end than synchronous call chains; tracing (`correlationId`/`causationId`/`traceId` in the canonical envelope) must be consistently propagated or this risk materializes as operational blind spots.
- Overuse of events for interactions that are genuinely request/response (e.g. simple lookups) would add unnecessary latency and complexity; `EA-01`'s command/query separation exists specifically to guard against this.

## Review Trigger

Revisit if: (a) Kafka operational cost/complexity proves unsustainable for the team's actual scale before Phase-1 delivers real traffic; (b) a workload emerges that is fundamentally synchronous and time-critical in a way events cannot serve (e.g. sub-100ms interactive decisioning); or (c) production benchmarking under `03i-production-kafka-topology-and-resilience.md` reveals the chosen topology cannot meet the platform's actual latency/throughput requirements.
