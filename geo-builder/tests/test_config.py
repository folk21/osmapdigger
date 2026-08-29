from pathlib import Path

from osmapdigger_geo.config import (
    load_categories,
    load_dataset_definition,
    load_metric_profile,
    load_preference_profile,
    select_categories,
)
from osmapdigger_geo.metric_catalog import build_metric_definitions
from osmapdigger_geo.models import MetricDefinition


ROOT = Path(__file__).resolve().parents[2]


def test_dataset_config_resolves_andorra():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "andorra")
    assert dataset.id == "andorra"
    assert dataset.source_pbf.name == "andorra-260821.osm.pbf"
    assert dataset.metric_profile == "full"
    assert dataset.metric_profiles_file.name == "metric-profiles.toml"
    assert dataset.preference_profile == "balanced-living"
    assert dataset.preference_profiles_file.name == "preference-profiles.toml"
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
        "beach",
        "industrial",
        "landfill",
        "railway_station",
        "bus_stop",
        "major_road",
        "school",
        "medical",
        "supermarket",
    ]

    beach = next(category for category in selected if category.id == "beach")
    assert beach.default_filter is False



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


def test_balanced_preference_profile_resolves_against_generated_catalog():
    dataset = load_dataset_definition(ROOT / "geo-builder/config/datasets.toml", "belarus")
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    category_ids = load_metric_profile(dataset.metric_profiles_file, dataset.metric_profile)
    definitions = build_metric_definitions(
        select_categories(categories, category_ids, dataset.metric_profile)
    )

    defaults = load_preference_profile(
        dataset.preference_profiles_file,
        dataset.preference_profile,
        definitions,
    )

    assert len(defaults) == 10
    forest = next(item for item in defaults if item.metric_id == "forest.distance_km")
    beach = next(item for item in defaults if item.metric_id == "beach.distance_km")
    landfill = next(item for item in defaults if item.metric_id == "landfill.distance_km")
    assert forest.direction == "lower"
    assert forest.default_enabled is True
    assert forest.target_value == 1.0
    assert forest.limit_value == 10.0
    assert forest.weight == 8
    assert beach.direction == "lower"
    assert beach.default_enabled is True
    assert beach.target_value == 2.0
    assert beach.limit_value == 15.0
    assert beach.weight == 8
    assert landfill.direction == "higher"
    assert landfill.target_value == 15.0
    assert landfill.limit_value == 3.0


def test_belarus_core10_publishes_addable_beach_distance_metric():
    _, all_categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    profile_ids = load_metric_profile(
        ROOT / "geo-builder/config/metric-profiles.toml",
        "core10",
    )
    selected = select_categories(all_categories, profile_ids, "core10")
    definitions = build_metric_definitions(selected)

    beach = next(item for item in definitions if item.metric_id == "beach.distance_km")
    assert beach.measure_type == "distance"
    assert beach.unit == "km"
    assert beach.default_enabled is False
    assert all(not item.metric_id.startswith("water.") for item in definitions)


def test_preference_profile_rejects_metric_not_generated_by_selected_metric_profile(tmp_path):
    profile_path = tmp_path / "preferences.toml"
    profile_path.write_text(
        """
[profiles.invalid]
preferences = [
    { metric_id = "not.generated", enabled = true, target = 1.0, limit = 5.0, weight = 5 },
]
""".strip(),
        encoding="utf-8",
    )
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    definitions = build_metric_definitions(categories[:1])

    try:
        load_preference_profile(profile_path, "invalid", definitions)
    except ValueError as exc:
        assert "not generated" in str(exc)
    else:
        raise AssertionError("Unknown preference metric must fail before PBF processing")


def test_neutral_metric_requires_explicit_preference_direction(tmp_path):
    profile_path = tmp_path / "preferences.toml"
    profile_path.write_text(
        """
[profiles.invalid]
[[profiles.invalid.preferences]]
metric_id = "scrub_heath.distance_km"
enabled = false
target = 1.0
limit = 5.0
weight = 5
""".strip(),
        encoding="utf-8",
    )
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    definitions = build_metric_definitions(categories)

    try:
        load_preference_profile(profile_path, "invalid", definitions)
    except ValueError as exc:
        assert "explicit direction" in str(exc)
    else:
        raise AssertionError("Neutral metric default must require an explicit scoreable direction")


def test_preference_profile_accepts_explicit_direction_for_neutral_metric(tmp_path):
    profile_path = tmp_path / "preferences.toml"
    profile_path.write_text(
        """
[profiles.explicit]
[[profiles.explicit.preferences]]
metric_id = "scrub_heath.distance_km"
direction = "higher"
enabled = false
target = 10.0
limit = 1.0
weight = 3
""".strip(),
        encoding="utf-8",
    )
    _, categories = load_categories(ROOT / "geo-builder/config/metrics.toml")
    definitions = build_metric_definitions(categories)

    defaults = load_preference_profile(profile_path, "explicit", definitions)

    assert defaults[0].direction == "higher"
    assert defaults[0].target_value == 10.0
    assert defaults[0].limit_value == 1.0


def test_preference_enabled_state_is_independent_from_hard_filter_visibility(tmp_path):
    profile_path = tmp_path / "preferences.toml"
    profile_path.write_text(
        """
[profiles.independent]
preferences = [
    { metric_id = "custom.distance_km", enabled = true, target = 1.0, limit = 5.0, weight = 4 },
]
""".strip(),
        encoding="utf-8",
    )
    definition = MetricDefinition(
        metric_id="custom.distance_km",
        category_id="custom",
        group_id="Test",
        title="Custom distance",
        description="Test metric",
        unit="km",
        measure_type="distance",
        preferred_direction="lower",
        default_enabled=False,
        sort_order=0,
    )

    defaults = load_preference_profile(profile_path, "independent", [definition])

    assert definition.default_enabled is False
    assert defaults[0].default_enabled is True
