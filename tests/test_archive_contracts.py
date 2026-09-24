from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ARCHIVE_SCRIPT = ROOT / "archive.sh"


def test_shareable_full_archive_excludes_ide_and_root_cache_metadata() -> None:
    script = ARCHIVE_SCRIPT.read_text(encoding="utf-8")

    for excluded_path in (
        "osmapdigger/.idea/*",
        "osmapdigger/.vscode/*",
        "osmapdigger/.pytest_cache/*",
        "osmapdigger/.mypy_cache/*",
        "osmapdigger/.ruff_cache/*",
        "osmapdigger/__pycache__/*",
    ):
        assert f"-x '{excluded_path}'" in script, f"Missing archive exclusion: {excluded_path}"
