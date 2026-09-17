# EWS 2.0 — Production Kafka Topology, Sizing and Resilience

**Status:** Draft / Part III production baseline  
**Scope:** Kafka backbone and Kafka Streams runtime  
**Sizing reference:** 10–50M financial events/day, ~1M external events/day, peak 5–10K events/sec

## 1. Decision summary

Phase-1 production uses a **KRaft-based Kafka cluster with at least three failure-domain-aware brokers, replication factor 3 for material topics, `min.insync.replicas=2`, and producers using `acks=all` + idempotence**.

Kafka is an operational event/replay platform, not the seven-to-ten-year compliance archive. Long-term evidence and analytical history live in governed object/analytical stores.

A second-site Kafka cluster is preferred for disaster recovery rather than a stretched synchronous Kafka cluster across high-latency WAN links.

## 2. Logical production topology

```text
                         +-----------------------+
                         |  KRaft controller     |
                         |  quorum               |
                         +-----------+-----------+
                                     |
            +------------------------+------------------------+
            |                        |                        |
       Broker A                  Broker B                  Broker C
    failure domain A          failure domain B          failure domain C
            |                        |                        |
            +------------------------+------------------------+
                                     |
             +-----------------------+-----------------------+
             |                       |                       |
        Producers              Kafka Streams             Consumers
   outbox/source adapters    feature/signal apps    evidence/projection/etc.
```

For small deployments controller and broker roles may be combined after failure testing. For larger/critical installations, dedicated controllers reduce blast radius and make controller capacity independent from broker workloads.

## 3. Durability baseline

Material event topics:

```text
replication.factor = 3
min.insync.replicas = 2
producer acks = all
enable.idempotence = true
unclean leader election = disabled
```

This deliberately trades availability for durability when fewer than two in-sync replicas are available. The outbox pattern allows EWS-owned business services to continue committing local business state/event intent while Kafka is unavailable, subject to outbox capacity controls.

Non-material telemetry topics may use different durability where documented.

## 4. Failure-domain placement

Use Kafka rack/failure-domain awareness so replicas for a partition are distributed across independent hosts/racks/zones where infrastructure permits.

Avoid placing three nominal brokers on the same physical failure domain and calling it HA.

For on-prem deployment, failure domains can map to separate VMware hosts/racks/power domains. For cloud deployment, map them to availability zones where supported.

## 5. Throughput baseline

Reference workload:

- 10M/day = ~116 events/sec average;
- 50M/day = ~579 events/sec average;
- plus ~1M/day external intelligence;
- peak design target = 5–10K events/sec;
- typical canonical event size assumption for capacity planning = 1–4 KB before broker-side compression;
- large documents never travel inline through Kafka.

Average rate is not the sizing constraint. Peak ingress, replication, stream repartitioning, replay catch-up and broker-loss operation drive capacity.

## 6. Storage sizing model

Use measured compressed bytes rather than record count alone.

```text
logical_ingress_per_day
  = events_per_day * measured_average_compressed_record_bytes

retained_logical_bytes
  = logical_ingress_per_day * retention_days

physical_replica_bytes
  = retained_logical_bytes * replication_factor

planned_disk_bytes
  = physical_replica_bytes
    * replay/repartition/changelog factor
    * operational headroom
```

Do not multiply every source event by an arbitrary worst-case 4 KB forever. Measure representative Avro + headers + compression during load testing.

### Illustrative planning envelope

At 51M events/day:

- 1 KB/event uncompressed is ~51 GB/day logical before compression;
- 2 KB/event is ~102 GB/day;
- 4 KB/event is ~204 GB/day.

At 7-day retention and RF=3, before compression/headroom/changelog/repartition effects, those correspond roughly to 1.1 TB, 2.1 TB and 4.3 TB of replicated log bytes.

These are planning examples, not production sizing numbers. Benchmarking decides final storage.

## 7. Retention classes

Recommended starting classes:

### R1 — high-volume immutable operational facts
Examples: account transactions, market trades.

Target local Kafka retention: 3–7 days initially, depending on replay SLA and downstream persistence.

