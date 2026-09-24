---
type: Specification
title: Desktop analysis workspace and explainable ranking
description: Desktop map-first weighted-analysis sub-spec with implementation substantially complete and acceptance verification pending.
document_role: subspec
spec_status: verification-pending
parent: ../spec-initial-functional-product.md
---
# Desktop analysis workspace and explainable ranking

## Status

Implementation substantially complete; acceptance verification pending.

Parent specification: [`../spec-initial-functional-product.md`](../spec-initial-functional-product.md).

This sub-spec is no longer the current coding focus. The umbrella now points to
[`settlement-shortlist-workflow.md`](settlement-shortlist-workflow.md). Strict country-scale ranked
analysis acceptance and the remaining native Desktop workspace checks still must be recorded before
this sub-spec can be archived. The configured Desktop/Android `make check-all` gate has been recorded
in the owning test documentation; changing the coding focus does not imply acceptance.

## Feature scope

- `ANALYSIS.PREFERENCES` — generic weighted Preference model and dataset defaults.
- `ANALYSIS.RANKING` — normalized quality, weighted coverage, score, and stable ranking.
- `ANALYSIS.EXPLANATION` — deterministic contribution/diagnostic presentation.
- `SEARCH.FILTERING` and `SEARCH.RADIUS` — hard eligibility and exact center/radius semantics.
- `PRESENTATION.ANALYSIS_WORKSPACE` — map-first Required/Preferences/results/details workflow.
- `MAP.RESULTS` — list/map selection and map-driven center interactions.
- `SETTINGS.PERSISTENCE` — persisted preference/search-context state.
- `PLATFORM.KMP` — shared logic with platform-owned adapters.
- `FORMAT.GEODATA` — compatibility-sensitive generated metric/default contract.

Feature identifiers are defined in [`../../../FEATURES.md`](../../../FEATURES.md).

## Goal

Turn the wide Desktop application from a form-oriented search pane beside a map into a map-first local
analysis workspace that answers two separate questions:

1. which settlements are eligible because they satisfy required constraints;
2. which eligible settlements best match the user's weighted preferences, and why.

The resulting workflow must remain offline-first, deterministic, explainable, country-agnostic, and
independent of an LLM.

The primary wide-Desktop interaction should combine:

- a persistent left analysis panel;
- a large central map;
- required constraints that determine eligibility;
- weighted preferences that determine ranking;
- a ranked result list linked to map selection;
- a compact settlement information pane beneath the map with explicit Details/External-search secondary views;
- deterministic score contribution and data-coverage explanations.

## Relationship to the umbrella specification

This work builds on the existing dynamic metric, local search, settlement-details, and map-overlay
requirements, especially R5, R8, R9, R11, R12, R13, and R17 of the parent specification.

It does not replace or weaken the parent's acceptance requirements. The original min/max
`SearchCondition` behavior remains the authoritative hard-constraint model. Preference ranking is an
additional analysis layer applied only after hard eligibility is established.

The sub-spec also reuses the application-owned user settings boundary implemented by
`user-preferences.md`, but it does not move mutable preference state into generated dataset SQLite.

## Current state

The map-first Desktop analysis workspace, generic Required/Preferences model, deterministic weighted scoring/ranking, candidate-relative explanation, batch candidate retrieval, preference persistence, map/list selection, and map-driven center selection are implemented. Stable implementation detail belongs in [`../../../IMPLEMENTATION.md`](../../../IMPLEMENTATION.md), [`../../../../mobile/IMPLEMENTATION.md`](../../../../mobile/IMPLEMENTATION.md), [`../../../USAGE.md`](../../../USAGE.md), and [`../../../TESTS.md`](../../../TESTS.md).

This sub-spec remains `verification-pending` only because strict representative country-scale scored acceptance and the remaining native Desktop workspace checks have not yet been recorded. The configured Desktop/Android gate is complete. No new product behavior should be added here merely because those checks are pending.

## Requirement map

