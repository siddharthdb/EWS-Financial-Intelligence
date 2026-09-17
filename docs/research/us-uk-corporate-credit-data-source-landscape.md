# US & UK Corporate Credit Intelligence — Data Source Landscape

**Status:** Research baseline / Part III  
**Research date:** 2026-09-17  
**Purpose:** Determine which US/UK corporate-credit and market-intelligence sources can realistically feed EWS 2.0 automatically.

## Executive conclusion

The international external-intelligence layer should not be designed as a generic web/news scraper. The strongest sources are structured official registries and market feeds, but availability differs sharply by jurisdiction.

The UK is unusually attractive for event-driven corporate registry intelligence because Companies House exposes REST, bulk data and real-time streams for companies, filings, insolvency cases, charges, officers and persons with significant control. The US has richer public-company disclosure through SEC EDGAR, including near-real-time submissions/XBRL and material 8-K events, but private-company legal/secured-lending intelligence is fragmented across state UCC systems and federal/state courts.

Therefore the platform needs a source capability model rather than a hard-coded country connector model.

## 1. Source capability classification

Each external source is classified by:

- authority: official regulator/court/registry, regulated venue, commercial vendor, media/open web;
- access: stream, REST API, bulk file, RSS/feed, licensed feed, search/UI only;
- latency: realtime/near-realtime, daily, monthly, irregular;
- entity coverage: public companies, private companies, financial institutions, securities, legal entities;
- data rights/licence;
- history/backfill support;
- stable entity identifiers;
- correction/revision semantics;
- machine readability;
- EWS value.

Recommended authority tiers:

```text
T1  authoritative government/regulator/court/official registry
T2  regulated market/venue or licensed primary market-data/ratings provider
T3  reputable commercial intelligence/news provider
T4  open-web/media/alternative observation
```

Tier is provenance, not automatic truth. A company filing is authoritative evidence of what the issuer disclosed; it is not independent verification of every assertion in the filing.

---

# 2. United States

## 2.1 SEC EDGAR — highest-value US public-company connector

### Availability

SEC `data.sec.gov` exposes unauthenticated REST JSON APIs for company submissions and XBRL data. The SEC states that submissions update throughout the day in real time, typically in less than a second, while XBRL APIs typically update in under a minute. Nightly bulk ZIPs are available for submissions and company facts.

SEC also exposes filing archives and RSS mechanisms. Automated access is permitted subject to fair-access controls, including a current maximum of 10 requests/second and a declared User-Agent.

### EWS data

Structured/semistructured observations include:

- 10-K / 10-Q financial statements;
- 8-K material events;
- 20-F / 6-K foreign issuer disclosures;
- XBRL financial facts;
- filing timeliness and amendments;
- auditor/accountant changes;
- management/director changes;
- bankruptcy/receivership disclosures;
- acceleration/default-related debt events;
- delisting/listing-compliance events;
- material impairments;
- credit agreements and amendments filed as exhibits.

Particularly useful 8-K items include bankruptcy/receivership (1.03), triggering events that accelerate/increase direct financial obligations (2.04), material impairments (2.06), delisting/listing-standard issues (3.01), changes in certifying accountant (4.01), and management/director changes (5.02).

### Architecture

```text
SEC submissions API / filing feed
        ↓
SEC connector
        ↓
CIK/entity resolver
        ↓
filing classifier
        ├── structured XBRL extractor
        ├── 8-K item classifier
        ├── exhibit/document fetcher
        └── NLP/document intelligence
        ↓
canonical observations/evidence
```

Do not poll every company page. Maintain a CIK watch universe, consume filing metadata efficiently, fetch only relevant documents/exhibits, and use nightly bulk data for reconstruction/backfill.

### Automation assessment

**Excellent.** Official, machine-readable, near-real-time and historically rich for SEC filers.

Limit: weak coverage of private US companies that are not SEC filers.

---

## 2.2 US bankruptcy / Chapter 11 — PACER

PACER provides an Authentication API and a public PACER Case Locator API. The Case Locator is a nationwide index of federal court cases and parties, making automated discovery of federal bankruptcy cases possible with PACER credentials.

### EWS observations

- Chapter 11 filing;
- Chapter 7 filing;
- case number/court;
- petition date;
- debtor/party matching;
- case status/proceedings;
- subsequently fetched docket/document evidence where permitted and needed.

### Architecture

```text
PACER Case Locator
      ↓
party/entity resolver
      ↓
case candidate
      ↓
verification / docket acquisition
      ↓
BANKRUPTCY_FILED / RESTRUCTURING_PROCEEDING observations
```

