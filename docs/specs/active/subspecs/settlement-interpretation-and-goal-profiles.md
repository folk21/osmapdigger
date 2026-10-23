---
type: Specification
title: Settlement interpretation and goal-oriented insights
description: Active staged P1 specification for transparent goal profiles, deterministic dimension evidence, and practical settlement insights.
document_role: subspec
spec_status: active
parent: ../spec-initial-functional-product.md
---
# Settlement interpretation and goal-oriented insights

## Status

Active P1 **design and implementation focus**, with no implementation stage accepted or authorized by this document. The P0 initial-product acceptance remains verification-pending, is not waived, and must be tracked independently. This explicit P1 focus change is not evidence that P0 has passed.

Parent specification: [`../spec-initial-functional-product.md`](../spec-initial-functional-product.md).

This is the normative contract. The [advisory implementation plan](settlement-interpretation-and-goal-profiles-implementation-plan.md) contains module/file guidance and independently derived test scenarios. One bounded stage is implemented and verified at a time by the developer-controlled ChatGPT PATCH workflow; no Aider or other automated worker is part of this change.

The stage sequence is a proposed implementation decomposition, not source code or acceptance evidence. Developer review and approval of the format and persistence decisions are required before stages crossing those boundaries.

## Feature scope

Primary capability:

- `ANALYSIS.INTERPRETATION` — goal-oriented aggregation of metric qualities into understandable dimensions and traceable practical insights.

Existing capabilities reused or refined by this work:

- `ANALYSIS.PREFERENCES` — transparent metric direction/target/limit/weight semantics;
- `ANALYSIS.RANKING` — the single authoritative overall settlement score/rank;
- `ANALYSIS.EXPLANATION` — lower-level criterion diagnostics and comparative explanations;
- `METRICS.RUNTIME_CONTRACT` and `FORMAT.GEODATA` — explicit, versioned configuration/evidence needed by generic interpretation;
- `PRESENTATION.ANALYSIS_WORKSPACE` — ranked-result and detail presentation;
- `PRESENTATION.LOCALIZATION` — localized dimension/insight text without changing semantics;
- `SETTINGS.PERSISTENCE` — dataset-scoped selected goal/custom Preference state where persistence is required;
- `TESTING.DETERMINISTIC_LOCAL` — deterministic interpretation validation without an LLM or public network.

Feature identifiers are defined in [`../../../FEATURES.md`](../../../FEATURES.md).

## Goal

Turn the existing metric-heavy analysis into practical decision support.

The application should help a user answer not only:

> What are the metric values for this settlement?

but also:

> What do these values mean for my current purpose, what are the strongest advantages and trade-offs, and which concrete metrics support that conclusion?

The first implementation should remain deterministic, transparent, country-agnostic, and offline-first. It should make existing OSM-derived evidence easier to understand before the project invests in more raw metrics or learned recommendation models.

## Current state

OsmapDigger already provides:

- generic numeric Required conditions and Preferences;
- normalized per-metric quality, weighted score, coverage, and deterministic ranking;
- candidate-relative criterion explanations;
- localized metric labels and missing-data diagnostics;
- a map-first ranked-result/details workspace.

The remaining product gap is interpretation. Users still have to mentally combine many metric values and criterion scores to decide what a settlement is practically good or bad for. There is no stable concept of human-readable analysis dimensions, no goal-specific product lens, and no higher-level conclusion that is explicitly traceable to the supporting metric evidence.

## Product concepts

### Dimension

A **dimension** is a human-readable grouping of normalized metric evidence. It does not replace or duplicate raw metrics.

Candidate initial concepts include:

- **Nature** — access to and surrounding presence of forests, water, meadows, protected areas, and similar natural context;
- **Everyday services** — access to shops, medical care, schools, and other routine infrastructure;
- **Transport accessibility** — access to roads, public transport, rail, and later travel-time/network metrics;
- **Seclusion** — distance from dense infrastructure, major roads, and other proxies for remoteness/quiet;
- **Environmental comfort** — distance from industrial, landfill, quarry, and similar adverse features.

These names and compositions are initial product candidates, not metric-ID-specific Kotlin branches. Dimension membership and user-visible metadata must be explicit configuration/runtime data rather than inferred from localized titles.

### Goal profile

A **goal profile** is an explicit reusable lens for a practical use case.

