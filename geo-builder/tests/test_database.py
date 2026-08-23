from pathlib import Path

from osmapdigger_geo.database import DatasetDatabaseWriter, validate_database
from osmapdigger_geo.models import MetricDefinition, SettlementRecord


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
        "metrics": 1,
        "values": 1,
    }
