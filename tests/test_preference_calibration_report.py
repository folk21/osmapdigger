from __future__ import annotations

import importlib.util
import sqlite3
import sys
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "scripts" / "preference_calibration_report.py"
SPEC = importlib.util.spec_from_file_location("preference_calibration_report", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

PreferenceDefinition = MODULE.PreferenceDefinition
build_report = MODULE.build_report
normalized_quality = MODULE.normalized_quality
percentile = MODULE.percentile
render_markdown = MODULE.render_markdown


def create_dataset(path: Path) -> Path:
    database = path / "georisk.sqlite"
    connection = sqlite3.connect(database)
    connection.executescript(
        """
        CREATE TABLE metric_definition (
            metric_id TEXT PRIMARY KEY NOT NULL,
            category_id TEXT NOT NULL,
            group_id TEXT NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            unit TEXT NOT NULL,
            measure_type TEXT NOT NULL,
            preferred_direction TEXT NOT NULL,
            default_enabled INTEGER NOT NULL,
            sort_order INTEGER NOT NULL
        );
        CREATE TABLE metric_preference_default (
            metric_id TEXT PRIMARY KEY NOT NULL,
            direction TEXT NOT NULL,
            target_value REAL NOT NULL,
            limit_value REAL NOT NULL,
            weight INTEGER NOT NULL,
            default_enabled INTEGER NOT NULL
        );
        CREATE TABLE settlement (
            settlement_id TEXT PRIMARY KEY NOT NULL
        );
        CREATE TABLE settlement_metric (
            settlement_id TEXT NOT NULL,
            metric_id TEXT NOT NULL,
            value REAL NOT NULL,
            PRIMARY KEY (settlement_id, metric_id)
        );
        """
    )
    connection.executemany(
        "INSERT INTO metric_definition VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        [
            ("forest.distance_km", "forest", "nature", "Forest", "", "km", "distance", "lower", 0, 1),
            ("landfill.distance_km", "landfill", "risk", "Landfill", "", "km", "distance", "higher", 0, 2),
        ],
    )
    connection.executemany(
        "INSERT INTO metric_preference_default VALUES (?, ?, ?, ?, ?, ?)",
        [
            ("forest.distance_km", "lower", 1.0, 10.0, 8, 1),
            ("landfill.distance_km", "higher", 15.0, 3.0, 2, 1),
        ],
    )
    connection.executemany(
        "INSERT INTO settlement(settlement_id) VALUES (?)",
        [("a",), ("b",), ("c",), ("d",)],
    )
    connection.executemany(
        "INSERT INTO settlement_metric VALUES (?, ?, ?)",
        [
            ("a", "forest.distance_km", 0.5),
            ("b", "forest.distance_km", 5.5),
            ("c", "forest.distance_km", 10.0),
            ("d", "forest.distance_km", 12.0),
            ("a", "landfill.distance_km", 15.0),
            ("b", "landfill.distance_km", 9.0),
        ],
    )
    connection.commit()
    connection.close()
    return database


def test_percentile_interpolates_deterministically() -> None:
    values = [0.0, 10.0, 20.0, 30.0]

    assert percentile(values, 0.0) == 0.0
    assert percentile(values, 0.5) == 15.0
    assert percentile(values, 1.0) == 30.0


def test_normalized_quality_matches_lower_and_higher_piecewise_formula() -> None:
    lower = PreferenceDefinition("lower", "g", "", "", "lower", 1.0, 10.0, 5, True)
    higher = PreferenceDefinition("higher", "g", "", "", "higher", 15.0, 3.0, 5, True)

    assert normalized_quality(0.5, lower) == 1.0
    assert normalized_quality(10.0, lower) == 0.0
    assert normalized_quality(5.5, lower) == 0.5
    assert normalized_quality(15.0, higher) == 1.0
    assert normalized_quality(3.0, higher) == 0.0
    assert normalized_quality(9.0, higher) == 0.5


def test_build_report_uses_runtime_preferences_and_preserves_missing_coverage(tmp_path: Path) -> None:
    create_dataset(tmp_path)

    report = build_report(tmp_path, saturation_warning_pct=90.0)

    assert report.total_settlements == 4
    assert report.preference_count == 2
    assert report.enabled_preference_count == 2
    assert report.total_enabled_weight == 10
    assert [group.group_id for group in report.groups] == ["nature", "risk"]
    assert [group.total_weight for group in report.groups] == [8, 2]

    forest = report.metrics[0]
    assert forest.metric_id == "forest.distance_km"
    assert forest.known_pct == 100.0
    assert forest.full_quality_pct_known == 25.0
    assert forest.transition_pct_known == 25.0
    assert forest.zero_quality_pct_known == 50.0
    assert forest.saturation_warning is False

    landfill = report.metrics[1]
    assert landfill.known_pct == 50.0
    assert landfill.full_quality_pct_known == 50.0
    assert landfill.transition_pct_known == 50.0
    assert landfill.zero_quality_pct_known == 0.0

    scores = report.scores
    assert scores.scored_settlements == 4
    assert scores.scored_pct == 100.0
    assert scores.coverage_p10 is not None
    assert scores.coverage_p90 == 100.0


def test_report_flags_extreme_endpoint_saturation(tmp_path: Path) -> None:
    create_dataset(tmp_path)
    connection = sqlite3.connect(tmp_path / "georisk.sqlite")
    connection.execute("UPDATE settlement_metric SET value = 0.0 WHERE metric_id = 'forest.distance_km'")
    connection.commit()
    connection.close()

    report = build_report(tmp_path, saturation_warning_pct=90.0)

    assert report.metrics[0].full_quality_pct_known == 100.0
    assert report.metrics[0].saturation_warning is True
    markdown = render_markdown(report, saturation_warning_pct=90.0)
    assert "Calibration warnings" in markdown
    assert "forest.distance_km" in markdown


def test_disabled_defaults_are_calibrated_but_excluded_from_weight_and_score(tmp_path: Path) -> None:
    create_dataset(tmp_path)
    connection = sqlite3.connect(tmp_path / "georisk.sqlite")
    connection.execute(
        "UPDATE metric_preference_default SET default_enabled = 0 WHERE metric_id = ?",
        ("landfill.distance_km",),
    )
    connection.commit()
    connection.close()

    report = build_report(tmp_path, saturation_warning_pct=90.0)

    assert report.preference_count == 2
    assert report.enabled_preference_count == 1
    assert report.total_enabled_weight == 8
    assert report.metrics[1].enabled is False
    assert [group.group_id for group in report.groups] == ["nature"]
    assert report.scores.coverage_p90 == 100.0
