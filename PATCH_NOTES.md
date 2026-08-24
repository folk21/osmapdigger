---
type: Patch Notes
title: Desktop config and search persistence patch notes
description: Historical notes for the Desktop configuration and persistence-related patch.
---
# Desktop config and search persistence patch

Implemented direction:

- add desktop local configuration for automatic dataset import;
- define persistent desktop storage location;
- define persistence model for search filters and search area.

Important:
The previous decision to move center settlement and radius below filters was intentional for
filter-first workflow, but for the current Desktop analytical workflow it is recommended to move
Search Area above Filters because it is often the first constraint users understand.
