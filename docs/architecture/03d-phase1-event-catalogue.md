# EWS 2.0 — Phase-1 Event Catalogue

**Status:** Draft / Part III  
**Scope:** Corporate EWS Phase 1

## 1. Contract boundary

Part II defines risk semantics. Part III transports those semantics without collapsing facts, features, signals and decisions into one event type.

```text
OPERATIONAL FACT
    ↓
CANONICAL DOMAIN EVENT
    ↓
FEATURE COMPUTATION
    ↓
FEATURE EVENT
    ↓
SIGNAL POLICY / ANALYTICAL ENGINE
    ↓
SIGNAL EVENT
    ↓
CORRELATION / RISK ASSESSMENT
    ↓
HUMAN DECISION
    ↓
OFFICIAL EWS STATE
```

A producer at an ingestion boundary must not emit `signal.*` merely because it observed an adverse-looking source record.

## 2. Phase-1 canonical domain events

| Event type | Aggregate / key | Primary source | Meaning | Typical downstream feature/signal |
|---|---|---|---|---|
| `obligation.dpd.changed` | facilityId | LMS/CBS | authoritative DPD state changed | current/max DPD → DPD signals |
| `payment.instruction.returned` | accountId | CBS/payment system | payment instruction returned | return count/value/rate |
| `facility.limit.changed` | facilityId | LOS/CBS | sanctioned/applicable limit changed | capacity/headroom/utilization |
| `facility.drawing_power.changed` | facilityId | CBS/stock statement | drawing power changed | capacity/utilization/excess |
| `facility.outstanding.changed` | facilityId | CBS/LMS | facility exposure/outstanding changed | utilization/headroom |
| `trade_finance.lc.devolved` | facilityId | trade finance | LC devolved | LC devolvement signal |
| `trade_finance.bg.invoked` | facilityId | trade finance | guarantee invoked | BG invocation signal |
| `financial.statement.received` | counterpartyId | document ingestion | financial statement received | document workflow/freshness |
| `financial.statement.validated` | counterpartyId | financial intelligence | statement facts validated | financial ratios/trends |
| `covenant.measurement.updated` | facilityId | covenant engine | governed covenant measurement updated | headroom/breach |
| `monitoring.document.status.changed` | counterpartyId | monitoring registry | required monitoring item status changed | delay days |
| `collateral.valuation.updated` | collateralId | collateral/valuation | approved valuation changed | cover/valuation age |
| `rating.action.published` | counterpartyId | approved rating source | verified rating action | notch/outlook/watch features |
| `relationship.changed` | relationshipId | entity resolution/registry | relationship created/changed/ended | graph features |
| `management.position.changed` | counterpartyId | registry/exchange/verified source | management/director role changed | governance features |
| `transaction.posted` | accountId | CBS | booked transaction fact | inflow/fund-flow/related-party features |

## 3. Domain-event design rules

1. Events use past-tense factual names.
2. Payload contains the fact and identifiers needed to interpret it, not a generated risk conclusion.
3. Source-specific codes are retained where required for audit but normalized semantic fields are also provided.
4. Corrections/reversals are explicit events or revisions; prior history is not silently rewritten.
5. Monetary amounts carry currency.
6. Financial/accounting facts carry period/scope and source status where relevant.
7. Event identity is stable across publisher retries.
8. Aggregate sequence is used where authoritative ordered state transitions exist.

## 4. Derived feature event

`feature.value.updated` is the standard event indicating a new governed feature value/revision.

It references the Part-II feature contract rather than duplicating feature semantics in the event name.

Examples:

```text
featureName = current_dpd
featureDefinitionId = REPAYMENT.CURRENT_DPD
entity = FACILITY/F123
value = 12
state = VALUE
knowledgeTime = ...
```

```text
featureName = returned_payment_count_30d
featureDefinitionId = CONDUCT.RETURNED_PAYMENT_COUNT_30D
entity = ACCOUNT/A456
value = 3
state = VALUE
```

```text
featureName = wc_utilization_ratio
featureDefinitionId = LIQUIDITY.WC_UTILIZATION_RATIO
entity = FACILITY/F123
value = 0.94
state = VALUE
```

A feature update may cause no signal at all. Detection remains the responsibility of signal policies/models.

## 5. Signal events

| Event type | Meaning |
|---|---|
| `signal.detected` | analytical policy/engine produced a new signal candidate |
| `signal.proposed` | signal entered governed analyst workflow |
| `signal.updated` | material evidence/severity/confidence changed |
| `signal.accepted` | authorized human/system policy accepted the proposed signal |
| `signal.rejected` | authorized analyst rejected it |
| `signal.resolved` | active signal condition resolved/mitigated |
| `signal.reopened` | resolved episode recurred under policy rules |

