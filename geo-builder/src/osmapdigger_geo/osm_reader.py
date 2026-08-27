"""Lazy Pyrosm adapter and optional regional PBF cropping.

The module deliberately separates broad source reads from category-specific local
filtering. Pyrosm receives a merged filter to reduce I/O; ``metrics.filter_category``
remains the authoritative implementation of category selector semantics.
"""

from __future__ import annotations

import inspect
from pathlib import Path
from typing import Any

import geopandas as gpd
from shapely.geometry.base import BaseGeometry

from .models import CategoryDefinition


COMMON_EXTRA_TAGS = [
    "population",
    "wikidata",
    "wikipedia",
    "admin_level",
    "operator",
    "website",
    "surface",
    "tracktype",
    "highway",
]


def _merge_custom_filter(
    categories: list[CategoryDefinition],
    include_roads: bool,
) -> dict[str, Any]:
    """Merge many category selectors into one broad Pyrosm read filter.

    This merge intentionally loses the original OR-of-AND category structure. It is an
    I/O optimization only; exact category membership is recalculated locally later.
    """
    result: dict[str, Any] = {}

    for category in categories:
        if category.road_batch != include_roads:
            continue

        for alternative in category.selectors:
            for selector in alternative:
                if "*" in selector.values:
                    result[selector.tag] = True
                    continue

                if result.get(selector.tag) is True:
                    continue

                values = set(selector.values)
                existing = result.get(selector.tag)
                if isinstance(existing, list):
                    values.update(existing)
                result[selector.tag] = sorted(values)

    return result


def _empty_gdf() -> gpd.GeoDataFrame:
    """Return an empty WGS84 GeoDataFrame with the geometry contract intact."""
    return gpd.GeoDataFrame(
        {"geometry": []},
        geometry="geometry",
        crs="EPSG:4326",
    )


class PbfReader:
    """Lazy Pyrosm adapter for one source PBF and optional context geometry.

    Constructing this class performs no PBF parsing. Pyrosm is imported and the OSM
    reader is created on first read, which keeps default unit tests/imports lightweight.
    """

    def __init__(
        self,
        pbf_path: Path,
        bounding_geometry: BaseGeometry | None = None,
        engine: str = "auto",
    ) -> None:
        self.pbf_path = pbf_path
        self.bounding_geometry = bounding_geometry
        self.engine = engine
        self._selected_engine: str | None = None
        self._osm = None

    def _get_osm(self):
        """Create and cache the Pyrosm OSM reader with size-aware engine selection."""
        if self._osm is not None:
            return self._osm

        try:
            import pyrosm
            from pyrosm import OSM
        except ImportError as exc:
            raise RuntimeError(
                "Pyrosm is required for real PBF builds. Install geo-builder dependencies."
            ) from exc

        parameters = set(inspect.signature(OSM).parameters)
        kwargs: dict[str, Any] = {}

        if self.bounding_geometry is not None:
            kwargs["bounding_box"] = self.bounding_geometry
            if "complete_relations" in parameters:
                # Boundary-crossing multipolygons should remain usable after clipping.
                kwargs["complete_relations"] = True

        if "keep_metadata" in parameters:
            kwargs["keep_metadata"] = False

        selected = "in_memory"
        if "engine" in parameters:
            selected = self.engine
            if selected == "auto":
                selected = (
                    "out_of_core"
                    if self.pbf_path.stat().st_size >= 250 * 1024 * 1024
                    else "in_memory"
                )
            kwargs["engine"] = selected
            if selected == "out_of_core" and "workers" in parameters:
                kwargs["workers"] = "auto"
        self._selected_engine = selected

        print(
            f"Pyrosm {getattr(pyrosm, '__version__', 'unknown')} reading {self.pbf_path} "
            f"with engine={selected}",
            flush=True,
        )
        self._osm = OSM(str(self.pbf_path), **kwargs)
        return self._osm

    def read_general(
        self,
        settlement_places: list[str],
        categories: list[CategoryDefinition],
        settlement_name_tags: tuple[str, ...],
    ) -> gpd.GeoDataFrame:
        """Read settlements plus all non-road category candidate features in one batch."""
        custom_filter = _merge_custom_filter(categories, include_roads=False)
        custom_filter["place"] = settlement_places
        return self._read(
            self._get_osm(),
            custom_filter,
            "general features",
            extra_tags=settlement_name_tags,
        )

    def read_roads(
        self,
        categories: list[CategoryDefinition],
    ) -> gpd.GeoDataFrame:
        """Read road-category candidates separately to contain high-volume highway data."""
        custom_filter = _merge_custom_filter(categories, include_roads=True)
        if not custom_filter:
            return _empty_gdf()
        return self._read(self._get_osm(), custom_filter, "roads")

    def _read(
        self,
        osm,
        custom_filter: dict[str, Any],
        title: str,
        extra_tags: tuple[str, ...] = (),
    ) -> gpd.GeoDataFrame:
        """Execute one Pyrosm custom-filter read and normalize geometry to WGS84.

        Large inputs use Pyrosm's out-of-core engine. In that mode OsmapDigger only
        needs selector/filter keys plus ``COMMON_EXTRA_TAGS``; retaining every other
        OSM tag would create an unnecessary heterogeneous ``tags`` cache column and
        can make GeoParquet shard schemas incompatible on country-scale extracts.
        """
        print(f"Reading OSM batch: {title}", flush=True)
        read_kwargs: dict[str, Any] = {
            "custom_filter": custom_filter,
            "filter_type": "keep",
            "keep_nodes": True,
            "keep_ways": True,
            "keep_relations": True,
            "extra_attributes": list(dict.fromkeys([*COMMON_EXTRA_TAGS, *extra_tags])),
        }
        if self._selected_engine == "out_of_core":
            read_kwargs["keep_other_tags"] = False

        data = osm.get_data_by_custom_criteria(**read_kwargs)

        if data is None or len(data) == 0:
            print(f"{title}: 0 features", flush=True)
            return _empty_gdf()

        if "geometry" not in data.columns:
            raise RuntimeError(f"Pyrosm returned {title} without geometry")

        data = data.set_crs("EPSG:4326") if data.crs is None else data.to_crs("EPSG:4326")
        data = data[data.geometry.notna() & ~data.geometry.is_empty].copy()
        print(f"{title}: {len(data)} features", flush=True)
        return data


def crop_pbf(
    source: Path,
    target: Path,
    geometry: BaseGeometry,
) -> Path:
    """Write a valid boundary-constrained PBF for regional PMTiles generation.

    Analytical reads can use Pyrosm's bounding geometry directly, but tilemaker needs a
    source PBF. This method creates that intermediate file in staging and the pipeline
    deletes it after map generation.
    """
    try:
        from pyrosm import OSM
    except ImportError as exc:
        raise RuntimeError("Pyrosm is required to crop a regional PBF") from exc

    target.parent.mkdir(parents=True, exist_ok=True)
    reader = OSM(
        str(source),
        bounding_box=geometry,
        keep_metadata=False,
        complete_relations=True,
        engine="out_of_core" if source.stat().st_size >= 250 * 1024 * 1024 else "in_memory",
    )
    output = reader.to_pbf(output_path=str(target), keep_relations=True)
    return Path(output)
