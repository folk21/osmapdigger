"""S02 acceptance for bounded and explicit builder interpretation resolution."""

from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest

from osmapdigger_geo.config import (
    load_categories,
    load_dataset_definition,
    load_metric_profile,
    load_preference_profile,
    select_categories,
)
from osmapdigger_geo.interpretation_config import load_interpretation_catalog
from osmapdigger_geo.metric_catalog import build_metric_definitions


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "geo-builder/config"


def _dataset(dataset_id: str):
    dataset = load_dataset_definition(CONFIG / "datasets.toml", dataset_id)
    _, categories = load_categories(CONFIG / "metrics.toml")
    selection = load_metric_profile(dataset.metric_profiles_file, dataset.metric_profile)
    metrics = build_metric_definitions(select_categories(categories, selection, dataset.metric_profile))
    defaults = load_preference_profile(dataset.preference_profiles_file, dataset.preference_profile, metrics)
    return dataset, metrics, defaults


def _catalog(dataset_id: str):
    dataset, metrics, defaults = _dataset(dataset_id)
    catalog = load_interpretation_catalog(
        dataset.interpretation_profiles_file, dataset.interpretation_profile, metrics, defaults
    )
    return catalog, metrics, defaults


def test_both_dataset_catalogs_are_explicit_schema_valid_and_profile_specific():
    full, full_metrics, full_defaults = _catalog("andorra")
    core, core_metrics, core_defaults = _catalog("belarus")

    for catalog, metrics, defaults in (
        (full, full_metrics, full_defaults), (core, core_metrics, core_defaults)
    ):
        assert catalog["version"] == 1
        assert len(catalog["goalProfiles"]) == 4
        assert all(len(goal["preferences"]) == len(defaults) for goal in catalog["goalProfiles"])
        assert all({pref["metricId"] for pref in goal["preferences"]} ==
                   {item.metric_id for item in defaults} for goal in catalog["goalProfiles"])
        assert all(set(dimension["metricIds"]) <= {metric.metric_id for metric in metrics}
                   for dimension in catalog["dimensions"])

    assert {item["id"] for item in full["dimensions"]} - {
        item["id"] for item in core["dimensions"]
    } == {"water-proximity"}
    assert full == json.loads(json.dumps(full, ensure_ascii=False))
    assert core == json.loads(json.dumps(core, ensure_ascii=False))


def test_catalogs_satisfy_v1_metadata_json_schema_when_validator_available():
    schema_validator = pytest.importorskip("jsonschema")
    schema = json.loads((ROOT / "geo-format/metadata.schema.json").read_text())
    validator = schema_validator.Draft202012Validator({
        "$defs": schema["$defs"], "$ref": "#/$defs/interpretationCatalog"
    })
    for dataset_id in ("andorra", "belarus"):
        catalog, _, _ = _catalog(dataset_id)
        validator.validate(catalog)


def test_unsupported_config_version_and_unmatched_definition_id_fail(tmp_path):
    with pytest.raises(ValueError, match="Unsupported interpretation configuration version"):
        _mutated_catalog(tmp_path, lambda text: text.replace("version = 1", "version = 2", 1))
    with pytest.raises(ValueError, match="matching id"):
        _mutated_catalog(tmp_path, lambda text: text.replace(
            '[dimensions.nature]\nid = "nature"', '[dimensions.nature]\nid = "other"', 1
        ))


def test_balanced_goal_exactly_reproduces_dataset_defaults_and_reverse_is_explicit():
    for dataset_id in ("andorra", "belarus"):
        catalog, _, defaults = _catalog(dataset_id)
        balanced = next(goal for goal in catalog["goalProfiles"] if goal["id"] == "balanced-living")
        assert balanced["preferences"] == [
            {
                "metricId": item.metric_id,
                "enabled": item.default_enabled,
                "direction": item.direction,
                "target": item.target_value,
                "limit": item.limit_value,
                "weight": item.weight,
            }
            for item in defaults
        ]
        remote = next(goal for goal in catalog["goalProfiles"] if goal["id"] == "remote-retreat")
        major_road = next(pref for pref in remote["preferences"]
                          if pref["metricId"] == "major_road.distance_km")
        assert (major_road["enabled"], major_road["direction"], major_road["target"],
                major_road["limit"]) == (True, "higher", 10.0, 1.0)


def test_optional_interpretation_selection_preserves_legacy():
    dataset, definitions, defaults = _dataset("belarus")
    assert load_interpretation_catalog(None, None, definitions, defaults) is None
    with pytest.raises(ValueError, match="requires builder.interpretation_profiles"):
        load_interpretation_catalog(None, dataset.interpretation_profile, definitions, defaults)


