---
type: Development Guide
title: Mobile/runtime development rules
description: Kotlin Multiplatform runtime boundaries, search invariants, platform ownership, testing, and documentation rules.
---
# Mobile/runtime development rules

Root [`../AGENTS.md`](../AGENTS.md) applies. Read [`README.md`](README.md), [`IMPLEMENTATION.md`](IMPLEMENTATION.md), and the active spec before changing runtime contracts.

## Module ownership

- `shared` owns immutable domain models, search orchestration, dynamic filter UI, filter descriptions, GeoJSON overlays, and shared MapLibre presentation.
- `desktopApp` owns JDBC SQLite, JVM filesystem/ZIP import, Desktop browser integration, and Desktop resource lifecycle.
- `androidApp` owns Android SQLite, Storage Access Framework import, app-private package installation, browser intents, and Activity lifecycle.
- External-search provider definitions are configuration-driven; do not hardcode provider/country branches in shared UI. The settings SQLite is the runtime source of truth after packaged seed configuration is inserted.

Common code must not import Android/JVM filesystem or database APIs.

## Search invariants

- UI must not construct SQL.
- `SearchCondition` uses generic metric IDs and optional min/max values.
- Missing metric rows are unknown, not zero.
- Platform repositories perform metric/bounding-box candidate reduction.
- Shared `SearchService` performs exact radius filtering so Android/Desktop semantics stay aligned.
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

- `shared/commonTest` for pure geography/filter-summary/link/search behavior.
- `desktopApp/jvmTest` for JDBC SQL/readers and Desktop package behavior where practical.
- Android compilation/host tests for Android-specific contracts.
- Real package/map acceptance belongs in repository `docs/TESTS.md`.

## Documentation and KDoc

Important public contracts/platform adapters and security/lifecycle-sensitive methods require useful KDoc. Comments should explain why platform/shared boundaries exist and what assumptions callers can rely on.
