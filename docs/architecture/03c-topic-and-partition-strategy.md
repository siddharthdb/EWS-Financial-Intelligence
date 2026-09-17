# EWS 2.0 — Kafka Topic, Key and Partition Strategy

**Status:** Draft / Part III

## 1. Rule

A Kafka key is selected from the ordering/state boundary, not from convenience. A topic groups events with compatible ownership, retention, throughput, security and consumer semantics.

## 2. Initial catalogue

| Topic | Primary key | Event examples | Retention mode | Notes |
|---|---|---|---|---|
| `ews.canonical.counterparty` | counterpartyId | master/status changed | delete + optional compacted projection | low/moderate volume |
| `ews.canonical.facility` | facilityId | limit/capacity changed | delete | strict facility transition ordering |
| `ews.canonical.account-transaction` | accountId | credit/debit/payment return | delete | highest-volume corporate stream; avoid counterparty hot key |
| `ews.canonical.repayment` | facilityId | obligation due/paid/DPD changed | delete | facility sequence important |
| `ews.canonical.trade-finance` | facility/instrumentId | LC devolved, guarantee invoked | delete | instrument/facility semantics |
| `ews.canonical.financial-statement` | counterpartyId | statement filed/received/validated/restated | delete | long analytical relevance |
| `ews.canonical.collateral` | collateralId | valuation/security changed | delete | facilities linked by references |
| `ews.canonical.rating` | counterpartyId | rating/outlook/watch action | delete | preserve provider/source evidence |
| `ews.canonical.relationship` | sourcePartyId | relationship created/ended/revised | delete | temporal graph edges |
| `ews.canonical.external-intelligence` | entityId | legal/regulatory/news/filing fact | delete | source-rights and resolution metadata required |
| `ews.canonical.market` | securityId | bond trade/price/yield/reference observation | short delete retention | high-volume facts become issuer features downstream |
| `ews.derived.feature` | entityId or feature-specific composite | feature updated/recalculated/stale | delete history | separate current-state topic where required |
| `ews.derived.signal` | counterpartyId | detected/proposed/status/severity | delete | analytical signal history |
| `ews.derived.risk` | counterpartyId | proposed/approved risk changed | delete | audit history |
| `ews.derived.classification` | entityId | classification proposed/approved/changed | delete | namespace is carried in payload; separate from EWS signal state |
| `ews.derived.decision` | caseId or decision subject | analyst/governance decision | delete | human governance |
| `ews.derived.case` | caseId | opened/assigned/escalated/closed | delete | workflow transitions |

## 3. History versus state topics

Do not use one topic simultaneously as immutable history and a compacted latest-state table unless deliberately designed for both.

```text
ews.derived.feature
  -> immutable change history

ews.state.feature-current
  -> compacted latest value only when Kafka state distribution is required
```

The same rule applies to counterparty, facility, signal, risk and classification projections.

## 4. Partition counts

Partition count is an operational capacity decision, not a semantic constant. Size from peak records/sec and bytes/sec, consumer processing cost, desired parallelism, hot-key distribution, broker/storage capacity, replay catch-up objective, ordering constraints and expected growth.

Increasing partitions can change the partition selected for a key. Consumers therefore use key-local ordering plus domain sequence/version checks where ordering is material.

Initial sizing and retention ranges are defined in `03i-production-kafka-topology-and-sizing.md`; production values remain benchmark-validated deployment configuration.

## 5. Hot keys

Do not key raw account transactions by counterparty merely to simplify later aggregation.

```text
account transaction -> key accountId
                    -> account-level feature
                    -> counterparty aggregation/repartition
                    -> key counterpartyId
```

## 6. Aggregate sequence

For state transitions include a monotonic authoritative aggregate sequence where feasible. Consumers can detect duplicates, gaps and stale transitions across replay/repartition scenarios. Do not manufacture a global sequence across unrelated aggregates.

## 7. Partition-key invariance

Changing logical partition key is a breaking operational change even if the Avro value remains compatible. Contract governance reviews topic, key type, key derivation, event semantic version and value schema together.

## 8. Topic ownership

Each production topic has one accountable bounded-context/platform owner for accepted record types, schema compatibility, retention/security, partition/key policy, producer authorization, SLO and runbook. Canonical facts should have a clear authoritative producer or normalizer.

## 9. Security

ACLs follow least privilege. Producers write only owned topics; consumers read only required domains. Restricted/PII-bearing topics are classified and audited. Sensitive documents are referenced by evidence/document ID rather than embedded in Kafka events.

## 10. Recommendation

Use domain-family topics with independent concrete record schemas. Avoid both one enterprise mega-topic and one topic per event type. `03i-production-kafka-topology-and-sizing.md` owns the initial production sizing, durability, retention, HA and DR recommendations; this document owns semantic topic/key boundaries.