---
type: Development Guide
title: Mobile/runtime development rules
description: Kotlin Multiplatform runtime boundaries, search invariants, platform ownership, testing, and documentation rules.
---
# Mobile/runtime development rules

Root [`../AGENTS.md`](../AGENTS.md) applies. Read [`README.md`](README.md), [`IMPLEMENTATION.md`](IMPLEMENTATION.md), and the active spec before changing runtime contracts.

## Module ownership

- `core` owns immutable domain/runtime models plus pure geographic calculations and must not depend on `shared`, Compose, or platform APIs.
- `shared` owns dataset/search/analysis orchestration, dynamic filter UI, filter descriptions, GeoJSON overlays, and shared MapLibre presentation.
- `desktopApp` owns JDBC SQLite, JVM filesystem/ZIP import, Desktop browser integration, and Desktop resource lifecycle.
- `androidApp` owns Android SQLite, Storage Access Framework import, app-private package installation, browser intents, and Activity lifecycle.
- External-search provider definitions are configuration-driven; do not hardcode provider/country branches in shared UI. The settings SQLite is the runtime source of truth after packaged seed configuration is inserted.

Common code must not import Android/JVM filesystem or database APIs.

## Logical dependency graph

[`README.md`](README.md) owns the current physical Gradle module table and logical `commonMain` package dependency DAG across `core` and `shared`. `tests/test_mobile_architecture.py` enforces both documented logical dependency direction and the physical Gradle DAG during `make check`.

When intentionally adding or changing a top-level shared package dependency, update the README graph/table and the architecture check in the same patch. Do not bypass a dependency rule by moving reusable code into a generic `util`, `common`, or `helpers` package.

## Search invariants

- UI must not construct SQL.
- `SearchCondition` uses generic metric IDs and optional min/max values.
- Missing metric rows are unknown, not zero.
- Dataset scoring defaults come from `GeoRepository.preferenceDefaults()`; UI/shared code must not read builder preference TOML or infer defaults from metric IDs.
- Shared `DatasetCandidateQueries` owns compatibility-sensitive hard-filter/ranked-candidate SQL, ordered bind semantics, and bounded imported-ID query partitioning; platform repositories own only JDBC/Android execution and row mapping for those migrated operations.
- Platform repositories perform metric/bounding-box candidate reduction by executing those shared query specifications.
- Shared `SearchService` performs exact radius filtering so Android/Desktop semantics stay aligned.
- Ranked analysis must batch-load only requested scoring metrics; never call full `details()` once per candidate.
- Imported candidate scopes are stable-ID restrictions, not synthetic metrics; repository adapters must apply them before shared scoring and must not broaden an empty imported scope to the full dataset.
- Persist reviewed imported stable IDs as analytical identity; retained source text may be stored only as editing provenance and must never replace stable settlement identity.
- Favorites/notebook membership is dataset-scoped persistent user state independent from candidate source and single map/details selection; adding/removing favorites must not trigger ranked recalculation.
- Ranked-analysis repository queries must not apply an unrelated final limit before shared scoring; exact radius, scoring, stable ranking, and final limiting belong in shared analysis orchestration.
- Search results must not depend on map rendering/style state.

## Dynamic filters

Do not add category-specific UI branches for ordinary numeric metrics. New generated metrics should flow through `MetricDefinition` and the generic min/max UI.

If a future metric genuinely requires non-numeric or specialized interaction, extend the persisted/runtime contract explicitly instead of detecting IDs by string convention in UI.

## Dataset/package handling

- Runtime opens generated SQLite/metadata/PMTiles only; it never reads PBF.
- Package import must preserve path traversal protection.
- Open generated SQLite read-only where the platform supports it.
- Local PMTiles URI rewriting belongs in platform package loading, not domain/search code.
- Platform resource owners must close/replace database handles deliberately.

## Map boundary

MapLibre types may appear in shared map/UI implementation but must not leak into domain/search models or repository SQL contracts.

Search-result GeoJSON is transient presentation data; do not persist it as an analytical source of truth.

## Testing

- `core/commonTest` for immutable domain invariants and pure geographic calculations.
- `shared/commonTest` for filter-summary/link/search/analysis/workspace behavior.
- `desktopApp/jvmTest` for JDBC SQL/readers and Desktop package behavior where practical.
- Android compilation/host tests for Android-specific contracts.
- Real package/map acceptance belongs in repository `docs/TESTS.md`.

## Documentation and KDoc

Important public contracts/platform adapters and security/lifecycle-sensitive methods require useful KDoc. Comments should explain why platform/shared boundaries exist and what assumptions callers can rely on.

## Operational errors and application settings

- Use `require`/`check` for programmer/domain invariants; do not recover them as ordinary UI failures.
- Expected storage/database/package/settings failures crossing migrated runtime boundaries use `OperationalFailure`; never render raw `Throwable.message` as the user contract.
- Always propagate coroutine cancellation. Do not use `getOrNull()`/`getOrDefault()` where absence and failure have different semantics.
- `ApplicationSettingsSchema` is the only logical owner of application settings DDL/version/migrations. Android/Desktop settings classes execute that contract rather than duplicating schema strings.
- Dataset ZIP replacement must remain staging/validation/publication based and preserve path-traversal checks.
