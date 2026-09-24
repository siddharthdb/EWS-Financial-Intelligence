# ADR-008 — Graph Intelligence Adoption

**Status:** Accepted for Phase-3 design baseline (not yet implemented — see Consequences)
**Decision date:** 2026-09-24

## Context

`02-canonical-risk-model.md` §5 already specifies relationships as first-class, temporal entities —
`(sourceParty)-[relationshipType]->(targetParty)` edges (`DIRECTOR_OF`, `OFFICER_OF`,
`BENEFICIAL_OWNER_OF`, `CONTROLS`, `PROMOTER_OF`, `OWNS`, `SUBSIDIARY_OF`, `GUARANTEES`,
`SUPPLIES_TO`, `CUSTOMER_OF`, `AUDITED_BY`, `LENDER_TO`, `GROUP_MEMBER_OF`), each recording
effective period, observation/knowledge period, source/evidence, jurisdiction where relevant,
confidence and resolution method — and §20's design-consequences list states plainly that "Graph
relationships and external identities are temporal/provenance-bearing." `04-signal-taxonomy.md` §1
already names graph (`G`) as one of the platform's seven detection methods and catalogues concrete
graph-method signals across three risk domains: `RELATED_PARTY_EXPOSURE_SPIKE` (R/A/G),
`RELATED_ENTITY_TRANSFER_SPIKE` (R/A/G), `ROUND_TRIPPING_PATTERN` (G/A/ML),
`RELATED_PARTY_DIVERSION_PATTERN` (G/A), `SHELL_ENTITY_EXPOSURE_SUSPECTED` (G/NLP),
`RELATIONSHIP_RISK_CONCENTRATION` (G/S), and `CONTAGION_SCORE_SPIKE` (G/ML). The research notes go
further, treating this as settled product direction rather than a speculative capability:
`part-2-research-notes.md` states "Graph analytics are first-class for group/related-party/end-use
intelligence" and "preserve graph/network analytics as a distinct analytical capability," and
`us-uk-corporate-credit-data-source-landscape.md` calls the target platform "identifier-graph-driven."

