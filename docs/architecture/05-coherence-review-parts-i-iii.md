# EWS 2.0 — Parts I–III Coherence Review

**Review status:** FINAL BLOCKER PASS  
**Branch:** `architecture/part-3-event-architecture`  
**Scope:** Part I foundation, Part II risk/feature/signal semantics, Part III event/streaming architecture, ADRs and executable schemas.

## 1. Exit criterion

Part III is ready for PR preparation when no unresolved BLOCKER remains across canonical semantics, schema lifecycle, event naming, source rights, temporal semantics, classification boundaries, Kafka guarantees, outbox ordering and replay/correction semantics.

## 2. Final blocker resolution register

| ID | Severity | Finding | Resolution | Status |
|---|---|---|---|---|
| C-01 | BLOCKER | Architecture blueprint retained market-specific assumptions in the global core. | Blueprint normalized to global source categories, jurisdiction adapters and India/US/UK reference implementations. | RESOLVED |
| C-02 | BLOCKER | Breaking draft schema edits under `v1` conflicted with published-contract compatibility language. | Repository now explicitly declares executable contracts DRAFT/PRE-PUBLICATION. Compatibility becomes binding at registry publication or explicit `PUBLISHED` lifecycle state; subsequent breaking changes require a new major contract. | RESOLVED |
| C-03 | BLOCKER | Source-rights vocabulary supported model training but signal policy could require model inference. | Added `modelInferenceAllowed`; source registry and signal-policy rights now distinguish analytics, LLM processing, model inference and model training. | RESOLVED |
| C-04 | BLOCKER | Outbox ordering guidance referenced aggregate sequence but persistence DDL did not carry it. | Added nullable `aggregate_sequence` to PostgreSQL/Oracle DDL, aggregate index and explicit propagation to canonical envelope. | RESOLVED |
| C-05 | BLOCKER | Avro namespaces were split between legacy `com.ews.*` and new `org.ewsfi.*`. | Active Part-III payload contracts normalized to `org.ewsfi.events.<domain>.v1`. | RESOLVED |
| C-06 | BLOCKER | Canonical envelope field `classification` could be confused with prudential/accounting classification state. | Renamed metadata concept to `dataClassification`; domain classification remains a separate namespaced state. | RESOLVED |
| C-07 | BLOCKER | External catalogue used `BANKRUPTCY_FILED` as an analytical signal despite global insolvency taxonomy. | Bankruptcy remains a jurisdiction-bearing legal observation and maps to `FORMAL_INSOLVENCY_PROCEEDING`; restructuring signal requires governed evidence/policy. | RESOLVED |
| C-08 | BLOCKER | Kafka topic catalogue omitted dedicated market and classification families used by the event architecture. | Added `ews.canonical.market` and `ews.derived.classification`; preserved classification separation from analytical signal/risk state. | RESOLVED |
| C-09 | BLOCKER | JSON feature-value contract allowed an unconstrained arbitrary object while Avro used typed values. | JSON feature value now has governed logical types and scalar/null constraints, with state/value validation; exact financial decimal wire representation remains authoritative in Avro/feature definition. | RESOLVED |
| C-10 | BLOCKER | Signal lifecycle schema treated `INSUFFICIENT_EVIDENCE` as both lifecycle status and disposition concept. | Removed it from lifecycle status; retained as a disposition/quality concept. | RESOLVED |
| C-11 | BLOCKER | Risk dimensions were free strings despite a canonical Part-II vocabulary. | Signal instance now constrains risk dimensions to the canonical vocabulary. | RESOLVED |
| C-12 | BLOCKER | Topic strategy cross-referenced a non-existent Kafka sizing filename. | Corrected references to `03i-production-kafka-topology-and-resilience.md`. | RESOLVED |

## 3. Major findings resolved or bounded

| ID | Severity | Finding | Resolution / boundary | Status |
|---|---|---|---|---|
| C-13 | MAJOR | Signal event payload lacked semantic scope/jurisdiction/source-rights/entity-resolution references present in canonical signal model. | Added the missing governance metadata to `SignalDetectedV1`. | RESOLVED |
| C-14 | MAJOR | Entity-resolution canonical target allowed arbitrary properties/types. | Closed the object and constrained canonical entity types. | RESOLVED |
| C-15 | MAJOR | India/US/UK could be read as the platform scope rather than reference implementations. | Part I and README explicitly define them as reference implementations/adapters to a jurisdiction-neutral core. | RESOLVED |
| C-16 | MAJOR | Kafka exactly-once terminology could imply database/workflow exactly-once. | Part III/outbox architecture explicitly limits Kafka transaction/EOS guarantees to Kafka processing scope and uses idempotency/outbox for cross-system outcomes. | RESOLVED |
| C-17 | MAJOR | Kafka was at risk of becoming the compliance archive. | Kafka retention is explicitly operational; long-term evidence/history is stored outside Kafka. | RESOLVED |
| C-18 | MAJOR | Replay/backfill could contaminate live state. | Execution modes and replay/correction design distinguish LIVE, recovery replay, rebuild, backtest and source backfill; production design requires isolated recovery capacity. | RESOLVED |
| C-19 | MAJOR | Schema identity could become coupled to Kafka topic naming. | Registry strategy is record/event-contract oriented with domain-family topics and concrete schema artifacts. | RESOLVED |
| C-20 | MAJOR | README no longer represented the repository architecture or schema lifecycle. | README rebuilt as the Parts I–III architecture index and contract lifecycle statement. | RESOLVED |

## 4. Intentionally retained design choices

These are not blockers:

- `ANALYTICAL_EWS` remains a classification namespace for the **official governed EWS state**, while signals and risk assessments remain analytical inputs. It must not be used as an alias for a signal lifecycle state.
- India-specific terms may remain in research, reference adapters, aliases/deprecation mappings and institution-specific policies. They are prohibited from silently defining global canonical semantics.
- `org.ewsfi` remains the draft schema namespace. Moving to an owned reverse-DNS namespace can be considered before first registry publication, but changing it after publication would be a major contract migration.
- Kafka partition counts, retention ranges and RTO/RPO values remain architecture targets/benchmark ranges, not untested production guarantees.
- JSON Schema and Avro serve different surfaces: JSON represents governed logical records/API persistence; Avro is the typed Kafka wire contract. Feature definitions remain authoritative for scale/unit/value semantics.

## 5. Non-blocking cleanup backlog

1. Add CI that parses every JSON/Avro schema and enforces contract lifecycle/compatibility.
2. Add schema fixtures and serialization round-trip tests.
3. Generate a machine-readable topic registry mapping event type -> schema artifact -> topic -> key strategy -> owner -> retention/security class.
4. Add shared schema artifacts/references for repeated envelope types once implementation begins.
5. Add automated terminology checks for deprecated canonical aliases.
6. Decide the permanent owned schema namespace before first production registry publication.
7. Add architecture diagrams generated from the normalized Parts I–III model.

## 6. Final disposition

**Unresolved BLOCKER count: 0.**

Parts I–III are semantically coherent enough to prepare the Part III pull request. Remaining items are implementation hardening, CI automation and pre-publication contract governance rather than architecture blockers.

No claim is made that production readiness is complete: performance benchmarks, failure tests, schema compatibility CI, DR rehearsal, security integration and implementation POCs remain required before production deployment.