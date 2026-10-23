---
type: Implementation Guide
title: Settlement interpretation and goal profiles implementation plan
description: Advisory stage-by-stage implementation guidance and independent verification oracles for the active P1 interpretation specification.
---
# Settlement interpretation and goal profiles: implementation plan

## Purpose and authority

This is an **advisory** plan for the normative [settlement interpretation specification](settlement-interpretation-and-goal-profiles.md). The spec's `SI-R1`–`SI-R16` requirements and `SI-S01`–`SI-S14` stages control intended behavior and acceptance. This plan provides anticipated files, boundaries, algorithm sketches and independently defined fixtures. It is not an approval to change protected contracts, an automated-run packet or a substitute for source inspection.

Read `README.md`, `AGENTS.md`, `docs/specs/README.md`, the normative spec, `docs/ARCHITECTURE.md`, `docs/IMPLEMENTATION.md`, `docs/CONFIGURATION.md`, `docs/TESTS.md`, nearest applicable `AGENTS.md`, and the current source/tests before each stage. The supplied evidence is a **text snapshot**, not a Git checkout. Confirm actual file paths and clean source identity against a current complete repository before preparing a new overlay. All work uses developer-controlled ChatGPT `DEFAULT` scoped PATCHes after this `GENSPEC` design; **no Aider, worker orchestrator, hidden reviewer, or automated Git promotion is used**.

### Architectural owners

| Owner | Role in this feature | Must not own |
|---|---|---|
| `geo-format/` | Optional `metadata.json.interpretation` schema and compatibility rules | Kotlin business logic, UI |
| `geo-builder/` | Catalog configuration, metric/profile resolution, semantic validation and staged package publication | Runtime computation |
| `mobile/core` | Existing immutable metric/settlement identities and pure geography, when needed | Package JSON parsing or scoring orchestration |
| `mobile/application` | Validated catalog, goal resolution, dimension math, typed insights, state restoration and controller orchestration | Android/JVM I/O, UI labels |
| `mobile/presentation` | Localized, deterministic, color-independent bands, templates and evidence view models | Ranking or insight eligibility algorithms |
| `mobile/shared` | Compose goal picker, cards, details, interaction/accessibility | SQL, metadata file parsing, scoring formulas |
| `mobile/desktopApp`, `mobile/androidApp` | Package file I/O, host persistence, native smoke evidence | Duplicate interpretation rules |

### Design decisions to preserve across sessions

1. **Wire ownership:** add optional `interpretation` object with `version: 1` to existing `metadata.json`, and its optional JSON Schema definition in `geo-format/metadata.schema.json`. Keep `geo-format/VERSION` at 1 **only if** semantic compatibility is demonstrated; any incompatible alternative must be separately reviewed. The current Kotlin `DatasetPackageMetadataParser` ignores extra metadata keys, permitting backward-compatible optional fields. Do not place dimension data in PMTiles or mutate `georisk.sqlite` for this feature.
2. **Builder source of truth:** `geo-builder/config/interpretation-profiles.toml` is a candidate separate catalog with explicit dataset selection. No automatic inference from metric titles, category names or missing values. Validate against the selected generated `metric_definition` and `metric_preference_default` rows; use the same source metric IDs in both ecosystems.
3. **Preset semantics:** a named goal contains a complete, explicit preference configuration, including `enabled`, `direction`, `target`, `limit`, and `weight`. Existing `PreferenceScorer` remains the sole source of normalized quality, weighted overall score and ranking. The baseline `balanced-living` preset reproduces dataset-scoped defaults exactly. Reverse-direction goals are permitted only with valid reversed endpoints and persisted direction overrides.
4. **User-state ownership:** existing `MetricPreferenceOverridePayloadCodec` version 1 has no direction; introduce version 2 or an equivalent explicitly versioned compatible payload, and persist selected goal identity without rewriting historical Favorite snapshots. Evaluate whether existing `user_preferences` schema can store goal identity inside the settings JSON cleanly; if not, add a strictly forward `ApplicationSettingsSchema` migration and test both platform executors. Never silently change v1 interpretation.
5. **Evidence model:** aggregate *enabled* `PreferenceContribution` objects, not a parallel quality formula or UI-loaded raw metrics. Maintain source order + stable-ID tiebreaks; make minimum known count and minimum weighted coverage explicit in each dimension/rule.
6. **Text:** assign each typed rule a stable template key resolved in `mobile/presentation` for RU and EN; metadata cannot contain executable expressions. Translate proxy-limited claims conservatively. `Unknown` must not be described as `weak` or zero.
7. **Performance:** reuse `SettlementAnalysisService` batched scoring metrics and cached contribution lists; never issue `details()` once per ranked row. Interpretation should be computed for bounded visible results and currently opened details only; preserve full eligible-candidate rank/Favorites diagnostic behavior.
8. **Stage approval:** a small, focused change is preferred. If stage scope requires an incompatible format change, new dependency, new module, or migration not yet authorized, stop for developer decision and revise the spec/plan before editing source.

