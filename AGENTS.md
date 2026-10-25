---
type: Development Guide
title: OsmapDigger repository development rules
description: Repository-wide implementation rules and verification boundaries.
---
# OsmapDigger development rules

Read this file and the nearest local `AGENTS.md` before editing. Read the owning code, tests, contracts, and relevant active specification. Ordinary implementation is a direct, developer-controlled workflow: make a bounded change, run checks, and provide reviewable source changes. The existence of an optional contributor-tooling specification or `STATE.json` checkpoint never activates execution.

## Source of truth

- Code and persisted schemas define **implemented** behavior; tests demonstrate only behavior actually verified.
- Active specs define **intended** changes and pending acceptance. Planned specs are not authorization; archived specs are historical.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) owns stable boundaries; [`docs/IMPLEMENTATION.md`](docs/IMPLEMENTATION.md) and local `IMPLEMENTATION.md` files own current wiring.
- [`docs/specs/README.md`](docs/specs/README.md) owns spec lifecycle, [`docs/FEATURES.md`](docs/FEATURES.md) stable capability IDs, and [`docs/TESTS.md`](docs/TESTS.md) validation commands.
- When docs disagree with actual code/contracts, verify and correct the owning document; do not create duplicate authorities or invent source state.

## Architecture

Build-time Python (`geo-builder/`) transforms local PBF and configuration into validated, versioned SQLite/metadata/PMTiles packages. Desktop/Android consume those packages offline. Normal runtime MUST NOT parse PBF, perform heavy GIS builds, or require a backend. Dataset logic is country-agnostic.

- `geo-format/` owns SQLite/metadata compatibility between Python writers and Kotlin readers.
- `mobile/core` owns immutable domain models and pure geographic calculations.
- `mobile/application` owns headless search, ranking, workspace, settings, Favorites, and integration contracts.
- `mobile/presentation` owns deterministic localization, formatting and explanations.
- `mobile/shared` owns Compose UI and renderer-neutral map integration.
- `mobile/desktopApp` / `mobile/androidApp` own platform files, SQLite adapters, package installation, native rendering, browser actions and lifecycle.

Dependencies go downward (`core` → `application` → `presentation` → `shared`, with platform hosts above them); never introduce a cycle or reach into a higher layer. UI MUST NOT own SQL, PBF/category selectors, metrics algorithms, scoring formulas or persisted-format parsing. Map renderer details MUST NOT determine analytical search results. Consult [`mobile/AGENTS.md`](mobile/AGENTS.md), [`geo-builder/AGENTS.md`](geo-builder/AGENTS.md) and [`geo-format/AGENTS.md`](geo-format/AGENTS.md) for owner-specific invariants.

## Code changes

- Change the **smallest coherent owner scope**. Preserve unrelated behavior; no opportunistic refactors, speculative frameworks or ceremonial layers.
- Add an abstraction or public interface only for a concrete caller, boundary, lifecycle or tested variation. Keep public APIs narrow; prefer composition, explicit ownership and immutable boundary values.
- Do not introduce or change module dependencies, public cross-module contracts or ownership without a requirement and corresponding owning-contract update.
- Configuration, not hardcoded country, dataset, provider or machine-specific branches, controls variability. Do not embed secrets, private paths, remote endpoints or generated asset locations in code.
- Make state, threading/cancellation, resource cleanup and failure semantics explicit. Kotlin coroutine cancellation MUST propagate. Separate programmer invariants (`require`/`check`) from typed expected operational failures; do not expose raw platform exceptions as user-facing contracts.
- For Python, keep imports side-effect-free (no PBF reads, network, tilemaker/Docker or output writes), make stages independently testable and publish artifacts only after staged validation.
- For KMP, keep Android/JVM-only APIs in platform hosts; do not duplicate common SQL or settings migrations across adapters.
- Document non-obvious public API invariants and important lifecycle/security decisions with concise English KDoc/docstrings. Do not add comments that restate code, and keep existing comments correct.

