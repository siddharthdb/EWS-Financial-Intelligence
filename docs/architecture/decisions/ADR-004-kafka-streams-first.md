# ADR-004 — Kafka Streams First for Phase-1 Event Processing

**Status:** Accepted  
**Date:** 2026-09-17

## Context

EWS 2.0 needs stateful event processing for rolling repayment/liquidity features, event-time windows, deduplication, feature revisions and deterministic signal policies. Apache Flink offers a richer dedicated streaming runtime, but Phase 1 is predominantly Kafka-sourced, Java/Spring-based and operationally focused on moderate joins/windows/aggregations.

Introducing Kafka Streams and Flink simultaneously would create two stateful streaming operating models before workloads demonstrate the need.

## Decision

Use **Kafka Streams as the Phase-1 default stream-processing runtime**.

Initial applications:

```text
ews-operational-feature-processor
ews-signal-policy-engine
```

Use Kafka Streams for:

- canonical Kafka event consumption;
- deduplication;
- keyed state;
- rolling/bucketed feature calculations;
- moderate joins and aggregations;
- feature event publication;
- deterministic signal policy evaluation;
- signal episode state.

Configure `processing.guarantee=exactly_once_v2` for material Kafka-to-Kafka stateful processing after operational validation.

## Flink escalation criteria

Evaluate/adopt Flink when concrete workloads require capabilities whose complexity or operability is materially better in a dedicated event-time engine, including:

- complex cross-domain event-time joins;
- heterogeneous lateness/watermark strategies;
- advanced CEP/pattern detection;
- very large stateful temporal joins;
- heterogeneous non-Kafka streaming sources;
- portfolio-scale stateful streaming requiring a dedicated processing platform.

Flink may coexist for those workloads without changing canonical event contracts.

## Consequences

### Positive

- fits existing Java/Spring engineering capability;
- Kafka remains both transport and state-recovery backbone;
- fewer Phase-1 platform components;
- simpler deployment/security/monitoring model;
- sufficient for the first operational EWS feature set.

### Trade-offs

- event-time/watermark semantics are less expressive than Flink for complex workloads;
- some rolling/correction patterns require careful state-store design;
- topology complexity must be monitored to avoid turning Streams into a substitute for a workload that belongs in Flink.

## Guardrail

`Kafka Streams first` is not `Kafka Streams forever`. Runtime selection is workload-driven. Domain events, feature definitions and signal contracts remain independent of the chosen stream processor.