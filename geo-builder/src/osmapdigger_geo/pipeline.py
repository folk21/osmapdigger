"""Staged end-to-end orchestration for one configured OsmapDigger dataset.

This module is the build composition root: it connects configuration, source reading,
metric calculation, persistence, optional map generation, validation, and atomic
publication without moving those responsibilities into one implementation class.
"""

from __future__ import annotations

import json
import shutil
import tempfile
from pathlib import Path

from .config import (
    load_categories,
    load_dataset_definition,
    load_metric_profile,
    select_categories,
)
from .database import DatasetDatabaseWriter, validate_database
from .geometry import buffer_wgs84, estimate_metric_crs, load_boundary
from .map_builder import build_pmtiles, write_style_template
from .metric_catalog import build_metric_definitions
from .metrics import calculate_metrics, extract_settlements
from .osm_reader import PbfReader, crop_pbf
from .package import create_package_zip, sha256, utc_now, validate_package


def build_dataset(
    dataset_id: str,
    datasets_path: Path,
    metrics_path: Path,
    skip_map: bool = False,
    map_backend: str = "auto",
) -> Path:
    """Build and atomically publish one configured OsmapDigger dataset.

    All artifacts are created in a temporary staging directory beneath the output
    parent. The final directory is replaced only after package validation succeeds, so
    an interrupted/failed build cannot advertise incomplete output as current.
    """
    dataset = load_dataset_definition(datasets_path, dataset_id)
    places, all_categories = load_categories(metrics_path)
    profile_category_ids = load_metric_profile(
        dataset.metric_profiles_file,
        dataset.metric_profile,
    )
    categories = select_categories(
        all_categories,
        profile_category_ids,
        dataset.metric_profile,
    )
    print(
        f"Metric profile {dataset.metric_profile}: "
        f"{len(categories)}/{len(all_categories)} categories selected",
        flush=True,
    )

    if not dataset.source_pbf.exists():
        raise FileNotFoundError(f"OSM PBF not found: {dataset.source_pbf}")

    format_version = int(dataset.format_version_file.read_text(encoding="utf-8").strip())
    metric_definitions = build_metric_definitions(categories)

    scope_geometry = load_boundary(dataset.boundary_geojson) if dataset.boundary_geojson else None
    metric_crs = None
    context_geometry = None

    if scope_geometry is not None:
        metric_crs = estimate_metric_crs(scope_geometry)
        context_geometry = buffer_wgs84(scope_geometry, dataset.context_km, metric_crs)

    reader = PbfReader(dataset.source_pbf, bounding_geometry=context_geometry)
    general = reader.read_general(places, categories)
    roads = reader.read_roads(categories)

    settlements_gdf, settlements = extract_settlements(general, places, scope_geometry)
    if not settlements:
        raise RuntimeError("No named settlements found in selected dataset scope")

    if metric_crs is None:
        # The point cloud is sufficient to select a local metric CRS for a compact dataset.
        metric_crs = settlements_gdf.estimate_utm_crs()
        if metric_crs is None:
            raise RuntimeError("Cannot determine metric CRS for settlement metrics")

    metrics_by_sid = calculate_metrics(
        settlements_gdf,
        general,
        roads,
        categories,
        metric_crs,
    )

    bounds = list(scope_geometry.bounds if scope_geometry is not None else general.total_bounds)
    final_dir = dataset.output_dir
    final_dir.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix=f"osmapdigger-{dataset_id}-", dir=final_dir.parent) as temp:
        staging = Path(temp)
        database_path = staging / "georisk.sqlite"

        metadata_values = {
            "format_version": str(format_version),
            "dataset_id": dataset.id,
            "display_name": dataset.display_name,
            "country_code": dataset.country_code or "",
        }

        DatasetDatabaseWriter(dataset.format_schema).write(
            database_path,
            metadata_values,
            metric_definitions,
            settlements,
            metrics_by_sid,
        )

        db_stats = validate_database(database_path)
        write_style_template(staging / "style.template.json")

        map_backend_used = None
        pmtiles_name = f"{dataset.id}.pmtiles"
        if not skip_map:
            map_input = dataset.source_pbf
            if scope_geometry is not None:
                map_input = crop_pbf(
                    dataset.source_pbf,
                    staging / f"{dataset.id}-map-source.osm.pbf",
                    scope_geometry,
                )
            map_backend_used = build_pmtiles(
                map_input,
                staging / pmtiles_name,
                backend=map_backend,
            )
            if map_input != dataset.source_pbf:
                map_input.unlink(missing_ok=True)

        metadata = {
            "formatVersion": format_version,
            "datasetId": dataset.id,
            "displayName": dataset.display_name,
            "countryCode": dataset.country_code,
            "generatedAt": utc_now(),
            "source": {
                "fileName": dataset.source_pbf.name,
                "sha256": sha256(dataset.source_pbf),
                "sizeBytes": dataset.source_pbf.stat().st_size,
            },
            "bounds": [float(value) for value in bounds],
            "center": {
                "latitude": dataset.initial_center_latitude,
                "longitude": dataset.initial_center_longitude,
                "zoom": dataset.initial_zoom,
            },
            "propertySearch": {
                "site": dataset.property_search_site,
                "terms": dataset.property_search_terms,
            },
            "artifacts": {
                "database": "georisk.sqlite",
                "map": pmtiles_name if not skip_map else None,
                "style": "style.template.json",
            },
            "build": {
                "mapBackend": map_backend_used,
                "contextKm": dataset.context_km,
                "metricProfile": dataset.metric_profile,
                "metricCategoryCount": len(categories),
                "metricDefinitionCount": len(metric_definitions),
            },
            "statistics": db_stats,
        }
        (staging / "metadata.json").write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

        validate_package(staging)

        if final_dir.exists():
            shutil.rmtree(final_dir)
        shutil.copytree(staging, final_dir)

    create_package_zip(final_dir, dataset.id)
    print(f"Published dataset: {final_dir}", flush=True)
    return final_dir