The public US Courts statistical bankruptcy tables are useful for macro/sector baselines, not counterparty-level EWS.

### Automation assessment

**Good but operationally more constrained than EDGAR.** Authentication, PACER charging/access terms and document retrieval workflows must be treated as connector-specific controls.

For listed issuers, SEC 8-K bankruptcy events can provide a second authoritative observation and often arrive in an easier machine-consumable path.

---

## 2.3 UCC financing statements / liens

UCC financing statements are primarily state-level records. There is no single federal equivalent of Companies House charges that provides a uniform nationwide event stream.

This is a major US data-fragmentation issue.

### Potential EWS observations

- new secured-party filing;
- collateral additions/changes;
- amendment;
- assignment;
- continuation;
- termination;
- increased secured-lending activity;
- creditor changes.

These are not automatically adverse. A new UCC filing can simply reflect normal financing.

### Automation reality

State availability varies materially. Some states expose searchable indexes or bulk datasets; terms can prohibit automated queries against interactive websites. Therefore EWS must not implement a generic screen scraper across Secretary of State websites.

### Recommended strategy

```text
UCC source adapter interface
      ├── approved state API/bulk connector
      ├── licensed nationwide commercial feed
      └── manual/on-demand verification
```

For a production US bank, a licensed commercial UCC/lien provider is likely operationally superior to building and maintaining 50-state scraping logic.

### Automation assessment

**Fragmented / vendor-assisted.** High potential value, poor national public-source uniformity.

---

## 2.4 US corporate bond market — FINRA TRACE

FINRA's API platform exposes fixed-income datasets and TRACE-related market data products; TRACE is the mandatory reporting mechanism for eligible OTC fixed-income transactions.

### EWS use

For publicly traded debt:

- price deterioration;
- yield/spread widening;
- liquidity deterioration;
- volume anomalies;
- peer spread divergence;
- market-implied distress features.

A market movement is an observation, not itself a default conclusion.

### Derived features

```text
bond_spread_change_5d
bond_spread_change_30d
bond_price_drawdown_30d
bond_liquidity_change
issuer_vs_sector_spread_zscore
issuer_vs_rating_bucket_spread
```

### Automation assessment

**Good, subject to dataset access/licensing and identifier mapping.** Requires security-to-issuer mapping (CUSIP/other security IDs -> canonical counterparty).

---

## 2.5 Credit ratings — commercial feeds

S&P Global, Moody's and Fitch data are valuable but should be treated as licensed data, not scraped from public webpages.

S&P Global's XpressAPI/ratings products expose issuer and issue current/historical ratings; Moody's offers APIs/feeds and broad entity/rating/default data products.

### Observations

- rating upgrade/downgrade;
- outlook change;
- watch/CreditWatch;
- issuer vs issue rating;
- default/recovery events where licensed;
- rating withdrawal.

### Automation assessment

**Excellent technically, commercial/licensing dependency.** Build a provider-neutral `RatingProviderAdapter`; do not make S&P/Moody's/Fitch fields the canonical ontology.

---

## 2.6 Syndicated loans

There is no public US source equivalent to TRACE providing a complete live syndicated-loan market dataset.

Commercial market practice relies heavily on datasets such as LPC/DealScan and other loan-market products. Federal Reserve research describes DealScan as a commercially available source of syndicated-loan originations, while Shared National Credit data available to supervisors is not a general public borrower-level feed.

SEC filings nevertheless provide useful partial intelligence for public borrowers: material credit agreements, revolving facilities, amendments and syndicated facilities are frequently filed as 8-K exhibits or incorporated into periodic reports.

### EWS strategy

```text
public borrower
   ├── SEC credit agreement/exhibit extraction
   └── commercial loan-market feed where licensed

private borrower
   ├── bank internal facility data (primary)
   └── commercial syndicated-loan feed where available
```

### Useful extracted terms

- facility amount;
- maturity;
- spread/margin;
- benchmark;
- lenders/agent;
- covenant definitions;
- amendment frequency;
- maturity extension;
- waiver;
- borrowing-base changes;
- collateral/guarantees;
- events of default.

### Automation assessment

**Partial via public filings; strong via licensed vendor data.** NLP/document extraction is required for agreements/exhibits.

---

## 2.7 US regulator/institution sources

FDIC failed-bank data, OCC institution/enforcement information and related official bank datasets are useful where the counterparty, lender, guarantor or connected entity is a financial institution.

These are relationship/contagion inputs rather than the primary corporate EWS feed.

---

# 3. United Kingdom

## 3.1 Companies House — core UK corporate-intelligence backbone

Companies House is substantially more useful for broad private-company monitoring than SEC EDGAR because it covers UK registered companies rather than primarily securities issuers.

