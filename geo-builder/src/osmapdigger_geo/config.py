"""TOML configuration loading for datasets and dynamic OSM metric categories."""

from __future__ import annotations

import math
import tomllib
from pathlib import Path

from .models import (
    CategoryDefinition,
    DatasetDefinition,
    MetricDefinition,
    MetricPreferenceDefault,
    Selector,
)


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

    dataset = DatasetDefinition(
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
        preference_profiles_file=(
            _resolve(base, builder["preference_profiles"])
            if builder.get("preference_profiles")
            else None
        ),
        preference_profile=(
            str(raw["preference_profile"]) if raw.get("preference_profile") is not None else None
        ),
        property_search_site=raw.get("property_search_site"),
        property_search_terms=raw.get("property_search_terms", "property"),
        settlement_name_tags=tuple(
            str(value)
            for value in raw.get(
                "settlement_name_tags",
                builder.get(
                    "settlement_name_tags",
                    ["name", "name:en", "official_name", "alt_name"],
                ),
            )
        ),
        settlement_deduplication_tolerance_m=float(
            raw.get(
                "settlement_deduplication_tolerance_m",
                builder.get("settlement_deduplication_tolerance_m", 0.0),
            )
        ),
        settlement_name_deduplication_distance_m=float(
            raw.get(
                "settlement_name_deduplication_distance_m",
                builder.get("settlement_name_deduplication_distance_m", 0.0),
            )
        ),
    )
    if dataset.settlement_deduplication_tolerance_m < 0:
        raise ValueError("settlement_deduplication_tolerance_m must be >= 0")
    if dataset.settlement_name_deduplication_distance_m < 0:
        raise ValueError("settlement_name_deduplication_distance_m must be >= 0")
    return dataset


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


def load_preference_profile(
    config_path: Path | None,
    profile_name: str | None,
    metric_definitions: list[MetricDefinition],
) -> list[MetricPreferenceDefault]:
    """Resolve one dataset preference profile against the generated metric catalog.

    Preference profiles are intentionally independent from metric-generation profiles
    and hard-filter ``default_enabled`` metadata. The selected metric catalog is passed
    explicitly so an invalid preference reference fails before PBF processing/output.
    """
    if profile_name is None:
        return []
    if config_path is None:
        raise ValueError(
            f"Preference profile '{profile_name}' requires "
            "builder.preference_profiles configuration"
        )

    payload = tomllib.loads(config_path.read_text(encoding="utf-8"))
    raw_profile = payload.get("profiles", {}).get(profile_name)
    if raw_profile is None:
        raise KeyError(f"Unknown preference profile '{profile_name}'")

    raw_preferences = raw_profile.get("preferences", [])
    if not isinstance(raw_preferences, list):
        raise ValueError(f"Preference profile '{profile_name}' preferences must be an array")

    definitions_by_id = {definition.metric_id: definition for definition in metric_definitions}
    seen_metric_ids: set[str] = set()
    result: list[MetricPreferenceDefault] = []

    for raw in raw_preferences:
        if not isinstance(raw, dict):
            raise ValueError(
                f"Preference profile '{profile_name}' entries must be tables"
            )
        metric_id = str(raw.get("metric_id", "")).strip()
        if not metric_id:
            raise ValueError(f"Preference profile '{profile_name}' contains a blank metric_id")
        if metric_id in seen_metric_ids:
            raise ValueError(
                f"Preference profile '{profile_name}' contains duplicate metric '{metric_id}'"
            )
        seen_metric_ids.add(metric_id)

        definition = definitions_by_id.get(metric_id)
        if definition is None:
            raise ValueError(
                f"Preference profile '{profile_name}' references metric '{metric_id}' "
                "that is not generated by the selected metric profile"
            )

        configured_direction = raw.get("direction")
        direction = (
            str(configured_direction).lower()
            if configured_direction is not None
            else definition.preferred_direction.lower()
        )
        if direction not in {"lower", "higher"}:
            raise ValueError(
                f"Preference metric '{metric_id}' requires explicit direction='lower' or 'higher'"
            )

        try:
            target_value = float(raw["target"])
            limit_value = float(raw["limit"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError(
                f"Preference metric '{metric_id}' requires numeric target and limit"
            ) from exc
        if not math.isfinite(target_value) or not math.isfinite(limit_value):
            raise ValueError(f"Preference metric '{metric_id}' target and limit must be finite")

        raw_weight = raw.get("weight")
        if isinstance(raw_weight, bool) or not isinstance(raw_weight, int):
            raise ValueError(
                f"Preference metric '{metric_id}' weight must be an integer from 1 to 10"
            )
        if raw_weight not in range(1, 11):
            raise ValueError(f"Preference metric '{metric_id}' weight must be in 1..10")

        if direction == "lower" and not target_value < limit_value:
            raise ValueError(
                f"Lower-is-better preference '{metric_id}' requires target < limit"
            )
        if direction == "higher" and not target_value > limit_value:
            raise ValueError(
                f"Higher-is-better preference '{metric_id}' requires target > limit"
            )

        enabled = raw.get("enabled", False)
        if not isinstance(enabled, bool):
            raise ValueError(f"Preference metric '{metric_id}' enabled must be boolean")

        result.append(
            MetricPreferenceDefault(
                metric_id=metric_id,
                direction=direction,
                target_value=target_value,
                limit_value=limit_value,
                weight=raw_weight,
                default_enabled=enabled,
            )
        )

    return result
