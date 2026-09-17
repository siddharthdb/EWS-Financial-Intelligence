# EWS 2.0 — Phase-1 Priority Signal Contracts

**Status:** Draft / Part II  
**Scope:** Corporate counterparty EWS  
**Purpose:** Convert high-value signal taxonomy entries into implementation-grade semantic contracts.

## 1. Contract rules

A signal contract defines semantic meaning and required inputs. A signal policy defines institution-specific thresholds, windows, severity mapping and risk impact. Regulatory classifications such as SMA/NPA remain separate authoritative classifications derived under applicable policy; EWS signals may precede, predict or contextualise them.

Every signal instance must carry: entity scope, effective time, knowledge time, evidence IDs, feature snapshots, policy/rule/model version, severity, confidence, data-quality assessment, lifecycle status and human-validation requirement.

## 2. Source authority tiers

Evidence is classified by authority for the fact asserted:

- **T1 — Authoritative internal/official:** CBS/LMS/LOS booked transactions, repayment schedules, sanctioned limits, signed/audited financials, official regulator/court/company-registry records.
- **T2 — Verified contracted external:** rating agency, bureau, market-data provider, verified GST/tax feeds where available, approved valuation providers.
- **T3 — Corroborated intelligence:** reputable news, company announcements, exchange disclosures, research feeds.
- **T4 — Unverified/open intelligence:** web/social/uncorroborated sources.

Authority is contextual: an exchange filing may be T1/T2 for the filing itself but not proof that every statement within it is economically correct. T4 evidence alone must not create an authoritative adverse classification.

## 3. Data-quality assessment

Each signal computes a data-quality state independently of risk severity:

```text
COMPLETE | PARTIAL | STALE | CONFLICTED | UNVERIFIED | INSUFFICIENT
```

The assessment considers completeness, freshness, reconciliation, source authority, extraction confidence and entity-resolution confidence. A high-risk calculation with poor evidence quality must expose degraded confidence or `INSUFFICIENT_EVIDENCE` rather than hiding uncertainty.

## 4. Priority contracts

### P01 — DPD_EMERGED

**Meaning:** A contractual payment obligation has become overdue.

- Sources: LMS/CBS repayment schedule and booked receipts (T1).
- Canonical observations: `OBLIGATION_DUE`, `PAYMENT_RECEIVED`, `OBLIGATION_OVERDUE`.
- Features: `current_dpd`, `overdue_amount`, `overdue_obligation_count`.
- Detection: deterministic comparison of due obligation versus settled amount as of day-end/business policy.
- Evidence: schedule item + settlement/absence-of-settlement evidence.
- Risk dimensions: Repayment/Conduct, Liquidity.
- Severity: policy-driven by DPD, amount, exposure and recurrence.
- Confidence: normally very high if schedule and receipts reconcile.
- Resolution: payment/cure; preserve historical episode.
- Human validation: generally not required for the factual DPD observation; required for discretionary EWS interpretation/action.

### P02 — DPD_WORSENING

**Meaning:** Repayment delinquency is materially increasing.

- Features: current DPD, DPD velocity, rolling max DPD, cure/relapse count.
- Detection: trend/state transition, not a duplicate of `DPD_EMERGED`.
- Risk dimensions: Repayment/Conduct, Credit Deterioration.
- Suppression: do not emit daily duplicates while DPD is unchanged; emit on configured material state transition.
- Regulatory state: SMA/NPA classification is represented separately and calculated under applicable regulatory policy.

### P03 — REPEATED_PAYMENT_RETURN

**Meaning:** Multiple borrower-issued payment instruments/debit instructions were returned for insufficient funds/drawing power or equivalent liquidity reason.

- Sources: CBS/payment systems (T1).
- Observations: `PAYMENT_INSTRUCTION_RETURNED` with reason code.
- Features: return count/value by rolling window, return rate, counterparty baseline.
- Detection: count/rate policy over event-time window.
- Risk dimensions: Liquidity, Repayment/Conduct.
- Deduplication: transaction/instruction ID.
- Exclusions: technical/network/beneficiary-detail returns should not be treated as liquidity returns.
- Decay: configurable after sustained period without recurrence.

### P04 — HIGH_VALUE_PAYMENT_RETURN

**Meaning:** A material-value payment instrument was returned for a borrower-funding reason.

- Materiality: absolute value plus exposure-relative threshold.
- Distinct from repeated returns: one event may be sufficiently material.
- Human validation: configurable based on materiality.

### P05 — WORKING_CAPITAL_UTILIZATION_HIGH

**Meaning:** Fund-based working-capital utilization is persistently close to available sanctioned/drawing capacity.

- Sources: facility master, sanctioned limit, drawing power, CBS outstanding (T1).
- Features: utilization ratio, available headroom, duration above policy bands.
- Formula baseline: `outstanding / min(applicable_limit, drawing_power)` where product semantics require; actual formula is product-policy versioned.
- Risk dimensions: Liquidity, Working Capital.
- Data quality: drawing-power freshness is explicit; stale DP must be visible.