Initial profiles should cover at least:

- **Balanced living**;
- **Nature & quiet**;
- **Family convenience**;
- **Remote retreat**.

A goal profile may provide a named preset for the existing Preference engine. If applying a profile changes ranking, it must do so only by resolving an explicit visible set of `MetricPreference` values. The resulting Preferences remain inspectable/editable, and manual edits should transition the UI to a clearly identified custom state rather than leaving a misleading named profile selected.

### Insight

An **insight** is a deterministic user-facing conclusion backed by explicit evidence.

Examples:

- "Strong natural surroundings, but everyday services are limited.";
- "Good family convenience with moderate environmental trade-offs.";
- "Well suited to a remote retreat; car dependence is likely high.".

An insight is not a hidden score. It must retain the dimension and metric evidence needed to explain why it was emitted.

### Evidence

Every dimension and insight must be traceable to the underlying known metric values, normalized qualities, weights/directions, and data-coverage state that participated in the conclusion.

Missing values remain unknown. They must never be converted into negative evidence merely to make an insight available.

## Requirement map

| Requirement | Feature ID | Purpose |
|---|---|---|
| SI-R1-SI-R3 | `ANALYSIS.INTERPRETATION`, `METRICS.RUNTIME_CONTRACT`, `FORMAT.GEODATA` | Explicit dimension/evidence model over dynamic metrics |
| SI-R4-SI-R5 | `ANALYSIS.INTERPRETATION`, `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING` | Goal profiles reuse the authoritative Preference/ranking engine |
| SI-R6-SI-R8 | `ANALYSIS.INTERPRETATION`, `ANALYSIS.EXPLANATION` | Deterministic strengths/trade-offs with traceable evidence and missing-data semantics |
| SI-R9-SI-R10 | `PRESENTATION.ANALYSIS_WORKSPACE`, `PRESENTATION.LOCALIZATION` | Compact color-assisted summaries plus progressive localized detail |
| SI-R11 | `SETTINGS.PERSISTENCE` | Dataset-scoped selected goal/custom state without mutating generated data |
| SI-R12 | `TESTING.DETERMINISTIC_LOCAL`, `PLATFORM.KMP` | Shared deterministic behavior and cross-platform parity |
| SI-R13 | `ANALYSIS.INTERPRETATION` | Explicit boundary for future ML-assisted extensions |
| SI-R14 | `FORMAT.GEODATA`, `METRICS.RUNTIME_CONTRACT` | Strict optional metadata contract and legacy fallback |
| SI-R15 | `SETTINGS.PERSISTENCE` | Direction-aware profile state and compatible versioned user persistence |
| SI-R16 | `ANALYSIS.INTERPRETATION`, `TESTING.DETERMINISTIC_LOCAL` | Truthfulness, offline operation, and bounded costs |

## Runtime contract decision

The existing `geo-format/VERSION` is `1`. Its `metric_definition`, sparse `settlement_metric`, and `metric_preference_default` SQLite tables already define numeric metric and ranking semantics. `DatasetPackageMetadataParser` reads a subset of `metadata.json` with `ignoreUnknownKeys = true`, so an **optional** interpretation field is the smallest initial reader-compatible representation. The new field belongs to `geo-format/metadata.schema.json` and is emitted by `geo-builder/src/osmapdigger_geo/pipeline.py`; no interpretation data belongs in the map style.

Version 1 of `metadata.json.interpretation` must contain:

- `version = 1`; ordered `dimensions`, `goalProfiles`, and `insightRules` arrays; an empty/absent `interpretation` field means unsupported, not an inferred catalog.
- Each dimension: stable `id`, explicit `labels` for `ru` and `en`, `sortOrder`, unique participating stable `metricIds`, `minimumCoverage` in 0..100, and `minimumKnownMetrics` (positive integer).
- Each goal profile: stable `id`, explicit localized `labels`, `sortOrder`, and a **complete ordered set** of explicit `metricId`, `enabled`, `direction`, `target`, `limit`, `weight` values for the available dataset preference-default catalog. The default `balanced-living` profile must reproduce the dataset's published Preference defaults, not diverge silently.
- Each insight rule: stable `id`, one supported typed operation (`dimension-strength`, `dimension-tradeoff`, `dimension-contrast`), referenced dimension IDs, explicit score/coverage thresholds, and an explicit localization template key mapped by shared presentation. Rule metadata is data, **not executable code or an expression language**.

