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


def validate_package(
    package_dir: Path,
    *,
    require_map: bool = False,
    require_archive: bool = False,
) -> dict:
    """Validate package structure and SQLite integrity before publication/use.

    Normal build-time validation accepts data-only packages. Acceptance/release callers
    may additionally require a non-empty declared PMTiles artifact and the published
    portable archive. This is structural/integrity validation rather than cryptographic
    trust validation; ZIP traversal protection remains an installer responsibility.
    """
    missing = sorted(name for name in REQUIRED_FILES if not (package_dir / name).exists())
    if missing:
        raise RuntimeError("Missing package files: " + ", ".join(missing))

    metadata = json.loads((package_dir / "metadata.json").read_text(encoding="utf-8"))
    dataset_id = metadata["datasetId"]
    map_name = _declared_map_name(metadata)
    has_map = _validate_declared_map(package_dir, map_name, require_map=require_map)
    if map_name is None and not require_map:
        has_map = any(package_dir.glob("*.pmtiles"))

    database_path = package_dir / "georisk.sqlite"
    connection = sqlite3.connect(f"file:{database_path}?mode=ro", uri=True)
    try:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity failed: {integrity}")
        settlements = connection.execute("SELECT COUNT(*) FROM settlement").fetchone()[0]
        metrics = connection.execute("SELECT COUNT(*) FROM metric_definition").fetchone()[0]
        has_preference_defaults = (
            connection.execute(
                "SELECT 1 FROM sqlite_master "
                "WHERE type = 'table' AND name = 'metric_preference_default'"
            ).fetchone()
            is not None
        )
        preference_defaults = (
            connection.execute("SELECT COUNT(*) FROM metric_preference_default").fetchone()[0]
            if has_preference_defaults
            else 0
        )
    finally:
        connection.close()

    if require_archive:
        _validate_portable_archive(package_dir, dataset_id, map_name if require_map else None)

    return {
        "datasetId": dataset_id,
        "settlements": int(settlements),
        "metrics": int(metrics),
        "preferenceDefaults": int(preference_defaults),
        "hasMap": has_map,
        "hasArchive": (package_dir / f"{dataset_id}.omd.zip").is_file(),
    }


def _declared_map_name(metadata: dict) -> str | None:
    artifacts = metadata.get("artifacts")
    if not isinstance(artifacts, dict):
        return None
    map_name = artifacts.get("map")
    if map_name is None:
        return None
    if not isinstance(map_name, str) or not map_name or Path(map_name).name != map_name:
        raise RuntimeError("Invalid metadata map artifact name")
    return map_name


def _validate_declared_map(package_dir: Path, map_name: str | None, *, require_map: bool) -> bool:
    if map_name is None:
        if require_map:
            raise RuntimeError("Full-package validation requires a declared PMTiles map artifact")
        return False

    map_path = package_dir / map_name
    if not map_path.is_file():
        raise RuntimeError(f"Declared map artifact is missing: {map_name}")
    if map_path.stat().st_size <= 0:
        raise RuntimeError(f"Declared map artifact is empty: {map_name}")
    return True


def _validate_portable_archive(package_dir: Path, dataset_id: str, required_map_name: str | None) -> None:
    archive_path = package_dir / f"{dataset_id}.omd.zip"
    if not archive_path.is_file() or archive_path.stat().st_size <= 0:
        raise RuntimeError(f"Portable package archive is missing or empty: {archive_path.name}")

    required_entries = set(REQUIRED_FILES)
    if required_map_name is not None:
        required_entries.add(required_map_name)

    try:
        with zipfile.ZipFile(archive_path) as archive:
            entries = {info.filename: info for info in archive.infolist() if not info.is_dir()}
            missing_entries = sorted(required_entries - entries.keys())
            if missing_entries:
                raise RuntimeError(
                    "Portable package archive is missing entries: " + ", ".join(missing_entries)
                )
            if required_map_name is not None:
                archived_map = entries[required_map_name]
                source_map = package_dir / required_map_name
                if archived_map.file_size <= 0 or archived_map.file_size != source_map.stat().st_size:
                    raise RuntimeError(
                        f"Portable package archive map entry does not match published artifact: {required_map_name}"
                    )
            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise RuntimeError(f"Portable package archive contains a corrupt entry: {corrupt_entry}")
    except zipfile.BadZipFile as exc:
        raise RuntimeError(f"Portable package archive is invalid: {archive_path.name}") from exc


def utc_now() -> str:
    """Return an offset-aware UTC timestamp for package build metadata."""
    return datetime.now(timezone.utc).isoformat()
