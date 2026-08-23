from pathlib import Path

from osmapdigger_geo.config import load_categories, load_dataset_definition
from osmapdigger_geo.metric_catalog import build_metric_definitions


ROOT = Path(__file__).resolve().parents[2]


def test_dataset_config_resolves_andorra():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "andorra")
    assert dataset.id == "andorra"
    assert dataset.source_pbf.name == "andorra-260821.osm.pbf"


def test_metrics_are_dynamic_and_have_defaults():
    places, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    definitions = build_metric_definitions(categories)

    assert "village" in places
    assert any(item.id == "forest" for item in categories)
    assert any(item.metric_id == "forest.distance_km" for item in definitions)
    assert any(item.default_enabled for item in definitions)
