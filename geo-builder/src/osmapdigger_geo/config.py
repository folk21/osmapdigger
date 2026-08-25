"""TOML configuration loading for datasets and dynamic OSM metric categories."""

from __future__ import annotations

import tomllib
from pathlib import Path

from .models import CategoryDefinition, DatasetDefinition, Selector


def _resolve(base: Path, value: str) -> Path:
    """Resolve config paths relative to the TOML file instead of process CWD."""
    path = Path(value)
    return path if path.is_absolute() else (base / path).resolve()


def load_dataset_definition(config_path: Path, dataset_id: str) -> DatasetDefinition:
    """Load and resolve one configured dataset into a build-ready immutable record.

    Raising for unknown IDs or malformed required fields is intentional: the pipeline
    should fail before expensive PBF processing rather than silently invent defaults.
    """
    config_path = config_path.resolve()
    payload = tomllib.loads(config_path.read_text(encoding="utf-8"))
    builder = payload["builder"]
    raw = payload.get("datasets", {}).get(dataset_id)
    if raw is None:
        raise KeyError(f"Unknown dataset '{dataset_id}'")

    base = config_path.parent
    source_root = _resolve(base, builder["source_root"])
    output_root = _resolve(base, builder["output_root"])
    boundary = raw.get("boundary_geojson")

    return DatasetDefinition(
        id=dataset_id,
        display_name=raw["display_name"],
        country_code=raw.get("country_code"),
        source_pbf=(source_root / raw["source_pbf"]).resolve(),
        output_dir=(output_root / dataset_id).resolve(),
        boundary_geojson=_resolve(base, boundary) if boundary else None,
        initial_center_latitude=float(raw["initial_center_latitude"]),
        initial_center_longitude=float(raw["initial_center_longitude"]),
        initial_zoom=float(raw["initial_zoom"]),
        context_km=float(raw.get("context_km", builder.get("context_km", 25.0))),
        format_schema=_resolve(base, builder["format_schema"]),
        format_version_file=_resolve(base, builder["format_version_file"]),
        metric_profiles_file=(
            _resolve(base, builder["metric_profiles"]) if builder.get("metric_profiles") else None
        ),
        metric_profile=str(raw.get("metric_profile", "full")),
        property_search_site=raw.get("property_search_site"),
        property_search_terms=raw.get("property_search_terms", "property"),
    )


def list_dataset_ids(config_path: Path) -> list[str]:
    """Return configured dataset IDs in deterministic order for CLI discovery."""
    payload = tomllib.loads(config_path.read_text(encoding="utf-8"))
    return sorted(payload.get("datasets", {}).keys())


def load_categories(config_path: Path) -> tuple[list[str], list[CategoryDefinition]]:
    """Load settlement place values and configuration-driven feature categories.

    The returned selector structure preserves OR-of-AND semantics so the broad Pyrosm
    batch filter can remain an optimization while local filtering remains authoritative.
    """
    payload = tomllib.loads(config_path.read_text(encoding="utf-8"))
    places = list(payload.get("settings", {}).get("settlement_places", []))
    categories: list[CategoryDefinition] = []

    for raw in payload.get("categories", []):
        alternatives = tuple(
            tuple(
                Selector(
                    tag=item["tag"],
                    values=tuple(str(value) for value in item.get("values", ["*"])),
                )
                for item in alternative
            )
            for alternative in raw["selectors"]
        )
        categories.append(
            CategoryDefinition(
                id=raw["id"],
                title=raw["title"],
                group=raw["group"],
                selectors=alternatives,
                default_filter=bool(raw.get("default_filter", False)),
                distance=bool(raw.get("distance", True)),
                coverage_radii_km=tuple(float(v) for v in raw.get("coverage_radii_km", [])),
                count_radii_km=tuple(float(v) for v in raw.get("count_radii_km", [])),
                preferred_direction=raw.get("preferred_direction", "lower"),
                road_batch=bool(raw.get("road_batch", False)),
            )
        )

    return places, categories


def load_metric_profile(
    config_path: Path | None,
    profile_name: str,
) -> tuple[str, ...] | None:
    """Load one named metric profile.

    ``None`` means the profile selects the complete configured category catalog.
    Explicit profiles contain stable category IDs and are validated before expensive
    PBF processing starts.
    """
    if config_path is None:
        if profile_name != "full":
            raise ValueError(
                f"Metric profile '{profile_name}' requires builder.metric_profiles configuration"
            )
        return None

    payload = tomllib.loads(config_path.read_text(encoding="utf-8"))
    raw = payload.get("profiles", {}).get(profile_name)
    if raw is None:
        raise KeyError(f"Unknown metric profile '{profile_name}'")

    category_ids = tuple(str(value) for value in raw.get("categories", []))
    if category_ids == ("*",):
        return None
    if not category_ids:
        raise ValueError(f"Metric profile '{profile_name}' has no categories")
    if "*" in category_ids:
        raise ValueError(
            f"Metric profile '{profile_name}' must use '*' alone or explicit category IDs"
        )
    if len(set(category_ids)) != len(category_ids):
        raise ValueError(f"Metric profile '{profile_name}' contains duplicate category IDs")
    return category_ids


def select_categories(
    categories: list[CategoryDefinition],
    category_ids: tuple[str, ...] | None,
    profile_name: str,
) -> list[CategoryDefinition]:
    """Select configured categories for one dataset while preserving catalog order."""
    if category_ids is None:
        return categories

    requested = set(category_ids)
    known = {category.id for category in categories}
    unknown = sorted(requested - known)
    if unknown:
        raise ValueError(
            f"Metric profile '{profile_name}' references unknown categories: "
            + ", ".join(unknown)
        )

    return [category for category in categories if category.id in requested]
