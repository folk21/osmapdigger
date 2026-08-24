---
type: Implementation
title: External services integration
description: Offline-first boundary and configuration model for optional external helper services.
---
# External services integration

## Purpose

OsmapDigger uses external services only as optional helpers. The core application remains
fully offline-first and all search/filtering works without network access.

The first external integration target is property search.

## Configuration model

External providers must not be hardcoded in UI code. The application resolves providers from
configuration based on dataset country metadata.

Example:

```yaml
country: BY
propertySearch:
  providers:
    - id: kufar
      name: Kufar
      enabled: true
      urlTemplate: "https://www.google.com/search?q=site:kufar.by+{settlement}"
```

## Provider model

A provider contains:

- stable identifier;
- display name;
- URL template;
- enabled flag;
- optional priority.

The UI only displays providers returned by the configuration layer.

## Future migration

The first implementation may use local configuration files. The model must allow migration to a
database table without changing the UI layer.

Future table:

`external_services(provider_id, country_code, service_type, url_template, enabled, priority)`

## Dataset relationship

Country information belongs to dataset metadata. A dataset imported into OsmapDigger should
identify its country/region so the application can select appropriate services.
