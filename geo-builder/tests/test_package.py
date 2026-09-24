import json
import sqlite3
from pathlib import Path

import pytest

from osmapdigger_geo.map_builder import write_style_template
from osmapdigger_geo.package import create_package_zip, validate_package


ROOT = Path(__file__).resolve().parents[2]


def _write_minimal_package(tmp_path: Path, *, map_name: str | None = None) -> None:
    db = tmp_path / "georisk.sqlite"
    connection = sqlite3.connect(db)
    try:
        connection.executescript((ROOT / "geo-format/schema.sql").read_text())
        connection.commit()
    finally:
        connection.close()

    write_style_template(tmp_path / "style.template.json")
    metadata = {"datasetId": "test"}
    if map_name is not None:
        metadata["artifacts"] = {
            "database": "georisk.sqlite",
            "map": map_name,
            "style": "style.template.json",
        }
    (tmp_path / "metadata.json").write_text(json.dumps(metadata), encoding="utf-8")


def test_package_validation_and_zip(tmp_path):
    _write_minimal_package(tmp_path)

    result = validate_package(tmp_path)
    assert result["datasetId"] == "test"
    assert result["preferenceDefaults"] == 0
    assert result["hasMap"] is False
    assert result["hasArchive"] is False

    archive = create_package_zip(tmp_path, "test")
    assert archive.exists()
    assert validate_package(tmp_path, require_archive=True)["hasArchive"] is True


def test_full_package_validation_requires_non_empty_declared_map_and_archive(tmp_path):
    _write_minimal_package(tmp_path, map_name="test.pmtiles")
    map_path = tmp_path / "test.pmtiles"
    map_path.write_bytes(b"pmtiles-fixture")
    create_package_zip(tmp_path, "test")

    result = validate_package(tmp_path, require_map=True, require_archive=True)

    assert result["hasMap"] is True
    assert result["hasArchive"] is True


def test_full_package_validation_rejects_empty_map(tmp_path):
    _write_minimal_package(tmp_path, map_name="test.pmtiles")
    (tmp_path / "test.pmtiles").write_bytes(b"")

    with pytest.raises(RuntimeError, match="map artifact is empty"):
        validate_package(tmp_path, require_map=True)


def test_full_package_validation_rejects_archive_missing_declared_map(tmp_path):
    _write_minimal_package(tmp_path, map_name="test.pmtiles")
    create_package_zip(tmp_path, "test")
    (tmp_path / "test.pmtiles").write_bytes(b"pmtiles-fixture")

    with pytest.raises(RuntimeError, match="archive is missing entries: test.pmtiles"):
        validate_package(tmp_path, require_map=True, require_archive=True)