### Proposed metadata v1 shape (illustrative wire fixture)

This is a target fixture for contract/schema tests, **not current generated package evidence**. Metric IDs below exist in the current `core10` configuration; quality values are never embedded in the catalog.

```json
{
  "interpretation": {
    "version": 1,
    "dimensions": [
      {
        "id": "nature",
        "labels": {"en": "Nature", "ru": "Природа"},
        "sortOrder": 10,
        "metricIds": ["forest.distance_km", "beach.distance_km"],
        "minimumCoverage": 60,
        "minimumKnownMetrics": 1
      }
    ],
    "goalProfiles": [
      {
        "id": "balanced-living",
        "labels": {"en": "Balanced living", "ru": "Сбалансированная жизнь"},
        "sortOrder": 10,
        "preferences": [
          {"metricId": "forest.distance_km", "enabled": true, "direction": "lower", "target": 0.25, "limit": 3.0, "weight": 8}
        ]
      }
    ],
    "insightRules": [
      {
        "id": "nature-strength",
        "type": "dimension-strength",
        "dimensionIds": ["nature"],
        "templateKey": "interpretation.dimension.strength",
        "minimumCoverage": 60,
        "minimumKnownMetrics": 1,
        "minimumScore": 75
      }
    ]
  }
}
```

The one-preference example above intentionally omits other dataset metrics for brevity; a real `balanced-living` profile **must** resolve the complete default catalog. Test fixtures must not confuse this excerpt with a publishable profile. A full valid specimen must include every required existing default metric, with its correct enabled/direction/target/limit/weight values. All final field names, range limits, version behavior, conditional typed-rule requirements and identifier patterns belong in `geo-format/metadata.schema.json` and are mirrored by semantic validators in Python and Kotlin. Bound arrays and localized label lengths to a documented practical maximum before allowing untrusted packages to allocate memory.

## Staged file-level implementation

Each stage's candidate paths are limited to its actual owner and affected focused tests. A file path is a navigation hint, not an allowance to edit unrelated code. New classes/files named below are **proposed**, not claimed to exist.

### SI-S01 — schema and compatibility fixture

**Owner:** `geo-format/`; tests/docs. **Candidate files:** `geo-format/metadata.schema.json`, `geo-format/IMPLEMENTATION.md` after actual behavior changes, `tests/test_documentation_contracts.py`, optionally a focused schema-contract test using the project's existing JSON Schema validation facilities. Confirm schema-validator dependency before use; do not add one without approval.

- Define a bounded, optional v1 `interpretation` object with explicit dimension, goal, and typed rule structures and no executable rule fragments.
- Define strict uniqueness/foreign-key semantics for the Python semantic validator; JSON Schema alone does not prove IDs resolve to selected metrics.
- Fixture oracles: absence and valid v1 accepted; unsupported version, missing `ru`, nonfinite number, duplicate ID, invalid threshold, unexpected rule operation rejected. Previously valid format-v1 metadata unchanged.
- **Gate:** focused schema fixture tests, `python3 tools/specs/validate_state.py`, then `make check` if environment supports it. Developer approves wire details before S02.

### SI-S02 — builder catalog/config resolver

**Owner:** `geo-builder/`. **Candidate files:** `config/interpretation-profiles.toml`, `config/datasets.toml`, `src/osmapdigger_geo/config.py` (or a cohesive new `interpretation_config.py`), `tests/test_config.py`, new `tests/test_interpretation_config.py`.

- Parse explicit dimension and goal definitions from dataset-selected profiles, localize metadata, validate duplicate/unknown IDs and rule references against `build_metric_definitions` plus resolved defaults. Keep configuration import free of PBF reads, external commands and network.
- Explicit profile variations per dataset are required: `core10` and the Andorra `full` catalog may differ; do not silently omit referenced metrics. Include at least one intentionally unavailable metric test.
- **Gate:** validator passes positive fixture, unknown ID/reversed direction/bad bounds fail before any GIS work, config-only tests run without real PBF.

