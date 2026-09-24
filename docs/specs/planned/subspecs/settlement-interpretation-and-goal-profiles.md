---
type: Specification
title: Settlement interpretation and goal-oriented insights
description: Planned P1 sub-spec for turning transparent metric/ranking evidence into goal-oriented dimensions, practical conclusions, and compact visual summaries.
document_role: subspec
spec_status: planned
parent: ../../active/spec-initial-functional-product.md
---
# Settlement interpretation and goal-oriented insights

## Status

Planned P1 follow-up; not the current implementation or acceptance focus.

Parent specification: [`../../active/spec-initial-functional-product.md`](../../active/spec-initial-functional-product.md).

This sub-spec preserves reviewed product intent while P0 acceptance is still being closed. It must not enlarge or delay the current initial-functional-product acceptance gate. Before implementation begins, move this document into `docs/specs/active/subspecs/`, change `spec_status` to `active`, and update the umbrella `current_focus`, roadmap, and specification index coherently.

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

Because datasets may expose different metric catalogs, dimension/profile definitions should be published through an explicit versioned runtime contract or another equally explicit configuration boundary. The implementation phase must choose the smallest compatible representation after inspecting the current `metric_definition` and preference-default contracts.

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

The UI must make the active profile visible. If the user edits effective Preferences after applying a named profile, the state must become visibly custom unless it still exactly matches the named profile contract.

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

They must remain under the existing application-owned settings boundary and must not be written into generated `georisk.sqlite`.

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

The exact labels, thresholds, and composition are configuration/product decisions to validate during implementation. The important contract is that the summary remains compact and every conclusion is traceable to deterministic evidence.

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
- Any persisted-format addition must follow normal `geo-format` compatibility/versioning rules and be validated by both Python writer and Kotlin readers.
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

## Promotion criteria

Promote this planned sub-spec to the active implementation focus only when:

- the current P0 acceptance work is closed or explicitly deprioritized by a conscious roadmap change;
- the initial dimension vocabulary and first goal profiles are agreed well enough to implement;
- the runtime-contract ownership for dimension/profile definitions is chosen after inspecting current `geo-format` compatibility constraints;
- the implementation can remain deterministic and explainable without introducing a hidden second score.