| Requirement | Feature ID | Purpose |
|---|---|---|
| DA-R1, DA-R11-DA-R12, DA-R14 | `PRESENTATION.ANALYSIS_WORKSPACE` | Wide Desktop analysis composition and progressive editing/details |
| DA-R2-DA-R5 | `SEARCH.FILTERING`, `ANALYSIS.PREFERENCES` | Required/Preference separation and generic defaults |
| DA-R6-DA-R10, DA-R16 | `ANALYSIS.RANKING` | Quality, score, coverage, batching, stable rank-before-limit, recalculation |
| DA-R13, DA-R17, DA-R19 | `MAP.RESULTS`, `SEARCH.RADIUS` | List/map selection, map-driven center, search/map separation |
| DA-R15 | `ANALYSIS.EXPLANATION` | Explainable ranked-analysis breakdown |
| DA-R18 | `SETTINGS.PERSISTENCE` | Application-owned Preference persistence |
| DA-R20 | `PLATFORM.KMP` | Shared/platform responsibility boundary |
| DA-R21 | `FORMAT.GEODATA` | Explicit dataset compatibility contract |

## Requirements

### DA-R1 — map-first wide Desktop composition

The map must be the primary visual workspace on wide Desktop layouts.

The default composition should use approximately:

- one third of the wide Desktop window for the persistent left analysis panel, with practical minimum
  and maximum width bounds;
- the remaining two thirds for the right map/details column;
- most of the right-side height for the map when no settlement is selected, reserving only a shallow lower status pane for ranked-result count and recalculation state;
- approximately the upper two thirds of the right-side height for the map and the lower third for
  settlement information while a settlement is selected.

The left panel should remain horizontally resizable. Desktop Compose controls that need transient or
expanded space must stay outside the platform map rectangle rather than depending on z-order above a
native/Swing renderer. Closing a settlement must collapse the lower area back to the shallow result-status pane.

Narrow Android composition is not redesigned by this sub-spec.

### DA-R2 — separate eligibility from preference

The analysis model must distinguish:

- **required constraints**, which determine whether a settlement is eligible;
- **preferences**, which influence ranking among eligible settlements but do not exclude settlements
  by themselves.

The UI must communicate this distinction explicitly. A preference weight must never be presented as
though it were a hard minimum or maximum filter bound.

The same metric may participate in both a hard constraint and a preference when the user deliberately
configures both.

### DA-R3 — preserve hard-filter semantics

Existing `SearchCondition(metricId, minValue, maxValue)` semantics remain unchanged for required
constraints.

Hard constraints must continue to support:

- minimum-only bounds;
- maximum-only bounds;
- bounded ranges;
- multiple simultaneous metric conditions;
- optional center/radius constraints;
- missing metric values as unknown/unavailable rather than numeric zero.

A settlement that does not satisfy an effective required condition must not enter the scoring stage.

### DA-R4 — generic preference model

Shared Kotlin must define an immutable generic preference model keyed by stable metric ID.

An enabled preference must represent at least:

- metric ID;
- preferred direction;
- target value representing a fully satisfactory result;
- limit value representing the point at which the preference contribution reaches zero;
- user weight on a 1–10 scale.

Ordinary numeric metrics must not require metric-ID-specific Kotlin branches such as forest/water/farm
conditionals.

`PreferredDirection.LOWER` and `PreferredDirection.HIGHER` are directly scoreable. A metric whose
persisted direction is `NEUTRAL` must not receive an automatic preference meaning. Supporting a user-
selected direction for neutral metrics may be added through the generic preference contract, but must
remain explicit rather than inferred from the metric ID or group name.

### DA-R5 — configuration-driven preference defaults

A dataset may provide default preference presentation/scoring values so common analysis can start with
useful one-click criteria instead of requiring every target and weight to be entered manually.

Default preference metadata must be distinct from the existing `default_enabled` hard-filter
visibility field. The following concepts must not be coupled semantically:

- whether a metric is physically generated by a metric profile;
- whether a hard-filter row is initially visible;
- whether a preference is initially enabled;
- the default target/limit/weight used for preference ranking.

The exact persisted representation must be decided during implementation after inspecting
`metric_definition`, builder configuration, both runtime readers, and package compatibility. It may be
an additive preference-definition table or an explicit versioned extension of metric metadata.

Whatever representation is chosen, Python configuration remains build-time input and the Kotlin UI
consumes only the generated runtime contract.

### DA-R6 — deterministic normalized quality

Preference scoring must be pure deterministic shared Kotlin logic.

For an enabled preference whose metric value is available, calculate normalized quality in `[0, 1]`.

