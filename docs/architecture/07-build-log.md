# EWS 2.0 — Build Log

**Status:** Living document — append-only, newest entry at the top
**Purpose:** A dated, chronological record of every implementation session: what was built and why,
which files changed, and exactly how it was verified. Paired with
`docs/architecture/08-roadmap-progress-tracker.md`, which tracks *what's left*; this file records
*what happened*. Every entry should let a future reader (human or another Claude session) understand
a change without re-deriving it from the diff alone.

## Entry format

```
## YYYY-MM-DD — <short title>

**Roadmap items:** <tracker row IDs this entry completes, e.g. 1.4, 1.5>
**What:** <what was built, in plain terms>
**Why:** <which spec/doc/ADR this implements, and the design reasoning>
**Files:** <key files touched, not an exhaustive diff>
**Verification:** <exact commands run and their outcome>
**Follow-ups:** <anything deliberately deferred, and why>
```

---

## 2026-09-24 — Machine-readable topic registry

**Roadmap items:** 0.8

**What:** Added `docs/architecture/topic-registry.json`, a single authoritative machine-readable
file mapping every event type named across the architecture docs to its topic, key field, schema
artifact (where one exists), owning service, and a default data-classification tag. Populated from
`03c-topic-and-partition-strategy.md` (18 topics: 11 `ews.canonical.*`, 6 `ews.derived.*`, plus
`ews.state.feature-current`) and `03d-phase1-event-catalogue.md` (Wave A/B/C event catalogue). Only
5 of the ~48 cataloged event types have an actual schema artifact today (the ones this session's
payment-return slice implemented); every other entry has `schemaArtifact: null`, an honest
statement that the contract doesn't exist yet rather than a fabricated placeholder path.

