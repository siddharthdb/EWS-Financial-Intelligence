# EWS 2.0 — Canonical Feature Model

**Status:** Draft / Part II  
**Scope:** Corporate-first, reusable meta-model for retail

## 1. Purpose

Features are governed, reproducible analytical facts between observations/evidence and rules/models/signals. They prevent downstream engines from querying operational systems independently and interpreting the same business concept differently.

A feature value is not merely `name=value`. It is a point-in-time, versioned calculation with source lineage, quality and definition semantics.

## 2. Canonical feature identity

A feature definition is identified by:

```text
featureName
featureDefinitionId
version
entityType
grain
valueType
unit
calculationSemantics
owner
```

A feature value references that definition plus entity, value, effective time, knowledge time, calculation time, input lineage and quality.

## 3. Definition IDs are mandatory for financial ratios

Ratio names are not sufficiently precise. Examples such as DSCR, Debt/EBITDA, TOL/ATNW, current ratio and drawing-power utilization may have institution/product-specific definitions.

Therefore:

```text
featureName: dscr
featureDefinitionId: FIN.DSCR.CREDIT_POLICY
version: 2.1
```

The definition specifies numerator, denominator, treatment of exceptional items, annualisation, lease/debt treatment, consolidation scope, currency, period and rounding. Numerator/denominator component lineage is retained with the feature value.

## 4. Feature grains

Supported grains include:

- COUNTERPARTY
- FACILITY
- ACCOUNT
- COLLATERAL
- FINANCIAL_PERIOD
- RELATIONSHIP
- GROUP
- PORTFOLIO_SEGMENT

A feature must not silently change grain. Facility utilization and counterparty aggregate utilization are different definitions.

## 5. Time semantics

Feature values carry:

- `effectiveFrom/effectiveTo`: economic validity interval where applicable;
- `knowledgeTime`: earliest time the platform was entitled to know/use the value;
- `calculatedAt`: processing timestamp;
- `windowStart/windowEnd`: aggregation interval where applicable;
- `sourceAsOf`: freshness of the most material input.

Historical model training and replay must perform point-in-time joins using `knowledgeTime`, not today's corrected dataset, to avoid look-ahead leakage.

## 6. Revision semantics

Corrections do not overwrite historical feature values. A recalculated value creates a new version/revision referencing superseded value(s) and changed evidence. This supports:

- operational correction;
- historical audit;
- as-was replay;
- as-corrected analysis;
- counterfactual model evaluation.

## 7. Null, zero, not-applicable and unavailable

These states are distinct:

```text
VALUE
ZERO
NULL_UNKNOWN
NOT_APPLICABLE
NOT_AVAILABLE
STALE
INVALID
```

A denominator of zero must not silently produce zero. A missing financial statement must not produce a synthetic neutral ratio. Models/rules define explicit missingness behaviour.

## 8. Quality metadata

Each feature value carries quality dimensions:

- completeness;
- freshness;
- reconciliation status;
- source authority;
- extraction confidence;
- entity-resolution confidence;
- transformation validation status.

A summarized state maps to `COMPLETE`, `PARTIAL`, `STALE`, `CONFLICTED`, `UNVERIFIED` or `INSUFFICIENT`.

## 9. Lineage

Minimum lineage:

```text
Feature Value
 <- Feature Definition + Version
 <- Transformation/Code Version
 <- Input Feature IDs and/or Observation IDs
 <- Evidence IDs
 <- Original Source Record/Document
```

Financial ratios additionally expose calculation components.

## 10. Online versus offline semantics

Physical storage may differ, but semantic equivalence is required.

- **Online/current features:** low-latency signal evaluation and serving.
- **Offline/historical features:** training, backtesting, portfolio analysis and audit.

Both reference the same feature definitions. Training datasets must be generated with point-in-time correctness from historical feature/evidence state.

## 11. Feature ownership

Each definition has a business owner and technical owner. Material feature-definition changes require versioning, validation and an effective date. Models and policies pin compatible feature versions rather than consuming an unversioned name.

## 12. Feature groups

Initial groups:

```text
REPAYMENT
LIQUIDITY_UTILIZATION
FINANCIAL_LIQUIDITY
LEVERAGE_SOLVENCY
PROFITABILITY
CASH_FLOW
WORKING_CAPITAL
COVENANT_CONDUCT
COLLATERAL
EXTERNAL_RATING
MANAGEMENT_GOVERNANCE
RELATIONSHIP_GRAPH
SECTOR_MACRO
DATA_QUALITY
```

## 13. Feature computation modes

- `EVENT_DERIVED` — incrementally updated from event streams.
- `SNAPSHOT_DERIVED` — calculated from authoritative periodic snapshots.
- `DOCUMENT_DERIVED` — calculated/extracted from financial documents.
- `GRAPH_DERIVED` — relationship/network computation.
- `EXTERNAL_DERIVED` — approved external source.
- `MODEL_DERIVED` — output/embedding/latent representation; use sparingly as a governed model artefact.

## 14. Reproducibility invariant

For any material feature used in a signal or prediction, the platform must be able to reproduce or explain why it cannot reproduce the value from the pinned definition/version and retained inputs. A feature lacking adequate lineage cannot be used as authoritative evidence for a material human-approved EWS action.

## 15. Feature freshness policy

Freshness is feature-specific. Daily CBS utilization, quarterly audited ratios and annual collateral valuation cannot share one global TTL. Each feature definition specifies expected refresh cadence and staleness thresholds by segment/product where necessary.

## 16. Feature change events

The feature platform may publish derived events such as:

```text
feature.value.updated
feature.quality.degraded
feature.became_stale
feature.definition.activated
feature.recalculated
```

Part III will define their event contracts and Kafka topic strategy.
