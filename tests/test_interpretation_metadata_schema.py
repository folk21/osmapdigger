"""Optional v1 interpretation metadata schema and compatibility fixtures."""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path

import pytest

try:
    from jsonschema import Draft202012Validator
except ImportError:
    Draft202012Validator = None


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "geo-format/metadata.schema.json"


def _metadata() -> dict:
    return {
        "formatVersion": 1,
        "datasetId": "synthetic",
        "displayName": "Synthetic fixture",
        "generatedAt": "2026-01-01T00:00:00Z",
        "source": {},
        "artifacts": {},
        "bounds": [0, 0, 1, 1],
        "center": {"latitude": 0, "longitude": 0, "zoom": 5},
        "statistics": {},
    }


def _catalog() -> dict:
    return {
        "version": 1,
        "dimensions": [
            {
                "id": "nature",
                "labels": {"en": "Nature", "ru": "Природа"},
                "sortOrder": 10,
                "metricIds": ["forest.distance_km", "beach.distance_km"],
                "minimumCoverage": 60,
                "minimumKnownMetrics": 1,
            },
            {
                "id": "services",
                "labels": {"en": "Services", "ru": "Услуги"},
                "sortOrder": 20,
                "metricIds": ["medical.distance_km"],
                "minimumCoverage": 70,
                "minimumKnownMetrics": 1,
            },
        ],
        "goalProfiles": [
            {
                "id": "balanced-living",
                "labels": {"en": "Balanced living", "ru": "Сбалансированная жизнь"},
                "sortOrder": 10,
                "preferences": [
                    {
                        "metricId": "forest.distance_km",
                        "enabled": True,
                        "direction": "lower",
                        "target": 0.25,
                        "limit": 3.0,
                        "weight": 8,
                    },
                    {
                        "metricId": "medical.distance_km",
                        "enabled": False,
                        "direction": "lower",
                        "target": 1.0,
                        "limit": 20.0,
                        "weight": 7,
                    },
                ],
            }
        ],
        "insightRules": [
            {
                "id": "nature-strength",
                "type": "dimension-strength",
                "dimensionIds": ["nature"],
                "templateKey": "interpretation.dimension.strength",
                "minimumCoverage": 60,
                "minimumKnownMetrics": 1,
                "minimumScore": 75,
            },
            {
                "id": "services-tradeoff",
                "type": "dimension-tradeoff",
                "dimensionIds": ["services"],
                "templateKey": "interpretation.dimension.tradeoff",
                "minimumCoverage": 70,
                "minimumKnownMetrics": 1,
                "maximumScore": 25,
            },
            {
                "id": "nature-vs-services",
                "type": "dimension-contrast",
                "dimensionIds": ["nature", "services"],
                "templateKey": "interpretation.dimension.contrast",
                "minimumCoverage": 70,
                "minimumKnownMetrics": 1,
                "minimumScore": 75,
                "maximumScore": 25,
            },
        ],
    }


def _schema() -> dict:
    return json.loads(SCHEMA.read_text(encoding="utf-8"))


def _validator():
    if Draft202012Validator is None:
        pytest.skip("Install jsonschema to run optional Draft 2020-12 fixture validation")
    schema = _schema()
    Draft202012Validator.check_schema(schema)
    return Draft202012Validator(schema)


def _assert_valid(metadata: dict) -> None:
    _validator().validate(metadata)


def _assert_invalid(metadata: dict) -> None:
    assert not _validator().is_valid(metadata)


def test_contract_is_optional_bounded_and_declares_typed_rules() -> None:
    schema = _schema()
    assert schema["$schema"].endswith("/draft/2020-12/schema")
    assert "interpretation" not in schema["required"]
    property_schema = schema["properties"]["interpretation"]
    assert {part.get("type", part.get("$ref")) for part in property_schema["oneOf"]} == {
        "null", "#/$defs/interpretationCatalog"
    }
    definitions = schema["$defs"]
    catalog = definitions["interpretationCatalog"]
    assert catalog["additionalProperties"] is False
    assert catalog["properties"]["version"]["const"] == 1
    assert catalog["properties"]["dimensions"]["maxItems"] == 32
    assert catalog["properties"]["goalProfiles"]["maxItems"] == 16
    assert catalog["properties"]["insightRules"]["maxItems"] == 64
    assert definitions["interpretationPreference"]["properties"]["weight"]["maximum"] == 10
    assert definitions["interpretationInsightRule"]["properties"]["type"]["enum"] == [
        "dimension-strength", "dimension-tradeoff", "dimension-contrast"
    ]


