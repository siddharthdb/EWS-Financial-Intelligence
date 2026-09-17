# EWS 2.0 — Phase-1 Feature Catalogue

**Status:** Draft / Part II  
**Important:** Formulas below are baseline definitions for architecture. Institution credit policy owns final definition/version and calibration.

## 1. Repayment features

### `current_dpd`
- Definition: elapsed calendar days since earliest currently unpaid contractual due amount, under applicable product/regulatory calculation semantics.
- Grain: facility/obligation; counterparty aggregate uses a separate max/weighted definition.
- Source: LMS/CBS authoritative schedule + receipts.
- Update: event/day-end.
- Missingness: INVALID if schedule cannot reconcile; never default to zero.
- Signals: `DPD_EMERGED`, `DPD_WORSENING`.

### `max_dpd_30d`, `max_dpd_90d`
- Definition: maximum point-in-time DPD observed in window.
- Grain: facility/counterparty variant.
- Point-in-time: computed only from states known by each historical timestamp.

### `returned_payment_count_30d`
- Definition: count of borrower-funding-related returned payment instructions in rolling 30-day event-time window.
- Excludes: technical/network/beneficiary-detail returns under reason-code policy.
- Companion features: `returned_payment_value_30d`, `returned_payment_rate_30d`.

## 2. Liquidity and utilization features

### `wc_utilization_ratio`
- Baseline: `eligible_outstanding / applicable_capacity`.
- `applicable_capacity` may be sanctioned limit, drawing power, or policy-defined minimum/combination depending product.
- Grain: facility; counterparty aggregation separate.
- Components retained: outstanding, sanctioned limit, drawing power, applicable-capacity rule.
- Quality: drawing-power freshness mandatory.

### `wc_available_headroom`
- Baseline: `applicable_capacity - eligible_outstanding`.
- Units: currency plus optional ratio variant.
- Negative values represent excess.

### `wc_utilization_delta_30d`
- Baseline: current utilization minus configured 30-day baseline (mean/median/other versioned definition).
- Used by utilization-spike detection.

### `limit_excess_days_30d`
- Definition: number of days in rolling 30 days where eligible outstanding exceeded applicable capacity under policy.
- Companion: `max_limit_excess_ratio_30d`, `consecutive_limit_excess_days`.

### `credit_inflow_change_30d_90d`
- Definition: recent account credit inflow versus prior/baseline period, normalized for seasonality where configured.
- Guardrail: exclude lender disbursements/internal transfers as configured.

## 3. Financial liquidity features

### `current_ratio`
- Baseline: `current_assets / current_liabilities`.
- Definition ID pins accounting adjustments, consolidation scope and classification treatment.
- Missingness: INVALID if denominator is zero/invalid; no synthetic neutral value.
- Context: compare with historical, covenant and peer/sector definitions rather than universal threshold.

### `quick_ratio`
- Baseline architecture definition: liquid/quick current assets divided by current liabilities; exact exclusions are policy-defined.
- Component lineage mandatory.

## 4. Leverage and debt-service features

### `debt_ebitda`
- Baseline: policy-defined debt / policy-defined EBITDA.
- Required components: gross/net debt choice, lease treatment, related-party debt treatment, EBITDA adjustments, period/annualisation.
- Guardrail: negative or near-zero EBITDA produces a governed special state rather than misleading numeric ratio.

### `tol_atnw`
- Baseline: total outside liabilities / adjusted tangible net worth.
- Exact treatment of quasi-equity, revaluation reserves, intangibles and related-party items is definition-versioned.

### `dscr`
- Definition: policy-defined cash available for debt service divided by policy-defined debt service for matched period.
- Components: numerator and principal/interest obligations retained individually.
- Guardrails: period alignment and annualisation explicit; projected and actual DSCR are separate feature definitions.

### `interest_coverage_ratio`
- Baseline: policy-defined operating earnings / interest expense.
- EBIT/EBITDA variant must be encoded in definition ID, not hidden in implementation.

## 5. Profitability features

### `revenue_yoy_change`
- Definition: comparable-period revenue percentage change.
- Quality: comparable period length/accounting scope required.

### `ebitda_margin`
- Baseline: policy-defined EBITDA / revenue.
- Companion: `ebitda_margin_change_yoy`, `ebitda_margin_peer_delta`.

### `operating_profit_projection_variance`
- Definition: actual operating profit versus sanctioned/approved projection for matched period.
- Projection version used at credit decision must be retained; do not compare against a subsequently revised plan without labelling it.

## 6. Cash-flow features

### `operating_cash_flow`
- Source: canonical cash-flow statement or governed derivation.
- Audited/provisional/extracted status retained.

### `ocf_to_ebitda`
- Purpose: identify divergence between accounting operating performance and cash conversion.
- Guardrail: sector/working-capital seasonality context required.

