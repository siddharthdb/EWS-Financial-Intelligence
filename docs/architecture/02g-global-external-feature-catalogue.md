# EWS 2.0 — Global External and Market Feature Catalogue

**Status:** Draft / Part II extension  
**Reference markets:** India, United States, United Kingdom

## 1. Purpose

This catalogue folds international external-intelligence research into governed feature semantics. Features are global where economic meaning is common; source acquisition and legal meaning remain jurisdiction-specific.

## 2. Refinancing and funding

- `nearest_material_debt_maturity_days`
- `debt_maturing_12m_ratio`
- `credit_facility_amendment_count_12m`
- `covenant_waiver_count_12m`
- `maturity_extension_count_24m`
- `pricing_spread_change_bps`
- `committed_facility_change_pct`
- `lender_count_change_12m`
- `secured_creditor_count_change_90d`
- `new_security_interest_count_90d`
- `security_interest_creation_velocity`

Potential signals: `REFINANCING_RISK_INCREASE`, `FUNDING_COST_INCREASE`, `COVENANT_WAIVER_FREQUENCY`, `AMEND_AND_EXTEND_ACTIVITY`, `NEW_SECURITY_INTEREST_ACTIVITY`, `LENDER_SUPPORT_WEAKENING`.

## 3. Market-implied credit

- `bond_price_drawdown_5d`
- `bond_price_drawdown_30d`
- `bond_yield_change_bps_5d`
- `bond_yield_change_bps_30d`
- `credit_spread_change_bps_5d`
- `credit_spread_change_bps_30d`
- `issuer_vs_sector_spread_zscore`
- `issuer_vs_rating_bucket_spread_zscore`
- `abnormal_bond_trade_volume`
- `market_liquidity_proxy_change`

Every market feature records security ID, issuer-resolution reference, currency, maturity/duration context, benchmark methodology, venue/source and liquidity-quality gate.

Potential signals: `MARKET_IMPLIED_CREDIT_STRESS`, `CREDIT_SPREAD_DIVERGENCE`, `BOND_PRICE_DISTRESS`, `MARKET_LIQUIDITY_DETERIORATION`.

## 4. External ratings

- `external_rating_notch_change_90d`
- `external_rating_notch_change_12m`
- `rating_outlook_state`
- `rating_watch_state`
- `days_since_rating_action`
- `rating_migration_velocity`

Original provider rating values remain preserved. A governed normalization layer provides ordinal/comparative semantics; provider scales are not assumed directly equivalent.

## 5. Legal / insolvency

- `active_insolvency_proceeding_flag`
- `insolvency_procedure_type`
- `days_since_insolvency_start`
- `bankruptcy_case_match_confidence`
- `debt_default_disclosure_count_12m`
- `debt_acceleration_count_12m`
- `affected_defaulted_debt_ratio`
- `adverse_legal_notice_count_90d`

Legal-state features require authoritative-source/entity-resolution quality. News extraction alone cannot confirm formal legal status when authoritative sources are available.

## 6. Governance and reporting

- `key_management_departure_count_180d`
- `cfo_turnover_24m`
- `director_turnover_12m`
- `clustered_key_departures_90d`
- `auditor_change_count_24m`
- `financial_non_reliance_flag`
- `material_control_weakness_count`
- `financial_restatement_count_24m`
- `accounts_filing_delay_days`
- `accounts_overdue_flag`
- `ownership_control_change_count_12m`

Potential signals: `KEY_MANAGEMENT_RESIGNATION`, `MANAGEMENT_TURNOVER_CLUSTER`, `AUDITOR_CHANGE`, `FINANCIAL_REPORTING_RELIABILITY_CONCERN`, `INTERNAL_CONTROL_WEAKNESS`, `MATERIAL_OWNERSHIP_OR_CONTROL_CHANGE`.

## 7. Security-interest mappings

Common economic feature semantics can derive from different legal sources:

```text
US authorised/licensed UCC filing ----+
                                       +--> new_security_interest_count_90d
UK Companies House charge ------------+
                                       +--> NEW_SECURITY_INTEREST_ACTIVITY
other jurisdiction security registry -+
```

Source/legal subtype remains in observation/evidence. The feature does not claim legal equivalence among regimes.

## 8. Origination-relative deterioration

To support jurisdictions/accounting frameworks and institution policies that depend on deterioration relative to origination, add:

- `origination_pd`
- `current_pd`
- `absolute_pd_change`
- `relative_pd_change`
- `origination_internal_grade`
- `current_internal_grade`
- `grade_notches_deteriorated`
- `origination_risk_profile_ref`
- `current_risk_profile_ref`

These features can support EWS and separate accounting/prudential adapters. They do not themselves determine SICR, CECL or regulatory default.

## 9. External feature quality

Every external feature carries or references:

```text
sourceAuthority
entityMatchConfidence
sourceCompleteness
observationFreshness
extractionConfidence
corroborationCount
marketLiquidityQuality (where applicable)
sourceRightsRef
knowledgeTime
```

Quality gates are policy/versioned. A high-confidence extraction attached to the wrong legal entity is not high-quality evidence.

## 10. International peer features

Peer cohort definitions include jurisdiction/market where material and cannot blindly pool accounting, market or industry data across incomparable populations.

Examples:

- `issuer_spread_peer_zscore`
- `leverage_peer_percentile`
- `margin_peer_delta`
- `refinancing_maturity_peer_percentile`

Cohort definition/version and currency/accounting normalization are mandatory lineage.

## 11. Point-in-time rule

External datasets used for training/backtesting obey `knowledgeTime <= predictionTime`. Later SEC/company filings, restatements, court updates, rating changes, entity-resolution corrections and market reference-data corrections cannot leak into historical predictors.

## 12. Phase sequencing

Core internal banking features remain Phase-1 priority for institutions with those data. International external intelligence should be introduced in parallel according to source availability and target market rather than being universally deferred to a later phase.

For US/UK public/registered corporates, authoritative external sources can be sufficiently structured and timely to justify early implementation.