@pytest.mark.parametrize("interpretation", [None, {"version": 1, "dimensions": [], "goalProfiles": [], "insightRules": []}])
def test_explicit_unavailable_or_empty_catalog_is_allowed(interpretation: dict | None) -> None:
    metadata = _metadata()
    metadata["interpretation"] = interpretation
    _assert_valid(metadata)


def test_legacy_metadata_remains_valid_and_full_v1_fixture_is_valid() -> None:
    legacy = _metadata()
    _assert_valid(legacy)
    extended = deepcopy(legacy)
    extended["interpretation"] = _catalog()
    _assert_valid(extended)


def _invalid_version(catalog: dict) -> None:
    catalog["version"] = 2


def _missing_locale(catalog: dict) -> None:
    del catalog["dimensions"][0]["labels"]["ru"]


def _blank_label(catalog: dict) -> None:
    catalog["dimensions"][0]["labels"]["en"] = "  "


def _duplicate_metric(catalog: dict) -> None:
    catalog["dimensions"][0]["metricIds"] *= 2


def _duplicate_rule_dimension(catalog: dict) -> None:
    catalog["insightRules"][2]["dimensionIds"] = ["nature", "nature"]


def _invalid_coverage(catalog: dict) -> None:
    catalog["dimensions"][0]["minimumCoverage"] = 101


def _invalid_known_count(catalog: dict) -> None:
    catalog["insightRules"][0]["minimumKnownMetrics"] = 0


def _invalid_direction(catalog: dict) -> None:
    catalog["goalProfiles"][0]["preferences"][0]["direction"] = "neutral"


def _invalid_weight(catalog: dict) -> None:
    catalog["goalProfiles"][0]["preferences"][0]["weight"] = 11


def _invalid_numeric_bound(catalog: dict) -> None:
    catalog["goalProfiles"][0]["preferences"][0]["limit"] = 10**15


def _unknown_rule_type(catalog: dict) -> None:
    catalog["insightRules"][0]["type"] = "eval-expression"


def _executable_rule(catalog: dict) -> None:
    catalog["insightRules"][0]["expression"] = "1 + 1"


def _missing_strength_threshold(catalog: dict) -> None:
    del catalog["insightRules"][0]["minimumScore"]


def _unexpected_strength_threshold(catalog: dict) -> None:
    catalog["insightRules"][0]["maximumScore"] = 10


def _contrast_requires_two_dimensions(catalog: dict) -> None:
    catalog["insightRules"][2]["dimensionIds"] = ["nature"]


def _unbounded_dimensions(catalog: dict) -> None:
    catalog["dimensions"] = catalog["dimensions"] * 17


@pytest.mark.parametrize(
    "mutate",
    [
        _invalid_version,
        _missing_locale,
        _blank_label,
        _duplicate_metric,
        _duplicate_rule_dimension,
        _invalid_coverage,
        _invalid_known_count,
        _invalid_direction,
        _invalid_weight,
        _invalid_numeric_bound,
        _unknown_rule_type,
        _executable_rule,
        _missing_strength_threshold,
        _unexpected_strength_threshold,
        _contrast_requires_two_dimensions,
        _unbounded_dimensions,
    ],
    ids=lambda fn: fn.__name__,
)
def test_invalid_v1_shapes_are_rejected(mutate) -> None:
    catalog = _catalog()
    mutate(catalog)
    metadata = _metadata()
    metadata["interpretation"] = catalog
    _assert_invalid(metadata)


def test_nonfinite_json_numbers_are_not_valid_json_wire_payloads() -> None:
    metadata = _metadata()
    metadata["interpretation"] = _catalog()
    metadata["interpretation"]["goalProfiles"][0]["preferences"][0]["target"] = float("nan")
    with pytest.raises(ValueError):
        json.dumps(metadata, allow_nan=False)
