# Part II Research Notes — Canonical Risk Model and Signal Taxonomy

**Research date:** 2026-09-17

This note records the research basis that materially influenced Part II. It is not a substitute for the underlying regulations or publications.

## RBI — EWS and fraud-risk architecture

The 2024 RBI fraud-risk directions strengthen EWS/RFA governance and expect appropriate EWS indicators, periodic effectiveness review, integration with CBS/operational systems, timely remedial action, and quantitative plus qualitative indicators drawing on transactional data, borrower financial performance, market intelligence and borrower conduct.

Architecture consequence: the platform cannot model EWS as only financial ratios or only fraud flags. It needs explicit risk intents and a common signal/evidence framework.

RBI also requires deeper investigation when EWS alerts suggest potential fraud. Architecture consequence: a fraud/integrity signal is an investigation trigger and must not itself be represented as a final fraud classification.

## Historical RBI stress indicators

Historical RBI guidance contains useful examples such as delayed stock/monitoring statements, large shortfalls in sales/operating profit versus sanctioned projections, repeated returned payment instructions, LC/BG devolvement, drawing-power deterioration and non-cooperation with audits.

Architecture consequence: these are useful candidate policies and features, but thresholds must remain versioned policy rather than canonical signal semantics.

## End-use, diversion and siphoning

RBI material on wilful default/end-use monitoring identifies patterns including use of working-capital funds for non-sanctioned purposes, transfers to group companies, routing outside agreed lender arrangements and unexplained deployment shortfalls.

Architecture consequence: fund-flow intelligence requires transaction evidence plus relationship/entity graphs; it should generate `SUSPECTED` signals subject to investigation rather than autonomous legal conclusions.

## RBI supervisory analytics direction

RBI's 2024-25 annual reporting describes advanced supervisory analytics including micro-data analytics, social-media monitoring, fraud vulnerability, borrower vulnerability and asset-quality prediction models.

Architecture consequence: multiple specialized analytical engines are preferable to a monolithic AI model.

## BIS Project Ellipse

Project Ellipse combines granular structured information with unstructured/current-event data and applies ML, NLP and network analytics to generate early-warning indicators and risk correlations.

Architecture consequence: use a canonical granular data model, integrate structured/unstructured evidence, and preserve graph/network analytics as a distinct analytical capability.

## BIS AI market-monitoring research

BIS research published in 2025 demonstrates a two-stage pattern: predictive modelling identifies stress and important drivers, then an LLM uses those drivers to seek contextual information and produce analyst-oriented narratives.

Architecture consequence: predictive models should produce structured outputs/drivers; LLM reasoning sits above them for context/explanation rather than replacing prediction.

## Explainability

BIS FSI work notes limitations in post-hoc explanations, including instability and potentially misleading explanations. EBA material similarly notes that RAG/source citation alone does not solve GPAI explainability.

Architecture consequence: explanation must be reconstructed from evidence, feature snapshots, model/rule versions and decisions. Generated narrative is an interface artefact, not the audit record.

## Financial-distress research

Recent research on corporate bankruptcy prediction indicates that distress-specific narrative information can add predictive information beyond accounting variables in studied datasets.

Architecture consequence: annual-report notes, auditor language, management commentary and other narrative evidence should become governed NLP features, but only after strong structured-data foundations are established.

## Model evaluation

Early-warning research emphasizes the practical cost asymmetry between false negatives and false positives rather than relying on a single accuracy measure.

Architecture consequence: signal/model evaluation must include lead time, precision/recall, alert burden, acceptance rate and outcome utility, with thresholds calibrated by segment and risk appetite.

## Resulting Part-II decisions

1. Four top-level risk intents: credit deterioration, fraud/integrity, operational conduct, external/contagion.
2. Stable signal ontology separated from versioned detection policy.
3. Bitemporal knowledge/effective-time model.
4. Immutable evidence IDs and end-to-end provenance.
5. Structured observations/features precede signal interpretation.
6. GenAI is a correlation/explanation layer over governed evidence.
7. Graph analytics are first-class for group/related-party/end-use intelligence.
8. Phase 1 prioritizes high-quality internal transactional and financial evidence.
9. Analyst dispositions become labelled feedback data.
10. Corporate and retail share the meta-model but not feature/model semantics.

## Primary sources consulted

- Reserve Bank of India — Revised Master Directions on Fraud Risk Management (2024) and related FAQs/press material.
- Reserve Bank of India — historical stressed-asset/EWS and wilful-default/end-use guidance used for candidate indicator discovery.
- Reserve Bank of India — Annual Report 2024-25, supervision/advanced analytics sections.
- Reserve Bank of India — FREE-AI Committee Report listing (2025) and AI/model-risk context.
- Bank for International Settlements Innovation Hub — Project Ellipse and Data and Knowledge Platform.
- BIS Financial Stability Institute — *Managing explanations: how regulators can address AI explainability* (2025).
- BIS Working Paper 1291 — *Harnessing artificial intelligence for monitoring financial markets* (2025).
- European Banking Authority — special topic on artificial intelligence/GPAI explainability.
- Bank of England — machine-learning early-warning/distress research used for evaluation principles.
- 2026 research on interpretable narrative signals for bankruptcy prediction, treated as research evidence rather than regulatory guidance.
