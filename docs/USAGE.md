---
type: Usage Guide
title: Usage
description: Operational commands and runtime workflows for building, validating, and using datasets.
---
# Usage

## Command ownership

This document is an operational reference. Architecture belongs in [`ARCHITECTURE.md`](ARCHITECTURE.md); concrete module behavior belongs in [`IMPLEMENTATION.md`](IMPLEMENTATION.md) and subproject implementation guides.

## List configured datasets

From the repository root:

```bash
PYTHONPATH=geo-builder/src \
python -m osmapdigger_geo.cli list-datasets \
  --datasets geo-builder/config/datasets.toml
```

The initial config includes `andorra` and `belarus`. A configured dataset is a build/package scope; it is not required to be a whole country.

## Build analytical data only

Use this first when validating Pyrosm extraction and SQLite generation:

```bash
make build-andorra-data
```

Equivalent explicit command:

```bash
PYTHONPATH=geo-builder/src \
python -m osmapdigger_geo.cli build andorra \
  --datasets geo-builder/config/datasets.toml \
  --metrics geo-builder/config/metrics.toml \
  --skip-map
```

Expected published directory:

```text
data/generated/packages/andorra/
```

A data-only package contains `georisk.sqlite`, `style.template.json`, `metadata.json`, and the portable `.omd.zip`; the map artifact is absent and runtime map UI reports that the package has no map.

## Build a complete package

With tilemaker or Docker available:

```bash
make build-andorra
```

Or explicitly choose the backend:

```bash
PYTHONPATH=geo-builder/src \
python -m osmapdigger_geo.cli build andorra \
  --datasets geo-builder/config/datasets.toml \
  --metrics geo-builder/config/metrics.toml \
  --map-backend direct
```

Supported values:

- `auto` — use local tilemaker first, Docker second;
- `direct` — require local `tilemaker`;
- `docker` — require Docker;
- `--skip-map` — do not run either backend.

## Diagnose settlement canonicalization

When a generated dataset still contains apparently duplicated nearby settlements, rerun
the analytical build with bounded raw-pair diagnostics:

```bash
PYTHONPATH=geo-builder/src \
python -m osmapdigger_geo.cli build belarus \
  --datasets geo-builder/config/datasets.toml \
  --metrics geo-builder/config/metrics.toml \
  --skip-map \
  --settlement-diagnostics \
  --settlement-diagnostics-limit 30
```

Diagnostics report unresolved geographically nearby raw OSM candidates and then audit the
final canonical settlements. `FINAL DUPLICATE SUSPECT` entries include canonical IDs, raw-member
provenance, shared normalized aliases, distance, and direct comparison/merge history. This makes it
possible to distinguish missed candidate pairing from a rejected merge without changing merge rules
or package contents.

## Validate a generated package

```bash
PYTHONPATH=geo-builder/src \
python -m osmapdigger_geo.cli validate \
  data/generated/packages/andorra
```

Validation checks required files and SQLite integrity/counts. It is also executed before the staging package is published by the normal build pipeline.

## Add another whole-country or regional PBF

1. Download or prepare a `.osm.pbf` locally.
2. Place it under `data/source/osm/`.
4. Add a dataset entry in `geo-builder/config/datasets.toml`.
5. Choose a practical initial map center/zoom.
6. Run the normal build command with the new dataset ID.

For a very large country, prefer an externally supplied regional PBF rather than configuring an impractically large whole-country runtime package.

## Build an extract from a larger local PBF

A dataset entry may declare `boundary_geojson`. In that case:

- Pyrosm analytical reads are constrained to a context buffer around the boundary;
- settlements are restricted to the requested scope;
- map generation crops a PBF for the boundary before invoking tilemaker.

This mode is useful when one large local source exists but the installable package should remain small.

## Run Desktop with an unpacked package

```bash
export OSMAPDIGGER_DATASET_DIR="$PWD/data/generated/packages/andorra"
make run-desktop
```

Desktop opens the configured directory at startup.

Without the environment variable:

```bash
make run-desktop
```

Use **Import dataset** / **Change** to choose either:

- an unpacked generated package directory; or
- a `.omd.zip` archive.

Imported ZIPs are installed under `~/.osmapdigger/datasets/`.

## Desktop analysis workflow

