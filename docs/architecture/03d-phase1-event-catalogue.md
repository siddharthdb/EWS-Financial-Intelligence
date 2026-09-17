# EWS 2.0 — Initial Global Event Catalogue

**Status:** Draft / Part III  
**Scope:** Corporate EWS; initial India/US/UK reference implementations

## 1. Contract boundary

```text
SOURCE FACT
    ↓
CANONICAL OBSERVATION / DOMAIN EVENT
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
    ↓                    ↓
HUMAN DECISION      JURISDICTION ADAPTER
    ↓                    ↓
OFFICIAL EWS       CLASSIFICATION STATE
```

Ingestion producers never emit adverse analytical signals merely because a source fact appears adverse.

## 2. Internal canonical events

| Event type | Key | Meaning | Typical downstream |
|---|---|---|---|
| `obligation.dpd.changed` | facilityId | authoritative DPD state changed | DPD features/signals |
| `payment.instruction.returned` | accountId | payment instruction returned | return count/value/rate |
| `facility.limit.changed` | facilityId | approved/committed limit changed | capacity/utilization |
| `facility.drawing_power.changed` | facilityId | drawing power/borrowing-base capacity changed | WC specialization |
| `facility.outstanding.changed` | facilityId | exposure changed | utilization/headroom |
| `trade_finance.lc.devolved` | facilityId | LC devolved | LC_DEVOLVEMENT |
| `trade_finance.guarantee.invoked` | facilityId | guarantee invoked | GUARANTEE_INVOCATION |
| `financial.statement.received` | counterpartyId | statement received | workflow/freshness |
| `financial.statement.validated` | counterpartyId | financial facts validated | ratios/trends |
| `covenant.measurement.updated` | facilityId | covenant measurement changed | breach/headroom |
| `monitoring.requirement.status.changed` | counterpartyId | required monitoring item changed | delay/non-cooperation |
| `collateral.valuation.updated` | collateralId | valuation changed | cover/age |
| `relationship.changed` | relationshipId | relationship changed | graph features |
| `transaction.posted` | accountId | booked transaction | cash-flow/graph features |

Historical aliases such as `trade_finance.bg.invoked` remain readable but new producers use the normalized event name.

## 3. External canonical observations

| Event type | Initial source examples | Key before/after resolution | Typical downstream |
|---|---|---|---|
| `rating.action.published` | rating provider/official publication | provider entity/instrument → entityId | rating features/signals |
| `management.position.changed` | company registry/issuer filing | source entity → entityId | governance features |
| `ownership.control.changed` | company/beneficial-owner registry | source entity → entityId | ownership/control features |
| `security.interest.created` | internal security, authorised UCC, Companies House charge, equivalent registry | debtor/security record → entityId | security-interest features |
| `security.interest.released` | same | security record → entityId | creditor/security state |
| `credit.facility.amended` | internal facility/issuer disclosure/licensed loan source | facility/debt instrument | refinancing features |
| `covenant.waiver.disclosed` | internal/issuer/lender disclosure | facility/instrument | waiver frequency |
| `facility.maturity.extended` | internal/issuer disclosure | facility/instrument | amend-and-extend |
| `facility.pricing.changed` | internal/authoritative disclosure | facility/instrument | funding cost |
| `debt.default.disclosed` | authoritative filing/lender source | debt instrument/entity | default/refinancing evidence |
| `debt.acceleration.disclosed` | authoritative filing/legal source | debt instrument/entity | DEBT_ACCELERATION |
| `insolvency.proceeding.started` | court/registry | legal party → entityId | FORMAL_INSOLVENCY_PROCEEDING |
| `insolvency.proceeding.status.changed` | court/registry | case/entity | legal state |
| `financial.reporting.non_reliance.disclosed` | issuer/regulatory filing | entityId | reporting reliability |
| `internal.control.weakness.disclosed` | issuer/regulatory filing | entityId | reporting/control concern |
| `financial.statement.restated` | issuer/company filing | entityId | restatement features |
| `market.bond.trade.observed` | authorised/contracted market feed | securityId | market features |
| `market.security.reference.changed` | reference-data provider | securityId | issuer/security resolution |

Jurisdiction/procedure/legal subtype is payload/evidence metadata, not encoded into separate country-specific event names unless semantics genuinely differ.

## 4. Domain-event rules

