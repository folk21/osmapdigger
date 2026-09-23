---
type: Development Guide
title: OsmapDigger repository development rules
description: Repository-wide rules for architecture, implementation, documentation, testing, and artifact hygiene.
---
# OsmapDigger repository development rules

This file defines repository-wide development rules for coding agents working on OsmapDigger.

Follow it strictly. Read the nearest local `AGENTS.md` before changing `mobile/`, `geo-builder/`, or `geo-format/`. Local instructions may add stricter rules for their scope, but must not weaken repository-wide invariants.

Prefer the smallest correct change that satisfies the task.

## Project intent

OsmapDigger is an offline-first Kotlin Multiplatform application plus a Python geospatial build pipeline for local settlement and geographic analysis.

The stable architectural rule is:

> Heavy OSM/GIS preparation happens at build time; Desktop and Android consume explicit generated contracts and perform deterministic local analysis without requiring a backend.

The runtime is country-agnostic. Dataset configuration may describe Belarus, Andorra, a region, or another supported extract, but country-specific behavior must not leak into generic runtime control flow.

## Source of truth

Use this ownership order when sources disagree:

1. production code, machine-readable persisted contracts, and schema/migration definitions define implemented behavior;
2. tests define behavior that has been verified;
3. active specifications define intended changes that are still in progress or awaiting acceptance;
4. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) defines stable system boundaries and dependency direction;
5. [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md) and subproject `IMPLEMENTATION.md` files explain current wiring, adapters, and implementation details;
6. other owning documents define configuration, installation, usage, testing, roadmap, and feature vocabulary;
7. archived specifications are historical context only.

For spec-driven work, read [`docs/specs/README.md`](docs/specs/README.md), the relevant active specification, owning current-state documentation, tests, and implementation before editing.

An active spec owns the intended delta, not already accepted current-state behavior. When documentation conflicts with implemented behavior, verify the behavior, fix the owning document, and avoid duplicating the same rule in multiple places.

## Architecture boundaries

- `geo-builder/` owns OSM parsing, configured feature extraction, metric calculation, map generation, package validation, and package publication.
- `geo-format/` owns persisted SQLite/metadata semantics and compatibility versioning.
- `mobile/core` owns immutable country-agnostic domain models and pure geographic calculations.
- `mobile/application` owns headless dataset/search/analysis/preferences/workspace/settings/notebook/external contracts and logic.
- `mobile/presentation` owns platform-independent localization, formatting, summaries, and score explanations.
- `mobile/shared` owns shared Compose UI plus renderer-neutral map/runtime composition contracts.
- `mobile/desktopApp` and `mobile/androidApp` own platform filesystem/database APIs, package installation, browser integration, native renderers, and lifecycle.

Dependency direction must remain acyclic. Lower modules must not reach upward for convenience.

UI must not own SQL, persisted-row parsing, metadata JSON parsing, OSM selector semantics, geospatial algorithms, or scoring formulas.

Python configuration must not leak directly into runtime code. Generated `metric_definition` rows are the runtime metric/filter contract; additive `metric_preference_default` rows are the separate dataset scoring-default contract.

MapLibre-specific concerns must not leak into domain/search logic.

## Code structure and design

- Keep architecture flexible but simple. Do not add abstractions, layers, interfaces, or indirection without a concrete boundary or variation.
- Apply DRY thoughtfully. Remove stable duplication that creates maintenance risk, but prefer limited duplication over premature generalization.
- Prefer composition over inheritance.
- Keep public APIs small and explicit.
- Keep mutable state and resource ownership obvious.
- Prefer immutable models at domain and cross-layer boundaries.
- Keep classes/functions reasonably sized and cohesive; do not split code mechanically to satisfy arbitrary size rules.
- Preserve unrelated behavior and avoid drive-by refactors.
- Do not introduce hidden conventions that cannot be discovered from the repository.
- Configuration is preferred over hardcoded environment-, provider-, country-, or dataset-specific behavior.

## Persisted contracts and user-owned state

`geo-format/VERSION`, `geo-format/schema.sql`, and `geo-format/metadata.schema.json` are compatibility-sensitive contracts between independent Python writers and Kotlin readers.

When changing persisted dataset semantics:

- inspect Python writers and validators;
- inspect Android and Desktop readers;
- update `geo-format/IMPLEMENTATION.md` and root `docs/IMPLEMENTATION.md` when current behavior changes materially;
- increment `geo-format/VERSION` for incompatible changes;
- add or update writer/reader tests.

Mutable application settings and Favorites belong to application-owned storage, not generated datasets. Keep historical migration semantics stable and use explicit forward migrations for later schema changes.

Stable metric IDs, settlement IDs, and published feature IDs are compatibility/navigation contracts. Do not reuse an existing ID for materially different semantics.

## Python rules

- Importing builder modules must not read PBF data, access the public network, invoke tilemaker/Docker, or write generated artifacts.
- Keep PBF access, metric calculation, persistence, map generation, package validation, and orchestration separable.
- Use explicit pipeline stages and deterministic processing.
- Public functions/classes should have type hints and useful docstrings when their contract is not obvious.
- Use synthetic GeoDataFrames and temporary directories in default tests; real PBF/tool integration is explicit.
- Build into staging, validate, then publish atomically.