All catalog IDs must be stable and unique in their respective scopes. Dimension metric IDs and goal preference metric IDs must resolve against generated `metric_definition`; goal metric IDs must also resolve against `metric_preference_default`. Duplicate IDs within a dimension/profile, nonfinite values, invalid direction or target/limit order, unknown rule operations/references, missing locales, unbounded collection sizes, invalid score/coverage thresholds, or unsupported interpretation versions must fail validation **before publication**. Runtime loading of an older package with no catalog must leave Required filters, Preferences, ranked results, Favorites, and offline map fully functional. Invalid optional interpretation data found when opening an external package must not generate insights or silently synthesize definitions; classify the interpretation as unavailable and expose a stable localized diagnostic without corrupting existing user settings. Existing package-level safety and format checks remain authoritative.

The builder owns a dedicated interpretation configuration (candidate: `geo-builder/config/interpretation-profiles.toml`) selected per dataset, independent of `metrics.toml` extraction selectors and ordinary Preference default profiles. Configured dimensions may omit measures absent from a dataset **only if the builder's selected profile explicitly declares the variation**; it must not silently drop a referenced metric. A small Andorra fixture and the selected Belarus `core10` catalog must be reviewed for truthful interpretations, including the generally missing beach evidence in Andorra. Dataset profile compatibility and metric IDs remain country-agnostic.

This is an additive **metadata format-v1 extension**, not permission to change an incompatible persisted format without a separate approval. The exact schema wire example and validator mapping are specified in the implementation plan.

## Domain and goal semantics

Interpretation aggregates **only enabled** `PreferenceContribution` entries whose metric IDs occur in the dimension, using the same effective direction/target/limit/weight and normalized `quality` produced by `PreferenceScorer`. A metric may contribute to more than one diagnostic dimension, but must appear only once in the authoritative Preference ranking; overlapping dimension definitions must be explicit and documented to avoid double-counting claims.

For each dimension, `knownWeight` is the sum of weights of known enabled participating components, `totalWeight` is the sum of all enabled participating weights, `score = 100 * sum(weight * quality) / knownWeight` only when `knownWeight > 0`, and `coverage = 100 * knownWeight / totalWeight` when `totalWeight > 0`, else `0`. When no enabled components exist or minimum evidence requirements are not met, the dimension is unavailable (not 0 and not weak). Evidence retains ordered raw values, quality, direction, thresholds, and weights. Use explicit deterministic tie-breakers: configured dimension/rule order, then stable IDs.

A goal profile can reverse a metric's direction relative to the dataset default; it must always include a corresponding valid target/limit ordering. Applying a profile atomically replaces the effective Preference configuration with the complete visible preset, including disabled entries. The editor and persistence must round-trip explicit direction, and a manual edit must not leave a misleading named profile badge. When preferences no longer match any known profile, the active state is `Custom`; clearing the selected profile must never erase valid preferences. Dataset-switch, stale-profile, and catalog-update restoration must preserve compatible user settings while refusing to attach a stale preset label.

Insight eligibility is separate from dimension existence: threshold rules must require minimum coverage and known metric count for **every referenced dimension**. Strong/weak labels describe only normalized quality under the **current effective goal**; they are not factual claims that a location is objectively quiet, safe, car-dependent, or family-suitable without supporting configured proxies. Rule templates must state proxy limitations and not invent travel time, safety, flood risk, availability, or other unmeasured facts. No rule may change candidate eligibility, ordering, or authoritative score.

## Failure semantics and performance

- Unsupported/missing optional interpretation metadata must disable interpretation without disabling the baseline analytical workflow. Invalid present metadata must produce a typed nonfatal interpretation diagnostic and no fabricated output; builder publication rejects it.
- Malformed app-owned preference payloads, conflicting direction/threshold values, duplicate metric IDs, and unsupported payload versions must not be partially applied. Restore must preserve the existing tested migration behavior for valid version-1 payloads; historical Favorite snapshots and exports remain frozen and are never rewritten implicitly.
- Cancellation must propagate through dataset loading, analyses, and preference persistence. No network, public API, LLM service, new geospatial source or extra per-result SQLite details query is allowed for interpretation. Reuse batched requested scoring metric values and score contributions, compute interpretation for bounded visible results and explicitly selected details, and benchmark any additional work.
- If interpretation metadata is present but a metric was removed in an updated package, never silently reinterpret the original catalog. Disallow invalid publication, and disable unsupported interpretation at runtime while preserving baseline ranking.

