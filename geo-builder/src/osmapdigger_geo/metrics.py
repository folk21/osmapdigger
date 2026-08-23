"""Settlement extraction and configuration-driven spatial metric calculation."""

from __future__ import annotations

import json
from typing import Any

import geopandas as gpd
import pandas as pd

from .metric_catalog import radius_token
from .models import CategoryDefinition, SettlementRecord


NAME_KEYS = ["name", "name:ru", "name:be", "name:en", "official_name"]


def _is_missing(value: Any) -> bool:
    """Handle pandas missing-value semantics without treating arrays as booleans."""
    if value is None:
        return True
    try:
        result = pd.isna(value)
        return result if isinstance(result, bool) else False
    except (TypeError, ValueError):
        return False


def _tags(row: pd.Series) -> dict[str, Any]:
    """Decode Pyrosm's optional catch-all tags value into a dictionary."""
    value = row.get("tags")
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}
    return {}


def extract_tag(row: pd.Series, key: str) -> Any:
    """Read a tag from a dedicated column first, then fall back to catch-all tags."""
    value = row.get(key)
    return _tags(row).get(key) if _is_missing(value) else value


def best_name(row: pd.Series) -> str | None:
    """Return the first usable configured OSM name variant for display/persistence."""
    for key in NAME_KEYS:
        value = extract_tag(row, key)
        if not _is_missing(value) and str(value).strip():
            return str(value).strip()
    return None


def tag_series(frame: gpd.GeoDataFrame, key: str) -> pd.Series:
    """Return one logical OSM tag as a Series even when Pyrosm stored it in `tags`."""
    if key in frame.columns:
        direct = frame[key].copy()
        if "tags" not in frame.columns or direct.notna().all():
            return direct
        missing = direct.isna()
        if missing.any():
            direct.loc[missing] = frame.loc[missing].apply(
                lambda row: extract_tag(row, key),
                axis=1,
            )
        return direct
    return frame.apply(lambda row: extract_tag(row, key), axis=1)


def filter_category(
    frame: gpd.GeoDataFrame,
    category: CategoryDefinition,
) -> gpd.GeoDataFrame:
    """Apply authoritative OR-of-AND selector semantics to the broad source batch."""
    if frame.empty:
        return frame

    final_mask = pd.Series(False, index=frame.index)
    for alternative in category.selectors:
        alternative_mask = pd.Series(True, index=frame.index)
        for selector in alternative:
            values = tag_series(frame, selector.tag)
            alternative_mask &= (
                values.notna()
                if "*" in selector.values
                else values.astype("object").isin(selector.values)
            )
        final_mask |= alternative_mask

    return frame[final_mask].copy()


def _text(value: Any) -> str | None:
    """Normalize optional OSM scalar text into a trimmed string or None."""
    if _is_missing(value):
        return None
    text = str(value).strip()
    return text or None


def extract_settlements(
    frame: gpd.GeoDataFrame,
    settlement_places: list[str],
    scope_geometry=None,
) -> tuple[gpd.GeoDataFrame, list[SettlementRecord]]:
    """Extract named settlements and derive one stable runtime point per place.

    Non-point OSM place features use ``representative_point`` so the runtime coordinate
    lies inside the mapped geometry. ``_sid`` is a temporary sequential build index used
    only to connect calculated metric dictionaries to persisted settlement records.
    """
    places = tag_series(frame, "place")
    settlements = frame[places.isin(settlement_places)].copy()

    if scope_geometry is not None:
        settlements = settlements[settlements.geometry.intersects(scope_geometry)].copy()

    if settlements.empty:
        return settlements, []

    settlements["source_geometry_type"] = settlements.geometry.geom_type
    settlements["geometry"] = settlements.geometry.apply(
        lambda geometry: (
            geometry if geometry.geom_type == "Point" else geometry.representative_point()
        )
    )
    settlements["_display_name"] = settlements.apply(best_name, axis=1)
    settlements = (
        settlements[settlements["_display_name"].notna()]
        .copy()
        .reset_index(drop=True)
    )
    settlements["_sid"] = range(len(settlements))

    records: list[SettlementRecord] = []
    for _, row in settlements.iterrows():
        osm_id = row.get("id") if not _is_missing(row.get("id")) else row.get("osm_id")
        osm_id = int(osm_id) if not _is_missing(osm_id) else None

        osm_type = (
            row.get("osm_type")
            if not _is_missing(row.get("osm_type"))
            else row.get("type")
        )
        osm_type = str(osm_type) if not _is_missing(osm_type) else None

        population = extract_tag(row, "population")
        try:
            population = (
                int(str(population).replace(",", ""))
                if not _is_missing(population)
                else None
            )
        except ValueError:
            population = None

        stable_id = (
            f"{osm_type or 'osm'}:{osm_id}"
            if osm_id is not None
            else f"generated:{int(row['_sid'])}"
        )
        local_name = (
            _text(extract_tag(row, "name"))
            or _text(extract_tag(row, "name:be"))
            or _text(extract_tag(row, "name:ru"))
        )

        records.append(
            SettlementRecord(
                settlement_id=stable_id,
                osm_id=osm_id,
                osm_type=osm_type,
                name=str(row["_display_name"]),
                name_local=local_name,
                name_en=_text(extract_tag(row, "name:en")),
                place_type=_text(extract_tag(row, "place")),
                population=population,
                latitude=float(row.geometry.y),
                longitude=float(row.geometry.x),
            )
        )

    return settlements, records


