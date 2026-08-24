---
type: Implementation
title: Desktop storage and settings
description: Current Desktop storage behavior and the boundary for planned user preference persistence.
---
# Desktop storage and settings

## Dataset auto import

Desktop can load a configured `.omd.zip` package automatically during startup.

The path is defined by the Desktop configuration used by the current host. Manual import remains available as a fallback.

## Current persistent storage

Installed datasets live under the Desktop application storage root and remain separate from project-generated package sources. Generated datasets are read-only runtime inputs.

The current implementation does not yet persist center settlement, radius, or dynamic filter state across application restarts.

## Planned user preferences

The active [`../specs/active/subspecs/user-preferences.md`](../specs/active/subspecs/user-preferences.md) sub-spec defines the first application-owned preference store.

Desktop will keep mutable user preferences separately from installed datasets at:

```text
~/.osmapdigger/settings/preferences.sqlite
```

The first persisted state is intentionally limited to the current dataset-scoped search context:

- dataset identifier;
- center settlement identity;
- optional radius;
- dynamic metric filter ranges.

Search history, favorites, notes, and multiple named saved searches are not part of this first persistence increment.
