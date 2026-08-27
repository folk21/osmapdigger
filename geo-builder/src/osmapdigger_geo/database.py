"""SQLite publication and integrity validation for generated runtime datasets."""

from __future__ import annotations

import sqlite3
from pathlib import Path

from .models import MetricDefinition, MetricPreferenceDefault, SettlementRecord


class DatasetDatabaseWriter:
    """Write one complete runtime database from already calculated builder records.

    The writer owns no OSM/GIS logic. It executes the authoritative schema from
    ``geo-format/schema.sql`` so persisted semantics remain centralized outside Python
    implementation code.
    """

    def __init__(self, schema_path: Path) -> None:
        self.schema_path = schema_path

    def write(
        self,
        target: Path,
        metadata: dict[str, str],
        definitions: list[MetricDefinition],
        settlements: list[SettlementRecord],
        metrics_by_sid: dict[int, dict[str, float]],
        preference_defaults: list[MetricPreferenceDefault] | None = None,
    ) -> None:
        """Replace ``target`` with a new SQLite database populated in one transaction.

        ``metrics_by_sid`` uses the temporary sequential settlement index returned by
        extraction. The index is translated here to persisted ``settlement_id`` values.
        """
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            target.unlink()

        connection = sqlite3.connect(target)
        try:
            connection.executescript(self.schema_path.read_text(encoding="utf-8"))
            connection.executemany(
                "INSERT INTO dataset_metadata(key,value) VALUES (?,?)",
                sorted(metadata.items()),
            )
            connection.executemany(
                """
                INSERT INTO metric_definition(
                    metric_id, category_id, group_id, title, description, unit,
                    measure_type, preferred_direction, default_enabled, sort_order
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                [
                    (
                        definition.metric_id,
                        definition.category_id,
                        definition.group_id,
                        definition.title,
                        definition.description,
                        definition.unit,
                        definition.measure_type,
                        definition.preferred_direction,
                        1 if definition.default_enabled else 0,
                        definition.sort_order,
                    )
                    for definition in definitions
                ],
            )
            connection.executemany(
                """
                INSERT INTO metric_preference_default(
                    metric_id, direction, target_value, limit_value, weight, default_enabled
                ) VALUES (?,?,?,?,?,?)
                """,
                [
                    (
                        preference.metric_id,
                        preference.direction,
                        preference.target_value,
                        preference.limit_value,
                        preference.weight,
                        1 if preference.default_enabled else 0,
                    )
                    for preference in (preference_defaults or [])
                ],
            )
            connection.executemany(
                """
                INSERT INTO settlement(
                    settlement_id, osm_id, osm_type, name, name_local, name_en,
                    place_type, population, latitude, longitude
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                [
                    (
                        settlement.settlement_id,
                        settlement.osm_id,
                        settlement.osm_type,
                        settlement.name,
                        settlement.name_local,
                        settlement.name_en,
                        settlement.place_type,
                        settlement.population,
                        settlement.latitude,
                        settlement.longitude,
                    )
                    for settlement in settlements
                ],
            )

            connection.executemany(
                """
                INSERT INTO settlement_name(
                    settlement_id, name, normalized_name, language, kind
                ) VALUES (?,?,?,?,?)
                """,
                [
                    (
                        settlement.settlement_id,
                        name.name,
                        name.normalized_name,
                        name.language,
                        name.kind,
                    )
                    for settlement in settlements
                    for name in settlement.names
                ],
            )

            metric_rows: list[tuple[str, str, float]] = []
            for sid, values in metrics_by_sid.items():
                if sid >= len(settlements):
                    continue
                settlement_id = settlements[sid].settlement_id
                for metric_id, value in values.items():
                    metric_rows.append((settlement_id, metric_id, float(value)))

            connection.executemany(
                "INSERT INTO settlement_metric(settlement_id,metric_id,value) VALUES (?,?,?)",
                metric_rows,
            )
            connection.commit()
            connection.execute("ANALYZE")
            connection.execute("PRAGMA optimize")
        finally:
            connection.close()


def validate_database(path: Path) -> dict[str, int]:
    """Open a generated database read-only, verify integrity, and return key counts."""
    connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity check failed: {integrity}")

        table_names = {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            ).fetchall()
        }
        return {
            "settlements": int(connection.execute("SELECT COUNT(*) FROM settlement").fetchone()[0]),
            "settlementNames": (
                int(connection.execute("SELECT COUNT(*) FROM settlement_name").fetchone()[0])
                if "settlement_name" in table_names
                else 0
            ),
            "metrics": int(
                connection.execute("SELECT COUNT(*) FROM metric_definition").fetchone()[0]
            ),
            "preferenceDefaults": (
                int(
                    connection.execute(
                        "SELECT COUNT(*) FROM metric_preference_default"
                    ).fetchone()[0]
                )
                if "metric_preference_default" in table_names
                else 0
            ),
            "values": int(
                connection.execute("SELECT COUNT(*) FROM settlement_metric").fetchone()[0]
            ),
        }
    finally:
        connection.close()