def calculate_metrics(
    settlements: gpd.GeoDataFrame,
    general_features: gpd.GeoDataFrame,
    road_features: gpd.GeoDataFrame,
    categories: list[CategoryDefinition],
    metric_crs,
) -> dict[int, dict[str, float]]:
    """Calculate every configured category measure for every extracted settlement.

    All spatial work occurs in one projected metric CRS. Missing category data leaves
    the corresponding metric absent except configured counts, where a known empty search
    region produces an explicit zero count.
    """
    result = {int(sid): {} for sid in settlements["_sid"]}
    settlement_metric = settlements[["_sid", "geometry"]].to_crs(metric_crs)

    for category in categories:
        source = road_features if category.road_batch else general_features
        selected = filter_category(source, category)
        print(f"Category {category.id}: {len(selected)} features", flush=True)
        if selected.empty:
            continue

        features_metric = selected.to_crs(metric_crs)
        if category.distance:
            _nearest(result, settlement_metric, features_metric, category)
        for radius in category.count_radii_km:
            _count(result, settlement_metric, features_metric, category, radius)
        for radius in category.coverage_radii_km:
            _coverage(result, settlement_metric, features_metric, category, radius)

    return result


def _nearest(result, settlements, features, category) -> None:
    """Store nearest feature distance for one category using vectorized spatial join."""
    joined = gpd.sjoin_nearest(
        settlements[["_sid", "geometry"]],
        features[["geometry"]],
        how="left",
        distance_col="_distance_m",
    )
    joined = joined.sort_values(["_sid", "_distance_m"]).drop_duplicates("_sid")
    metric_id = f"{category.id}.distance_km"

    for _, row in joined.iterrows():
        if not _is_missing(row.get("_distance_m")):
            result[int(row["_sid"])][metric_id] = round(
                float(row["_distance_m"]) / 1000.0,
                3,
            )


def _count(result, settlements, features, category, radius: float) -> None:
    """Count matching features intersecting each settlement buffer."""
    buffers = settlements[["_sid", "geometry"]].copy()
    buffers["geometry"] = buffers.geometry.buffer(radius * 1000.0)
    joined = gpd.sjoin(buffers, features[["geometry"]], how="left", predicate="intersects")
    counts = joined[joined["index_right"].notna()].groupby("_sid").size().to_dict()
    metric_id = f"{category.id}.count_{radius_token(radius)}"

    for sid in result:
        result[sid][metric_id] = float(counts.get(sid, 0))


def _coverage(result, settlements, features, category, radius: float) -> None:
    """Calculate unioned polygon area share inside each settlement buffer."""
    polygons = features[
        features.geometry.geom_type.isin(["Polygon", "MultiPolygon"])
    ].copy()
    if polygons.empty:
        return

    spatial_index = polygons.sindex
    metric_id = f"{category.id}.coverage_pct_{radius_token(radius)}"

    for _, settlement in settlements.iterrows():
        sid = int(settlement["_sid"])
        buffer_geometry = settlement.geometry.buffer(radius * 1000.0)
        candidate_ids = spatial_index.query(buffer_geometry, predicate="intersects")

        if len(candidate_ids) == 0:
            result[sid][metric_id] = 0.0
            continue

        geometries = polygons.iloc[list(candidate_ids)].geometry
        union = (
            geometries.union_all()
            if hasattr(geometries, "union_all")
            else geometries.unary_union
        )
        percentage = union.intersection(buffer_geometry).area / buffer_geometry.area * 100.0
        result[sid][metric_id] = round(min(100.0, max(0.0, percentage)), 2)
