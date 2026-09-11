---
type: Development Guide
title: OsmapDigger repository development rules
description: Repository-wide rules for architecture, implementation, documentation, testing, and artifact hygiene.
---
# OsmapDigger repository development rules

OsmapDigger is an offline-first KMP + Python GIS project. Read this file first, then read the nearest subproject `AGENTS.md` before changing `mobile/`, `geo-builder/`, or `geo-format/`.

## Source-of-truth order

For a task driven by an active specification:

1. read [`docs/specs/README.md`](docs/specs/README.md);
2. read the relevant file under `docs/specs/active/`;
3. read the owning architecture/implementation documentation;
4. inspect current code and tests before editing.

An active spec owns the intended delta while implementation is in progress. Current-state documents and code own behavior that is not being changed by that spec. After acceptance, move stable knowledge into owning docs and archive the completed spec.

## Product invariants

- OsmapDigger is offline-first.
- Runtime settlement search works from a local generated SQLite dataset.
- Runtime code must not parse OSM PBF data.
- The basemap and analytical search database are separate artifacts.
- Search/filter behavior must be deterministic and explainable.
- Filters are data/configuration-driven rather than hardcoded UI branches.
- Missing metric data is not equivalent to numeric zero.
- No LLM is required for core filtering or filter-description generation.
- External web searches are explicit user actions only.
- The runtime is country-agnostic; country names belong in dataset configuration/metadata, not control flow.

## Architecture boundaries

- `geo-builder/` owns OSM parsing, configured feature extraction, metric calculation, map generation, package validation, and package publication.
- `geo-format/` owns persisted SQLite/metadata semantics and compatibility versioning.
- `mobile/core` owns immutable country-agnostic domain models and pure geographic calculations.
- `mobile/application` owns headless search/analysis/workspace/settings/notebook/external contracts and logic; `mobile/presentation` owns platform-independent formatting/localization/explanation; `mobile/shared` owns shared Compose UI plus map/runtime composition contracts.
- `mobile/desktopApp` and `mobile/androidApp` own filesystem APIs, SQLite adapters, package import, browser opening, and platform lifecycle.
- UI must not construct SQL, parse SQLite rows, parse metadata JSON directly, or know OSM selector details.
- Python configuration must not leak directly into runtime code; generated `metric_definition` rows are the runtime metric/filter contract and additive `metric_preference_default` rows are the separate dataset scoring-default contract.
- MapLibre-specific concerns must not leak into domain/search logic.

## Persisted-format discipline

`geo-format/VERSION`, `geo-format/schema.sql`, and `geo-format/metadata.schema.json` are compatibility-sensitive.

When changing persisted semantics:

- inspect Python writers and validators;
- inspect Android and Desktop readers;
- update `geo-format/IMPLEMENTATION.md` and root `docs/IMPLEMENTATION.md`;
- increment `geo-format/VERSION` for incompatible changes;
- add or update tests that exercise writer/reader expectations.

## Data hygiene

Raw and generated geographic data is local-only. Do not commit:

- `data/source/osm/`;
- `data/generated/`;
- `.osm.pbf`;
- `.pmtiles`;
- generated `.sqlite` databases;
- `.omd.zip` packages;
- caches, virtual environments, build outputs, or local IDE state.

Shareable repository archives and source snapshots must apply the same exclusions.

## Python rules

- Importing builder modules must not read PBF data, invoke tilemaker/Docker, or write generated artifacts.
- Keep PBF access, metric calculation, persistence, map generation, package validation, and orchestration separable.
- Public functions/classes should have type hints and useful docstrings when their contract is not obvious.
- Use synthetic GeoDataFrames in default tests; real PBF tests are explicit integration checks.
- Build into staging and publish only after validation succeeds.

## Kotlin rules

- Keep reusable domain/search/UI in `commonMain` when practical.
- Keep platform APIs behind small interfaces.
- Prefer immutable domain models.
- Keep SQL out of shared UI and search orchestration.
- KDoc important public contracts and platform adapters, especially where lifecycle, read-only behavior, URI rewriting, or security assumptions matter.

## Language and documentation

All generated software code, comments, KDoc/docstrings, tests, configuration comments, `AGENTS` files, README files, and project documentation must be in English.

Use Mermaid for architectural/process/sequence diagrams. Do not add ASCII-art flow diagrams.

Documentation should be concise but sufficient for a future contributor or LLM to reconstruct ownership, call paths, invariants, and validation expectations without relying on chat history.

### Documentation ownership

Keep current-state implementation knowledge in the smallest authoritative set:

- `docs/IMPLEMENTATION.md` owns cross-project current implementation and links to subsystem guides;
- each code subproject's `IMPLEMENTATION.md` owns concrete module/platform classes, libraries, lifecycle, and call paths;
- `docs/USAGE.md` owns operational/user workflows and commands;
- `docs/CONFIGURATION.md` owns configuration fields and their runtime/build implications.

Do not create a parallel `docs/implementation/` topic-document layer by default. Split out a dedicated implementation document only when it has substantial independent ownership that does not fit the cross-project or owning-subproject guide; otherwise extend the authoritative owner and link to it.

### OKF document metadata

Project Markdown documentation uses an Open Knowledge Format (OKF)-compatible YAML frontmatter profile so document purpose and specification relationships are machine-readable without loading the whole document.

Keep the profile minimal:

- every managed Markdown document uses `type`, `title`, and `description`;
- specification workflow/relationship fields are defined in `docs/specs/README.md`;
- do not add timestamps, provenance, tags, dependency lists, or other metadata unless they have a concrete current use;
- frontmatter must summarize document identity/relationships, not duplicate substantive documentation;
- preserve valid YAML frontmatter when editing or moving documentation.

`docs/specs/README.md` owns the project-specific specification metadata convention.

## Testing

Default checks must not require network access, Docker, tilemaker, Android SDK, or a production-size PBF:

```bash
make check
```

When KMP/Android toolchains are available:

```bash
make check-all
```

Real local PBF integration is explicit:

```bash
make build-andorra-data
```

See [`docs/TESTS.md`](docs/TESTS.md).


## Documentation update policy

Do not add small fixes or local refactorings that do not change behavior or architecture to CHANGELOG or other documentation. Documentation updates are intended for meaningful feature changes, architectural decisions, user-visible behavior changes, or important implementation changes.
