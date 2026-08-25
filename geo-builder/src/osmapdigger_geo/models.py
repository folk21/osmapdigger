"""Immutable contracts shared by Geo Builder pipeline stages.

These dataclasses intentionally contain no I/O behavior. Keeping configuration,
calculation, persistence, and external-process stages connected through small immutable
records makes the build pipeline easier to test without parsing a real PBF.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Literal


@dataclass(frozen=True)
class Selector:
    """One OSM tag constraint inside a category selector alternative.

    ``values=("*",)`` means the tag must exist regardless of its concrete value.
    Several selectors inside one alternative are AND-ed; alternatives are OR-ed.
    """

    tag: str
    values: tuple[str, ...]


@dataclass(frozen=True)
class CategoryDefinition:
    """Build-time definition of one conceptual OSM feature category.

    The category describes source matching plus which numeric runtime measures should
    be generated. ``road_batch`` keeps high-volume highway geometry out of the general
    Pyrosm read without changing runtime metric semantics.
    """

    id: str
    title: str
    group: str
    selectors: tuple[tuple[Selector, ...], ...]
    default_filter: bool
    distance: bool
    coverage_radii_km: tuple[float, ...]
    count_radii_km: tuple[float, ...]
    preferred_direction: Literal["lower", "higher"]
    road_batch: bool = False


@dataclass(frozen=True)
class MetricDefinition:
    """Persisted runtime description of one numeric filterable metric."""

    metric_id: str
    category_id: str
    group_id: str
    title: str
    description: str
    unit: str
    measure_type: str
    preferred_direction: str
    default_enabled: bool
    sort_order: int


@dataclass(frozen=True)
class DatasetDefinition:
    """Resolved configuration for building one installable dataset package.

    A dataset is a package scope, not necessarily a country. ``source_pbf`` may point
    to a whole-country or regional extract; ``boundary_geojson`` optionally creates a
    smaller package from a larger local source.
    """

    id: str
    display_name: str
    country_code: str | None
    source_pbf: Path
    output_dir: Path
    boundary_geojson: Path | None
    initial_center_latitude: float
    initial_center_longitude: float
    initial_zoom: float
    context_km: float
    format_schema: Path
    format_version_file: Path
    metric_profiles_file: Path | None
    metric_profile: str
    property_search_site: str | None
    property_search_terms: str


@dataclass(frozen=True)
class SettlementRecord:
    """SQLite-ready settlement record produced from OSM place geometry."""

    settlement_id: str
    osm_id: int | None
    osm_type: str | None
    name: str
    name_local: str | None
    name_en: str | None
    place_type: str | None
    population: int | None
    latitude: float
    longitude: float
