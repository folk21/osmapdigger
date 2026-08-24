---
type: Installation Guide
title: Installation
description: Developer-machine setup for builder, Desktop, Android, and map tooling.
---
# Installation

## Scope

This document describes developer-machine setup. Normal OsmapDigger runtime use eventually needs only an installed dataset package; Python/Pyrosm/tilemaker are developer or dataset-builder dependencies.

## Python Geo Builder

Python 3.11+ is recommended.

From the repository root:

```bash
python -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -e './geo-builder[dev]'
```

Verify imports and network-free tests:

```bash
make check
```

Pyrosm/GeoPandas depend on native geospatial components. If a platform-specific pip installation fails, use a clean Conda/Miniforge environment with packages from `conda-forge` rather than mixing system GIS libraries with unrelated environments.

## Local OSM source files

Place source files under the ignored directory:

```text
data/source/osm/
```

The checked-in configuration currently expects the local Andorra test fixture at:

```text
data/source/osm/andorra-260821.osm.pbf
```

The Belarus config expects a future local file:

```text
data/source/osm/belarus-latest.osm.pbf
```

Do not force-add either file to Git.

## PMTiles generation

Full packages require tilemaker. The builder supports two execution modes.

### Direct tilemaker

Install `tilemaker` and verify:

```bash
tilemaker --help
```

Then a normal build auto-detects it.

### Docker fallback

If `tilemaker` is not installed but Docker is available, `--map-backend auto` may use the configured tilemaker container path.

Verify:

```bash
docker version
```

### Data-only development

PMTiles is optional for analytical builder tests:

```bash
make build-andorra-data
```

This is useful while debugging extraction/SQLite independently from map tooling.

## JDK and Desktop

Use JDK 17+; the generated project currently targets JVM 17 bytecode.

Verify:

```bash
java -version
```

Desktop MapLibre Compose requires the native runtime supported by the current dependency version and host OS/architecture. The Desktop Gradle build selects the MapLibre native binding capability from host OS/architecture.

Run:

```bash
make run-desktop
```

If a package already exists, set:

```bash
export OSMAPDIGGER_DATASET_DIR="$PWD/data/generated/packages/andorra"
```

## Android

Install Android Studio or an Android SDK matching the versions in `mobile/gradle/libs.versions.toml`.

Typical verification:

```bash
cd mobile
./gradlew :androidApp:assembleDebug
```

Android does not need Python or tilemaker. It imports already generated `.omd.zip` packages.

## Gradle network note

A first Gradle build normally needs access to Gradle/plugin/dependency repositories. Once required distributions/artifacts are cached, routine local work can be more offline-friendly, but the repository does not vendor those dependencies.

## Optional source snapshot helper

`concat_osmapdigger.sh` expects an external `concat_files_to_txt.py` helper at `~/work/python/concat_files_to_txt.py` by default, mirroring the Sibyl workflow.

Override it with:

```bash
export OSMAPDIGGER_CONCAT_TOOL=/path/to/concat_files_to_txt.py
```

## Desktop map compatibility

The Desktop application can run on Intel macOS, but MapLibre Compose 0.13.x has no native `macos-amd64` JNI runtime. On Intel macOS the application therefore starts with the map panel disabled while SQLite search, filters, result lists, settlement details, and external links remain functional. Android and supported Desktop hosts use the native MapLibre map.

No local installation can add the missing Intel macOS MapLibre artifact; this is an upstream platform-support limitation rather than a JDK or Gradle configuration issue.