1. Open a dataset.
2. Choose the candidate source. **Full dataset** keeps normal dataset-wide analysis. **Import settlement list** opens a left-pane review workflow where you can paste one settlement name per line or load a UTF-8 `.txt` file. Reopening **Edit list** restores the previous user-entered text. If a center and positive radius are active, review alternatives are limited to settlements inside that radius. Unique exact aliases are accepted automatically; ambiguous exact names can select one, several, or **Select all**, while non-exact suggestions always require explicit selection. **Disable import** returns analysis to the full dataset without deleting the saved list; **Enable import** reuses its reviewed stable IDs; **Delete saved list** removes the retained import entirely.
3. Optionally select a center settlement and radius in **Search area**.
4. Use **Required** for hard eligibility constraints. Controls are labeled by persisted metric semantics: distance metrics use **Min/Max distance** with their distance unit, count metrics use **Min/Max number** for the number of mapped features inside the metric's fixed radius, and coverage metrics use **Min/Max coverage** with `%`. The **Add filter** chooser explains the measurement type so a nearest-feature distance is not confused with a fixed-radius feature count.
5. Use **Preferences** to enable ranking criteria, expand one row at a time, and adjust target/limit/weight when needed.
6. Results recalculate automatically after valid analytical changes and appear in ranked order with score and data coverage; the Desktop analysis workflow does not require pressing Search after each change.
7. Radius is optional: blank or `0` means no radius limit, and **Clear** removes the current radius value.
8. When no settlement is selected, the compact pane below the map shows the current number of ranked settlements and whether recalculation is still running.
9. Scan ranked cards using the numeric score and the matching 0–100 horizontal score bar. Incomplete data coverage remains textual, and compact strongest/weakest cues summarize the existing deterministic contribution model. Use the independent **Favorites** checkbox to save promising settlements without changing map/details focus or candidate-source filtering. **Open favorites** shows the persistent dataset-scoped notebook. Cards include compact current context plus the frozen analysis saved when the Favorite was added; notes are visually separated from saved analytical criteria. Checkbox multi-selection is independent from map/details selection: click a Favorite's analytical content to make it the current highlighted settlement and focus the map while keeping Favorites open; clicking the already current Favorite again re-focuses it after manual map panning. The same repeat-focus behavior applies to ranked result cards. Use **Open** to return to the normal details workflow. Current filter/weight changes do not rewrite saved score/coverage/criteria/preferences; Favorites that remain eligible show their current rank/score even when they fall below the visible result limit, and **Update snapshot** can explicitly capture that complete current ranking state. Available entries can be selected individually or with **Select all**, removed, or cleared. **Copy to import list** replaces the retained import with the selected stable IDs, activates it, closes Favorites, and returns to ranked results without resolving names again. **External search** builds one or more bounded queries for the same selected Favorites using the configured providers. Batch-capable providers use quoted settlement names joined with `OR`; when a query would become too long it is split deterministically. OsmapDigger shows every generated provider/chunk action first and opens only the action you explicitly click. **Export** writes the entire notebook, including historical Favorites unavailable in the current rebuilt dataset, as `osmapdigger-favorites.zip` containing `favorites.json` and `favorites.md`. Desktop asks where to save the archive and then reveals its location.
10. Select a ranked result to highlight/focus it on the map and replace that status pane with a compact settlement summary. The summary includes score/coverage plus every active search metric, prioritizing effective Required constraints and then enabled Preferences; scroll the compact criteria strip horizontally when many criteria are active.
11. Use **Details** to open complete deterministic score explanation and grouped raw metrics in a scrollable view in the same lower pane.
12. Use the separate **External search** action to open configured Google/Yandex/other provider buttons when relevant.

The analysis sidebar is resizable on wide Desktop windows and defaults to roughly one third of the window. **Add filter** opens a chooser that fills the left analysis pane only; it never covers the map. With no selected settlement, only a shallow search-status pane is reserved below the map. When a settlement is selected, the right side is split vertically so the map remains above and settlement information appears below it. Closing the settlement returns to the shallow result-count pane. An empty visible Required range has no filtering effect. Dataset-scoped center, radius, hard constraints, and sparse preference overrides are saved locally and restored for the same dataset.

Native-supported Desktop hosts use MapLibre Compose; Intel macOS uses the local JCEF web renderer; other unsupported hosts keep the analytical fallback. Supported map renderers show a metric scale in the bottom-right corner. Ranked settlement markers show their settlement names when space/zoom permits, with the selected settlement label kept visually prominent. Clicking a ranked settlement marker selects the same result shown in the left list and opens its lower summary. **Pick center on map** is different: click anywhere on the map and OsmapDigger selects the geographically nearest settlement from the complete dataset as the search center. The chosen settlement name appears in Search area, and existing radius, persistence, and automatic recalculation semantics are reused.

