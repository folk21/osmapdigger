"""Small GIS helpers shared by dataset-scope and context calculations."""

from __future__ import annotations

from pathlib import Path

import geopandas as gpd
from shapely.geometry.base import BaseGeometry


def load_boundary(path: Path) -> BaseGeometry:
    """Load a configured boundary and normalize it to one WGS84 geometry.

    Boundary files must carry a CRS. Silent CRS guessing would corrupt both distance
    metrics and PBF cropping, so invalid/empty files fail immediately.
    """
    frame = gpd.read_file(path)
    if frame.empty or frame.crs is None:
        raise RuntimeError(f"Invalid boundary: {path}")

    frame = frame.to_crs("EPSG:4326")
    geometries = frame.geometry[frame.geometry.notna() & ~frame.geometry.is_empty]
    if geometries.empty:
        raise RuntimeError(f"Boundary has no usable geometry: {path}")

    return geometries.union_all() if hasattr(geometries, "union_all") else geometries.unary_union


def estimate_metric_crs(geometry: BaseGeometry):
    """Choose a local projected CRS suitable for meter-based distance/area work."""
    crs = gpd.GeoSeries([geometry], crs="EPSG:4326").estimate_utm_crs()
    if crs is None:
        raise RuntimeError("Cannot determine metric CRS")
    return crs


def buffer_wgs84(geometry: BaseGeometry, km: float, metric_crs) -> BaseGeometry:
    """Create a kilometer buffer in metric CRS and return the result in WGS84.

    The context buffer lets settlements near a dataset boundary see nearby features on
    the other side of that boundary while keeping settlements themselves inside scope.
    """
    if km <= 0:
        return geometry
    series = gpd.GeoSeries([geometry], crs="EPSG:4326").to_crs(metric_crs)
    return series.buffer(km * 1000.0).to_crs("EPSG:4326").iloc[0]