## Ordered implementation stages

Stage IDs are normative work units; each requires its own reviewed, tested source PATCH. Stages do not authorize changes outside their documented scope. Source paths and test seams are detailed in the paired advisory plan.

| Stage | Deliverable and owning boundary | Requirements | Independent completion evidence |
|---|---|---|---|
| **SI-S01** | Explicit v1 schema, invariants and acceptance fixtures (`geo-format/`, docs/tests) | SI-R1, SI-R2, SI-R14 | Schema validates correct/incorrect catalogs; old metadata valid |
| **SI-S02** | Builder config/resolution for dimensions and goal profiles (`geo-builder/`) | SI-R2, SI-R4, SI-R14 | Known/unknown IDs, directions, duplicates and dataset-profile mismatches tested |
| **SI-S03** | Staged metadata publication/package validation (`geo-builder/`) | SI-R2, SI-R14 | Published full/data-only packages contain valid identical interpretation payloads; failures do not replace output |
| **SI-S04** | Shared Kotlin metadata parsing and legacy fallback (`mobile/application`, platform hosts) | SI-R2, SI-R12, SI-R14 | Old/new/invalid/unsupported metadata on both hosts |
| **SI-S05** | Pure interpretation models and evidence types (`mobile/application`) | SI-R1, SI-R3, SI-R7 | Input validation, immutable trace, finite/unknown contracts |
| **SI-S06** | Dimension aggregation from existing contributions (`mobile/application`) | SI-R3, SI-R5, SI-R8 | Exact score/coverage, missing evidence, deterministic ties, no score drift |
| **SI-S07** | Named goal preset resolution and custom state (`mobile/application`) | SI-R4, SI-R5 | Apply/compare/edit/restore, opposite directions, stale profile handling |
| **SI-S08** | Versioned user-owned persistence and migration (`mobile/application`, Desktop/Android storage) | SI-R4, SI-R11, SI-R15 | Legacy version-1 payload, restart, dataset isolation, frozen Favorites |
| **SI-S09** | Deterministic typed insight rules (`mobile/application`) | SI-R6, SI-R7, SI-R8, SI-R16 | Positive/negative/coverage/rule conflicts, truthful trace |
| **SI-S10** | Localized bands, rule templates and explanation (`mobile/presentation`) | SI-R7, SI-R9, SI-R10 | RU/EN parity, missing-data wording, accessible text |
| **SI-S11** | Shared goal selector/preferences UI (`mobile/shared`) | SI-R4, SI-R9, SI-R11 | Visible effective preset, custom transition, no hidden reweighting |
| **SI-S12** | Compact list and progressive detail UI (`mobile/shared`) | SI-R7, SI-R9, SI-R10 | Drilldown, color-independent semantics, UI state correctness |
| **SI-S13** | Workspace integration, bounded analysis and regression (`application/shared`) | SI-R5, SI-R12, SI-R16 | Full rank parity, stale/cancel cases, stable Favorites/export, volume budget |
| **SI-S14** | Real-package/Desktop/Android acceptance and owning documentation | SI-R12, SI-R14–SI-R16 | End-to-end evidence, `make check`, configured gate and native/manual record |

Stages **SI-S01 → SI-S14** are ordered. Format/schema approval is required before SI-S01, and versioned user-state migration approval before SI-S08. Stage SI-S14 cannot be called complete from synthetic tests alone. Avoid turning the implementation plan into a substitute for these acceptance conditions.

## Independent acceptance scenario matrix