`01-architecture-blueprint.md` §9 (Financial Data Platform) is equally explicit about sequencing,
however: the logical platform includes "relationship/graph state" as one of several storage
characteristics, but the pragmatic Phase-1 baseline is `Kafka / PostgreSQL / Elasticsearch / Redis /
S3-compatible object storage` — no graph database — with the express rule that "Dedicated graph,
analytical or feature-store products are introduced only where validated workloads justify them."
No relationship/graph table exists yet anywhere in this platform's schema or DDL (only
`entity_resolution` — a same-entity matching record, not a between-entity relationship edge — is
implemented, in `db/migration/V1__init_phase1_baseline.sql`). Roadmap item 3.1 ("Graph intelligence
adoption (ADR-008, not yet written) + graph-method signals") names this ADR as its own explicit
prerequisite, the same pattern ADR-006 and ADR-010 already followed for items 2.5 and 2.7.

## Decision

1. **Relationships become a first-class, queryable model — in Postgres, not a dedicated graph
   database, for now.** Per §9's explicit sequencing rule, this ADR does not introduce a graph
   database product; it commits to representing §5's relationship edges as a standard relational
   table (a `party_relationship` table: `source_entity_type`/`source_entity_id`,
   `target_entity_type`/`target_entity_id`, `relationship_type`, effective period, observation/
   knowledge period, source/evidence reference, jurisdiction, confidence, resolution method — one
   row per edge, mirroring §5's field list exactly), queried with recursive SQL (`WITH RECURSIVE`)
   for the traversal depths this platform's near-term graph-method signals need (the taxonomy's own
   catalogue is about direct/near relationships — related-party exposure, group membership,
   round-tripping between a small number of counterparties — not deep multi-hop network science).
2. **A dedicated graph database is an explicit future decision, not this one.** This ADR names the
   trigger for revisiting product choice (see Review Trigger) rather than picking one now — no
   document in this repository names a preferred graph database product, and §9's own rule requires
   a validated workload first. This mirrors ADR-006's and ADR-010's pattern of separating
   "architecture decision" from "product/vendor selection."
3. **Graph-method (`G`) signals consume the relationship table the same way every other method
   consumes `feature_value`/`canonical_event_envelope`.** No new detection paradigm is introduced:
   a graph-method Kafka Streams topology reads the relationship table (or a changelog of it) the same
   way `UtilizationFeatureTopology` reads `facility.limit.changed`/`facility.outstanding.changed` —
   joining/aggregating relationship edges rather than event fields — and still produces ordinary
   `signal_instance` rows with `evidenceIds` per ADR-005. Graph intelligence is a new *input shape*
   to the existing signal pipeline, not a parallel pipeline.
4. **Relationship data is temporal/provenance-bearing, matching every other governed record in this
   platform.** Per §20's design consequence, a `party_relationship` row is never destructively
   updated — a changed or ended relationship gets a new row with its own effective period and a
   supersession/end-date on the prior row, mirroring the bitemporal model (§6) already governing
   every other canonical record.
5. **No graph-method signal is implemented by this ADR.** Consistent with ADR-006/ADR-010, this ADR
   fixes the representation and querying approach; the taxonomy's seven cataloged graph-method
   signals (e.g. `RELATIONSHIP_RISK_CONCENTRATION`, `CONTAGION_SCORE_SPIKE`) remain future
   implementation work, gated additionally on this platform actually ingesting relationship data
   (currently: none — no source adapter in this platform populates `party_relationship` yet, since
   the SEC EDGAR connector (item 2.1) extracts only filing metadata, not officer/beneficial-owner
   relationships from filing contents).

## Alternatives Considered

1. **Adopt a dedicated graph database (e.g. Neo4j, Amazon Neptune) now, since the taxonomy already
   names seven graph-method signals and the research docs treat graph analytics as "first-class."**
   Rejected: directly contradicts `01-architecture-blueprint.md` §9's explicit sequencing rule
   ("introduced only where validated workloads justify them") — there is no validated workload yet,
   since no relationship data is even ingested. Committing to a graph database product now would be
   the same category of premature, unsupported product commitment ADR-010 rejected for MLflow.
2. **Defer this ADR entirely until a relationship-data source adapter exists, on the theory that
   designing the representation before there's data to represent is premature.** Rejected: unlike
   ADR-006 (where the evidence spine to gate genuinely didn't exist yet), §5's relationship model is
   already fully specified with a concrete field list, and the tracker names this ADR as item 3.1's
   own explicit prerequisite the same way ADR-006/ADR-010 were named for 2.5/2.7. Recording the
   representation decision now — while §5, §9, and the taxonomy's graph-method catalogue are all
   fresh in context — is cheaper than re-deriving it later, and unblocks a future source adapter to
   populate a table whose shape is already settled.
3. **Represent relationships as a Kafka Streams `GlobalKTable` only, without a durable Postgres
   table, mirroring how feature values flow through this platform.** Rejected: relationship data is
   fundamentally graph-shaped (multi-hop traversal: "is party A related to party D through B and
   C?"), which recursive SQL over a durable table supports far better than a Kafka Streams
   point-lookup table does; a `GlobalKTable` remains a reasonable *serving-layer* cache in front of
   the Postgres table once a real workload exists; that's implementation detail for whoever builds
   3.1's actual code, not excluded by this decision.
4. **Build a minimal `party_relationship` table and one real graph-method signal now, to avoid
   shipping "only documentation" the way 2.5/2.7 did.** Rejected for the same reason those two ADRs
   rejected it: no relationship data source exists yet to populate the table honestly (fabricating
   sample relationship data to exercise a signal would produce exactly the "invented input" pattern
   this project has consistently avoided — see `MaxDpdFeatureTopology`'s tests, which use real
   payload shapes from real upstream events, never synthetic shortcuts). A schema with no real
   ingestion path behind it would be indistinguishable from a skeleton.

## Rationale

The decision that matters most here is sequencing, not technology: §9 already settles "graph
database or not yet" in favor of "not yet, until validated" for the whole platform, and this ADR
simply applies that rule concretely to graph intelligence rather than re-litigating it. Choosing
Postgres-with-recursive-queries over "no representation at all" lets a future relationship-data
source adapter (whichever one is built first — Companies House officer/PSC data is the most
obviously relationship-shaped source already named in this platform's research docs, though which
source to prioritize remains its own future decision) have a concrete, already-governed table to
write into, without this platform prematurely operating a graph database product it cannot yet
justify by workload.

## Consequences

- Item 3.1 in `08-roadmap-progress-tracker.md` can move from `NOT_STARTED` to `DONE (partial)`: the
  architecture decision this row named as its prerequisite is now written, but no
  `party_relationship` table, source adapter, or graph-method signal exists yet.
- A future increment implementing item 3.1's actual code must add the `party_relationship` table to
  `db/migration/` per point 1's field list, and should reuse the existing `ews-persistence-core`
  module's pattern for shared JPA entities other services both read and write, mirroring how
  `feature_value`/`signal_instance` are already shared there.
- No graph-method signal can be honestly implemented until some source adapter populates
  `party_relationship` with real data — this ADR does not itself unblock signal implementation,
  only the representation it would be built against.
- The relationship table's temporal/provenance fields (point 4) mean any future query pattern must
  account for effective-period filtering (avoid counting a superseded or not-yet-effective
  relationship), the same discipline `02-canonical-risk-model.md` §6 already requires for every other
  bitemporal record in this platform.

## Risks

- Recursive SQL over a `party_relationship` table can degrade badly at scale for deep or
  high-fan-out traversals (e.g. a large corporate group with hundreds of subsidiaries). This ADR
  accepts that risk for the near-term, taxonomy-scoped signals (direct/near relationships, not deep
  network science), but a genuinely validated workload requiring multi-hop traversal at scale is
  precisely the trigger §9 already names for introducing a dedicated graph product.
- Because no relationship data source exists yet, none of this ADR's representation choices are
  validated against real data volume, shape, or query patterns — the field list and traversal
  approach should be treated as a reasoned starting point, not a settled contract, until a real
  source adapter and real workload exist to test it against.

## Review Trigger

Revisit if: (a) a relationship-data source adapter is built (e.g. Companies House officer/PSC data,
still itself blocked per item 1.13's human-decision gate) and produces real query patterns/volume
that recursive SQL cannot serve adequately, at which point a dedicated graph database product
decision — deferred here per §9 — should be made against that concrete, validated workload; (b) a
graph-method signal from the taxonomy's catalogue is actually implemented and needs traversal depth
or performance this ADR's approach cannot support; or (c) ADR-009 (feature-store strategy, not yet
written) surfaces a need to materialize graph-derived features (e.g. a `GRAPH_METRIC`-typed feature
per `02-canonical-risk-model.md`'s feature-value taxonomy) in a way this ADR's representation should
account for.