## Kotlin rules

- Use idiomatic Kotlin and explicit nullability/state contracts.
- Keep platform APIs behind small interfaces or adapters.
- Keep core/application logic free of Android/JVM UI/filesystem/database APIs.
- Keep SQL out of shared UI and high-level search orchestration.
- Always propagate coroutine cancellation; do not accidentally translate cancellation into an ordinary operational failure.
- Use `require`/`check` for programmer/domain invariants and typed operational failures for expected runtime/storage/package failures crossing user-facing boundaries.
- KDoc important public contracts and platform adapters, especially where lifecycle, read-only behavior, URI rewriting, compatibility, or security assumptions matter.
- Do not expose raw `Throwable.message` as a stable user-facing contract.

## Configuration and external I/O

Do not hardcode secrets, developer-machine paths, external URLs, provider choices, country-specific rules, or behavior that belongs in configuration.

External web searches are explicit user actions only. Core filtering/ranking must remain local and deterministic.

Dataset/package operations must preserve path-traversal protection, explicit resource ownership, validation-before-publication, and failure-safe replacement semantics.

## Dependencies

Keep the dependency surface small.

Before adding a library, check whether Kotlin/JDK/Python standard facilities or an existing dependency already solve the problem clearly.

Do not add or upgrade dependencies/plugins, introduce a new persistence technology, or add a framework-wide abstraction for one local problem unless the task explicitly requires it or the user approves it.

## Testing rules

Every behavioral or contract change requires appropriate tests.

Use the smallest useful layer first:

- Python unit/synthetic GIS tests for builder logic;
- `core/commonTest` for domain invariants and pure calculations;
- `application/commonTest` for headless search/analysis/workspace/settings/notebook behavior;
- `presentation/commonTest` for deterministic formatting/localization/explanation;
- `shared/commonTest` for shared UI/map/runtime integration contracts;
- Desktop/Android platform tests for platform adapters and persistence;
- focused headless/acceptance tests for cross-feature workflows;
- configured/manual acceptance only where real PBF, MapLibre/native UI, browser, or platform dialogs are essential.

Default tests must not depend on:

- public network access;
- live external websites/APIs;
- production credentials;
- large downloaded datasets;
- nondeterministic timing when a controllable source can be used.

Feature-level tests may reference stable IDs from [`docs/FEATURES.md`](docs/FEATURES.md) when that improves traceability. Do not invent ad-hoc feature codes in tests.

Never disable or weaken a failing test merely to make the build green. Never claim a build/test passed unless it was actually run successfully.

`make check` is the canonical network-free repository gate. `make check-all` is the broader configured KMP/Android gate. Focused commands and the current validation matrix belong in [`docs/TESTS.md`](docs/TESTS.md).

When adding a new repository-wide verification step, wire it into the appropriate canonical workflow and update `docs/TESTS.md` when the command matrix changes.

## Documentation ownership

Each topic should have one authoritative owner:

| Topic | Owner |
|---|---|
| Repository overview and quick start | `README.md` |
| Agent development rules | `AGENTS.md` + nearest local `AGENTS.md` |
| Stable architecture and dependency direction | `docs/ARCHITECTURE.md` |
| Current cross-project implementation/wiring | `docs/IMPLEMENTATION.md` |
| Dataset/build/runtime configuration | `docs/CONFIGURATION.md` |
| Installation/toolchain setup | `docs/INSTALLATION.md` |
| User/operator workflows and commands | `docs/USAGE.md` |
| Test strategy and validation commands | `docs/TESTS.md` |
| Future work | `docs/ROADMAP.md` |
| Stable capability vocabulary | `docs/FEATURES.md` |
| Completed notable changes | `CHANGELOG.md` |
| Specification workflow/navigation | `docs/specs/README.md` |
| Intended significant changes | active specifications |
| Persisted SQLite/metadata contract | `geo-format/` |
| Subproject implementation details | owning subproject `IMPLEMENTATION.md` |

Do not create a second document that restates an existing owner's content. Link to the owning document instead.

Specifications describe intended changes, not permanent current-state architecture. After acceptance, move stable knowledge into the owning documentation and archive the completed specification.

Keep specification lifecycle state unambiguous:

- a spec exists in exactly one lifecycle location (`active/` or `archive/`), never both;
- archival is a move, not a duplicated copy;
- umbrella `current_focus`, `docs/specs/README.md`, and the active tree must agree;
- an implemented-but-unverified slice may remain active with `verification-pending`;
- accepted behavior belongs in current-state documentation rather than being maintained twice in an active spec.

Keep specs synchronized with [`docs/FEATURES.md`](docs/FEATURES.md):

- identify existing feature IDs in each active spec's `Feature scope`;
- add a genuinely new durable capability to the catalog before using its ID elsewhere;
- do not create a feature ID merely because a new spec, patch, refactor, or implementation stage exists;
- never rename/remove a published feature ID in one document only.