**Why:** This is coherence-review backlog item 3
(`05-coherence-review-parts-i-iii.md` §5: "Generate a machine-readable topic registry mapping event
type -> schema artifact -> topic -> key strategy -> owner -> retention/security class"). It gives
any future source adapter or signal-detection work a single place to look up "what topic does this
event go to, keyed by what, owned by which service" instead of re-deriving it from prose across
three different architecture documents each time.

**A documented limitation:** `dataClassification` values are informative defaults (`INTERNAL` for
internal facts, `CONFIDENTIAL` for externally-sourced facts, `RESTRICTED` for classification state),
not real per-source rights data — `schemas/sources/source-registry-v1.schema.json` has no populated
source entries yet. Said explicitly in the registry's own `notesOnDataClassification` field rather
than presented as authoritative.

**Files:** `docs/architecture/topic-registry.json` (new),
`test/ews-event-contracts-test/src/test/java/org/ewsfi/contracts/TopicRegistryTest.java` (new),
`test/ews-event-contracts-test/pom.xml` (added `jackson-databind`).

**Verification:** New `TopicRegistryTest` (3 tests, JVM-native, no broker/DB needed — same style as
the existing `AvroSchemaParseTest`/`JsonSchemaParseTest`) checks: every event's `topic` is one of
the declared topics (catches a typo'd or invented topic name), every non-null `schemaArtifact` path
resolves to a real file under `schemas/` (catches a stale or invented path), and there are no
duplicate `eventType` entries. `mvn -B -ntp verify` green across all 14 modules, 22 tests total.

---

## 2026-09-24 — Automated deprecated-terminology check

**Roadmap items:** 0.10

**What:** Added `docs/architecture/deprecated-signal-aliases.json`, a machine-readable extraction of
the 17-row "Alias/deprecation mapping" table in `04-signal-taxonomy.md` §17 (e.g. `BG_INVOCATION` →
rename `GUARANTEE_INVOCATION`, `INSOLVENCY_PROCEEDING_FILED` → normalize
`FORMAL_INSOLVENCY_PROCEEDING`), cross-checked line-by-line against the source table before writing
the test. New `DeprecatedTerminologyTest` (JVM-native, no broker/DB) scans every `.java` file under
`services/`/`platform/`, every `.avsc`/`.schema.json` under `schemas/`, and the topic registry for
any deprecated alias appearing as a live quoted string literal, and fails the build if one is found
— excluding `04-signal-taxonomy.md` itself and the new registry file, both of which legitimately
name the aliases as historical/mapping context rather than live usage.

**Why:** Coherence-review backlog item 5 (`05-coherence-review-parts-i-iii.md` §5: "Add automated
terminology checks for deprecated canonical aliases"). Nothing previously prevented a future
increment — autonomous or manual — from accidentally reintroducing a deprecated alias (e.g. writing
`"BG_INVOCATION"` instead of `"GUARANTEE_INVOCATION"` in a new signal policy) as a live identifier.
This closes that gap with an enforced, automated check rather than relying on someone remembering
to consult the taxonomy doc's alias table by hand.

**Files:** `docs/architecture/deprecated-signal-aliases.json` (new),
`test/ews-event-contracts-test/src/test/java/org/ewsfi/contracts/DeprecatedTerminologyTest.java`
(new).

**Verification:** Confirmed the check passes today (no deprecated alias is live-used anywhere in
the codebase — the only implemented signal type is `REPEATED_PAYMENT_RETURN`, not an alias). Then,
per this project's established "prove the test actually tests something" bar, temporarily appended
a `"BG_INVOCATION"` string literal to a scratch line in `IngestionServiceConfig.java`, reran the
test, and confirmed it failed with a precise, correct violation message naming the file and alias;
reverted the scratch change (`git checkout --`) and confirmed the working tree was clean again
before committing. `mvn -B -ntp verify` green across all 14 modules, 23 tests total.

---

## 2026-09-24 — Shared schema artifact for repeated `semanticScope` enum

**Roadmap items:** 0.9 (partial)

**What:** Added `schemas/common/semantic-scope-v1.schema.json`, a single shared JSON Schema
definition for the `GLOBAL_CORE`/`GLOBAL_PRODUCT_SPECIFIC`/`JURISDICTION_EXTENSION`/
`INSTITUTION_POLICY_SPECIFIC` portability-class enum, and updated its 4 identical occurrences
(`signal-instance-v1.schema.json`, `signal-policy-v1.schema.json`, `feature-definition-v1.schema.json`,
and the nested `definition.semanticScope` in `feature-value-v1.schema.json`) to `$ref` it instead of
each independently redefining the same 4-symbol enum inline.

**Why:** Coherence-review backlog item 4 (`05-coherence-review-parts-i-iii.md` §5: "Add shared
schema artifacts/references for repeated envelope types once implementation begins"). `semanticScope`
was chosen as the first (and, this session, only) extraction because it is byte-for-byte identical
in all 4 occurrences with no nullability or symbol-set variance — a safe, unambiguous case. Verified
this wasn't just a cosmetic change: `ews-schemas`'s `jsonschema2pojo` codegen now generates one real
shared `org.ewsfi.contracts.common.SemanticScopeV1Schema` type, and all 4 consumer classes
(`SignalInstanceV1Schema`, `SignalPolicyV1Schema`, `FeatureDefinitionV1Schema`, the nested
`Definition` class) reference that same generated type rather than each getting their own duplicate
generated enum.

**A real bug found and fixed by testing this before rolling it out further:** every schema in this
repo declares a fictional `https://ews-financial-intelligence/...` `$id` (the project's own
convention, not a real host). Per the JSON Schema spec, a relative `$ref` resolves against the
containing schema's own `$id`, not against how the schema happened to be loaded — so the new
`$ref: "../common/semantic-scope-v1.schema.json"` resolved to an absolute
`https://ews-financial-intelligence/schemas/common/semantic-scope-v1.schema.json`, which the
existing `JsonSchemaParseTest` (networknt validator) then tried to fetch over the network and
failed. Fixed by configuring a `SchemaMapper` in the test that rewrites that fictional host prefix
back to the real local `schemas/` directory (`file://` URI), keeping resolution fully offline —
consistent with every other contract-validation step in this project. Confirmed the Python CI
step (`Draft202012Validator.check_schema`) was unaffected, since schema-validity checking doesn't
resolve `$ref` targets at all (only instance validation would).

**Why this session did *not* extract more fields:** `qualityState`/`dataQuality.state`
(`COMPLETE|PARTIAL|STALE|CONFLICTED|UNVERIFIED|INSUFFICIENT`) and `sourceAuthorityTier`
(`T1|T2|T3|T4`) are also repeated across multiple files, but with nullability variance between
occurrences (some are `["string","null"]` with a `null` enum member, others are plain `"string"`).
Naively sharing those would need either two ref variants per field or an `anyOf`-with-null wrapper
at each use site — a real design decision, not a mechanical extraction, and rushing it risked
either a broken contract or a silently weakened one. Left for a future, dedicated increment rather
than attempted under time pressure in the same pass as the first (already-verified-safe) extraction.
Also confirmed during this work that several *entity type* enums that looked superficially similar
across files (`signal-instance`, `feature-value`, `classification-state`, `entity-resolution`) are
**not** true duplicates — each deliberately scopes a different subset of valid entity kinds for its
context, and DRY-ing them into one shared enum would silently widen validation in at least 3 of the
4 files. Left alone; documented here so a future pass doesn't "fix" this into a regression.

**Files:** `schemas/common/semantic-scope-v1.schema.json` (new),
`schemas/signals/signal-instance-v1.schema.json`, `schemas/signals/signal-policy-v1.schema.json`,
`schemas/features/feature-definition-v1.schema.json`, `schemas/features/feature-value-v1.schema.json`
(all: one field each changed from inline enum to `$ref`),
`test/ews-event-contracts-test/src/test/java/org/ewsfi/contracts/JsonSchemaParseTest.java` (schema
mapper fix).

**Verification:** `mvn -B -ntp verify` green across all 14 modules, 22 tests total — including the
existing `JsonSchemaParseTest`/`TopicRegistryTest`/`AvroSchemaParseTest` contract tests and every
payment-return-slice integration test (proving the `$ref` change didn't silently alter runtime
(de)serialization anywhere it's actually used). Manually confirmed via `javap`/generated-source
inspection that a single shared Java type is produced and referenced, not 4 duplicates.

---

## 2026-09-24 — Autonomous continuation Routine established

**Roadmap items:** none (process/infrastructure, not a build item)

**What:** Created a recurring Routine (`trig_019oiicqW6q3sv5tXLpiq4Q2`, hourly, self-bound to this
session) via the `claude-code-remote` MCP server's `create_trigger`. Each firing re-enters this same
conversation with full context and a fixed protocol: read `08-roadmap-progress-tracker.md`, pick the
first `NOT_STARTED` row in phase order (skipping anything under "Items requiring a human decision"),
implement it with the same rigor as every entry in this log (real logic, real tests against real
Postgres and embedded/TopologyTestDriver Kafka, `mvn -B -ntp verify` green, honest documentation of
any simplification), add a build-log entry, update the tracker, commit, and push. It stays silent on
routine progress and only messages the user when a roadmap Phase completes, a human-decision item
blocks further autonomous progress, a blocker can't be resolved, or the entire tracker is DONE (at
which point it disables itself).

**Why:** The user asked for continuous enhancement of the codebase "till you reach the final end
stage of this product," with every step documented. That end state is a multi-year, multi-engine
platform (per the gap-analysis roadmap's own Phase 2/3 scoping) — not reachable in one session. This
Routine is how work continues across sessions without requiring the user to re-prompt each time,
while `07-build-log.md` and `08-roadmap-progress-tracker.md` keep that ongoing work legible and
honestly tracked between check-ins.

**Verification:** `create_trigger` returned `outcome: CREATE_TRIGGER_OUTCOME_CREATED`,
`persistent_session_id` matching this session, `enabled: true`, `next_run_at:
2026-09-24T08:32:00Z`.

**Follow-ups:** The Routine is genuinely best-effort and long-horizon — Phase 2/3 items in the
tracker (AI Gateway, graph intelligence, full jurisdiction classification adapters, a properly
designed Experience UI) are realistically years of work, not something this loop will exhaust
quickly. Several tracker items are explicitly gated on a human decision (schema namespace, legacy
system discovery, external API credentials, auth/IAM design) and will sit `NOT_STARTED` until the
user provides that decision.

---

## 2026-09-24 — Experience API: evidence drill-down (payment-return slice complete)

**Roadmap items:** 1.10, 1.11

**What:** `ProposedSignalQueryController` gives `ews-experience-api` — standing in for Layer 9
("Experience") of `01-architecture-blueprint.md` §2 — its first real handlers:
`GET /api/v1/proposed-signals?status=PROPOSED` lists signals by status, and
`GET /api/v1/proposed-signals/{id}` returns one signal with its evidence drill-down resolved: each
`evidenceIds` entry is looked up as a `feature_value` row and returned inline
(`ProposedSignalView`/`FeatureValueEvidenceView`), per the drill-down chain in
`01-architecture-blueprint.md` §16 ("Explanation -> Signal -> ... -> Features -> Observations ->
Evidence -> Original source"). This is deliberately read-only — accept/reject stays in
`ews-case-workflow-service` — matching `EA-01`'s command/query separation
(`03-event-architecture.md` §2).

**Why:** This closes the payment-return vertical slice end-to-end: ingest → outbox → Kafka →
feature → signal → human disposition → **experience API view**. Every link in that chain now has
real, tested logic, not a stub.

**A documented gap:** the drill-down only reaches feature values, not further back to the original
canonical events/evidence — `FeatureValue` doesn't carry `observationIds`/`sourceRecordIds` lineage
in this slice (that's in the full `feature_value_lineage_ref` DDL table, unused so far). The full
chain to "original transaction/document/external source" is real future work, documented in
`ProposedSignalView`'s own Javadoc, not silently claimed as done.

**Also added:** getters on `SignalInstance` (severity, confidenceValue, materialityBand, detectedAt,
knowledgeTime, policyId, policyVersion) and `FeatureValue` (windowStart, windowEnd, calculatedAt) in
`ews-persistence-core` — needed by the view DTOs here and not previously exposed.

**Files:** `services/ews-experience-api/src/main/java/org/ewsfi/experience/signals/*.java`,
`platform/ews-persistence-core/.../signal/SignalInstance.java`,
`platform/ews-persistence-core/.../feature/FeatureValue.java` (new getters only).

**Verification:** New `ProposedSignalQueryControllerTest` (`MockMvc`, real Postgres) seeds a real
`feature_value` row and a `signal_instance` whose `evidenceIds` references it, then asserts the
`GET /{id}` response actually resolves and inlines that evidence (not just echoes the ID), plus a
404 case for an unknown signal. `mvn -B -ntp verify` green across all 14 modules — **19 tests
total** across the whole payment-return slice (outbox 1, ingestion 1, feature-processor 3,
signal-policy-engine 5, case-workflow 3, experience-api 2, persistence-core 2, contract tests 2).
No single combined "walk the whole slice in one test" integration test exists; coverage is
per-hop instead, each proven against real Postgres and (where relevant) real/embedded Kafka.

**This completes Part B of the payment-return vertical slice** (plan: "Phase 3: Payment-Return
Vertical Slice + Autonomous Continuation Loop"). Next: set up the autonomous continuation Routine
(Part C) to keep working through `08-roadmap-progress-tracker.md`.

---

## 2026-09-24 — Case-workflow disposition endpoints (the human-validation gate)

**Roadmap items:** 1.9

**What:** `SignalDispositionController` implements the mandatory gate ADR-002 requires:
`GET /api/v1/signals?status=PROPOSED` lists proposed signals; `POST /api/v1/signals/{id}/disposition`
(`{"disposition": "ACCEPTED"|"REJECTED", "reason": "..."}`) validates the signal is currently
`PROPOSED` (409 Conflict otherwise — a signal can only be dispositioned once), updates
`signal_instance.status`, and publishes an immutable `signal.disposition.recorded` event
(`JsonSignalDisposition`, new in `ews-schemas`'s interim package) via the outbox to
`ews.derived.decision`, in the same transaction as the status update.

**Why:** This is the human-validation link in the payment-return slice's chain — the point where
`00-vision-and-principles.md`'s "AI does not own the risk state. Evidence does." stops being a
principle and becomes enforced behavior: no signal reaches a terminal state without an explicit,
recorded human decision, and that decision itself becomes an immutable event, not just a database
mutation.

**A documented gap:** `actorId`/`actorRole` on the disposition event are hard-coded placeholders
(`"unauthenticated-analyst"` / `"ANALYST"`), not real attribution — there is no authentication yet
(roadmap item 1.16, not started). Dispositions work correctly but are not yet properly attributable
to a real analyst; flagged in the controller's own Javadoc, not silently assumed.

**A real bug found and fixed:** the controller's first version used `@PathVariable String signalId`
and `@RequestParam(defaultValue = "PROPOSED") String status` without explicit parameter names.
Spring MVC needs either the `-parameters` javac flag (not set in this project) or an explicit name
to resolve a path/query parameter by reflection; without either, every request failed with
`IllegalArgumentException: Name for argument of type [java.lang.String] not specified`. All three
new controller tests caught this immediately. Fixed with explicit `@PathVariable("signalId")` and
`@RequestParam(name = "status", ...)` rather than adding a global compiler flag, since explicit
names are more robust than relying on every future build configuration preserving `-parameters`. A
repo-wide grep confirmed no other controller had the same latent bug.

**Files:** `platform/ews-schemas/src/main/java/org/ewsfi/contracts/interim/JsonSignalDisposition.java`
(new), `services/ews-case-workflow-service/src/main/java/org/ewsfi/caseworkflow/disposition/*.java`.

**Verification:** New `SignalDispositionControllerTest` (`@SpringBootTest` + `MockMvc`, real
Postgres) covers: accepting a proposed signal updates its status and creates the outbox row;
dispositioning an already-dispositioned signal returns 409; an invalid disposition value returns
400. `mvn -B -ntp verify` green across all 14 modules, 17 tests total (outbox 1, ingestion 1,
feature-processor 3, signal-policy-engine 5, case-workflow 3, persistence-core 2, contract tests 2).

---

## 2026-09-24 — Signal policy engine: `REPEATED_PAYMENT_RETURN` (P03)

**Roadmap items:** 1.8

**What:** `SignalPolicyTopology` (the "signal policy engine" named in ADR-004) consumes
`ews.derived.feature`, evaluates a hard-coded rule-method policy
(`SignalPolicyLoader`: `returned_payment_count_30d >= 3`, matching the shape of the jurisdiction-
neutral policy example in `04-signal-taxonomy.md` §18), and emits `signal.detected` JSON
(`JsonSignalDetected`, new in `ews-schemas`'s interim package) to `ews.derived.signal` whenever the
threshold is met. Each signal's `signalId` is deterministically derived
(`UUID.nameUUIDFromBytes(featureValueId)`) so re-processing the same feature value under Kafka's
at-least-once delivery produces the same ID rather than a duplicate proposed signal.
`SignalInstancePersistenceListener` (`@KafkaListener`, mirroring the feature-processor's
computation/persistence split) persists each as a `signal_instance` row with its evidence child
row — and explicitly does **not** overwrite a row that already exists, since by the time a
re-delivered message arrives an analyst may have already accepted or rejected the signal.

**Why:** This is the signal-detection link in the payment-return slice's chain, and the first place
in the codebase evidence-first governance (ADR-005) and the human-validation gate (ADR-002) become
concrete: every persisted `signal_instance` carries at least one `evidenceIds` entry (enforced by
`SignalInstance`'s constructor, added in the persistence-core session) and starts life in `PROPOSED`
status, never `ACTIVE`, pending human disposition (implemented next).

**A documented simplification:** a single hard-coded policy, not the persisted, versioned
`signal_policy` table (`schemas/signals/signal-policy-v1.schema.json`) that the architecture
specifies. Loading and evaluating arbitrary persisted policies is real future work once more than
one signal family exists — flagged in code (`SignalPolicyLoader`'s Javadoc), not silently assumed.

**Files:** `platform/ews-schemas/src/main/java/org/ewsfi/contracts/interim/JsonSignalDetected.java`
(new), `services/ews-signal-policy-engine/src/main/java/org/ewsfi/signalpolicy/**`.

**Verification:** `SignalPolicyTopologyTest` (`TopologyTestDriver`, no broker) proves the threshold
rule, the below-threshold no-op case, and the unrelated-feature-name no-op case.
`SignalInstancePersistenceListenerTest` (`@EmbeddedKafka` + real Postgres) proves both the
happy-path persistence (with evidence) and — deliberately, since this is exactly the kind of subtle
correctness property that's easy to get wrong — that a re-delivered `signal.detected` message does
**not** clobber a signal an analyst already moved to `ACCEPTED`. `mvn -B -ntp verify` green across
all 14 modules, 14 tests total (outbox 1, ingestion 1, feature-processor 3, signal-policy-engine 5,
persistence-core 2, contract tests 2).

---

## 2026-09-24 — Feature processor: `returned_payment_count_30d`

**Roadmap items:** 1.7

**What:** `FeatureProcessorTopology` (the "operational feature processor" named in ADR-004) consumes
`ews.canonical.account-transaction`, filters to countable payment returns (excludes `TECHNICAL` and
`BENEFICIARY_DETAIL` reason categories per P03's policy in
`02a-priority-signal-contracts.md`), and computes `returned_payment_count_30d` using Kafka Streams'
`SlidingWindows` — a genuinely rolling 30-day count as of each new event, not a fixed-bucket
tumbling/hopping count, matching the feature catalogue's actual definition
(`02d-phase1-feature-catalogue.md` §1). Results publish to `ews.derived.feature` as JSON
(`JsonFeatureValue`, new alongside `JsonEventEnvelope` in `ews-schemas`'s interim package). A
separate `FeatureValuePersistenceListener` (`@KafkaListener`, not part of the Streams topology
itself) consumes that topic and persists each value via `ews-persistence-core`'s
`FeatureValueRepository` — deliberately kept out of the Streams processing thread and independently
testable. `KafkaStreamsConfig` wires the topology via Spring Kafka's `@EnableKafkaStreams`.

**Why:** This is the feature-computation link in the payment-return slice's chain: ingestion →
outbox → Kafka → **feature** → signal → disposition → experience API.

**A known simplification:** the topology uses Kafka's default record timestamp (producer send time)
rather than a custom `TimestampExtractor` reading the JSON envelope's own `eventTime` field. True
bitemporal point-in-time correctness (`02-canonical-risk-model.md` §6) would need the latter; for
this slice, where ingestion publishes close to real time, the difference is negligible. Not tracked
as a separate roadmap item since it's a narrow implementation detail of one topology, not a platform
capability gap — noted here so it isn't silently forgotten.

**Files:** `platform/ews-schemas/src/main/java/org/ewsfi/contracts/interim/JsonFeatureValue.java`
(new), `services/ews-feature-processor/src/main/java/org/ewsfi/featureprocessor/**`.

**Verification:** Two new test classes. `FeatureProcessorTopologyTest` uses Kafka Streams'
`TopologyTestDriver` (fully in-process, no broker at all — not even embedded) to prove the
sliding-window count and the reason-category filter both work: 3 `FINANCIAL` returns plus 1
`TECHNICAL` and 1 `BENEFICIARY_DETAIL` return for the same account produce a window count of
exactly 3, and an unrelated event type produces no output. `FeatureValuePersistenceListenerTest`
uses `@EmbeddedKafka` plus the real local Postgres database to prove a `feature.value.updated`
message published to `ews.derived.feature` is actually persisted as a `feature_value` row,
queryable back out via the repository. Both passed on first real run. `mvn -B -ntp verify` green
across all 14 modules — 9 tests total (outbox 1, ingestion 1, feature-processor 3 (1 persistence + 2
topology), persistence-core 2, contract tests 2).

---

## 2026-09-24 — Payment-return ingestion adapter

**Roadmap items:** 1.6

**What:** `ews-ingestion-service` gained real logic: `PaymentInstructionReturnedAdapter.record(...)`
builds an interim JSON envelope (new shared `org.ewsfi.contracts.interim.JsonEventEnvelope` in
`ews-schemas`, since this is wire-format DTO code both producer and consumer need, not something
`jsonschema2pojo`/`avro-maven-plugin` generate) and stages it via `OutboxEvent.newEvent(...)` to
topic `ews.canonical.account-transaction` (per `03c-topic-and-partition-strategy.md` §2), keyed by
`accountId`, in the same `@Transactional` method — satisfying ADR-003's core guarantee that the
business fact and the outbox insert share one local transaction. `PaymentReturnIngestionController`
exposes `POST /api/v1/internal/payment-returns`, explicitly documented as a stand-in for the real
internal payment-system integration that doesn't exist yet, not a claim of one.

**Why:** This is the entry point for the payment-return vertical slice — the fact that flows through
outbox → Kafka → feature computation → signal detection.

**Files:** `platform/ews-schemas/src/main/java/org/ewsfi/contracts/interim/JsonEventEnvelope.java`
(new), `services/ews-ingestion-service/src/main/java/org/ewsfi/ingestion/adapter/internal/*.java`.

**Verification:** New `PaymentInstructionReturnedAdapterTest` (`@SpringBootTest` against the real
local Postgres `ews` database) posts a request through the adapter and asserts a `NEW` outbox row
was created with the correct topic, partition key, and payload contents. `mvn -B -ntp verify` green
across all 14 modules (7 tests total: outbox 1, ingestion 1, persistence-core 2, contract tests 2 —
plus the reactor's other modules with no tests of their own yet).

**Follow-ups:** `CompaniesHouseAdapter` remains an empty stub (roadmap item 1.13, blocked on an API
credential decision).

---

## 2026-09-24 — Outbox claim + publish (real Kafka)

**Roadmap items:** 1.5

**What:** Implemented the actual outbox mechanics in `platform/ews-platform-outbox-starter`:
- `OutboxEvent` gained the full column set from `outbox_event` in
  `db/migration/V1__init_phase1_baseline.sql` (previously only a handful of fields existed), a
  `newEvent(...)` factory generating a stable `eventId`, and `markPublished`/`markFailed` mutators.
- `OutboxClaimStrategy` runs a single native
  `UPDATE ... WHERE event_id IN (SELECT ... FOR UPDATE SKIP LOCKED) RETURNING *` statement in its
  own `REQUIRES_NEW` transaction, so concurrent publisher instances never contend for the same rows
  (ADR-003, `03b-outbox-reference-design.md`).
- `OutboxPublisherWorker` claims a batch, publishes each row via `KafkaTemplate<String, String>`
  outside the claim transaction (per `03-event-architecture.md` §11: "Kafka publication occurs
  outside DB row-lock holding"), and marks each row `PUBLISHED` (with partition/offset) or `FAILED`
  in its own `REQUIRES_NEW` transaction.
- `EwsOutboxAutoConfiguration` now declares its own `KafkaTemplate<String, String>` bean (Spring
  Boot's own `KafkaAutoConfiguration` default template is typed `<Object, Object>` and would not
  satisfy the `<String, String>` dependency) and enables `@Scheduled` so the publisher runs on a
  timer (`ews.outbox.publish-interval-ms`, default 1s).

**Why:** This is the mechanism every other module in the payment-return slice depends on to move a
canonical event from a local database transaction into Kafka reliably. ADR-003 and
`03-event-architecture.md` §11 specify the pattern in detail; this entry converts that spec into
working code for the first time.

**A documented deviation:** `payload`/`headers` are stored and published as JSON strings, not Avro
binary. `03-event-architecture.md` §10 states Avro + Schema Registry as the recommended production
default, and ADR-011 commits to Apicurio Registry as the concrete product — but wiring a real
Avro/Schema-Registry producer path requires a running registry, which requires Docker, which is not
available in this sandboxed environment (confirmed in the 2026-09-24 ADR/skeleton session). Using
JSON on the wire for this slice is faster to build and fully testable without that dependency, at
the cost of diverging from the documented target wire format. Tracked explicitly as roadmap item
1.17 rather than silently left as a gap.

**Files:** `platform/ews-platform-outbox-starter/src/main/java/org/ewsfi/platform/outbox/*.java`,
`platform/ews-platform-outbox-starter/pom.xml` (switched to `spring-boot-starter-data-jpa` for
Hibernate's `@JdbcTypeCode`/`SqlTypes.JSON`, needed to map the `jsonb` columns correctly).

**Verification:** New `OutboxPublisherWorkerTest` uses `@EmbeddedKafka` (in-process, no Docker
needed) plus the real local Postgres `ews` database: inserts a `NEW` outbox row, calls
`publishClaimedBatch()` directly (not waiting on the scheduler, for determinism), then asserts (a) a
real consumer on the embedded broker receives the message with the expected key and payload, and
(b) the row's status flipped to `PUBLISHED` in the database. First run surfaced a genuine, expected
finding: Postgres's `jsonb` column type canonicalizes stored JSON (reorders object keys, normalizes
whitespace), so the payload read back by the claim query was semantically but not byte-for-byte
identical to what was inserted — the test's raw-string assertion was wrong, not the pipeline; fixed
by comparing parsed JSON trees instead. `mvn -B -ntp verify` green across all 14 modules (5 tests
total: outbox 1, persistence-core 2, contract tests 2).

**Follow-ups:** Roadmap item 1.17 (Avro + Schema Registry wire format) is now tracked and open.

---

## 2026-09-24 — `ews-persistence-core` shared JPA module

**Roadmap items:** 1.4

**What:** Added a new shared Maven module, `platform/ews-persistence-core`, with hand-written JPA
entities and Spring Data repositories for the three cross-service tables in
`db/migration/V1__init_phase1_baseline.sql` that the payment-return slice needs multiple services to
read and write: `canonical_event_envelope`, `feature_value`, and `signal_instance` (mapping its
`signal_instance_evidence` / `_risk_intent` / `_risk_dimension` / `_disposition_tag` child tables as
`@ElementCollection`s). `SignalInstance`'s constructor throws `IllegalArgumentException` if
`evidenceIds` is empty, enforcing ADR-005's evidence-first requirement (and the JSON Schema
contract's `evidenceIds minItems: 1`) at the Java level, not just in the database.

**Why:** `ews-feature-processor` (writes `feature_value`), `ews-signal-policy-engine` (writes
`signal_instance`), `ews-case-workflow-service` (updates `signal_instance.status`), and
`ews-experience-api` (reads both, plus `canonical_event_envelope`, for evidence drill-down) all need
the same entity shapes. A shared module avoids four divergent copies. Entities are hand-written
rather than generated from `schemas/` (unlike `ews-schemas`), because `jsonschema2pojo` produces
plain POJOs without JPA annotations — this is a real module-boundary decision, not an oversight.

**Files:** `platform/ews-persistence-core/**` (new module), `platform/pom.xml` (module registration),
`pom.xml` (dependencyManagement entry).

**Verification:** Discovered and fixed a real schema/ORM mismatch in the process: Hibernate's default
mapping for a plain Java `String` field is `VARCHAR`, but the DDL declared `jurisdiction`,
`primary_jurisdiction`, `currency`, and `source_jurisdiction` as fixed-width `CHAR(n)`. Hibernate's
schema validator correctly rejected this at startup
(`SchemaManagementException: wrong column type ... found [bpchar], but expecting [varchar]`).
Fixed by changing all `CHAR(2)`/`CHAR(3)` columns in `db/migration/V1__init_phase1_baseline.sql` to
`VARCHAR(2)`/`VARCHAR(3)` (the right fix — fixed-width padding was never actually wanted for
jurisdiction/currency codes). Re-applied the corrected migration to a real local Postgres 16
database (role `ews`, database `ews`, matching `docker-compose.yml`'s `postgres` service
credentials) and added `SignalInstanceRepositoryTest`, a `@SpringBootTest` integration test that
persists and queries a real `SignalInstance` row, plus a unit test confirming the empty-evidence
guard. Both tests pass against the real database (`mvn -B -ntp verify` → `Tests run: 2, Failures: 0,
Errors: 0`). The test class initially used an `*IT` suffix and was silently never run by Surefire's
default include pattern (that suffix is Failsafe's convention, not Surefire's, and this project has
no Failsafe plugin configured) — renamed to `*Test` to match the convention `ews-event-contracts-test`
already established. The integration test skips (via `Assumptions.assumeTrue`) rather than fails when
no Postgres is reachable, so `mvn verify` stays green without a database; `.github/workflows/ci.yml`'s
`build` job now runs a real `postgres:16` service container with the migration applied before the
Maven build, so this coverage is real in CI, not just locally. Full reactor `mvn -B -ntp verify`
confirmed green across all 14 modules afterward.

**Follow-ups:** The local Postgres `ews` database created this session is ephemeral to the sandbox;
a future session (or CI) recreates it the same way (`docker compose up postgres` +
`psql -f db/migration/V1__init_phase1_baseline.sql`, or the CI service block above).

---

## 2026-09-24 — ADRs + Phase-1 spine skeleton (retrospective entry)

**Roadmap items:** 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 1.1, 1.2, 1.3

**What:** First implementation session against the (until then) pure-specification repository.
Wrote 4 ADRs (ADR-001 event-driven architecture, ADR-002 human-validated formal EWS, ADR-005
evidence-first AI architecture, ADR-011 schema registry baseline) following `docs/adr/README.md`'s
canonical 8-section format. Scaffolded the full Phase-1 spine as a buildable-but-empty skeleton: a
Java 21 / Spring Boot 3 Maven multi-module project (`ews-schemas` generating Java classes directly
from the `schemas/` contracts, `ews-platform-outbox-starter` implementing ADR-003's outbox shape,
and six service shells — `ews-ingestion-service`, `ews-core-registry-service`,
`ews-feature-processor`, `ews-signal-policy-engine`, `ews-case-workflow-service`,
`ews-experience-api` — consolidated per `01-architecture-blueprint.md` §23's explicit
anti-microservice-mandate). Added Postgres DDL (`db/migration/V1__init_phase1_baseline.sql`) with
one table (plus child tables for array-shaped fields) per existing schema contract, a
`docker-compose.yml` local dev stack matching the Phase-1 storage baseline (§9), and
`.github/workflows/ci.yml` validating every schema contract and the Maven build.

**Why:** The gap analysis (`docs/architecture/06-gap-analysis-and-implementation-roadmap.md`,
written the same session, preceding this entry) found the repository was 100% specification and 0%
implementation. This session converted the documented Phase-1 spine (`01-architecture-blueprint.md`
§25) and the roadmap's own Phase 0/1 scoping into a real, buildable project skeleton — no business
logic yet, but every module boundary, table, and topic name traceable to a specific spec section.

**Files:** `pom.xml`, `platform/**`, `services/**`, `test/**`, `db/migration/V1__init_phase1_baseline.sql`,
`docker-compose.yml`, `.github/workflows/ci.yml`, `docs/architecture/adr/ADR-{001,002,005,011}-*.md`,
`docs/adr/README.md`, `README.md`.

**Verification:** `mvn -B -ntp verify` succeeded across all 13 modules (67 Avro/JSON-Schema-generated
classes compiled cleanly). The DDL was applied against a real local Postgres 16 instance
(`service postgresql start`; every `CREATE TABLE`/`CREATE INDEX` succeeded with zero errors) then
torn down. `docker compose config` validated the stack definition (no live Docker daemon was
available in this sandboxed environment, so the stack itself was not brought up end-to-end — flagged
as a real gap, not silently skipped). While building `ews-schemas`, the JSON-Schema-POJO generation
step surfaced a genuine pre-existing bug: `schemas/sources/source-registry-v1.schema.json` had a
missing closing brace around the `"rights"` object (invalid JSON). Fixed in this same session — the
new schema-validation tooling caught a real defect on its first real run, which is exactly the
backlog item (`05-coherence-review-parts-i-iii.md` §5, item 1) it exists to satisfy.

**Follow-ups:** No business logic anywhere — every controller/topology/adapter class is an
intentionally empty shell. The next session (this one, see below) turns the payment-return signal
family into real, tested logic end-to-end.

---