### Interfaces

Companies House provides:

- REST Public Data API;
- real-time Streaming API;
- bulk company snapshots;
- daily/monthly electronic accounts data in XBRL/iXBRL;
- PSC bulk data;
- filing history and documents.

The Streaming API exposes streams for:

- company information;
- filings;
- insolvency cases;
- charges;
- officers;
- persons with significant control;
- disqualified officers;
- exemptions/PSC statements.

The stream includes a published timestamp and resumable `timepoint`, allowing a connector to resume from a known point. Companies House recommends snapshot + stream for maintaining a complete current dataset.

### EWS observations

```text
company.status.changed
company.filing.received
company.accounts.received
company.accounts.overdue
company.officer.changed
company.psc.changed
company.charge.created
company.charge.satisfied
company.insolvency.changed
company.strikeoff.proposed
```

### Accounts

Electronic accounts bulk files are XBRL/iXBRL. Current official guidance says electronic accounts data represents only a portion of total accounts filings, so document/fallback handling remains necessary.

### Architecture

```text
monthly/baseline snapshot
          +
Companies House realtime streams
          ↓
checkpointed connector (timepoint)
          ↓
company-number entity resolution
          ↓
canonical UK corporate observations
          ↓
financial / graph / legal / EWS features
```

### Automation assessment

**Excellent.** This should be the primary UK corporate-registry connector.

---

## 3.2 UK charges — especially valuable

Companies House exposes both REST charge resources and a real-time charges stream.

This is a material advantage over the fragmented US UCC landscape.

Potential observations/features:

- new charge/security registration;
- charge holder;
- secured assets/collateral descriptions;
- charge satisfaction/release;
- frequency of new secured financing;
- secured-creditor concentration;
- charge creation around liquidity stress.

A new charge is not intrinsically adverse; it becomes useful when correlated with refinancing, cash-flow, rating and payment evidence.

### Automation assessment

**Excellent.** Official and streamable.

---

## 3.3 UK insolvency

Companies House exposes company insolvency via REST and a real-time insolvency-case stream. Case types include compulsory liquidation, creditors' voluntary liquidation, administration, CVA, receivership, moratorium and foreign insolvency among others.

The Gazette is the official public record for formal insolvency notices and offers a data service with company/notice filtering and scheduled delivery options. It is a useful independent/official-notice evidence source.

The Insolvency Service publishes official aggregate statistics, useful for sector/macro baseline features rather than individual counterparty alerts.

### Recommended hierarchy

```text
Companies House insolvency stream -> primary automated entity event
The Gazette notice              -> corroborating/legal notice evidence
Insolvency Service statistics   -> portfolio/sector baseline
```

### Automation assessment

**Excellent at Companies House level; Gazette data feed subject to service/licensing arrangement.**

---

## 3.4 UK court judgments

The National Archives' Find Case Law is the official free source for many judgments/tribunal decisions and exposes an API. However, its terms explicitly distinguish normal access from computational analysis and require a separate licence for computational analysis at scale.

It also does not represent every lower-court event or every petition/order, so it cannot replace insolvency registry/Gazette monitoring.

### EWS use

Potential high-materiality observations:

- adverse judgment;
- material contractual dispute;
- tax dispute;
- director/company litigation;
- restructuring/insolvency-related judgments.

### Automation assessment

**Selective/licence-sensitive.** Use for targeted legal intelligence after confirming permitted computational use, not indiscriminate bulk NLP crawling.

---

## 3.5 FCA data

FCA sources have two different roles.

### Counterparty/entity intelligence

The Financial Services Register provides regulated-firm data and an API developer route. This matters when the counterparty or connected entity is FCA/PRA regulated.

### Market/ratings data

FCA data portals include financial-instrument and public-ratings datasets and machine-to-machine services for some registers.

### Regulatory knowledge

The FCA Handbook API provides structured machine-readable Handbook content. This belongs in regulatory-policy/change management, not borrower risk evidence.

### Automation assessment

**Good for regulated entities and regulatory-change intelligence; not a general UK corporate financial-data substitute for Companies House.**

---

## 3.6 Bank of England / PRA

Bank of England public statistics provide macro-financial and rate context. Some database series can be downloaded programmatically using parameterised downloads; yield-curve archives are downloadable but the Bank explicitly states that yield-curve data are not available through an API.

BEEDS is a regulated-firm submission portal, not a public counterparty-intelligence API. Do not architect EWS as though PRA submissions can simply be harvested externally.

### EWS use

- policy/risk-free rates;
- yield curve shifts;
- credit-condition/macroeconomic variables;
- stress-scenario inputs;
- sector/portfolio context.