Do not update broad documentation or CHANGELOG for trivial local fixes that do not change behavior, contracts, architecture, validation workflow, or contributor-facing knowledge.

### Documentation writing style

Repository documentation is working context for both developers and coding agents. Optimize it for precise interpretation and efficient retrieval.

- Use clear, direct, modern English.
- Preserve technical precision; simplify language, not contracts or invariants.
- Use stable terminology for the same concept across code, docs, specs, and tests.
- Prefer short paragraphs with one primary assertion.
- State ownership, dependency direction, ordering, failure semantics, compatibility, and extension points explicitly when they affect implementation.
- Use normative words consistently in specifications: `must`, `must not`, `should`, and `may`.
- Separate processing sequences from logical rules and failure semantics.
- Clearly distinguish implemented behavior from planned work, technical debt, and future extensions.
- Prefer concrete references to modules, schemas, configuration keys, and persistence owners over abstract descriptions.
- Use Mermaid for architectural/process/sequence diagrams; avoid ASCII-art flow diagrams.
- All code, comments, KDoc/docstrings, tests, configuration comments, `AGENTS` files, README files, and project documentation are in English.

### Document metadata

Managed Markdown uses a minimal OKF-compatible YAML frontmatter profile:

- `type`;
- `title`;
- `description`.

Specification relationship/lifecycle metadata is owned by [`docs/specs/README.md`](docs/specs/README.md). Do not add timestamps, tags, provenance, dependency lists, or other metadata without a concrete current use.

## Data and artifact hygiene

Raw/generated geographic data and local runtime artifacts are local-only. Do not commit or include in shareable source archives:

- `data/source/osm/`;
- `data/generated/`;
- `.osm.pbf`;
- `.pmtiles`;
- generated `.sqlite` databases;
- `.omd.zip` dataset packages;
- caches, virtual environments, build outputs, secrets, local `.env` values, IDE metadata, or temporary files.

FULL archives preserve a clean `osmapdigger/` repository root. PATCH archives contain only added/modified files and explicitly list deleted paths.

## Always / Ask first / Never

### Always

- Read this file and the nearest applicable local `AGENTS.md`.
- Inspect existing implementation, contracts, tests, and active specification before introducing a new pattern.
- Identify the owning layer/module/document before editing.
- Stay inside the requested scope unless the task requires a coherent cross-boundary change.
- Preserve public contracts and data ownership.
- Make the smallest coherent change.
- Update tests for behavioral/contract changes.
- Run and report relevant validation.
- Report changed files, deleted files, and anything not verified.

### Ask first, unless the task explicitly authorizes it

- Add or upgrade dependencies/plugins.
- Introduce a new Gradle module or change module dependency direction/ownership.
- Make an incompatible `geo-format` or dataset-package contract change.
- Change stable metric IDs, feature IDs, or externally meaningful persisted semantics.
- Introduce a backend/network requirement for core runtime behavior.
- Introduce a new persistence technology or cross-cutting infrastructure.
- Perform a broad refactor affecting unrelated areas.

### Never

- Create circular module dependencies.
- Put SQL, PBF parsing, or domain algorithms into UI code.
- Parse OSM PBF in normal Desktop/Android runtime behavior.
- Treat missing metric data as numeric zero unless an explicit contract says so.
- Reach around an owning module's public contract for convenience.
- Hardcode developer-machine paths, secrets, country/provider behavior, or generated dataset locations.
- Commit generated geographic data or build/cache artifacts.
- Disable tests to hide regressions.
- Invent requirements, APIs, migrations, benchmark numbers, dataset evidence, or test results.
- Perform unrelated cleanup while completing a focused task.

## Working style

1. Identify the owning module/document and applicable local instructions.
2. Inspect implementation, persisted contracts, tests, active spec, and owning docs.
3. State the intended scope when the task is substantial.
4. Reuse existing patterns when they are sound; do not clone a bad pattern merely for consistency.
5. Implement the smallest correct change.
6. Run focused validation, then broader checks appropriate to the changed boundary.
7. Update only owning documentation and affected feature/spec references.
8. Report changes, validation, remaining uncertainty, deleted files, and a concise commit message.

## Progressive disclosure

Read detailed guidance only when relevant:

- [`docs/FEATURES.md`](docs/FEATURES.md) — stable feature vocabulary;
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — stable system/module boundaries;
- [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md) — current cross-project implementation and wiring;
- [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) — dataset/metric/provider configuration;
- [`docs/TESTS.md`](docs/TESTS.md) — test layers and validation commands;
- [`docs/specs/README.md`](docs/specs/README.md) — specification lifecycle and ID conventions;
- [`mobile/AGENTS.md`](mobile/AGENTS.md) — KMP/runtime rules;
- [`geo-builder/AGENTS.md`](geo-builder/AGENTS.md) — geospatial builder rules;
- [`geo-format/AGENTS.md`](geo-format/AGENTS.md) — persisted compatibility rules.

When in doubt, preserve existing contracts and module boundaries, choose the smaller change, and do not invent missing requirements.