### SI-S03 — builder publication, validation, archive parity

**Owner:** `geo-builder/`. **Candidate files:** `src/osmapdigger_geo/pipeline.py`, `src/osmapdigger_geo/package.py`, `tests/test_pipeline.py`, `tests/test_package.py`.

- Resolve catalog before heavy PBF processing and write `interpretation` into metadata after existing artifact fields. Validate it before publication; include it unchanged in the existing `metadata.json` archive member rather than adding a second competing source.
- Verify staging failure leaves the previously published package usable. Both full-map and `--skip-map` packages retain the same interpretation semantics.
- **Gate:** synthetic/temp SQLite package and ZIP tests; real Andorra publication remains a separate configured acceptance, not an automatic stage check.

### SI-S04 — shared runtime metadata reader / fallback

**Owner:** `mobile/application` and platform dataset entry points. **Candidate files:** `mobile/application/src/commonMain/kotlin/com/permieware/osmapdigger/dataset/DatasetPackageContract.kt`, `mobile/application/src/commonTest/kotlin/com/permieware/osmapdigger/dataset/DatasetPackageContractTest.kt`, `mobile/desktopApp/src/jvmMain/kotlin/com/permieware/osmapdigger/desktop/runtime/DesktopDataset.kt`, `mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/runtime/AndroidDataset.kt` and focused host tests.

- Decode the existing base `DatasetPackageMetadata` independently, then parse and validate the optional `interpretation` subtree from the same JSON input so malformed optional content cannot invalidate otherwise valid baseline package metadata. Existing no-catalog payloads decode without changing `toDatasetInfo` and normal host I/O. Reject/disable malformed optional interpretation without inventing fallback dimensions; use typed stable diagnostics.
- The host must not parse a second platform-specific dialect. Cancellation propagates; invalid interpretation must not leave package/settings partially replaced.
- **Gate:** old/valid/invalid/unsupported v1 fixtures give consistent Desktop/Android interpretation availability and preserve existing dataset import/search.

### SI-S05 — immutable dimension/evidence models

**Owner:** `mobile/application`. **Candidate files:** new `analysis/InterpretationModels.kt`, `analysis/InterpretationCatalogValidator.kt` and matching `commonTest` files. Keep `mobile/core` changes limited to genuinely shared pure domain types.

- Define `DimensionDefinition`, `GoalProfile`, `DimensionResult`, `MetricEvidence`, `InterpretationInsight`, `InterpretationAvailability`; do not duplicate/replace `MetricPreference` and `SettlementScore`.
- Reject duplicate IDs and nonfinite values as input invariants; model a known raw zero separately from absent (`null`) raw value.
- **Gate:** immutable evidence identity, exact source IDs, empty/missing/boundary fixtures, and deterministic order tests.

### SI-S06 — dimension aggregation

**Owner:** `mobile/application`. **Candidate files:** new `analysis/DimensionEvaluator.kt`, `commonTest/analysis/DimensionEvaluatorTest.kt`; reuse `analysis/PreferenceScorer.kt` contribution semantics instead of copying formulas.

- Calculate `score` and `coverage` as declared in the spec, using only enabled contributions. When `knownWeight == 0`, score is null; when `totalWeight == 0`, coverage is 0 and availability is false. Apply dimension evidence minima inclusively; a known zero-quality metric is valid negative evidence.
- Test 0.8 weight 3 + 0.2 weight 1 → 65; known quality 1.0 weight 2 + missing weight 3 → score 100, coverage 40; none known → unavailable; exact threshold equality → eligible; tie → configured order then stable ID.
- **Gate:** `:application:desktopTest` focused tests, no change to baseline ranking or missing-data semantics.

### SI-S07 — goal profile application and custom identity

**Owner:** `mobile/application`. **Candidate files:** new `analysis/GoalProfileResolver.kt`, `commonTest/analysis/GoalProfileResolverTest.kt`, selected `preferences/UserPreferences.kt` helper tests.

- Resolve a complete preset against actual preference defaults, allow direction reversal only with valid endpoints, preserve explicit disabled metrics, and use exact effective-state equality for named badge vs `Custom`.
- Applying a preset is atomic; malformed references or absent profile leave prior effective settings intact. Switching dataset or editing values must not display a stale named goal.
- **Gate:** scenarios for stable unchanged rank under the same preferences and changed rank only through `PreferenceScorer` under a different visible preference configuration.

### SI-S08 — app-owned persistence and upgrade

