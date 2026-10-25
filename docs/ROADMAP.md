---
type: Roadmap
title: Roadmap
description: Feature-oriented product roadmap, acceptance gaps, and next bounded increments.
---
# Roadmap

This roadmap is the changing status/navigation view of OsmapDigger capabilities. Stable capability identity belongs in [`FEATURES.md`](FEATURES.md); normative change requirements belong in [`specs/`](specs/README.md); accepted implementation detail belongs in the owning architecture/implementation documents.

Do not add acceptance state to `FEATURES.md`. Feature IDs remain stable while roadmap/spec status changes over time.

## Current position

The initial functional product is implementation-heavy and acceptance-oriented rather than greenfield. The KMP architecture-hardening track is accepted and archived: `PLATFORM.MODULAR_KMP` now enforces the `:core` → `:application` → `:presentation` → `:shared` direction with platform hosts above shared contracts.

The current **P0 acceptance** focus remains [`specs/active/subspecs/settlement-shortlist-workflow.md`](specs/active/subspecs/settlement-shortlist-workflow.md). Its implementation, automated shared/headless acceptance, and representative Belarus/Andorra calibration evidence are complete; native/manual Desktop/Android evidence remains before archival.

The new **P1 development focus** is the staged [`ANALYSIS.INTERPRETATION` sub-spec](specs/active/subspecs/settlement-interpretation-and-goal-profiles.md) and its [advisory plan](specs/active/subspecs/settlement-interpretation-and-goal-profiles-implementation-plan.md). This explicit shift does not accept, cancel, or enlarge any P0 verification obligation.

`desktop-analysis-workspace`, `desktop-map`, and `user-preferences` are also verification-pending. They remain separate because changing the current focus does not implicitly accept their configured/manual evidence. The representative Andorra data-only and strict full-package build/publication evidence is now complete; native Desktop/Android consumption remains tracked by the platform rows below.

Status vocabulary used below:

- **accepted** — required evidence has been recorded and no active spec remains for that slice;
- **verification-pending** — implementation exists, but required configured/manual/representative evidence is still missing;
- **planned** — a likely next capability direction without an active implementation commitment;
- **deferred** — intentionally outside the current roadmap horizon.

## Contributor tooling — parked, outside the current product plan

The existing [development-agent-pipeline design](specs/active/subspecs/development-agent-pipeline.md) and the independent contributor-tooling `STATE.json` checkpoint are historical/planning context only for the present product work. **No optional contributor-tooling implementation or staged automation is part of this P1 plan.** This parked workstream does not affect P0 acceptance or the newly selected P1 product `current_focus`.

## P0 — initial functional product acceptance

Active umbrella: [`specs/active/spec-initial-functional-product.md`](specs/active/spec-initial-functional-product.md).

### Acceptance map

| Capability | Feature IDs | State | Remaining acceptance gap | Next evidence |
|---|---|---|---|---|
| Dataset build, format, metrics, and package publication | `DATASET.SCOPE`, `DATASET.BUILD`, `DATASET.PACKAGE`, `FORMAT.GEODATA`, `METRICS.GENERATION`, `METRICS.RUNTIME_CONTRACT` | accepted | None for builder/package publication structure; current Andorra data-only and strict full-package evidence is recorded | Keep the structural acceptance target green; native Desktop/Android package consumption is tracked separately below |
| Search, preferences, ranking, and analysis workspace | `SEARCH.FILTERING`, `SEARCH.DESCRIPTION`, `SEARCH.RADIUS`, `SEARCH.SETTLEMENTS`, `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `ANALYSIS.EXPLANATION`, `PRESENTATION.ANALYSIS_WORKSPACE` | verification-pending | Strict country-scale scored acceptance plus remaining native Desktop workflow checks | Run strict analysis acceptance on a representative scoring-enabled package, complete native Desktop checks, and archive `desktop-analysis-workspace` after evidence is recorded |
| Shortlist, Favorites, export, and external discovery | `CANDIDATES.IMPORT`, `FAVORITES.NOTEBOOK`, `FAVORITES.SNAPSHOTS`, `FAVORITES.EXPORT`, `EXTERNAL.PROPERTY_SEARCH` | verification-pending | Native/manual Desktop/Android workflow evidence; representative Belarus/Andorra calibration and configured automated gates are complete | Complete the native checklist, move stable findings to owning docs, then archive the sub-spec |
| Offline Desktop map and result interaction | `MAP.OFFLINE`, `MAP.RESULTS`, `PLATFORM.DESKTOP` | verification-pending | Intel macOS JCEF startup crash regression plus offline full-package rendering, marker/focus behavior, data-only fallback, and renderer/fallback evidence | Verify repeated pre-AWT JCEF startup on the affected Intel host, then run native Desktop map smoke and archive `desktop-map` after evidence is recorded |
| Mutable user state | `SETTINGS.PERSISTENCE` | verification-pending | Android restore/isolation behavior including unavailable center and removed metrics | Run Android persistence acceptance and archive `user-preferences` |
| Android package/runtime path | `DATASET.IMPORT`, `PLATFORM.ANDROID`, `PLATFORM.KMP`, `RUNTIME.OFFLINE_FIRST` | verification-pending | Configured `androidApp` compilation is covered by `make check-all`; runtime import/share/persistence plus local filtering/map behavior on a device/emulator remain unverified | Import the representative package on Android and record local search/map/share/persistence evidence |
| Deterministic repository validation | `TESTING.DETERMINISTIC_LOCAL` | accepted | No P0 gap in the default network-free or configured repository gate; real-package/native evidence still remains above | Keep `make check`, focused acceptance targets, and `make check-all` green while closing the remaining rows |
| Physical KMP architecture | `PLATFORM.MODULAR_KMP` | accepted | None for the archived hardening scope | Create a new bounded spec only when a concrete structural requirement justifies more architecture work |

### P0 closure order

1. Finish `settlement-shortlist-workflow` calibration/native acceptance and archive it.
2. Record strict country-scale analysis evidence and close `desktop-analysis-workspace`.
3. Close `desktop-map` and `user-preferences` native/manual acceptance.
4. Exercise the accepted representative Andorra full package on Desktop and Android and close the remaining package-import/offline-runtime evidence.
5. Move any newly discovered stable behavior into owning docs and archive `spec-initial-functional-product` only when every blocking row above is accepted.

Do not start another broad architecture track while these acceptance gaps are open unless a concrete correctness issue blocks them.

## P1 — goal-oriented analysis interpretation

Capability anchors: `ANALYSIS.INTERPRETATION`, `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `ANALYSIS.EXPLANATION`, `PRESENTATION.ANALYSIS_WORKSPACE`.

