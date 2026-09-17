# EWS 2.0 — International External Event Catalogue

**Status:** Draft / Part III extension

## 1. Purpose

Defines canonical observations required to support India/US/UK and future-market external intelligence without embedding provider-specific payloads into the global event model.

## 2. Company/registry events

```text
company.status.changed
company.registered_address.changed
ownership.control.changed
management.position.changed
security.interest.created
security.interest.updated
security.interest.released
```

Possible source mappings include national company registries, Companies House officer/PSC/charge streams, authorised UCC/lien data and equivalent registries.

## 3. Filing/reporting events

```text
financial.statement.filed
financial.statement.restated
financial.reporting.non_reliance.disclosed
internal.control.weakness.disclosed
auditor.changed
going_concern.disclosed
```

Source-specific filing form/item identifiers remain evidence metadata.

## 4. Financing events

```text
credit.facility.executed
credit.facility.amended
covenant.waiver.disclosed
facility.maturity.extended
facility.pricing.changed
lender.set.changed
debt.default.disclosed
debt.acceleration.disclosed
```

## 5. Insolvency/legal events

```text
insolvency.proceeding.started
insolvency.proceeding.status.changed
bankruptcy.case.filed
bankruptcy.case.status.changed
legal.creditor.notice.published
regulatory.enforcement.published
```

Payloads carry jurisdiction and legal procedure subtype. Global event names do not assert equivalence between Chapter 11, administration, liquidation, CVA, NCLT proceedings or other legal regimes.

## 6. Rating events

```text
rating.action.published
rating.outlook.changed
rating.watch.changed
```

Provider, original scale/value, instrument/issuer scope, effective/publication time and provider entity IDs remain preserved.

## 7. Market events

```text
market.bond.trade.observed
market.bond.price.observed
market.bond.yield.observed
market.security.reference.changed
```

Derived processing converts these into issuer-level market features. Do not emit one EWS signal per trade.

## 8. News/announcement events

```text
issuer.announcement.published
news.item.observed
```

NLP extraction may produce candidate observations, but extracted facts reference source spans and extraction confidence.

## 9. Common external observation payload requirements

```text
sourceObservationId
sourceId
sourceRecordId
jurisdiction
sourceAuthority
sourcePublishedAt
effectiveTime
knowledgeTime
rawEvidenceRef
sourceRightsRef
entityResolutionRef
entityMatchConfidence
correctionOf / supersedes (optional)
```

Document-derived observations additionally include document hash/span and extraction model/version/confidence. Market observations additionally include security identifier, issuer-resolution reference, currency, venue/source and market-quality metadata.

## 10. Example mappings

```text
US SEC debt-acceleration disclosure
  -> debt.acceleration.disclosed
  -> debt_acceleration_count_12m
  -> DEBT_ACCELERATION / REFINANCING_RISK_INCREASE

UK Companies House charge creation
  -> security.interest.created {legalSubtype=UK_REGISTERED_CHARGE}
  -> new_security_interest_count_90d
  -> NEW_SECURITY_INTEREST_ACTIVITY

US UCC financing statement
  -> security.interest.created {legalSubtype=US_UCC_FINANCING_STATEMENT}
  -> new_security_interest_count_90d
  -> NEW_SECURITY_INTEREST_ACTIVITY

UK insolvency stream
  -> insolvency.proceeding.started {procedureType=...}
  -> active_insolvency_proceeding_flag
  -> FORMAL_INSOLVENCY_PROCEEDING

US bankruptcy source
  -> bankruptcy.case.filed {chapter=11 where authoritative}
  -> active_bankruptcy_case_flag
  -> FORMAL_INSOLVENCY_PROCEEDING
  -> may contribute to FORMAL_RESTRUCTURING_EVENT when the governed policy/evidence establishes restructuring semantics
```

`BANKRUPTCY_FILED` is deliberately not a global analytical signal. Bankruptcy filing is a jurisdiction-bearing legal observation that feeds the normalized insolvency/restructuring signal taxonomy.

## 11. Correction semantics

Corrections use new event identities with `supersedesEventId` or stable source record/revision semantics. They never mutate historical knowledge time.

## 12. Signal boundary

Connectors do not emit analytical signals directly. They emit observations; governed feature, policy and correlation engines own risk interpretation.