| ID | Inputs and action | Required result |
|---|---|---|
| `SI-P01` | Old package without `interpretation`, open and rank | Old workflow and numeric ranking unchanged; insights unavailable |
| `SI-P02` | Valid metadata with two known weighted qualities 0.8 and 0.2, weights 3 and 1 | Dimension score 65, coverage 100, four exact evidence parameters retained |
| `SI-P03` | One known quality 1.0 (weight 2), another unavailable (weight 3) | Score 100, coverage 40; suppress if configured minimum coverage is higher |
| `SI-P04` | Apply a goal preset that flips `major_road.distance_km` direction and has valid reversed endpoints | Visible effective Preferences flip; only baseline `PreferenceScorer` changes overall rank |
| `SI-P05` | RU and EN render same data, toggle language | Identical numeric results, appropriate localized labels and readable non-color cues |
| `SI-N01` | Duplicate/unknown metric ID, unknown dimension reference, invalid score threshold, unsupported operation/version | Builder rejects; runtime does not emit fabricated insights |
| `SI-N02` | Missing services metrics, rule claims weak services | Conclusion suppressed, never inferred from absence |
| `SI-N03` | Profile with reversed direction but unchanged incompatible target/limit ordering | Reject complete profile, no partial state mutation |
| `SI-N04` | Stale profile after catalog update or switching dataset | No stale named label or cross-dataset state leakage |
| `SI-B01` | Zero enabled/known metrics, one metric, coverage exactly on threshold, score exactly on band boundary | Explicit unavailable/eligible outcomes with documented inclusive boundaries |
| `SI-B02` | Equal dimension scores, equal rule priority, omitted locale, large bounded catalog | Stable order; missing locale invalid; size is rejected beyond limits |
| `SI-R01` | P0 shortlist, Required/radius/import filters, Favorites snapshots/export and external search fixture | Original candidate eligibility, ranks, frozen data and outputs unchanged without explicit goal action |
| `SI-R02` | Restore existing preference override payload v1 on Android and Desktop | Same enabled/target/limit/weight; direction inherited from dataset default; upgrade round-trip safe |
| `SI-R03` | Offline Desktop and Android read same representative package, inspect same settlement | Identical score/coverage/evidence/rule identity; no network and no per-result details N+1 query |

All exact expected outputs above are independent acceptance oracles, not claims of tests already executed.

## Requirements

### SI-R1 — preserve factual metrics as the source evidence

Raw persisted metric values remain factual evidence and must stay inspectable.

The interpretation layer must not overwrite, relabel, or replace a factual metric with an inferred conclusion. A user must be able to drill from a summary or dimension back to the exact metric values that supported it.

### SI-R2 — explicit configuration-driven dimensions

Dimension definitions must be explicit and generic.

A dimension definition must identify at least:

- stable dimension ID;
- localized presentation metadata or stable localization key;
- participating metric IDs;
- the aggregation/evidence rules needed to combine known metric qualities;
- a deterministic presentation order.

The implementation must not infer dimension membership from localized metric titles or ad-hoc Kotlin conditionals.

Because datasets may expose different metric catalogs, dimension/profile definitions should be published through an explicit versioned runtime contract or another equally explicit configuration boundary. This specification chooses an additive optional, versioned `interpretation` object in `metadata.json` (see **Runtime contract decision** below), not a new category-dependent SQL table. A different representation requires an explicit design revision before implementation.

Old datasets without interpretation metadata must continue to support the existing Required/Preferences/ranking workflow; the insight UI must degrade to unavailable/hidden rather than making up defaults from metric names.

### SI-R3 — transparent dimension score and coverage

A dimension score must be deterministic and explainable.

For the initial implementation, the preferred default is to aggregate the same normalized known qualities and effective weights already used by the Preference engine for the metrics assigned to that dimension:

```text
dimensionScore = 100 * sum(weight * quality) / sum(known weight)
```

Dimension coverage must represent the known participating weight relative to the total enabled participating weight, following the same missing-data principles as overall score coverage.

If no participating metric is known, the dimension is unavailable rather than zero.

A dimension score is a grouped diagnostic. It must not be silently summed into a second overall settlement score.

### SI-R4 — explicit goal profiles

Goal profiles must be named, inspectable, and deterministic.

If a profile affects authoritative ranking, applying it must resolve to explicit existing Preference parameters such as enabled state, direction, target, limit, and weight. The current `ANALYSIS.RANKING` implementation remains the only overall score/rank formula.

The UI must make the active profile visible. If the user edits effective Preferences after applying a named profile, the state must become visibly custom unless it still exactly matches the named profile contract. A goal may explicitly reverse a metric direction (for example, major-road distance); the user-owned persistence and editor must preserve that direction without changing the dataset default.

### SI-R5 — no hidden second ranking model

The interpretation layer must not introduce TOPSIS, a separate weighted overall score, an opaque heuristic rank, an LLM-generated rank, or a machine-learned rank behind the existing score.