### Automation assessment

**Good for macro/reference features, mixed interfaces.** Use scheduled official-series ingestion rather than pretending all BoE data are realtime APIs.

---

## 3.7 UK market disclosures — RNS

LSEG's Regulatory News Service is a major regulated-information channel for UK listed issuers. LSEG states that RNS processes hundreds of thousands of announcements annually and provides a data-feed technical specification.

Potential events:

- profit warnings/trading updates;
- refinancing/funding;
- covenant/going-concern commentary;
- management changes;
- acquisitions/disposals;
- capital raising;
- auditor changes;
- material litigation;
- insolvency/restructuring;
- rating/debt announcements.

### Automation assessment

**High value, but production feed access is commercial/contractual.** Use an LSEG/vendor adapter rather than scraping announcement webpages.

---

## 3.8 UK bond consolidated tape

The FCA's UK bond consolidated tape launched in June 2026 and is operated by ETS Connect UK. FCA rules require bond-trade information from UK venues/APAs to be consolidated, with machine-readable dissemination including API/CSV requirements. It covers venue and OTC bond trades within scope.

This materially improves the feasibility of UK bond-market EWS features.

### Features

- issuer bond price/yield deterioration;
- spread widening;
- liquidity/volume change;
- peer divergence;
- abnormal trade activity.

### Automation assessment

**Strong new market source; access/commercial terms of the tape provider must be evaluated before implementation.**

---

# 4. US vs UK practical source comparison

| Intelligence need | US | UK |
|---|---|---|
| Public-company filings | SEC EDGAR — excellent | RNS + Companies House — strong |
| Private-company registry | fragmented by state / commercial providers | Companies House — excellent |
| Machine-readable financials | SEC XBRL — excellent for filers | Companies House XBRL/iXBRL — useful but incomplete |
| Officer/ownership changes | SEC disclosures + state/commercial enrichment | Companies House officers/PSC streams — excellent |
| Security interests | state UCC — fragmented | Companies House charges — excellent |
| Insolvency event discovery | PACER + SEC for listed issuers | Companies House + Gazette — excellent |
| Court/legal | PACER/federal + state systems | Find Case Law + Gazette; coverage/licence constraints |
| Corporate bond trading | FINRA TRACE | UK bond consolidated tape |
| Ratings | licensed S&P/Moody's/Fitch | licensed S&P/Moody's/Fitch + FCA public ratings sources where applicable |
| Syndicated loans | commercial datasets + SEC agreements | commercial datasets + issuer disclosures/internal data |
| Macro/rates | Federal Reserve/FRED/Treasury etc. | BoE/PRA/public statistics |

## Key asymmetry

The UK offers a much more coherent official corporate-registry event surface. The US offers exceptional listed-company disclosure but a much more fragmented private-company legal/collateral surface.

---

# 5. What should be automated first

## US Tier A

1. SEC submissions/8-K metadata.
2. SEC XBRL/company facts.
3. relevant EDGAR documents/exhibits.
4. PACER bankruptcy case discovery for monitored counterparties.
5. licensed ratings feed.
6. TRACE/bond market feed for issuers with traded debt.

## US Tier B

7. commercial UCC/lien data.
8. commercial syndicated-loan data.
9. licensed news/event feed.
10. state court/judgment sources according to portfolio geography.

## UK Tier A

1. Companies House company stream.
2. filing stream.
3. insolvency stream.
4. charges stream.
5. officer + PSC streams.
6. Companies House accounts XBRL/iXBRL.
7. RNS/licensed listed-company announcement feed.
8. licensed ratings feed.

## UK Tier B

9. Gazette insolvency/legal notices.
10. UK bond consolidated tape.
11. FCA regulated-entity/public ratings sources.
12. BoE macro/rates series.
13. targeted Find Case Law connector subject to licensing.

---

# 6. Required canonical source events

The international connector layer should normalize source-specific payloads into observations such as:

```text
corporate.filing.received
financial.statement.published
financial.statement.restated
management.officer.changed
ownership.control.changed
security.interest.created
security.interest.modified
security.interest.released
insolvency.proceeding.started
insolvency.proceeding.changed
bankruptcy.filed
credit.agreement.executed
credit.agreement.amended
covenant.waiver.disclosed
debt.acceleration.disclosed
listing.compliance.issue.disclosed
auditor.changed
rating.action.published
bond.market.observation
legal.proceeding.observed
regulatory.action.observed
```

Source terms remain in evidence metadata. The canonical event type describes the economic/legal observation without pretending different legal systems are identical.

---

# 7. Entity resolution becomes critical