Events are factual/past-tense; retain source codes and normalized fields; corrections/reversals are explicit; amounts carry currency; financial facts carry period/accounting scope; event identity is stable; authoritative sequences are retained; external observations carry jurisdiction, source authority, source-rights reference and entity/security-resolution reference.

## 5. Derived feature event

`feature.value.updated` remains the common governed feature-change event. Feature names are semantic contracts, not topic names.

Examples:

```text
current_dpd
returned_payment_count_30d
utilization_ratio
wc_utilization_ratio
credit_spread_change_bps_30d
new_security_interest_count_90d
covenant_waiver_count_12m
```

A feature update may cause no signal.

## 6. Signal events

`signal.detected`, `signal.proposed`, `signal.updated`, `signal.accepted`, `signal.rejected`, `signal.resolved`, `signal.reopened`.

New signal instances use normalized global signal names. Historical aliases are not silently rewritten.

## 7. Decision, risk and classification events

- `signal.disposition.recorded` — immutable human disposition.
- `risk.assessment.proposed` — analytical/policy assessment.
- `risk.assessment.approved` — authorized EWS risk state.
- `classification.state.proposed` / `classification.state.approved` / `classification.state.changed` — namespaced accounting/prudential/supervisory state.

Machine risk output and jurisdiction classification remain independently auditable.

## 8. Example — returned payment

```text
payment.instruction.returned
   ↓
returned_payment_count_30d / value / rate
   ↓
REPEATED_PAYMENT_RETURN policy
   ↓
signal.detected
   ↓
correlation / analyst disposition
   ↓
risk.assessment.approved
```

Threshold/window is policy, not event semantics.

## 9. Example — utilization

```text
facility.limit.changed
facility.outstanding.changed
[facility.drawing_power.changed where product requires]
       ↓
utilization_ratio
[wc_utilization_ratio specialization]
       ↓
UTILIZATION_HIGH / UTILIZATION_SPIKE
```

This prevents working-capital/drawing-power mechanics from defining the global utilization ontology.

## 10. Example — US/UK security-interest activity

```text
US authorised UCC record --------+
                                  +--> security.interest.created
UK Companies House charge -------+           |
                                              v
                             entity resolution
                                              |
                                              v
                          new_security_interest_count_90d
                                              |
                                              v
                          NEW_SECURITY_INTEREST_ACTIVITY
```

The legal subtype remains distinct in evidence.

## 11. Example — refinancing stress

```text
credit.facility.amended
facility.maturity.extended
facility.pricing.changed
covenant.waiver.disclosed
rating.action.published
market.bond.trade.observed
        ↓
refinancing + funding + market features
        ↓
REFINANCING_RISK_INCREASE
COVENANT_WAIVER_FREQUENCY
MARKET_IMPLIED_CREDIT_STRESS
        ↓
REFINANCING_STRESS hypothesis
```

## 12. Example — insolvency

```text
court / insolvency registry
       ↓
insolvency.proceeding.started
  {jurisdiction, procedureType, caseRef}
       ↓
FORMAL_INSOLVENCY_PROCEEDING
       ↓
EWS correlation
       +--------------------------+
                                  ↓
                       jurisdiction classification
                       where separately applicable
```

## 13. Example — suspected diversion

Transactions + relationship + financing-purpose evidence produce features and `FUND_DIVERSION_SUSPECTED`; human investigation remains mandatory. No event, graph engine or LLM emits a confirmed legal/fraud conclusion autonomously.

## 14. Schema evolution

Additive optional fields use compatible defaults. Semantic reinterpretation requires new event contract/version. Aliases are permitted only where meaning is unchanged. `eventVersion` is semantic and independent of registry schema ID.

## 15. Implementation waves

### Wave A — internal operational spine
`obligation.dpd.changed`, `payment.instruction.returned`, facility capacity/outstanding, trade finance, covenant, monitoring.

### Wave B — financial/document/collateral
financial statements, collateral valuation, financial reporting facts.

### Wave C — structured external/market
ratings, management/ownership, security interests, financing amendments/waivers/default/acceleration, insolvency and market data.

Wave C can run in parallel with A/B for portfolios such as public US/UK corporates where authoritative structured external sources are available.

### Common derived contracts
`feature.value.updated`, signal lifecycle, disposition, risk assessment and classification-state events.