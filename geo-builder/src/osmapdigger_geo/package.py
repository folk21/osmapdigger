"""Generated package hashing, structural validation, and portable ZIP creation."""

from __future__ import annotations

import hashlib
import json
import sqlite3
import zipfile
from datetime import datetime, timezone
from pathlib import Path


REQUIRED_FILES = {"georisk.sqlite", "style.template.json", "metadata.json"}


def sha256(path: Path) -> str:
    """Return a streaming SHA-256 digest for source provenance metadata."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def create_package_zip(package_dir: Path, dataset_id: str) -> Path:
    """Create the portable runtime archive from already published package artifacts.

    Optional PMTiles is included only when present so data-only development packages
    remain valid and importable.
    """
    target = package_dir / f"{dataset_id}.omd.zip"
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in [
            "georisk.sqlite",
            f"{dataset_id}.pmtiles",
            "style.template.json",
            "metadata.json",
        ]:
            path = package_dir / name
            if path.exists():
                archive.write(path, arcname=name)
    return target


def validate_package(package_dir: Path) -> dict:
    """Validate required package structure and SQLite integrity before publication/use.

    This is structural/integrity validation rather than cryptographic trust validation.
    ZIP traversal protection is enforced by platform installers during extraction.
    """
    missing = sorted(name for name in REQUIRED_FILES if not (package_dir / name).exists())
    if missing:
        raise RuntimeError("Missing package files: " + ", ".join(missing))

    metadata = json.loads((package_dir / "metadata.json").read_text(encoding="utf-8"))
    database_path = package_dir / "georisk.sqlite"

    connection = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity failed: {integrity}")
        settlements = connection.execute("SELECT COUNT(*) FROM settlement").fetchone()[0]
        metrics = connection.execute("SELECT COUNT(*) FROM metric_definition").fetchone()[0]
    finally:
        connection.close()

    return {
        "datasetId": metadata["datasetId"],
        "settlements": int(settlements),
        "metrics": int(metrics),
        "hasMap": any(package_dir.glob("*.pmtiles")),
    }


def utc_now() -> str:
    """Return an offset-aware UTC timestamp for package build metadata."""
    return datetime.now(timezone.utc).isoformat()