### P06 — WORKING_CAPITAL_UTILIZATION_SPIKE

**Meaning:** Utilization increased materially relative to the counterparty's recent baseline.

- Features: current utilization, 30/90-day baseline, delta, z-score/robust deviation.
- Methods: statistical/anomaly; segment policy controls minimum history.
- Difference from P05: detects change even when absolute utilization remains below a static high threshold.

### P07 — LIMIT_EXCESS_RECURRING

**Meaning:** Outstanding exposure repeatedly or continuously exceeds applicable sanctioned/drawing capacity.

- Sources: facility/DP/outstanding (T1).
- Features: excess amount, excess ratio, consecutive days, episode count.
- Regulatory linkage: regulatory SMA/out-of-order logic is separately implemented and versioned; EWS may use earlier warning bands.
- Resolution: return within applicable capacity; retain episode history.

### P08 — LC_DEVOLVEMENT

**Meaning:** A letter-of-credit obligation devolved onto the borrower/lender.

- Sources: trade-finance system/CBS (T1).
- Features: devolved amount, age, count, exposure-relative amount.
- Risk dimensions: Liquidity, Repayment/Conduct.
- Correlation: unpaid devolvement increases materiality.

### P09 — BG_INVOCATION

**Meaning:** A bank guarantee has been invoked.

- Sources: trade-finance/guarantee system (T1).
- Features: invoked amount, reason/category, beneficiary, paid/unpaid status.
- Risk dimensions: Liquidity, Operational Conduct, Legal/External depending context.
- Note: invocation is not automatically borrower default or fraud.

### P10 — DSCR_DERIORATION

**Meaning:** Debt-service capacity has materially weakened.

- Sources: canonical financial statements + debt schedule.
- Features: DSCR current/prior, trend, covenant level, peer band.
- Detection: absolute band plus trend policy.
- Data quality: statement type (audited/provisional), period, extraction confidence and reconciliation required.
- Risk dimensions: Cash Flow/Debt Service.

### P11 — CURRENT_RATIO_DERIORATION

**Meaning:** Short-term balance-sheet liquidity has materially weakened.

- Features: current ratio, historical trend, sanctioned projection, peer percentile.
- Detection: trend + optional absolute/covenant threshold.
- Guardrail: sector-specific interpretation; never assume a universal healthy ratio.

### P12 — DEBT_EBITDA_DERIORATION

**Meaning:** Leverage relative to operating earnings has materially worsened.

- Features: gross/net debt to EBITDA as defined by policy, prior periods, peer distribution.
- Guardrails: negative/near-zero EBITDA requires special treatment rather than misleading ratio arithmetic.

### P13 — OPERATING_PROFIT_MATERIAL_DECLINE

**Meaning:** Operating performance materially deteriorated versus history, sanctioned projections or comparable period.

- Features: EBITDA/EBIT, YoY change, projection variance, peer change.
- Detection: policy can combine absolute variance and relative deterioration.
- Evidence: financial statement/management accounts with provenance.

### P14 — OPERATING_CASH_FLOW_NEGATIVE

**Meaning:** Operating cash flow is negative for a relevant period.

- Correlation: persistent negative CFO plus positive accounting profit is separately captured by `CASH_FLOW_PROFIT_DIVERGENCE`.
- Guardrail: seasonality and growth-stage business models require contextual policy.

### P15 — RECEIVABLE_DAYS_DERIORATION

**Meaning:** Collection cycle has lengthened materially.

- Features: DSO/receivable days, trend, ageing buckets, peer delta, customer concentration.
- Detection: trend/anomaly plus policy threshold.
- Correlation: utilization increase + DSO deterioration is a strong working-capital stress combination but remains a hypothesis until evaluated.

### P16 — INVENTORY_DAYS_DERIORATION

**Meaning:** Inventory holding period has increased materially.

- Features: DIO/inventory days, ageing where available, sector/seasonality baseline.
- Guardrail: distinguish deliberate stocking/commodity effects from distress using context.

### P17 — COVENANT_BREACH

**Meaning:** A measured contractual covenant is outside its permitted terms.

- Sources: facility covenant definition + verified feature values.
- Evidence: covenant contract/version and calculation snapshot.
- Features: actual, threshold, headroom, breach duration.
- Severity: covenant type/materiality/waiver status.
- Resolution: cure/waiver/amendment creates new governed records; original breach remains immutable.

### P18 — FINANCIAL_MONITORING_DATA_DELAY

**Meaning:** Required financial/monitoring information was not received by its governed due date.

- Sources: document obligations/calendar + submission registry.
- Features: days late, recurrence, document type.
- Risk dimensions: Operational Conduct, Information Quality.
- Important: absence of data is itself an observation but must not be fabricated as financial deterioration.