Active staged sub-spec: [`specs/active/subspecs/settlement-interpretation-and-goal-profiles.md`](specs/active/subspecs/settlement-interpretation-and-goal-profiles.md) with [advisory implementation plan](specs/active/subspecs/settlement-interpretation-and-goal-profiles-implementation-plan.md).

The selected P1 improvement makes existing metrics easier to understand rather than primarily adding more raw metrics. Its ordered implementation stages are `SI-S01` through `SI-S14`; no stage is yet accepted. Intended outcomes:

- aggregate effective metric qualities into a small set of human-readable dimensions such as nature, everyday services, transport accessibility, seclusion, and environmental comfort;
- provide explicit goal profiles such as balanced living, nature and quiet, family convenience, and remote retreat;
- reuse the existing transparent Preference engine when a goal profile affects ranking instead of introducing a second hidden overall score;
- generate deterministic strengths, trade-offs, and practical conclusions that are traceable back to the exact dimensions and metric evidence that produced them;
- use compact color/band semantics in ranked-result summaries while keeping text/labels authoritative so color is never the only carrier of meaning;
- keep missing-data coverage visible and suppress conclusions whose evidence is insufficient;
- progressively disclose `summary → dimension → contributing metrics` instead of putting every raw metric into the result list.

This work is P1 and does not block the current P0 acceptance closure. The first implementation should remain deterministic and explainable; ML-assisted similarity, personalization, or anomaly detection should be considered only after this evidence model exists and through separate bounded specifications.

## P1 — dataset experience

Capability anchors: `DATASET.IMPORT`, `DATASET.PACKAGE`, `DATASET.USER_BUILD`, `PLATFORM.DESKTOP`, `PLATFORM.ANDROID`.

Planned directions:

- installed-dataset manager instead of one current dataset;
- package metadata/details and explicit compatibility/error UX;
- Desktop workflow for selecting a local PBF/config and invoking the Python builder with progress/logging;
- optional smaller-region package creation from a large local source.

Create new Feature IDs only when a bounded spec makes a durable capability distinct from the existing anchors above.

## P1 — map quality

Capability anchors: `MAP.OFFLINE`, `MAP.RESULTS`, `PRESENTATION.LOCALIZATION`.

Planned directions:

- richer vector style;
- offline labels/glyph strategy;
- icons/sprites where needed;
- better layer visibility by zoom;
- richer selected/result marker interactions and map-driven analysis affordances.

## P1 — user workflow

Capability anchors: `ANALYSIS.PREFERENCES`, `ANALYSIS.RANKING`, `FAVORITES.NOTEBOOK`, `SETTINGS.PERSISTENCE`.

Planned directions after the current shortlist slice is accepted:

- named saved filters/searches;
- richer comparison of multiple settlements;
- reusable named preference profiles.

These concepts do not receive new Feature IDs until a concrete spec establishes stable ownership and semantics.

## P2 — additional data sources

Capability anchors: `METRICS.GENERATION`, `METRICS.RUNTIME_CONTRACT`, `FORMAT.GEODATA`.

Potential sources:

- elevation and slope;
- flood/hydrology risk;
- satellite-derived land indicators;
- climate and soil;
- pollution/environmental sources;
- public cadastral/property context where legally and technically available.

Additional sources should enter through builder adapters and versioned metrics rather than bypassing the current SQLite/filter contract.

## P2 — learned decision support

Capability anchors: `ANALYSIS.INTERPRETATION`, `ANALYSIS.RANKING`, `METRICS.RUNTIME_CONTRACT`.

Potential directions after deterministic interpretation is accepted and enough representative data exists:

- settlement similarity and archetype discovery from derived metric vectors;
- personalized preference learning from explicit Favorites or pairwise choices;
- OSM completeness/anomaly detection that distinguishes likely missing map data from genuinely absent infrastructure.

Classical interpretable models should be preferred before custom neural networks for tabular metric data. Any learned result must be labeled as inferred, keep provenance/confidence separate from measured facts, and remain explainable enough to trace user-visible recommendations back to input evidence.

## Deferred

Deferred until a concrete product requirement justifies a bounded spec:

- cloud account/sync;
- Natural-language query parsing;
- embedded listing aggregation/scraping;
- Android-side Pyrosm/tilemaker processing.

