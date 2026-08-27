from pathlib import Path

from osmapdigger_geo.config import (
    load_categories,
    load_dataset_definition,
    load_metric_profile,
    select_categories,
)
from osmapdigger_geo.metric_catalog import build_metric_definitions


ROOT = Path(__file__).resolve().parents[2]


def test_dataset_config_resolves_andorra():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "andorra")
    assert dataset.id == "andorra"
    assert dataset.source_pbf.name == "andorra-260821.osm.pbf"
    assert dataset.metric_profile == "full"
    assert dataset.metric_profiles_file.name == "metric-profiles.toml"
    assert dataset.settlement_name_tags == ("name", "name:en", "official_name", "alt_name")
    assert dataset.settlement_deduplication_tolerance_m == 0.0
    assert dataset.settlement_name_deduplication_distance_m == 0.0


def test_metrics_are_dynamic_and_have_defaults():
    places, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    definitions = build_metric_definitions(categories)

    assert "village" in places
    assert any(item.id == "forest" for item in categories)
    assert any(item.metric_id == "forest.distance_km" for item in definitions)
    assert any(item.default_enabled for item in definitions)


def test_belarus_uses_core10_metric_profile():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "belarus")
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    category_ids = load_metric_profile(dataset.metric_profiles_file, dataset.metric_profile)
    selected = select_categories(categories, category_ids, dataset.metric_profile)

    assert dataset.metric_profile == "core10"
    assert dataset.settlement_deduplication_tolerance_m == 250.0
    assert dataset.settlement_name_deduplication_distance_m == 1000.0
    assert "name:be" in dataset.settlement_name_tags
    assert "name:ru" in dataset.settlement_name_tags
    assert [category.id for category in selected] == [
        "forest",
        "water",
        "industrial",
        "landfill",
        "railway_station",
        "bus_stop",
        "major_road",
        "school",
        "medical",
        "supermarket",
    ]


def test_full_metric_profile_selects_complete_catalog():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "andorra")
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    category_ids = load_metric_profile(dataset.metric_profiles_file, dataset.metric_profile)

    assert category_ids is None
    assert select_categories(categories, category_ids, dataset.metric_profile) == categories


def test_metric_profile_rejects_unknown_category(tmp_path):
    profile_path = tmp_path / "profiles.toml"
    profile_path.write_text(
        '[profiles.invalid]\ncategories = ["forest", "does_not_exist"]\n',
        encoding="utf-8",
    )
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    category_ids = load_metric_profile(profile_path, "invalid")

    try:
        select_categories(categories, category_ids, "invalid")
    except ValueError as exc:
        assert "does_not_exist" in str(exc)
    else:
        raise AssertionError("Unknown profile category must fail before PBF processing")