## Data and compatibility

`geo-format/VERSION`, `geo-format/schema.sql` and `geo-format/metadata.schema.json` are compatibility-sensitive. Before changing them, inspect writer, validator and **both** platform readers. Add writer/reader regression coverage and update the owning contract. Incompatible persisted semantics require versioning/migration; never silently reuse stable metric, settlement or feature IDs.

- Missing `settlement_metric` values are unknown, **not numeric zero**.
- Generated `metric_definition` provides runtime filter semantics; `metric_preference_default` independently provides scoring defaults. Runtime must not read Python TOML or infer metric behavior from ID strings.
- Generated dataset SQLite stays read-only. Mutable user settings/Favorites are application-owned, dataset-scoped, versioned and migration-safe.
- Package import/publishing must validate paths, reject traversal, manage resources explicitly and preserve existing valid output on failure.

## Tests and evidence

- For behavior changes, write or update observable positive, negative, boundary and regression cases derived from the requirement or reproduced defect **independently of implementation**. Prefer owner-level unit/synthetic tests and temporary data; add integration/platform checks for affected boundaries.
- Default tests MUST NOT need public network, production credentials, large PBF datasets, tilemaker/Docker, external services or nondeterministic sleeps. Use controllable clocks/fakes where practical.
- Diagnose test failures as product defects, invalid expectations or environment errors. Do not skip, weaken, mock away or rewrite a valid assertion to obtain PASS; preserve original regression coverage when correcting an invalid test.
- Run focused checks, then canonical `make check`; run applicable `make check-all`, real PBF/package, native Desktop/Android or PMTiles gates according to [`docs/TESTS.md`](docs/TESTS.md). Report exact results and unavailable gates, never invent PASS.
- Install a project-local `.venv` before Python checks when needed (`python3 -m venv .venv`; `. .venv/bin/activate`; `python -m pip install -e './geo-builder[dev]'` for a new environment or missing test dependencies). Do not alter global Python settings.

## Documentation and specification lifecycle

Only update the **owning** document when behavior, architecture, contracts, configuration, commands or acceptance evidence change. Do not add a changelog entry or broad documentation rewrite for a trivial local fix. English is required for repository documentation, code/test comments and configuration comments.

Use existing Feature IDs from [`docs/FEATURES.md`](docs/FEATURES.md) where they improve traceability; do not invent IDs per patch or refactor. An active spec records an intended delta and required evidence, not a second implementation history. Move accepted knowledge into current-state docs and archive a completed spec **by move**, not copy. `verification-pending` remains active until its acceptance evidence is complete.

## Optional contributor tooling

The [contributor-stage tooling design](docs/specs/active/subspecs/development-agent-pipeline.md) is **not an installed executor**. `docs/specs/STATE.json`, its schema and validator form a read-only-validated, developer-maintained **between-run checkpoint**; they are not locks, permissions, runtime status or acceptance records. **Ordinary product work must not launch the optional stage runner, mutate `STATE.json`, or invoke promotion rules.** Any future automated execution requires separately approved tooling, source identity, risk policy, checks and developer authorization. No automated check or review grants developer acceptance. This repository does not prescribe how or with which tools contributors author changes.

## Approval and handoff

**Always:** inspect affected owners/contracts/tests; make the narrowest correct change; preserve compatibility and user data; run real checks; disclose missing evidence, changed/deleted files and remaining risks.

**Ask before:** dependency/plugin upgrades, new modules or cross-module ownership, incompatible persisted-format changes, new storage/backend/network requirements, stable-ID semantic changes or broad refactors not expressly requested.

**Never:** bypass module APIs, mix SQL/PBF processing into UI, silently treat missing data as zero, commit generated PBF/SQLite/PMTiles/ZIP data or secrets, weaken tests, fabricate Git/test/evidence state, or modify unrelated code merely for cleanup.
