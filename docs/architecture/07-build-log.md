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