For `LOWER`:

- values at or below the target produce quality `1`;
- values at or beyond the limit produce quality `0`;
- values strictly between target and limit interpolate linearly.

For `HIGHER`:

- values at or above the target produce quality `1`;
- values at or below the limit produce quality `0`;
- values strictly between limit and target interpolate linearly.

Invalid target/limit combinations must be rejected by the owning configuration/package validation or
runtime contract validation rather than producing undefined scores.

### DA-R7 — deterministic weighted score

For all enabled preferences whose metric values are known:

```text
score = 100 * sum(weight * quality) / sum(known weight)
```

The UI may round the displayed score, but internal precision must remain sufficient for deterministic
ordering.

If no enabled preference has a known metric value, the score is unavailable rather than zero.

Scoring must not depend on map state, locale-sensitive floating-point formatting, current time, random
values, network services, or an LLM.

### DA-R8 — explicit missing-data coverage

Missing metric values remain unknown. They must not be silently converted into zero, success, or
failure.

Unknown preference metrics are excluded from both the weighted score numerator and the known-weight
denominator.

The ranked result must also expose weighted data coverage:

```text
coverage = 100 * known enabled weight / total enabled weight
```

Ranking order must use:

1. score descending;
2. coverage descending;
3. a deterministic stable tie-breaker based on stable settlement data.

The UI must make materially incomplete coverage visible so a high score based on sparse data is not
presented with false confidence.

### DA-R9 — batch scoring input contract

The repository boundary must support retrieving eligible settlement candidates together with the
numeric values required by the currently enabled preferences in a bounded number of SQLite queries.

The implementation must not hydrate full `SettlementDetails` separately for every candidate.

Platform repositories should perform:

- hard metric/radius coarse candidate reduction as appropriate;
- batch retrieval of only the scoring metric IDs needed for the current request.

Shared Kotlin owns exact radius semantics, normalized quality, weighted score, coverage, and final
ordering.

The exact repository model name is an implementation decision, but it should be immutable and expose
only data needed by shared analysis rather than SQLite-specific row structures.

### DA-R10 — rank before final result limit

Hard filtering and exact-radius filtering must establish the eligible candidate set before final
preference ranking.

The user-visible result limit must be applied only after deterministic scoring and sorting.

Repository optimizations must not truncate candidates in unrelated name/database order before scoring
when that truncation could remove a settlement that belongs in the requested top ranked results.

If a bounded pre-scoring safety cap is required for very large datasets, it must be explicit,
deterministic, documented as a product limit, and validated against realistic country-scale data.

### DA-R11 — compact progressive criterion editing

The left analysis panel should keep the common state compact.

Required constraints and preferences must be visually separated, for example as **Required** and
**Preferences** sections.

A compact preference row should normally expose:

- enabled state;
- metric title;
- concise target summary when useful;
- current weight as a compact value/badge.

Enabling a preference must not automatically expand every active slider/editor. Selecting a row may
expand target/limit/weight controls, with at most one ordinary preference editor expanded at a time
unless usability testing later justifies another interaction.

### DA-R12 — ranked results remain in the left panel

The ranked result list should appear below the analysis criteria in the left panel rather than in a
separate permanent Desktop pane.

Each result should expose at least:

- score when available;
- settlement display name;
- compact distinguishing context such as place type or selected metric highlights;
- an indication when data coverage is incomplete.

The result list and map must share one selection model.

### DA-R13 — bidirectional list/map selection

Selecting a ranked result in the list must:

- select and visually distinguish the corresponding map marker;
- move or adjust the map camera when necessary;
- open the settlement details pane beneath the map.

Selecting a result marker on the map must:

- select the same settlement in shared application state;
- reveal or scroll to the corresponding result when practical;
- open the same details pane beneath the map.

Ranked result markers should expose settlement display names at useful zoom levels. Ordinary labels may be collision/zoom suppressed to keep the map readable, but the selected settlement label must remain visually prominent. Labels are transient presentation state and must not alter search semantics.

Desktop hover linkage is optional. Selection linkage is required.

### DA-R14 — settlement details pane

On wide Desktop, selected settlement details must appear in a dedicated lower pane beneath the map
instead of overlapping the map or being appended inside the left search list. The map/details split
must preserve the selected marker and map session while the lower pane is visible.