### P19 — EXTERNAL_RATING_DOWNGRADE

**Meaning:** An approved external rating agency downgraded the counterparty/instrument.

- Sources: contracted/official rating feed or verified agency publication (T2/T1-contextual).
- Features: notch movement, prior/current rating, outlook, watch status.
- Deduplication: agency + instrument/entity + rating action ID/date.
- Risk dimensions: External/Credit Deterioration.

### P20 — COLLATERAL_COVER_EROSION

**Meaning:** Verified collateral value relative to secured exposure has materially declined.

- Sources: collateral registry, approved valuation, exposure (T1/T2).
- Features: collateral cover, haircut-adjusted value, valuation age, concentration.
- Data quality: stale valuation explicitly reduces confidence.
- Guardrail: legal enforceability/perfection is distinct from market value.

### P21 — FUND_DIVERSION_SUSPECTED

**Meaning:** Evidence suggests sanctioned funds may have been deployed outside approved end use.

- Sources: disbursement, transactions, sanctioned purpose, invoices/contracts, related-party graph.
- Methods: deterministic reconciliation + anomaly + graph analytics.
- Risk intent: Fraud/Integrity and Operational Conduct.
- Status semantics: always `SUSPECTED` until governed investigation determines outcome.
- Human validation: mandatory.
- AI role: summarize paths/evidence; never independently classify diversion.

### P22 — RELATED_PARTY_TRANSFER_SPIKE

**Meaning:** Transfers to known related entities increased materially relative to expected/historical behaviour.

- Sources: transactions + temporal relationship graph.
- Features: amount/share/velocity by related party, baseline, sanctioned purpose.
- Guardrail: related-party transfer is not intrinsically adverse; signal expresses anomalous/material change.
- Correlation: can contribute to diversion hypothesis only with additional evidence.

### P23 — AUDITOR_QUALIFICATION_ADVERSE

**Meaning:** Audited financial statements contain a material qualification/adverse audit matter relevant to risk monitoring.

- Sources: signed audit report/document (T1 for document, extracted fact quality tracked).
- Methods: NLP extraction followed by deterministic classification/analyst verification for material cases.
- Evidence: page/span/document hash and extracted structured category.
- Human validation: required before material score impact where NLP extraction is involved.

### P24 — KEY_MANAGEMENT_RESIGNATION

**Meaning:** A material key management/personnel departure occurred.

- Sources: official filing/exchange/company announcement preferred; news corroboration secondary.
- Features: role criticality, succession, frequency, proximity to other stress signals.
- Guardrail: resignation alone is contextual, not automatic deterioration.

### P25 — GROUP_ENTITY_DISTRESS

**Meaning:** A related group entity entered a materially adverse risk state capable of transmitting risk to the monitored counterparty.

- Sources: temporal relationship graph + verified risk event on related entity.
- Features: relationship type/ownership/control, guarantees, intercompany exposure, dependency, distressed entity severity.
- Methods: graph + deterministic propagation policy initially; learned graph propagation later only after validation.
- Guardrail: do not propagate full risk score merely because entities share a group.

## 5. Regulatory classification adapter

EWS does not replace regulatory asset classification. A dedicated policy adapter calculates applicable states such as SMA/NPA using authoritative obligations, DPD/out-of-order state and the regulatory rule version.

```text
Operational facts -> Regulatory Classification Adapter -> SMA/NPA state
        |
        +-> EWS feature/signal engines -> earlier/broader warning signals
```

The two outputs may correlate but have different semantics, governance and effective dates.

## 6. Signal deduplication and episodes

Repeated observations should form signal episodes rather than flooding analysts.

A signal episode has `openedAt`, `lastObservedAt`, `peakSeverity`, `currentSeverity`, `occurrenceCount`, `evidenceIds`, `state`, `cooldownUntil` and `resolvedAt`.

Emit a new analyst-visible update when severity changes materially, new evidence changes the hypothesis, the policy requires escalation, or a resolved signal recurs after the configured reset period.

## 7. Severity, confidence and materiality

These are orthogonal:

- **Severity:** magnitude of potential risk consequence.
- **Confidence:** reliability that the asserted signal is true.
- **Materiality:** importance relative to borrower/exposure/portfolio.

A high-value adverse-news allegation can be high materiality but low confidence. A small payment return can be high confidence but low materiality.

## 8. Score-impact rule

Signal contracts do not hard-code `+8`, `+12`, etc. Score impact is supplied by a separately governed risk aggregation policy. This avoids double-counting correlated signals and allows calibration by segment and portfolio.

## 9. Phase-1 implementation order

**Wave A — authoritative operational evidence:** P01-P09, P17-P18.  
**Wave B — canonical financial intelligence:** P10-P16, P20.  
**Wave C — external/relationship/document intelligence:** P19, P21-P25.

This order maximizes early signal quality and creates labelled feedback before relying heavily on probabilistic external intelligence.