### R2 — canonical business/risk events
Examples: facility, repayment, financial statement, relationship, external legal/company observations.

Target: 7–14 days initially.

### R3 — derived feature/signal/risk/decision events
Target: 14–30 days where replay/reconstruction value justifies it.

### R4 — current-state projections
Use compaction where the topic is explicitly a latest-state projection; do not convert immutable history topics into compacted state topics merely to save storage.

Final retention is workload/SLA-specific and must align with downstream archive recovery capability.

## 8. Tiered storage position

Kafka tiered storage is **not required for Phase 1**.

Current Apache Kafka tiered storage separates local and remote retention, but Apache Kafka requires a `RemoteStorageManager` implementation rather than shipping a production remote-storage implementation directly. Therefore adopting tiered storage introduces a provider/plugin dependency and must be evaluated as a separate platform decision.

Use governed object storage/evidence/history stores for long-term platform retention regardless of whether Kafka tiered storage is later adopted.

## 9. Partition strategy and initial planning

Partition counts are derived from:

- peak records/sec and bytes/sec;
- consumer processing cost;
- desired stream parallelism;
- key distribution/hot-key behaviour;
- replay catch-up SLA;
- broker count;
- state-store footprint;
- expected growth.

Do not choose a universal partition count.

Illustrative initial ranges for benchmark planning only:

| Topic family | Initial benchmark range |
|---|---:|
| account transactions | 24–72 |
| market observations | 24–72 |
| repayment/facility | 12–36 |
| external intelligence | 12–36 |
| financial statements | 6–18 |
| feature events | 24–72 |
| signal/risk events | 12–36 |
| decision/case events | 6–18 |

These are not committed production counts. Validate with load tests and key-cardinality analysis.

Increasing partitions can alter key-to-partition mapping; aggregate/source sequence checks remain required where ordering matters.

## 10. Kafka Streams capacity

Phase-1 applications:

```text
ews-operational-feature-processor
ews-signal-policy-engine
```

Parallelism is bounded by input/repartition topic partitions. Stateful stores require capacity for active state plus standby/recovery overhead.

Recommended baseline:

```text
processing.guarantee=exactly_once_v2
num.standby.replicas=1
```

where operationally validated.

Scale by adding instances/threads only after checking partition availability, state-store restore time, rebalance behaviour and downstream capacity.

## 11. Recovery and replay capacity

Production capacity must tolerate more than live traffic. Benchmark at least:

1. normal peak live load;
2. one broker unavailable;
3. Streams instance loss + state restoration;
4. backlog drain after Kafka/source outage;
5. source backfill;
6. historical projection/feature rebuild isolated from live processing.

A recovery job must not consume all broker I/O and violate live EWS latency.

Use quotas, separate consumer groups, bounded concurrency and where necessary dedicated replay windows/topics/clusters.

## 12. RTO / RPO classes

Define recovery objectives by capability rather than claiming one number for the whole platform.

Suggested architecture targets for validation:

| Capability | RPO target | RTO target |
|---|---:|---:|
| local broker/node failure | 0 committed Kafka records under RF3/minISR2 assumptions | minutes / automatic |
| Streams processor failure | 0 committed Kafka input; derived state reconstructed | minutes |
| complete primary Kafka cluster loss | asynchronous DR lag bound | 30–120 min depending deployment |
| evidence/object store | governed storage-specific | deployment-specific |
| official risk/decision DB | database HA/DR-specific | database HA/DR-specific |

These are architecture targets, not guarantees until infrastructure testing validates them.

## 13. Disaster recovery

Preferred design:

```text
Primary site / region
   Kafka + Streams
       |
       | asynchronous topic replication
       v
DR site / region
   Kafka standby
```

Do not stretch one synchronous Kafka cluster across distant data centres merely to call it DR.

Replicate only topics required for recovery and preserve schemas/configuration/ACLs separately through infrastructure-as-code and registry backup/replication strategy.

DR design must address:

- topic/config recreation;
- schema registry artifacts/versions;
- ACLs/service identities;
- consumer offsets or documented reset strategy;
- Streams application state reconstruction from replicated source/changelog topics where supported by the selected replication design;
- source connector checkpoints;
- DNS/service discovery cutover;
- split-brain prevention;
- failback/reconciliation.

