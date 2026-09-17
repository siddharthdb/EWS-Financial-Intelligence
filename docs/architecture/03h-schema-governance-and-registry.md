# EWS 2.0 — Schema Governance and Registry Strategy

**Status:** Draft / Part III  
**Decision direction:** portable record-oriented schema governance; Apicurio-compatible baseline; no vendor-specific subject naming in domain semantics

## 1. Problem

Part III deliberately uses domain-family Kafka topics such as `ews.canonical.external-intelligence` and `ews.derived.feature`. These topics can contain multiple event record types. A registry design that assumes one evolving value schema per topic can therefore create false compatibility conflicts between unrelated records.

## 2. Decision

Schema identity is **record/event-contract oriented**, not topic-name oriented.

```text
Kafka topic = routing / retention / ownership / throughput boundary
Schema artifact = typed event contract and its evolution history
```

Do not make the Kafka topic name the canonical schema identity.

## 3. Registry portability

The architecture uses registry-neutral concepts:

```text
schemaArtifactId
schemaVersion
schemaType
compatibilityMode
references[]
```

Apicurio Registry is the preferred open/on-prem baseline because it supports Avro, JSON Schema and Protobuf artifacts, compatibility rules and cross-artifact references, while also exposing Confluent-compatible APIs. Confluent Schema Registry remains a supported deployment choice where licensed/standardized by the institution.

Applications must not embed vendor-specific subject naming into domain event names or payloads.

## 4. Artifact naming

Recommended logical groups:

```text
ewsfi.common
ewsfi.canonical
ewsfi.feature
ewsfi.signal
ewsfi.risk
ewsfi.classification
ewsfi.identity
ewsfi.external
```

Recommended artifact IDs use the fully qualified record/event name, for example:

```text
org.ewsfi.events.feature.v1.FeatureValueUpdatedV1
org.ewsfi.events.external.v1.SecurityInterestCreatedV1
org.ewsfi.events.risk.v1.RiskAssessmentProposedV1
```

Topic mappings live in deployment/configuration metadata rather than schema identity.

## 5. Shared schema references

Common records should be registered once and referenced rather than copied indefinitely:

```text
EntityReference
SourceReference
JurisdictionContext
MonetaryAmount
EvidenceReference
DataClassification
ExecutionMetadata
```

Avro cross-schema references use namespace-qualified named types plus registry reference metadata. Build tooling must pre-register referenced common artifacts before dependent event artifacts.

## 6. Compatibility

Default event-contract policy:

```text
BACKWARD_TRANSITIVE
```

for ordinary event evolution, because new consumers must remain able to read retained historical events across versions.

Use `FULL_TRANSITIVE` selectively for contracts where old and new producers/consumers must coexist bidirectionally and the stronger restriction is operationally justified.

Breaking semantic changes require a new major event contract rather than gaming schema compatibility.

Typical compatible evolution:
- add optional/defaulted field;
- add metadata that old consumers can ignore;
- preserve existing field meaning.

Typical breaking semantic evolution:
- reinterpret a field;
- change units/scale meaning;
- change entity grain;
- change legal meaning;
- change an existing field type incompatibly.

## 7. Heterogeneous domain-family topics

The platform rejects a single mega-union schema containing every possible event type. That creates central coupling and makes independent bounded-context evolution difficult.

Preferred approach:

```text
Domain-family Kafka topic
        |
        +-- Record A -> Artifact A
        +-- Record B -> Artifact B
        +-- Record C -> Artifact C
```

Serializer/registry integration resolves the concrete record artifact. Consumers either subscribe to known record types or route using the canonical envelope/event type before typed deserialization, depending on implementation.

A deployment using Confluent serializers may use a record-name-oriented strategy internally, but that is an adapter/configuration concern rather than a canonical architecture dependency.

## 8. Envelope composition

The canonical envelope is a shared metadata contract. Domain event schemas should compose/reference common envelope types rather than repeatedly redefining semantically identical metadata.

Do not serialize the payload as opaque JSON/bytes merely to make heterogeneous topics easy. That would weaken typing, compatibility checks and code generation.

## 9. Feature values

Financial numeric values must not use binary floating point where exact decimal semantics matter. `FeatureValueUpdatedV1` therefore uses Avro decimal with precision 38 / scale 12 for decimal-valued features and explicit typed alternatives for integer, boolean, string, date and timestamp.

Application validation enforces that exactly the value field corresponding to `valueType` is populated; Avro alone does not express every cross-field business constraint.

## 10. Build-time governance

CI should:
1. validate schema syntax;
2. resolve all references;
3. run compatibility against the registry or a controlled compatibility test set;
4. generate SpecificRecord classes for JVM producers/consumers where appropriate;
5. run serialization/deserialization tests against retained prior schema versions;
6. fail builds on incompatible changes without an explicit major-version migration;
7. validate domain rules not expressible by Avro separately.

## 11. Registry availability

Producers and consumers should cache resolved schemas/IDs appropriately. Registry failure must not automatically imply loss of already-known schemas. New/unseen schema publication or resolution can fail closed according to service criticality.

The registry itself requires HA, backup and disaster-recovery procedures; schema metadata is production control-plane state.

## 12. Security

Registry access is authenticated and authorized. Production schema registration is CI/CD controlled; ordinary application runtime identities should not freely mutate schemas. Audit schema publication, compatibility changes and rule changes.

## 13. Migration portability

Registry export/migration procedures retain:
- artifact identity;
- versions;
- references;
- compatibility policy;
- metadata/labels;
- lifecycle state.

Wire-format and serializer choice must be documented separately because changing registry products is easier when domain identity is not tied to proprietary subject semantics.

## 14. Consequence

This strategy preserves domain-family topics while allowing independent typed contracts and avoids choosing between `topic-per-event-type` explosion and a vendor-specific mega-topic schema trick.