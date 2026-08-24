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
3. Add a dataset entry in `geo-builder/config/datasets.toml`.
4. Choose a practical initial map center/zoom.
5. Run the normal build command with the new dataset ID.

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

## Desktop search workflow

1. Open a dataset.
2. Optionally search for a center settlement and select it.
3. Enter a radius if the center should constrain the search.
4. Fill any visible **From** / **To** values.
5. Add additional filters from the generated metric catalog when needed.
6. Review the human-readable filter description.
7. Run **Search settlements**.
8. Select a result to inspect all available metrics.
9. Use the Google/Yandex property-search actions when relevant.
10. Inspect the map to see the result overlay. Native-supported Desktop hosts use MapLibre Compose; Intel macOS uses the local JCEF web renderer; other unsupported hosts keep the analytical fallback.

An empty visible filter row has no effect until at least one bound is supplied.

The current dataset-scoped center, radius, and dynamic filter rows/ranges are saved locally and restored on the next launch when the same dataset is opened. State from a different dataset is not applied.

## Android package workflow

1. Build a full `.omd.zip` on a workstation.
2. Transfer it to the Android device using any normal file mechanism.
3. Start OsmapDigger.
4. Choose **Import dataset**.
5. Select the ZIP through Android Storage Access Framework.
6. The package is installed to app-private storage and opened locally.

Importing another package replaces the current app-private package in the current initial implementation.

## Property web search

A selected settlement exposes Google and Yandex actions. The URL is generated from:

- settlement display name;
- optional dataset-configured site restriction;
- dataset-configured property terms.

For a Belarus dataset this can produce a query equivalent to:

```text
site:kufar.by "Settlement name" дом недвижимость
```

The app does not scrape the result page.

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