Technology choice for cross-cluster replication is deployment-specific; MirrorMaker 2 or supported platform replication can be evaluated. Do not make application contracts depend on the replication product.

## 14. Security baseline

All production client/broker traffic is authenticated and encrypted unless a formally approved isolated exception exists.

Preferred controls:

- TLS for transport encryption;
- mTLS or SASL/SCRAM/OAUTHBEARER according to enterprise identity architecture;
- never use SASL/PLAIN without TLS;
- Kafka `StandardAuthorizer`/equivalent authorization under KRaft;
- least-privilege ACLs per producer/consumer/service identity;
- separate identities for source adapters, feature processors, signal engine, case/workflow consumers and operators;
- secrets from Vault/enterprise secret management, not repository/property files;
- certificate/credential rotation tested before expiry;
- restricted administrative listener/network access;
- audit changes to topics, ACLs, schemas and connector/source configuration.

## 15. ACL pattern

Example intent:

```text
repayment-adapter
  WRITE ews.canonical.repayment

operational-feature-processor
  READ  ews.canonical.* required inputs
  WRITE ews.derived.feature
  WRITE internal repartition/changelog topics

signal-policy-engine
  READ  ews.derived.feature
  WRITE ews.derived.signal

risk-correlation-processor
  READ  ews.derived.signal
  WRITE ews.derived.risk
```

Avoid broad `READ/WRITE ews.*` grants for application principals.

## 16. Network and payload controls

- brokers are not exposed to the public internet;
- source connectors terminate external connectivity in controlled integration zones;
- large documents live in object/evidence storage and events carry immutable references/hashes;
- licensed raw market datasets are not copied into unrestricted topics;
- message-size limits remain intentionally bounded;
- compression is enabled based on benchmark results and CPU/network trade-offs.

## 17. Observability

Monitor at minimum:

### Broker / cluster
- under-replicated/offline partitions;
- ISR shrink/expand;
- request latency/error rates;
- bytes/records in/out;
- disk utilization and disk latency;
- controller health;
- partition leadership distribution;
- produce/fetch throttling;
- authentication/authorization failures.

### Consumer / Streams
- consumer lag by group/topic/partition;
- processing latency;
- rebalance frequency/duration;
- state-store size;
- restore rate/time;
- dropped/late/quarantined records;
- transaction abort/failure rates;
- output feature/signal lag.

### Business freshness
Infrastructure health is insufficient. Track source-to-observation, observation-to-feature and feature-to-signal freshness SLOs.

## 18. Capacity alert thresholds

Operational policy should alert before saturation. Examples requiring environment-specific calibration:

- broker disk warning/critical thresholds;
- oldest outbox/source backlog age;
- sustained consumer lag versus freshness SLO;
- ISR below expected replica count;
- state-store restoration exceeding RTO;
- source checkpoint lag;
- schema/authorization failure rate;
- DR replication lag exceeding RPO.

## 19. Production validation gates

Before go-live:

1. representative Avro payload/compression benchmark;
2. peak + burst load test;
3. broker kill test;
4. controller failover test;
5. Streams process kill/restart/state restore;
6. network partition test;
7. Kafka outage with outbox accumulation and controlled drain;
8. source connector replay/checkpoint test;
9. duplicate delivery/idempotency test;
10. late/correction/reversal test;
11. schema compatibility rejection test;
12. ACL/credential rotation test;
13. DR replication/cutover/failback rehearsal;
14. evidence reconstruction of an approved risk decision after replay.

## 20. Phase-1 recommendation

Start with a deliberately conservative, operable topology:

```text
3+ Kafka brokers
KRaft
RF=3 / minISR=2 for material topics
acks=all + producer idempotence
rack/failure-domain awareness
Kafka Streams with one standby replica
TLS + enterprise authentication + least-privilege ACLs
7–30 day topic-specific operational retention
long-term history/evidence outside Kafka
asynchronous second-cluster DR where business criticality requires it
```

Scale brokers, partitions and storage from benchmark evidence rather than architectural aesthetics.