Identifiers differ by source:

```text
US: CIK, EIN where legally/contractually available, ticker, CUSIP, LEI, PACER party names, state entity IDs
UK: Companies House company number, LEI, ticker/ISIN, FCA FRN, RNS issuer/security IDs
Commercial: vendor entity IDs
Internal: customer/counterparty IDs
```

Build an identifier graph:

```text
CanonicalCounterpartyId
   ├── CIK
   ├── CompaniesHouseNumber
   ├── LEI
   ├── FRN
   ├── ticker
   ├── ISIN/CUSIP -> security -> issuer
   ├── vendor IDs
   └── internal customer IDs
```

Never join legal/bankruptcy events to a borrower using normalized company name alone when stronger identifiers are available. Name matching should generate candidates with confidence and human/entity-resolution controls.

---

# 8. Data-rights architecture

Every source connector must register:

```text
sourceId
provider
jurisdiction
authorityTier
licenceClass
permittedUses
redistributionAllowed
rawRetentionAllowed
modelTrainingAllowed
llmProcessingAllowed
crossBorderTransferAllowed
requestRateLimit
credentialClass
retentionPolicy
```

This is not administrative decoration. Commercial ratings, RNS, court data, market feeds and public registries have different reuse rights. A source being publicly viewable does not automatically permit unrestricted bulk collection, model training or redistribution.

---

# 9. Recommended source ingestion architecture

```text
                EXTERNAL SOURCE PLANE

 Official APIs       Streams       Bulk        Licensed feeds
      |                 |            |               |
      +-----------------+------------+---------------+
                        |
                  Source Adapters
                        |
          +-------------+--------------+
          |                            |
    Raw Evidence Store          Source Checkpoints
    immutable/hash              cursor/timepoint/etc.
          |
          v
    Schema Validation
          |
          v
     Entity Resolution
          |
          v
  Jurisdiction Normalizer
          |
          v
 Canonical Observation Events
          |
          v
        Kafka
          |
   +------+------+----------------+
   |             |                |
Financial     Graph          Legal/Market
Features      Features        Features
   |             |                |
   +-------------+----------------+
                 v
          Signal Policies
                 v
        Correlation / AI
```

## Connector modes

Each connector declares one of:

```text
PUSH_STREAM
POLL_INCREMENTAL
BULK_SNAPSHOT_PLUS_STREAM
BULK_SCHEDULED
LICENSED_FEED
ON_DEMAND
MANUAL_VERIFICATION
```

This prevents the architecture from assuming every external source behaves like Kafka.

---

# 10. Evidence and AI rules

For SEC filings, RNS announcements, court documents and Companies House filings:

1. preserve original source URI/identifier and retrieval time;
2. hash retained documents where rights allow storage;
3. parse deterministic metadata first;
4. extract structured/XBRL facts before LLM interpretation;
5. use NLP/LLM for clauses, narrative and classification that are not already structured;
6. cite evidence spans/document IDs in proposed signals;
7. never let an LLM turn a filing directly into an approved risk classification.

Example:

```text
SEC 8-K Item 2.04
       ↓
DEBT_ACCELERATION_DISCLOSED observation
       ↓
source evidence + extracted obligation details
       ↓
policy/feature correlation
       ↓
proposed liquidity/refinancing signal
       ↓
human validation
```

---

# 11. Architectural conclusion

The research validates an international architecture, but changes its external-source layer materially:

- **UK:** registry-first/event-stream-heavy architecture is realistic.
- **US public companies:** disclosure-first architecture centered on EDGAR is realistic.
- **US private companies:** vendor/enrichment strategy is necessary because UCC, corporate registry and legal data are fragmented.
- **Market intelligence:** licensed provider abstraction is mandatory; do not bind canonical contracts to one ratings/news/loan-data vendor.
- **Legal intelligence:** source rights and coverage must be modeled explicitly.
- **Entity resolution:** becomes a core platform capability, not a convenience service.

The Financial Intelligence Platform should therefore be **source-pluggable, rights-aware, jurisdiction-aware and identifier-graph-driven**, while preserving a jurisdiction-neutral evidence/observation/feature/signal core.

## Primary research sources

Research used current official material from the SEC EDGAR developer/API documentation; PACER developer resources; FINRA developer/TRACE documentation; Companies House REST/Streaming API and bulk-data documentation; UK Insolvency Service statistics; The Gazette; FCA Register/data/Handbook and bond consolidated-tape material; Bank of England statistics; The National Archives Find Case Law; and current official/commercial documentation for ratings/market-data providers. Detailed URLs are intentionally maintained in research notes/commit history rather than embedded as dependencies in canonical contracts.