**Owner:** `mobile/application`, `mobile/desktopApp`, `mobile/androidApp`. **Candidate files:** `preferences/UserPreferences.kt` (including `MetricPreferenceOverridePayloadCodec`), `settings/ApplicationSettingsSchema.kt` only if a migration is necessary, platform `SqliteUserPreferencesRepository.kt`, `AndroidUserPreferencesRepository.kt`, relevant tests.

- Extend payload encoding to round-trip `direction` (and goal ID in an application-owned versioned field) while accepting v1 unchanged. Decide how to store selected goal ID only after reading concrete platform DAO columns; avoid adding a column if existing JSON safely owns it.
- Old payload v1 still inherits dataset default direction and old target/limit/weight; user-specific v2 override changes direction explicitly; invalid v2 never partially restores. Work through app-settings migration only if required, preserving v7 forward migration rules.
- Test save/reopen on both hosts, mismatched dataset IDs, no available goal, removed metrics, and frozen Favorite snapshot bytes before/after change.
- **Gate:** common versioned-codec tests + focused JDBC/Android adapter tests. Developer approval is required for schema migration.

### SI-S09 — insight rule engine

**Owner:** `mobile/application`. **Candidate files:** new `analysis/InterpretationInsightEngine.kt`, `commonTest/analysis/InterpretationInsightEngineTest.kt`.

- Evaluate a finite, whitelisted typed rule set over qualified dimension results; all referenced dimensions must meet explicit coverage/count gates. Return rule key + trace and deterministic order, never freeform generated prose or a score used in ranking.
- Test high Nature + low Services with both known, low coverage suppresses, missing metric does not mean poor Services, and no claim about objective noise/safety or car dependence from proxies alone.
- **Gate:** deterministic output across shuffled candidate input order; zero network and zero new SQL calls.

### SI-S10 — localization and semantic bands

**Owner:** `mobile/presentation`. **Candidate files:** `presentation/UiLocalization.kt`, `presentation/ScoreExplanationBuilder.kt` or a focused `InterpretationPresentationBuilder.kt`, `commonTest/presentation/...`.

- Map stable typed insight rule keys to RU/EN sentences with proxy-aware wording, quality bands and explicit `Unknown`. Preserve number formatting and raw metric value provenance. Keep scoring thresholds in the interpretation catalog/application logic.
- Verify score exactly at band boundaries, unavailable states, localization-independent numeric outputs, accessible text descriptions and unsupported template handling.
- **Gate:** presentation-only deterministic common tests.

### SI-S11 — Compose goal selector and preference controls

**Owner:** `mobile/shared`. **Candidate files:** `ui/DesktopPreferencesUi.kt`, `ui/DesktopAnalysisSidebar.kt`, shared UI tests, and relevant Android/shared entry points if exposed.

- Display the active goal name and complete editable effective preferences, including direction; label `Custom` after changes; do not silently reset Required filters or candidate source.
- Test apply, edit, undo-to-exact-preset, selected dataset change, disabled metrics and persistence under rerender. Guard against accidental repeated apply from composition.
- **Gate:** shared UI adapter tests and configured Compose compilation.

### SI-S12 — ranked summary and evidence details

**Owner:** `mobile/shared` with `mobile/presentation`. **Candidate files:** `ui/RankedResultsUi.kt`, `ui/DesktopAnalysisWorkspace.kt`, relevant selected-details components and UI/presentation tests.

- Show at most a small configured number of meaningful dimension bands and concise supported insights; keep authoritative rank/score visible. Details disclose ordered dimensions and `MetricEvidence` including raw values, qualities, missing entries and score/coverage.
- Verify text and numeric labels convey strength/weak/unknown without relying only on color, long RU/EN labels do not obscure controls, no duplicated contradictory explanations, and previously selected result stays stable.
- **Gate:** shared/common presentation tests plus manual accessibility review when host is available.

### SI-S13 — workspace integration and bounded computation

**Owner:** `mobile/application`, `mobile/shared`. **Candidate files:** `workspace/AnalysisWorkspaceController.kt`, `analysis/SettlementAnalysisService.kt` only where necessary, and `commonTest/workspace/AnalysisWorkspaceControllerTest.kt`, existing shortlist acceptance fixture in `mobile/presentation/src/commonTest/kotlin/com/permieware/osmapdigger/workspace/SettlementShortlistWorkflowAcceptanceTest.kt`.