When no settlement is selected, a shallow lower pane must expose the current ranked-result count and whether automatic recalculation is still running, so search feedback is visible without inspecting the left results list.

When a settlement is selected, the default lower pane must remain compact and expose:

- settlement name;
- place type and population when available;
- coordinates;
- current ranked-result count;
- score when available;
- weighted data coverage;
- every metric participating in the current analysis, prioritizing effective Required constraints before enabled Preferences, with missing values shown as unknown; the criteria strip may scroll horizontally when necessary so no active criterion is silently omitted;
- an explicit **Details** action;
- a separate external-search action when providers are configured;
- a close action.

Complete grouped raw metrics and the full deterministic comparative explanation must be available only
after the user explicitly opens **Details**. Configured external property-search
provider buttons must likewise be shown only after the separate **External search** action. Both views
remain in the lower pane beneath the map; neither may overlap the platform map rectangle.

The existing `GeoRepository.details()` result remains the source for complete selected-settlement
measurements. Score explanation is additional derived presentation data.

### DA-R15 — explainable contribution breakdown

For every enabled preference, shared analysis must be able to expose enough structured data to explain
the result:

- metric ID/title/unit;
- raw value when known;
- preferred direction;
- target;
- limit;
- weight;
- normalized quality;
- weighted contribution;
- unknown state.

The UI explanation must be generated deterministically from this structure. No LLM-generated reason
or opaque composite score is required.

### DA-R16 — automatic local recalculation

Changing an effective required constraint or preference should update Desktop results automatically
without requiring the primary workflow to end with a separate **Search settlements** button.

Rapid input changes, especially sliders, must be debounced and stale calculations must be cancellable
or superseded so old results cannot overwrite newer state.

An explicit apply/search fallback is allowed only if measurements on a supported realistic dataset
show that automatic recalculation cannot meet acceptable interaction latency. The measured reason and
fallback behavior must then be documented.

### DA-R17 — map-driven center selection

Desktop users must be able to select/change the analysis center from the map in addition to the
multilingual settlement-name picker.

Map-driven center selection must produce the same shared center/radius semantics as name-based
selection and must not leak MapLibre/JCEF types into shared domain/search contracts.

In Desktop **Pick center on map** mode, a click anywhere on the map must select the geographically nearest
settlement from the complete current dataset settlement index. The selected settlement becomes the
shared center, so its canonical display name appears in Search area and existing radius/persistence
semantics remain unchanged. Center picking must not change the currently selected ranked settlement,
open settlement details, or move the ranked-results scroll position. Current hard filters, ranking
preferences, and ranked-result visibility must not constrain which settlement can be chosen as center.

### DA-R18 — preference persistence extends application-owned state

User-customized preference state belongs to the application-owned settings boundary, not generated
`georisk.sqlite`.

Persistence should extend the existing dataset-scoped current search context with stable metric IDs
and generic preference values. The application settings database retains its own schema/version
lifecycle independent from `geo-format`.

Restore must tolerate removed preference definitions in the same way current hard-filter restoration
tolerates removed metric IDs.

This sub-spec does not require named saved profiles or search history.

### DA-R19 — map/search separation remains invariant

PMTiles and renderer state remain presentation infrastructure.

Generated SQLite/runtime metric values remain the analytical source of truth. Map styling, camera
position, marker visibility, hover state, or renderer implementation must never alter hard-filter or
scoring semantics.

Search-result and selected-settlement GeoJSON remain transient presentation data.

### DA-R20 — platform boundaries

Shared Kotlin should own:

- immutable preference/scoring models;
- deterministic score/coverage calculation;
- ranking/tie-breaking;
- analysis state that must behave consistently across platforms;
- map/result selection contracts where platform-neutral.

Desktop-specific code may own:

- wide workspace composition details;
- resizable-pane behavior;
- pointer/hover affordances;
- Desktop host renderer integration.

Android must continue to compile and preserve its existing hard-filter workflow. This sub-spec does
not require an Android UI redesign, although shared scoring contracts should remain reusable there.

### DA-R21 — persisted dataset compatibility is explicit

If preference defaults require a changed generated dataset contract, compatibility must follow the
existing `geo-format` discipline.

Before implementation changes persisted semantics:

