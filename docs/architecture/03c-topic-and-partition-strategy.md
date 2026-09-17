# EWS 2.0 — Kafka Topic, Key and Partition Strategy

**Status:** Draft / Part III

## 1. Rule

A Kafka key is selected from the ordering/state boundary, not from convenience. A topic groups events with compatible ownership, retention, throughput, security and consumer semantics.

## 2. Initial catalogue

| Topic | Primary key | Event examples | Retention mode | Notes |
|---|---|---|---|---|
| `ews.canonical.counterparty` | counterpartyId | master changed, status changed | delete + optional derived compacted projection | low/moderate volume |
| `ews.canonical.facility` | facilityId | limit changed, drawing power changed | delete | strict facility transition ordering |
| `ews.canonical.account-transaction` | accountId | credit/debit/payment return | delete | highest-volume corporate stream; avoid counterparty hot key |
| `ews.canonical.repayment` | facilityId | obligation due/paid/overdue state | delete | facility sequence important |
| `ews.canonical.trade-finance` | facility/instrumentId | LC devolved, BG invoked | delete | instrument/facility semantics |
| `ews.canonical.financial-statement` | counterpartyId | statement received/validated/restated | delete | low volume, long analytical relevance |
| `ews.canonical.collateral` | collateralId | valuation/security changed | delete | correlation to facilities via references |
| `ews.canonical.rating` | counterpartyId | rating/outlook/watch action | delete | preserve agency/source evidence |
| `ews.canonical.relationship` | sourcePartyId | relationship created/ended/revised | delete | graph processor must handle temporal edges |
| `ews.canonical.external-intelligence` | entityId | legal/regulatory/news fact | delete | source-quality/entity-resolution metadata required |
| `ews.derived.feature` | entityId or feature-specific composite key | feature updated/recalculated/stale | delete for history; separate compacted state topic if needed | don't overload history with latest-state semantics |
| `ews.derived.signal` | counterpartyId | detected/proposed/status/severity | delete | counterparty correlation ordering |
| `ews.derived.risk` | counterpartyId | proposed/approved risk changed | delete | audit history |
| `ews.derived.decision` | caseId or decision subject | analyst decision | delete | human governance |
| `ews.derived.case` | caseId | opened/assigned/escalated/closed | delete | workflow state transitions |

## 3. History versus state topics

Do not use one topic simultaneously as an immutable event history and a compacted latest-state table unless the semantics are deliberately designed for both.

Preferred pattern:

```text
ews.derived.feature              # immutable change/event history
ews.state.feature-current        # compacted latest value, if Kafka state distribution is needed
```

Likewise for counterparty/facility projections where a compacted state topic has a real consumer requirement.

## 4. Partition counts

Partition count is an operational capacity decision, not a semantic constant in source code. Estimate using:

- peak records/sec and bytes/sec;
- consumer processing cost;
- desired parallelism;
- hot-key distribution;
- broker count/storage;
- replay catch-up objective;
- expected growth;
- ordering implications.

Increasing partitions later can change the partition chosen for a key. Therefore consumers must rely on key-local ordering within the active partitioning epoch and domain sequence/version checks where ordering is material.

## 5. Hot keys

Corporate groups can have extremely uneven event volume. Do not key raw account transactions by counterparty merely to simplify later aggregation.

```text
account transaction -> key accountId
                    -> account-level feature
                    -> counterparty aggregation stage
                    -> key counterpartyId
```

This introduces a deliberate repartition boundary where counterparty aggregation is actually required.

## 6. Aggregate sequence

For state-transition events, include monotonic sequence/version from the authoritative aggregate where feasible:

```text
facilityId=F123
aggregateSequence=41
facility.limit.changed

facilityId=F123
aggregateSequence=42
facility.drawing_power.changed
```

Consumers can detect duplicates, gaps and stale transitions even across replay/repartition scenarios.

Do not manufacture a global sequence across unrelated aggregates.

## 7. Partition-key invariance

Changing the logical partition key is a breaking operational change even if the Avro value schema remains compatible. Event contract governance therefore reviews:

- topic;
- key type;
- key derivation;
- event semantic version;
- value schema.

Keys should normally be simple stable strings/identifiers rather than mutable serialized business objects.

## 8. Topic ownership

Each production topic has exactly one owning bounded context/platform team responsible for:

- accepted event types;
- schema compatibility;
- retention/security classification;
- partition/key policy;
- producer authorization;
- SLOs and operational runbook.

Multiple services may publish only when the topic contract explicitly supports that ownership model; canonical facts should preferably have a clear authoritative producer/normalizer.

## 9. Security

Topic ACLs follow least privilege. Producers receive write access only to owned topics; consumers receive read access only for required domains. Restricted/PII-bearing topics are separately classified and audited.

Sensitive source documents are referenced by evidence/document ID rather than embedded in Kafka events.

## 10. Initial recommendation

Start with domain-family topics above and size partitions from measured POC throughput plus replay tests. Avoid both extremes:

- one enterprise mega-topic, which couples unrelated retention/security/throughput semantics;
- one topic per event type, which creates excessive operational/schema overhead.

Part III sizing will assign initial partition/retention values after applying the assumed EWS workload and explicit recovery objectives.