## Review Preference calibration

The checked-in `balanced-living` defaults are fixed product heuristics. Before changing their target, limit, or weight values, inspect a representative generated runtime database:

```bash
make preference-calibration
```

The default target is `data/generated/packages/belarus`. Override it for another generated package:

```bash
make preference-calibration \
  PREFERENCE_CALIBRATION_DATASET=data/generated/packages/andorra
```

The command reports actual generated metric distributions, endpoint saturation, enabled-weight grouping, and overall score/coverage percentiles. Treat the report as calibration evidence only: OsmapDigger does not derive hidden percentile-based thresholds at runtime. If defaults are changed after review, rebuild the affected analytical packages before evaluating the application again.

## Record ranked-analysis acceptance performance

After opening a realistic country-scale package on Desktop, exercise representative Required/Preferences/radius changes and let the latest recalculation complete. Then run:

```bash
make analysis-acceptance
```

If the installed dataset has no enabled Preferences, the command reports `Mode: filter-only`; this is useful throughput evidence but not final ranked-scoring acceptance. After rebuilding/loading preference defaults and running a scored analysis, use:

```bash
make analysis-acceptance-strict
```

The command reads the persistent Desktop diagnostics log and prints the latest qualifying ranked-analysis sample with candidate/scoring volumes and retrieval/shared/end-to-end latency. By default a sample must contain at least 1000 repository candidates and at least one enabled scoring metric. The command fails instead of silently accepting a smaller run.

For a deliberately different representative threshold or copied diagnostics log:

```bash
make analysis-acceptance \
  ANALYSIS_LOG=/path/to/desktop.log \
  ANALYSIS_MIN_CANDIDATES=5000
```

This report is acceptance evidence only; it does not affect search, scoring, ranking, or persisted user state.

## Android package workflow

1. Build a full `.omd.zip` on a workstation.
2. Transfer it to the Android device using any normal file mechanism.
3. Start OsmapDigger.
4. Choose **Import dataset**.
5. Select the ZIP through Android Storage Access Framework.
6. The package is installed to app-private storage and opened locally.

Importing another package replaces the current app-private package in the current initial implementation.

## Property web search

A selected settlement and the Favorites batch-search surface expose enabled application-configured providers. Open **External search settings** to edit the additional query terms for each provider directly.

Each text field contains the effective extra terms that will be appended for that provider. Clear a field completely when no extra terms are wanted. **Set defaults** fills all visible provider fields with the opened dataset's `property_search_terms`; the values are still editable independently before saving.

The site restriction remains owned by the provider URL template. For example, clearing the Kufar field can produce a query equivalent to:

```text
site:re.kufar.by "Заболотье"
```

Legacy provider rows with no explicit stored value continue to resolve to dataset terms until the settings are saved. Existing customized provider rows are not overwritten by packaged seed updates. Batch search applies the same provider-specific terms before deterministic URL chunking. The app does not scrape the result page.

## Troubleshooting

### Builder says PBF is missing

Check `source_root` and `source_pbf` in `datasets.toml`, and remember that paths are resolved relative to the config location.

### No settlements found

Inspect whether the PBF contains named `place=*` nodes/ways/relations for the configured `settlement_places`. If a boundary is configured, verify its CRS and intersection with the PBF.

### PMTiles build fails

Retry with `--skip-map` to isolate the analytical pipeline. Then verify tilemaker/Docker separately.

### Desktop starts without a map

A data-only package is valid but has no PMTiles artifact. Rebuild without `--skip-map`.

### A requested filter excludes all settlements

A settlement without that metric value does not satisfy the filter. Check the selected settlement details or source-data coverage; missing is intentionally not treated as zero.

## UI language

The application starts in Russian. Use the language control in the application header to switch between `Русский` and `English` for the current application session. The selected language survives ordinary Compose state recreation, but it is not yet stored in the application settings database across a complete application restart.

The language switch changes OsmapDigger-owned controls, labels, status messages, deterministic filter summaries, and map fallback text. Names and descriptions stored inside a dataset are shown in the form supplied by that dataset.


### Selected settlement presentation

The ranked result currently selected from either the list or the map is highlighted in the results list. The lower Desktop summary places active criteria in two balanced rows. With Russian UI, current built-in metric categories use Russian presentation labels; unknown future categories retain their dataset-provided titles. Settlement names use a matching persisted language alias when available (`ru`/`en`) and otherwise fall back to the canonical dataset name.