- inspect Python writers and validators;
- inspect Desktop and Android readers;
- choose an additive/backward-compatible representation where practical;
- increment `geo-format/VERSION` only when existing readers cannot safely interpret the changed
  semantics;
- update format documentation and cross-boundary tests together.

Opening an older package with no preference-default metadata must have explicit behavior. A preferred
compatibility path is hard-filter-only operation plus user-created preferences where the runtime has
enough generic metadata; unsupported behavior must never be guessed from metric IDs.

## Scenarios

### DA-S1 — start from useful defaults

A user opens a dataset. The left panel presents required constraints and configured preferences in
compact sections. Enabled preferences have generic target/limit/weight defaults from the runtime
contract. Eligible settlements are scored and ranked locally without an LLM.

Exercises: DA-R1, DA-R2, DA-R4, DA-R5, DA-R6, DA-R7, DA-R11, DA-R12.

### DA-S2 — combine hard constraints and preferences

A user requires settlements to be within 50 km of a selected center and requires a risk-related metric
to satisfy a hard minimum distance. Forest and beach proximity are strong preferences while medical
access has a smaller weight.

A settlement violating a required condition is absent. Remaining settlements are ranked by preference
score and coverage.

Exercises: DA-R2, DA-R3, DA-R7, DA-R8, DA-R9, DA-R10.

### DA-S3 — customize one preference

The user selects one preference row, changes its target and raises its weight from 5 to 8. Only that
row expands. After a short debounce, current ranking and map state update without blocking the UI or
requiring an explicit search action.

Exercises: DA-R11, DA-R16, DA-R18.

### DA-S4 — inspect a ranked settlement

The user selects a result in the left list. The corresponding marker is highlighted, the map moves if
needed, and the lower details pane explains score, coverage, key contributions, unknown metrics, and
complete grouped measurements.

Exercises: DA-R12, DA-R13, DA-R14, DA-R15.

### DA-S5 — select from the map

The user clicks a result marker. The same settlement becomes selected in application state, the result
list follows the selection when practical, and the same lower details pane opens.

Exercises: DA-R13, DA-R14, DA-R19.

### DA-S6 — incomplete metric coverage

A settlement has no value for one enabled preference but has known values for the others. The missing
metric is not interpreted as zero. The score uses known weighted preferences, coverage is reduced, and
the detail explanation identifies the unknown metric.

Exercises: DA-R7, DA-R8, DA-R15.

### DA-S7 — generic new preference-capable metric

A developer adds an ordinary numeric metric and generic preference defaults through the owning builder
configuration/runtime contract, rebuilds the dataset, and the Desktop preferences UI exposes it
without adding metric-ID-specific shared Compose code.

Exercises: DA-R4, DA-R5, DA-R20, DA-R21.

### DA-S8 — country-scale ranked search

A large dataset returns many hard-filter candidates. Platform SQLite retrieves active scoring values in
batch, shared Kotlin scores them, and the final result limit is applied after ranking. No per-candidate
`details()` loop is used.

Exercises: DA-R9, DA-R10.

### DA-S9 — map-driven center

The user selects a supported settlement center from the map and sets a radius. Search behavior matches
the shared semantics used by name-based center selection.

Exercises: DA-R17, DA-R19.

## Non-goals

This sub-spec does not require:

- accepting or archiving the parent initial-product specification;
- redesigning Android around the wide Desktop workspace;
- hardcoded `Living`, `Tourism`, `Nature`, or `Risk` score formulas;
- named saved scoring profiles;
- favorites, notes, or multi-settlement comparison;
- cloud accounts or synchronization;
- an LLM or natural-language query parser;
- scraping or embedding property listings;
- route planning;
- new DEM, climate, soil, pollution, cadastral, or satellite data sources;
- production-complete cartographic styling;
- category-specific scoring code for forest, water, farms, transport, or other ordinary numeric
  metrics.

Preset domain profiles may be considered later as configuration built on the same generic scoring
engine; they are not required for this increment.

## Design constraints

- Core analysis remains offline-first.
- Hard filtering and scoring remain deterministic and inspectable.
- Missing data remains unknown, never implicit numeric zero.
- Generated datasets remain immutable runtime inputs.
- Mutable user preference state remains application-owned.
- Generic stable metric IDs and versioned metadata drive ordinary numeric criteria.
- Metric build profiles, hard-filter default visibility, and preference defaults remain separate
  concepts.
