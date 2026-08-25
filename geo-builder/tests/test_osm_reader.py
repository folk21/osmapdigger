from pathlib import Path

import geopandas as gpd

from osmapdigger_geo.osm_reader import PbfReader


class RecordingOsm:
    def __init__(self):
        self.calls = []

    def get_data_by_custom_criteria(self, **kwargs):
        self.calls.append(kwargs)
        return gpd.GeoDataFrame(
            {"geometry": []},
            geometry="geometry",
            crs="EPSG:4326",
        )


def test_out_of_core_custom_read_drops_unrequested_tags():
    reader = PbfReader(Path("large.osm.pbf"), engine="out_of_core")
    reader._selected_engine = "out_of_core"
    osm = RecordingOsm()

    reader._read(osm, {"highway": ["primary"]}, "roads")

    assert osm.calls[0]["keep_other_tags"] is False


def test_in_memory_custom_read_keeps_pyrosm_default_tag_behavior():
    reader = PbfReader(Path("small.osm.pbf"), engine="in_memory")
    reader._selected_engine = "in_memory"
    osm = RecordingOsm()

    reader._read(osm, {"place": ["village"]}, "general features")

    assert "keep_other_tags" not in osm.calls[0]
