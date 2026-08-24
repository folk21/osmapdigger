# Desktop UI implementation

## Goal

Desktop is a development and power-user interface for validating the offline geo pipeline.
It should expose search capabilities clearly before mobile UX optimization.

## Search workflow

The main workflow:

1. Import a dataset.
2. Configure optional search area.
3. Configure filters.
4. Execute search.
5. Inspect settlement details.

## Search area

Center settlement and radius are optional constraints.

Without a center:

- search runs over the full dataset.

With a center:

- only settlements inside the radius are considered.

## Filter UI

Filters should be represented by generic conditions:

```
Metric + minimum + maximum
```

Example:

```
Forest distance: 0 .. 5 km
Water distance: 0 .. 3 km
Railway distance: 0 .. 20 km
```

The UI should generate a readable description:

"Find villages where forest distance <= 5 km and water distance <= 3 km."

## Settlement card

The settlement details view should show:

- name;
- type;
- coordinates;
- calculated distances;
- nearby infrastructure;
- environmental indicators;
- external property search actions.

## Current limitations

The Desktop map renderer intentionally uses a fallback implementation on Intel macOS.
Search and analytical features are independent from map rendering.
