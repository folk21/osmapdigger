---
type: Implementation
title: Desktop storage and settings
description: Current Desktop dataset storage and application-owned user preference persistence.
---
# Desktop storage and settings

## Dataset auto import

Desktop can load a configured `.omd.zip` package automatically during startup.

The path is defined by the Desktop configuration used by the current host. Manual import remains available as a fallback.

## Persistent storage boundaries

Installed datasets are immutable runtime inputs stored separately from mutable user state.

Desktop uses:

```text
~/.osmapdigger/
    datasets/
    settings/
        preferences.sqlite
```

Generated project packages under `data/generated/` remain installation sources and are not used for mutable application state.

## User preferences database

`SqliteUserPreferencesRepository` owns the Desktop settings database. It stores one current dataset-scoped search context containing:

- dataset identifier;
- center settlement stable ID and optional display name;
- optional radius in kilometers;
- a versioned JSON payload of dynamic metric ID/min/max conditions.

The settings database has its own SQLite `PRAGMA user_version`. It is application-owned and is not part of `geo-format`; changing its schema does not change `geo-format/VERSION`.

Preference I/O runs outside the UI dispatcher. Missing state loads as empty, while malformed or unsupported filter payloads are treated as unavailable saved state so normal dataset defaults can be used.

## Restore behavior

Shared runtime state is restored only after the opened dataset metadata and metric definitions are available.

Saved state is applied only when its `dataset_id` matches the opened dataset. Unknown metric IDs are ignored. A saved center is resolved only by stable settlement ID; if that settlement no longer exists, the center and radius constraint are cleared rather than reconstructed from the display name.

Search history, favorites, notes, and multiple named saved searches are not part of this persistence increment.