- Feed active catalog/effective Preferences into score-contribution-backed interpretation. Batch candidate metrics once; compute interpretation only for bounded displayed candidates and details; no `details()` N+1 loop or all-dataset re-interpretation on every UI interaction.
- Preserve cancellation, stale-result protection, Required/radius/import eligibility, complete candidate rank for Favorites beyond visible top-N, notebook updates and snapshot/export identity.
- **Gate:** independent regression cases from `SI-R01`, plus metrics-request-volume assertions and deterministic rebrowse/refresh behavior; no synthetic performance numbers claimed.

### SI-S14 — integration, real datasets and acceptance documentation

**Owner:** existing acceptance facilities and owning docs. **Candidate files:** `docs/TESTS.md` when commands/acceptance matrix changes, `docs/CONFIGURATION.md` for builder config, `geo-format/IMPLEMENTATION.md` for metadata, `docs/IMPLEMENTATION.md` and `mobile/IMPLEMENTATION.md` for accepted runtime wiring. Do not archive the spec at this stage without developer acceptance.

- Run focused Python builder/catalog/package tests; canonical `make check`; configured `make check-all` where available; Android APK assembly; real Andorra `make andorra-full-package-acceptance` and explicit Android/Desktop offline package import/rendering; representative Belarus profile/calibration and strict headless scoring when datasets/toolchain exist.
- Independently verify old v1 packages, profile-switch ranking parity, missing beach evidence in Andorra, locale/accessibility, state migration, invalid external metadata and native JCEF/Android behavior. Record exact fixture identity, source bytes, commands/results and missing gates.
- **Gate:** evidence is complete for the new P1 feature and reviewed by the developer; only then update ownership docs and consider spec+plan archival. P0 acceptance gaps are tracked independently and must not be silently marked accepted.

## Cross-stage negative and regression matrix

| Oracle | Required test setup | Failure that must never be hidden |
|---|---|---|
| Baseline/legacy | Two identical old datasets with no interpretation field | No UI or stored-score drift; no generated dimensions |
| Semantic validation | Unknown metric, duplicated profile metric, missing locale, negative bound, unsupported version or rule | No partial publication, no fabricated runtime output |
| Missing data | Known metric 0 plus missing metric; dimension one known vs none | Missing is unknown, not 0; weak insight requires qualified known evidence |
| Profile conflicts | Goal flips major-road direction with mismatched endpoints | Atomic rejection, prior ranking and settings intact |
| Preset identity | Edit one enabled preference then restore exact previous values | Named→Custom→named only when exact effective values match |
| User state | Old payload v1, new v2, stale goal ID, switched dataset | Correct inherited direction, compatible persisted values, no state leakage |
| Ranking invariance | Same effective Preferences with and without interpretation enabled | Identical eligible set, score/coverage/order and full candidate rank |
| Notebook/history | Favorite with frozen snapshot + export before switching goal | Frozen bytes and preexisting export unchanged until explicit refresh |
| Determinism | Reverse dimension/rule definition order with stable explicit sort keys | Canonical stable result and selection tie-breakers |
| Offline/cancellation | Kill analysis mid-flight, reopen without network | Cancellation propagated; no stale insights/partial settings mutation |
| Volume | 1000+ candidates with only top-N visible | No per-result `details()` calls and no interpretation-specific unbounded full-dataset work |
| Platform parity | Same package/settings/metric values on Desktop and Android | Same deterministic data results; only platform rendering differs |

## Handoff and next iteration discipline

- The **next two stages** after this `GENSPEC` deliverable are `SI-S01` and `SI-S02`. Treat each as a separate requested `DEFAULT` change with focused coverage and `apatch` ZIP+JSON handoff. Do not run implementation automatically from this document.
- At each stage, inspect current files/tests, produce independent expected results before coding, perform scoped changes, run real focused checks and clearly report unrun configured/native gates.
- Update the companion plan only for grounded discoveries; update the normative spec when an actual requirement, ordering, schema or acceptance rule changes. Do not silently weaken test or acceptance requirements to fit implementation.
- `docs/specs/STATE.json` is a separate contributor-tooling checkpoint (currently A1 / DESIGN in the supplied snapshot). It is **not** the source of truth for this product-spec stage, and must not be repurposed without a separately approved change.
- The developer owns baseline approval, Git and acceptance. A repository-changing `GENSPEC` and any subsequent implementation use the same two-artifact `apatch` handoff; a findings-only `REVIEW` yields no source PATCH. The branch is one `feature/*` for this spec, not one branch per stage, unless the developer explicitly chooses otherwise.