Dimensions and insights explain the active evidence. They do not compete with `ANALYSIS.RANKING` for ownership of the result order.

If a future feature introduces a different learned or multi-objective ranking model, it requires a separate feature/specification and explicit user-visible semantics.

### SI-R6 — deterministic practical insights

The first insight engine must be deterministic and local.

It should support at least:

- strongest dimension advantages for the active goal;
- strongest dimension trade-offs for the active goal;
- cross-dimension conclusions when a configured rule has sufficient evidence, for example strong Nature combined with weak Everyday services.

Rules/templates must be configuration-driven or centralized in a deterministic interpretation owner. UI composables must not contain scattered business thresholds or prose-generation logic.

An LLM must not be required to generate core insights.

### SI-R7 — trace every insight to evidence

Every emitted insight must carry enough structured evidence for Details to explain it.

At minimum the explanation should be able to show:

- the dimension(s) that triggered the insight;
- dimension score/band and coverage;
- the most influential known metric components;
- raw metric values and normalized qualities where available;
- which evidence was unavailable.

The list view may stay concise, but the underlying explanation must remain inspectable.

### SI-R8 — conservative missing-data semantics

Unknown data must reduce evidence coverage rather than being interpreted as good or bad.

The insight engine must suppress or qualify conclusions when configured minimum evidence is not met. A missing school/medical/shop metric must not produce "poor services" unless the product has positive evidence for that conclusion.

Coverage thresholds, if needed, must be explicit and testable rather than hidden constants spread through presentation code.

### SI-R9 — compact visual semantics

Ranked results should expose interpretation at a glance without turning the list into another metric table.

The initial presentation should support a compact combination of:

- overall authoritative score/rank when Preferences are active;
- a small number of dimension summaries or bands;
- one concise strength insight and one concise trade-off when evidence supports them.

Color may reinforce strong/mixed/weak/unknown bands, but color must never be the only carrier of meaning. Text, numeric score/band, iconography, or another accessible cue must preserve meaning for users with color-vision differences and for screenshots/printing.

Color semantics must apply to fit/quality under the active goal/profile, not to the intrinsic name of a category. For example, an industrial-distance metric is not itself "good" or "bad"; its normalized quality depends on the explicit active preference semantics.

### SI-R10 — progressive disclosure

The intended information hierarchy is:

```text
ranked result summary
    -> practical insight / dimension summary
        -> dimension breakdown
            -> contributing metric values and diagnostics
```

The ranked list should not display every raw metric. Details must remain the authoritative place for a full breakdown.

Existing candidate-relative criterion explanations may remain available as lower-level diagnostics, but the UI should avoid showing redundant competing explanations in the compact result card once the interpretation summary is present.

All user-visible dimension/insight text must support the existing Russian/English localization boundary.

### SI-R11 — persistence ownership

The selected goal profile and any resulting custom Preference state are mutable application settings, not generated geographic truth.

They must remain under the existing application-owned settings boundary and must not be written into generated `georisk.sqlite`. The existing version-1 preference-override JSON payload lacks a direction field; this work must introduce a compatible versioned payload, preserving legacy meaning and allowing explicit direction changes. Selected goal identity is dataset-scoped, but the effective preferences are authoritative.

Default dimension/profile definitions may be dataset-provided through the generated runtime contract, but user choice/overrides remain separate mutable state.

### SI-R12 — shared deterministic behavior

Dimension calculation, evidence selection, insight rule evaluation, and profile resolution must be reusable shared Kotlin/application logic with focused pure tests.

Desktop and Android must produce the same dimension/insight result for the same dataset, goal profile, Preferences, and candidate metric values. Platform code owns layout/native integration only.

The default automated suite must not require network access, an LLM endpoint, or production credentials.

### SI-R13 — future ML boundary

Machine learning is explicitly outside the first implementation of `ANALYSIS.INTERPRETATION`.

The deterministic evidence contracts introduced here should make later experiments possible, including:

- settlement similarity/archetype discovery;
- personalized preference learning from explicit user choices;
- OSM completeness/anomaly detection.

Any learned output must be labeled as inferred, keep confidence/provenance distinct from measured facts, and be introduced through a separate bounded specification before it affects user-visible ranking or conclusions. Custom neural networks are not a default requirement; interpretable classical models should be evaluated first for tabular metric data.