- UI code must not construct SQL or own scoring algorithms.
- Shared analysis code must not depend on JDBC, Android SQLite, filesystem, MapLibre, or JCEF types.
- The map must not become an analytical source of truth.
- Scoring must be reproducible in tests without network access, map rendering, generated large data,
  or an LLM.
- Avoid N+1 database access when ranking candidates.

## Compatibility / migration

Two independent persisted boundaries may evolve during this increment:

1. generated dataset preference-default metadata, governed by `geo-format` when persisted semantics
   change;
2. mutable user preference state, governed by the application settings database and its independent
   migration/version owner.

The generated dataset decision is now explicit:

1. preference defaults use additive table `metric_preference_default`, separate from `metric_definition.default_enabled`;
2. rows reference stable generated `metric_id` values and store resolved scoreable direction, target, limit, weight, and initial enabled state;
3. `geo-format/VERSION` remains `1` because older readers ignore the additive table and updated readers explicitly fall back to an empty default catalog when it is absent;
4. Python profile validation, SQLite constraints, Desktop reader tests, and Android reader parity protect the writer/reader contract;
5. user-customized targets/weights remain outside generated dataset artifacts and belong to the application settings boundary.

The application settings payload/schema may need a new version to persist preference state. Existing
saved hard-filter context must either migrate deterministically or remain loadable with documented
fallback behavior.

## Validation

Acceptance requires all of the following:

1. Shared scoring tests cover `LOWER` and `HIGHER` directions, target saturation, limit saturation,
   linear interpolation, weight handling, unavailable scores, and deterministic tie-breaking.
2. Missing-data tests prove unknown metric values are not converted to zero and verify weighted
   coverage behavior.
3. Hard-filter regression tests prove existing `SearchCondition` and center/radius semantics remain
   unchanged.
4. Repository/search tests prove active scoring metrics are loaded in batch without per-candidate
   `details()` hydration.
5. Ranking tests prove the final visible result limit is applied after scoring and deterministic sort.
6. Generic-contract tests prove a newly configured preference-capable metric can appear without a
   metric-ID-specific shared UI branch.
7. Persisted-format tests cover the chosen preference-default representation and legacy package
   compatibility behavior.
8. Application-settings tests cover preference save/restore, payload/schema migration, dataset
   scoping, and removed metrics.
9. Desktop with a real package shows the map-first layout, compact criteria, ranked result list,
   linked map markers, a left-contained Add filter chooser, and lower details pane.
10. List-to-map and map-to-list selection select the same settlement and show the same details.
11. The details pane explains contribution structure and visibly reports incomplete coverage without
    overlapping the map rectangle.
12. Rapid preference changes do not freeze the Desktop UI and stale calculations cannot replace newer
    results.
13. Map-driven center selection produces the same shared center/radius semantics as name-based center
    selection for the supported center type.
14. Android/shared builds continue to compile and the existing hard-filter workflow remains usable; the configured gate must compile the real `androidApp` host rather than only shared Android-host source sets.
15. `make check` remains network-free and passes.
16. Relevant configured Gradle tests pass where the toolchain is available.
17. Stable implementation knowledge is moved into owning architecture/implementation/usage/test docs
    after acceptance before this sub-spec is archived.

Performance should be measured with at least one realistic country-scale package or representative
candidate volume. Record candidate count, enabled scoring metric count, batch retrieval time, shared
scoring/sort time, and end-to-end recalculation latency. If DA-R16 requires an explicit apply fallback,
record the measured bottleneck that justifies it.

## Remaining acceptance

1. Run the strict country-scale scoring acceptance on a representative generated package with enabled scoring metrics and record candidate count, scoring metric count, batch retrieval time, shared scoring/sort time, and end-to-end latency.
2. Confirm the real Desktop workspace still satisfies map/list selection, Details/explanation, map-driven center, missing-data coverage, and non-overlap constraints on the supported renderers.
3. Move any newly discovered stable implementation knowledge into the owning current-state documents rather than expanding this spec.
4. Archive this sub-spec after the required evidence is recorded.

The implemented call paths, file ownership, UI decomposition, persistence schema history, and performance-report tooling are intentionally documented outside this active spec.
