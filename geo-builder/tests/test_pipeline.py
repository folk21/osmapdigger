from pathlib import Path

import geopandas as gpd
from shapely.geometry import Point

from osmapdigger_geo import pipeline
from osmapdigger_geo.database import validate_database


ROOT = Path(__file__).resolve().parents[2]


class FakeReader:
    def __init__(self, pbf_path, bounding_geometry=None):
        self.pbf_path = pbf_path

    def read_general(self, places, categories, settlement_name_tags):
        return gpd.GeoDataFrame(
            [
                {"id": 1, "osm_type": "node", "name": "Village", "place": "village", "natural": None},
                {"id": 2, "osm_type": "way", "name": "Wood", "place": None, "natural": "wood"},
            ],
            geometry=[Point(1.5, 42.5), Point(1.505, 42.5).buffer(0.001)],
            crs="EPSG:4326",
        )

    def read_roads(self, categories):
        return gpd.GeoDataFrame({"geometry": []}, geometry="geometry", crs="EPSG:4326")


def test_pipeline_publishes_data_package_without_map(tmp_path, monkeypatch):
    source_root = tmp_path / "source"
    output_root = tmp_path / "output"
    source_root.mkdir()
    pbf = source_root / "fixture.osm.pbf"
    pbf.write_bytes(b"synthetic-pbf-placeholder")

    datasets = tmp_path / "datasets.toml"
    datasets.write_text(
        f"""
[builder]
source_root = "{source_root.as_posix()}"
output_root = "{output_root.as_posix()}"
format_schema = "{(ROOT / 'geo-format/schema.sql').as_posix()}"
format_version_file = "{(ROOT / 'geo-format/VERSION').as_posix()}"
context_km = 5.0

[datasets.test]
display_name = "Test"
source_pbf = "fixture.osm.pbf"
initial_center_latitude = 42.5
initial_center_longitude = 1.5
initial_zoom = 10.0
property_search_site = "example.test"
property_search_terms = "house"
""".strip(),
        encoding="utf-8",
    )

    metrics = tmp_path / "metrics.toml"
    metrics.write_text(
        """
[settings]
settlement_places = ["village"]

[[categories]]
id = "forest"
title = "Forest"
group = "Nature"
default_filter = true
distance = true
preferred_direction = "lower"
selectors = [[{ tag = "natural", values = ["wood"] }]]
""".strip(),
        encoding="utf-8",
    )

    monkeypatch.setattr(pipeline, "PbfReader", FakeReader)

    package_dir = pipeline.build_dataset(
        dataset_id="test",
        datasets_path=datasets,
        metrics_path=metrics,
        skip_map=True,
    )

    assert (package_dir / "georisk.sqlite").exists()
    assert (package_dir / "metadata.json").exists()
    assert (package_dir / "test.omd.zip").exists()
    assert validate_database(package_dir / "georisk.sqlite")["settlements"] == 1
