import json
import sqlite3
from pathlib import Path

from osmapdigger_geo.map_builder import write_style_template
from osmapdigger_geo.package import create_package_zip, validate_package


ROOT = Path(__file__).resolve().parents[2]


def test_package_validation_and_zip(tmp_path):
    db = tmp_path / "georisk.sqlite"
    connection = sqlite3.connect(db)
    try:
        connection.executescript((ROOT / "geo-format/schema.sql").read_text())
        connection.commit()
    finally:
        connection.close()

    write_style_template(tmp_path / "style.template.json")
    (tmp_path / "metadata.json").write_text(
        json.dumps({"datasetId": "test"}),
        encoding="utf-8",
    )

    result = validate_package(tmp_path)
    assert result["datasetId"] == "test"
    assert result["preferenceDefaults"] == 0

    archive = create_package_zip(tmp_path, "test")
    assert archive.exists()