Signal payload references `signalId`, `signalType`, policy/model versions, feature snapshots and evidence IDs. It does not copy every underlying transaction/document into Kafka.

## 6. Decision and risk events

`signal.disposition.recorded` captures human disposition as an immutable governance fact.

`risk.assessment.proposed` captures the analytical/policy assessment before approval.

`risk.assessment.approved` captures the authorized official EWS risk state.

These are separate because an analyst can reject/modify a proposal without altering the original machine output.

## 7. End-to-end example — returned payments

### Step 1 — source fact
CBS reports a returned payment instruction.

```text
payment.instruction.returned
key = accountId
reason = INSUFFICIENT_FUNDS
amount = INR 2,500,000
```

The canonical event references the authoritative transaction/source record evidence.

### Step 2 — feature processing
A stateful feature processor updates:

```text
returned_payment_count_30d = 3
returned_payment_value_30d = INR 6,800,000
returned_payment_rate_30d = ...
```

Technical/network returns are excluded according to the governed reason-code policy.

### Step 3 — signal policy
The active `REPEATED_PAYMENT_RETURN` policy evaluates the new feature snapshot.

If its condition is satisfied it creates a Signal Instance and emits:

```text
signal.detected
signalType = REPEATED_PAYMENT_RETURN
policyVersion = ...
featureSnapshotIds = [...]
evidenceIds = [...]
severity = ...
confidence = ...
materiality = ...
```

The numeric threshold/window belongs to the versioned policy, not to the event contract.

### Step 4 — correlation
The correlation layer may combine this signal with independent signals such as:

```text
WORKING_CAPITAL_UTILIZATION_HIGH
RECEIVABLE_DAYS_DERIORATION
RATING_OUTLOOK_NEGATIVE
```

and produce an `EMERGING_LIQUIDITY_STRESS` hypothesis while preserving all source signal IDs.

### Step 5 — analyst workflow
The proposed signal/hypothesis is placed into analyst review. Analyst action creates:

```text
signal.disposition.recorded
```

with actor, timestamp, decision, reason and before/after references.

### Step 6 — official state
Only the governed risk workflow emits:

```text
risk.assessment.approved
```

The original returned-payment event remains a fact and is never mutated into a risk decision.

## 8. End-to-end example — working-capital utilization

Three independent facts can arrive in any order:

```text
facility.limit.changed
facility.drawing_power.changed
facility.outstanding.changed
```

The feature processor reconstructs applicable capacity under the pinned feature definition and calculates:

```text
wc_utilization_ratio
wc_available_headroom
wc_utilization_delta_30d
```

A stale drawing-power input can set feature quality to `STALE` or `PARTIAL`; the signal quality gate may then degrade confidence or produce `INSUFFICIENT_EVIDENCE` instead of treating stale capacity as current truth.

## 9. End-to-end example — suspected fund diversion

```text
transaction.posted
relationship.changed
facility/disbursement facts
sanction-purpose evidence
invoice/end-use evidence
        ↓
reconciliation + graph + anomaly features
        ↓
FUND_DIVERSION_SUSPECTED
        ↓
mandatory human investigation
```

No transaction event, graph engine or LLM emits a confirmed fraud/diversion classification.

## 10. Schema-evolution rule

Additive optional fields require defaults appropriate to Avro schema resolution. A field rename uses a governed alias only where semantics are unchanged. A semantic reinterpretation is not hidden behind an alias; it requires a new contract/event version.

Event `eventVersion` represents the semantic contract version and is not replaced by the registry's internal schema identifier.

## 11. Initial implementation order

### Wave A — operational facts

1. `obligation.dpd.changed`
2. `payment.instruction.returned`
3. `facility.limit.changed`
4. `facility.drawing_power.changed`
5. `facility.outstanding.changed`
6. `trade_finance.lc.devolved`
7. `trade_finance.bg.invoked`
8. `covenant.measurement.updated`
9. `monitoring.document.status.changed`

### Wave B — financial/document facts

10. `financial.statement.received`
11. `financial.statement.validated`
12. `collateral.valuation.updated`

### Wave C — external/graph facts

13. `rating.action.published`
14. `relationship.changed`
15. `management.position.changed`
16. `transaction.posted` for governed fund-flow analytics

### Common derived contracts

17. `feature.value.updated`
18. `signal.detected`
19. `signal.proposed`
20. `signal.disposition.recorded`
21. `risk.assessment.proposed`
22. `risk.assessment.approved`