### SI-R14 — strict additive interpretation metadata contract

The publisher must validate the optional versioned interpretation catalog against the generated metric and preference-definition catalogs before atomic publication. The common runtime parser must use one validated semantic model on Android and Desktop, preserve format-v1 backward compatibility, and fail closed for malformed present interpretation data without breaking the base dataset. Catalog collections must have documented finite size limits and deterministic ordering.

### SI-R15 — direction-aware user-state migration

The user-owned preference serialization must round-trip explicit score direction and selected goal identity using a versioned migration contract. Version-1 payloads remain readable with their original inherited direction; stale or mismatched goal IDs never override effective saved Preferences. Frozen Favorite analysis snapshots and existing exports must not change during restoration or profile selection.

### SI-R16 — bounded truthful, offline interpretation

Interpretation must reuse scores and batch-fetched metric evidence from the existing analysis path, avoid per-candidate details queries and hidden scoring, honor cancellation, and limit UI work to the visible/result-details scope. Interpretive statements must be distinguishable from measured facts and must not claim unmeasured objective safety, travel time, noisiness, or availability.

## Example interaction

With a goal profile such as **Nature & quiet**, a ranked result might present:

```text
Uzhlyatino                                      72
Nature                  89  strong
Environmental comfort   83  strong
Everyday services       41  weak

↑ Strong natural surroundings
↓ Everyday services are limited
```

Details could then explain:

```text
Nature 89 / coverage 100%
- Forest distance             96
- Forest coverage             88
- Water access                74

Everyday services 41 / coverage 82%
- Supermarket                 38
- Medical                     44
- School                      unavailable
```

The example is illustrative rather than a real published metric catalog. The runtime must not synthesize unavailable metrics or claim these numbers were calculated from an actual dataset. The important contract is that the summary remains compact and every conclusion is traceable to deterministic evidence.

## Non-goals

The first implementation does not require:

- an LLM-generated narrative;
- a custom neural network;
- automatic learning from private user behavior;
- a replacement for existing Required filters;
- a second overall ranking formula;
- hiding raw metrics or removing detailed analysis;
- downloading new external datasets solely for interpretation;
- hardcoded country-specific goal logic;
- user-authored arbitrary rule/programming syntax.

## Compatibility and migration

The preferred implementation is additive.

- Existing datasets without dimension/profile metadata continue to work with the current analysis UI.
- Existing scoring/ranking semantics remain unchanged unless the user explicitly applies a goal profile that resolves to visible Preference values.
- Existing Favorites/snapshots remain interpretable because their effective Preference parameters remain authoritative even if optional profile identity is unavailable.
- The additive optional `metadata.json.interpretation` field must follow `geo-format/metadata.schema.json` compatibility rules and be validated by the Python writer/validator and common Kotlin metadata parser used by both platform hosts. An incompatible contract change requires an explicit `geo-format/VERSION` decision and migration before implementation.
- No generated dataset may contain mutable user profile selection or custom Preference state.

## Validation

Before acceptance, automated tests should prove at least:

1. deterministic dimension score/coverage from known and partially missing components;
2. unavailable dimension state when no component is known;
3. named goal-profile resolution to the exact existing Preference model;
4. ranking parity: applying a profile changes rank only through those explicit Preferences and never through a second score;
5. deterministic strength/trade-off/cross-dimension insight selection;
6. insight suppression when evidence coverage is below the configured minimum;
7. evidence traceability from insight to dimensions and raw metric contributions;
8. stable localization-independent calculation and localized presentation labels;
9. graceful behavior for old datasets with no interpretation metadata;
10. Desktop/Android parity for the same synthetic fixture;
11. accessibility/presentation checks showing that meaning survives without color alone.

Representative manual acceptance should compare several deliberately different settlements and confirm that the generated conclusions are practically understandable, non-contradictory, and supported by the displayed metrics.

## Start and acceptance decisions

This change is now the selected P1 implementation focus by an explicit developer request. It does **not** accept or discard the separate P0 verification-pending backlog. Before any implementation stage, the developer must review the optional metadata v1 contract, semantics for direction overrides, and goal profile composition in the representative dataset catalogs.

After all stages, product acceptance requires independent evidence for positive, negative, boundary, regression, cross-platform, and real-package scenarios, plus ownership-document updates. A successful PATCH application, source stage tests, or a model review is not acceptance.

