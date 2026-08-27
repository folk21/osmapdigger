from pathlib import Path

from osmapdigger_geo.database import DatasetDatabaseWriter, validate_database
from osmapdigger_geo.models import MetricDefinition, SettlementNameRecord, SettlementRecord


ROOT = Path(__file__).resolve().parents[2]


def test_database_writer_round_trip(tmp_path):
    database = tmp_path / "georisk.sqlite"
    writer = DatasetDatabaseWriter(ROOT / "geo-format/schema.sql")

    metric = MetricDefinition(
        metric_id="forest.distance_km",
        category_id="forest",
        group_id="Nature",
        title="Distance to forest",
        description="Test",
        unit="km",
        measure_type="distance",
        preferred_direction="lower",
        default_enabled=True,
        sort_order=0,
    )
    settlement = SettlementRecord(
        settlement_id="node:1",
        osm_id=1,
        osm_type="node",
        name="Village",
        name_local="Village",
        name_en=None,
        place_type="village",
        population=10,
        latitude=42.5,
        longitude=1.5,
        names=(
            SettlementNameRecord(
                name="Village",
                normalized_name="village",
                language=None,
                kind="primary",
            ),
            SettlementNameRecord(
                name="Деревня",
                normalized_name="деревня",
                language="ru",
                kind="localized",
            ),
        ),
    )

    writer.write(
        database,
        {"format_version": "1", "dataset_id": "test", "display_name": "Test"},
        [metric],
        [settlement],
        {0: {"forest.distance_km": 0.5}},
    )

    assert validate_database(database) == {
        "settlements": 1,
        "settlementNames": 2,
        "metrics": 1,
        "values": 1,
    }

    import sqlite3

    connection = sqlite3.connect(database)
    try:
        aliases = connection.execute(
            "SELECT name, normalized_name, language, kind FROM settlement_name ORDER BY name"
        ).fetchall()
    finally:
        connection.close()
    assert aliases == [
        ("Village", "village", None, "primary"),
        ("Деревня", "деревня", "ru", "localized"),
    ]


def test_validate_database_accepts_legacy_v1_without_settlement_name(tmp_path):
    import sqlite3

    database = tmp_path / "legacy.sqlite"
    connection = sqlite3.connect(database)
    try:
        connection.executescript(
            """
            CREATE TABLE settlement(settlement_id TEXT PRIMARY KEY);
            CREATE TABLE metric_definition(metric_id TEXT PRIMARY KEY);
            CREATE TABLE settlement_metric(settlement_id TEXT, metric_id TEXT, value REAL);
            INSERT INTO settlement VALUES ('node:1');
            """
        )
        connection.commit()
    finally:
        connection.close()

    assert validate_database(database) == {
        "settlements": 1,
        "settlementNames": 0,
        "metrics": 0,
        "values": 0,
    }
