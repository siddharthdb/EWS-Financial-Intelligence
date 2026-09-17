# EWS 2.0 — Multi-Jurisdiction Risk Model

**Status:** Draft  
**Jurisdictions initially profiled:** India, United States, United Kingdom

## 1. Decision

The canonical EWS model is jurisdiction-neutral at the evidence, observation, feature and signal layers. Jurisdiction-specific regulatory/accounting classifications are represented through versioned adapters and typed classification namespaces.

```text
Evidence -> Observation -> Feature -> Signal -> Analytical Risk Assessment
                                               |
                   +---------------------------+---------------------------+
                   |                           |                           |
                   v                           v                           v
             India adapters                US adapters                UK adapters
```

## 2. Why

Borrower distress economics are portable; regulatory consequences are not.

`DPD=45`, `DSCR deterioration`, `cash-flow stress`, `covenant breach` and `rating downgrade` can be canonical facts/features/signals. `SMA`, `Special Mention`, `Substandard`, `SICR`, `IFRS9 Stage 2`, `prudential default` and `CECL treatment` are different governed classifications.

They must not share a generic `riskStatus` field.

## 3. Jurisdiction context

Every regulated classification/policy evaluation can resolve a context containing:

```text
borrowerDomicile
bookingJurisdiction
bookingLegalEntity
reportingLegalEntity
accountingFramework
prudentialRegime
portfolio
product
customerType
policyEffectiveDate
```

The applicable regime is determined by governed legal-entity/policy configuration, not inferred from borrower country alone.

## 4. Classification namespaces

Initial namespaces:

```text
ANALYTICAL_EWS
INTERNAL_CREDIT_GRADE
WATCHLIST
WORKOUT

IN_SMA_NPA

US_SUPERVISORY_CLASSIFICATION
US_ACCRUAL_STATUS
US_CECL

UK_IFRS9_STAGE
UK_SICR
UK_PRUDENTIAL_DEFAULT
UK_IRB
```

Each classification record includes:

```text
classificationId
namespace
classificationType
value
entity/exposure scope
effectiveTime
knowledgeTime
policyId/version
input fact/feature/signal references
authority
approval/provenance
supersedes
```

## 5. Adapter boundary

Adapters consume canonical facts and governed analytical outputs but publish their own state.

Example:

```text
obligation.dpd.changed
       |
       +--> DPD features --> EWS signals
       |
       +--> IN regulatory classification
       |
       +--> US classification/nonaccrual policy inputs
       |
       +--> UK prudential-default policy inputs
```

No adapter rewrites the canonical DPD event.

## 6. Accounting impairment boundary

Accounting impairment is downstream from, and may consume, EWS information.

```text
US_GAAP -> CECL engine/workflow
IFRS    -> SICR + IFRS9 Stage/ECL engine/workflow
```

EWS severity is not an accounting stage. Accounting stage/allowance is not an EWS signal.

## 7. Model purpose boundary

Model registry metadata must include:

```text
modelPurpose
jurisdictionalUse
permittedUses
prohibitedUses
legalEntities
portfolios
modelRiskTier
validationStatus
owner
independentValidator
limitations
```

Example purposes:

```text
EWS_PRIORITISATION
PD_ESTIMATION
LGD_ESTIMATION
CECL
IFRS9_SICR
IFRS9_ECL
IRB_CAPITAL
STRESS_TEST
ANOMALY_DETECTION
GRAPH_CORRELATION
GENAI_EXPLANATION
```

Approval for one purpose does not imply approval for another.

## 8. Event architecture extension

Regulatory/accounting classifications use a dedicated derived event family, for example:

```text
ews.derived.classification
```

Candidate event types:

```text
classification.assigned
classification.changed
classification.cured
classification.superseded
```

Payload includes the classification namespace so that a US supervisory grade can never be confused with UK IFRS9 staging or Indian SMA/NPA state.

## 9. Policy architecture

Policy resolution key conceptually includes:

```text
policyFamily
jurisdiction/regime
legalEntity
portfolio/product/segment
effectiveDate
```

Historical replay resolves the policy that was effective for the intended analytical mode. Counterfactual replay may explicitly pin a different policy version.

## 10. UI implication

Analyst screens separate:

```text
Analytical EWS
Internal credit grade
Regulatory classification
Accounting impairment state
Default state
Watchlist/workout state
```

The UI may correlate them but must not flatten them into one badge.

## 11. Audit implication

A reviewer must be able to reconstruct independently:

```text
Why did EWS flag the borrower?
Why did internal grade change?
Why was a US exposure classified Special Mention/Substandard/etc.?
Why did a UK exposure enter/exit SICR/Stage 2?
Why was an exposure considered defaulted?
Why did the accounting allowance change?
```

Each question may have overlapping evidence but a different policy/model/approval chain.

## 12. Implementation sequencing

1. Keep current canonical signal/feature model unchanged where semantics are portable.
2. Add jurisdiction/regime context and classification contract.
3. Implement India adapter first for current deployment.
4. Add US supervisory-classification + CECL integration profile.
5. Add UK SICR/IFRS9 + prudential-default/IRB integration profile.
6. Add jurisdiction-specific signal-policy packs only where evidence demonstrates genuine differences.
7. Validate all regulatory adapters with local credit-policy/regulatory SMEs before production use.