def _mutated_catalog(tmp_path: Path, mutate, *, selected="core10", defaults_override=None):
    original = (CONFIG / "interpretation-profiles.toml").read_text()
    changed = mutate(original)
    path = tmp_path / "interpretation-profiles.toml"
    path.write_text(changed, encoding="utf-8")
    _, definitions, defaults = _dataset("belarus")
    return load_interpretation_catalog(
        path, selected, definitions, defaults if defaults_override is None else defaults_override
    )


@pytest.mark.parametrize(
    ("old", "new", "expected"),
    [
        ('metricIds = ["forest.distance_km", "beach.distance_km"]',
         'metricIds = ["forest.distance_km", "not_generated.distance_km"]', "not generated"),
        ('metricIds = ["forest.distance_km", "beach.distance_km"]',
         'metricIds = ["forest.distance_km", "forest.distance_km"]', "duplicate"),
        ('labels = { en = "Nature-related access", ru = "Доступность природных объектов" }',
         'labels = { en = "Nature-related access" }', "ru"),
        ('minimumCoverage = 45', 'minimumCoverage = 101', "minimumCoverage"),
        ('minimumKnownMetrics = 1', 'minimumKnownMetrics = 3', "minimumKnownMetrics"),
        ('goal_ids = ["balanced-living", "nature-quiet", "family-convenience", "remote-retreat"]',
         'goal_ids = ["balanced-living", "unknown", "family-convenience", "remote-retreat"]', "unknown"),
        ('{ metricId = "forest.distance_km", weight = 10 }',
         '{ metricId = "unknown.distance_km", weight = 10 }', "absent from preference defaults"),
        ('{ metricId = "forest.distance_km", weight = 10 }',
         '{ metricId = "forest.distance_km", weight = 11 }', "weight"),
        ('{ metricId = "forest.distance_km", weight = 10 }',
         '{ metricId = "forest.distance_km", direction = "higher" }', "target/limit"),
        ('{ metricId = "forest.distance_km", weight = 10 }',
         '{ metricId = "forest.distance_km", weight = 10, enabled = "true" }', "enabled"),
        ('{ metricId = "forest.distance_km", weight = 10 }',
         '{ metricId = "forest.distance_km", weight = nan }', "integer"),
        ('dimensionIds = ["nature", "services"]',
         'dimensionIds = ["nature", "nonexistent"]', "unknown dimensions"),
        ('type = "dimension-contrast"', 'type = "execute-python"', "unsupported operation"),
        ('minimumScore = 75', 'minimumScore = 101', "minimumScore"),
    ],
)
def test_invalid_configuration_fails_closed(tmp_path, old, new, expected):
    assert old in (CONFIG / "interpretation-profiles.toml").read_text()
    with pytest.raises(ValueError, match=expected):
        _mutated_catalog(tmp_path, lambda contents: contents.replace(old, new))


def test_explicit_selected_profile_cannot_refer_to_full_only_metric(tmp_path):
    with pytest.raises(ValueError, match="not generated"):
        _mutated_catalog(
            tmp_path,
            lambda contents: contents.replace(
                'metricIds = ["forest.distance_km", "beach.distance_km"]',
                'metricIds = ["river_stream.distance_km"]', 1
            ),
        )


def test_duplicate_override_and_bad_rule_signature_fail_before_publication(tmp_path):
    with pytest.raises(ValueError, match="duplicate override"):
        _mutated_catalog(
            tmp_path,
            lambda contents: contents.replace(
                '{ metricId = "forest.distance_km", weight = 10 },',
                '{ metricId = "forest.distance_km", weight = 10 },\n'
                '    { metricId = "forest.distance_km", weight = 9 },', 1
            ),
        )
    with pytest.raises(ValueError, match="requires only"):
        _mutated_catalog(
            tmp_path,
            lambda contents: contents.replace(
                'maximumScore = 35', 'minimumScore = 10\nmaximumScore = 35', 1
            ),
        )


def test_selected_metric_catalog_not_mutated_and_order_is_deterministic():
    dataset, metrics, defaults = _dataset("belarus")
    before = copy.deepcopy((metrics, defaults))
    first = load_interpretation_catalog(dataset.interpretation_profiles_file,
                                        dataset.interpretation_profile, metrics, defaults)
    second = load_interpretation_catalog(dataset.interpretation_profiles_file,
                                         dataset.interpretation_profile, metrics, defaults)
    assert first == second
    assert (metrics, defaults) == before
    assert [dimension["sortOrder"] for dimension in first["dimensions"]] == sorted(
        dimension["sortOrder"] for dimension in first["dimensions"]
    )
