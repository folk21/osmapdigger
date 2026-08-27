"""Configuration-driven OSM category filtering and spatial metric calculation."""

from __future__ import annotations

import geopandas as gpd
import pandas as pd

from .metric_catalog import radius_token
from .models import CategoryDefinition
from .settlements import tag_series


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

    for index, category in enumerate(categories, start=1):
        source = road_features if category.road_batch else general_features
        selected = filter_category(source, category)
        print(
            f"Category {index}/{len(categories)} {category.id}: {len(selected)} features",
            flush=True,
        )
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
        if pd.notna(row.get("_distance_m")):
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