### `free_cash_flow`
- Formula is policy-defined; capital-expenditure and financing treatment pinned by definition.

## 7. Working-capital features

### `receivable_days`
- Baseline: policy-defined average/trailing receivables relative to credit sales/revenue, normalized to period days.
- Definition must state average vs closing balance and sales denominator.
- Companion: ageing-bucket features where debtor ageing exists.

### `inventory_days`
- Baseline: policy-defined average/trailing inventory relative to COGS, normalized to period days.
- Sector/seasonality context required.

### `payable_days`
- Baseline: policy-defined payables relative to purchases/COGS.
- Exact denominator pinned.

### `cash_conversion_cycle`
- Baseline: receivable days + inventory days - payable days, using compatible component definitions.

## 8. Covenant and conduct features

### `covenant_headroom`
- Definition: normalized distance between actual covenant metric and contractual threshold with directionality.
- Required inputs: covenant contract/version, feature value, waiver/amendment state.
- A covenant amendment does not rewrite prior headroom history.

### `monitoring_document_delay_days`
- Definition: days between governed submission due date and received date/current date.
- Grain: obligation/document type.
- Missing document is represented explicitly rather than zero.

## 9. Collateral features

### `collateral_cover_ratio`
- Baseline: policy/haircut-adjusted eligible collateral value / secured exposure.
- Components: market/assessed value, haircut, eligibility, valuation date, exposure.
- Separate features: `collateral_valuation_age_days`, `security_perfection_status`.
- Legal enforceability is not inferred from valuation.

## 10. External-rating features

### `external_rating_notch_change`
- Definition: ordinal movement on an agency-specific normalized scale.
- Do not assume direct equivalence between agencies without governed mapping.
- Companion: `rating_outlook_state`, `rating_watch_state`, `days_since_rating_action`.

## 11. Relationship and transaction-graph features

### `related_party_transfer_ratio_30d`
- Baseline: qualifying transfers to resolved related parties / qualifying total debit flow in window.
- Requires temporal relationship graph valid at transaction time/knowledge time.
- Quality depends on relationship/entity-resolution completeness.

### `related_party_transfer_delta_90d`
- Change versus historical baseline, preferably robust to episodic legitimate flows.

### `distressed_group_exposure_ratio`
- Definition: exposure economically dependent on/guaranteed by/materially linked to distressed related entities divided by relevant total exposure.
- Relationship strength and dependency are explicit; group membership alone does not imply full contagion.

## 12. Management/document features

### `key_management_change_count_180d`
- Count of verified changes in configured key roles.
- Source hierarchy and entity matching retained.

### `auditor_qualification_categories`
- Structured multi-label categories extracted from signed audit report.
- NLP extraction is not itself authoritative until quality/verification policy is met.
- Evidence includes document hash, page/span and extraction model version.

## 13. Data-quality features

Quality itself is available for monitoring/model inputs where appropriate:

- `financial_statement_age_days`
- `drawing_power_age_days`
- `collateral_valuation_age_days`
- `relationship_resolution_coverage`
- `financial_extraction_confidence`
- `source_reconciliation_status`

Models must not use quality proxies in ways that create unintended bias without validation; their primary purpose is governance and confidence gating.

## 14. Peer features

Peer comparisons are separate features, not embedded into base ratios:

```text
current_ratio_peer_percentile
debt_ebitda_peer_percentile
receivable_days_peer_zscore
ebitda_margin_peer_delta
```

Peer cohort definition is versioned by industry, scale, geography and other approved dimensions. Cohorts below minimum sample size return `NOT_AVAILABLE` rather than unstable statistics.

## 15. Feature dependency examples

```text
CBS outstanding ---------------------+
Facility sanctioned limit -----------+--> wc_utilization_ratio
Drawing power -----------------------+
                                        |
                                        +--> WORKING_CAPITAL_UTILIZATION_HIGH
                                        +--> WORKING_CAPITAL_UTILIZATION_SPIKE

Financial statement
  + current assets ------------------+
  + current liabilities -------------+--> current_ratio
                                        |
                                        +--> CURRENT_RATIO_DERIORATION

Transactions ------------------------+
Relationship graph ------------------+--> related_party_transfer_ratio_30d
                                        |
                                        +--> RELATED_PARTY_TRANSFER_SPIKE
                                        +--> FUND_DIVERSION_SUSPECTED (only with additional evidence/policy)
```

## 16. Training safety

Offline training extracts must use the feature value/version available at prediction time (`knowledgeTime <= predictionTime`). Later financial restatements, corrected entity links, subsequent defaults and analyst outcomes may be used as labels/evaluation data but must not leak into historical predictor features.

## 17. Phase-1 implementation recommendation

Implement deterministic/event-derived features first, then financial ratios, then graph/external features. Maintain feature definitions as code/configuration under version control and persist calculated feature snapshots used by every material signal/model prediction.
