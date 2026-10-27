"""Independent structural regression tests for specification archival."""
from __future__ import annotations

from pathlib import Path

import pytest

from tools.specs.validate_lifecycle import inspect_lifecycle

ROOT = Path(__file__).resolve().parents[1]


def _write_spec(root: Path, lifecycle: str, name: str, status: str, body: str) -> Path:
    path = root / "docs/specs" / lifecycle / "subspecs" / f"{name}.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        "---\ntype: Specification\ndocument_role: subspec\n"
        f"spec_status: {status}\n---\n# Example\n\n{body}",
        encoding="utf-8",
    )
    return path


def test_real_repository_has_consistent_lifecycle_structure() -> None:
    records = inspect_lifecycle(ROOT)
    assert records
    assert any(item.status == "archived" for item in records)
    assert any(item.status == "verification-pending" for item in records)


def test_accepted_archival_moves_are_allowed(tmp_path: Path) -> None:
    _write_spec(tmp_path, "archive", "completed", "archived", "## Status\n\nAccepted and archived.\n")
    records = inspect_lifecycle(tmp_path)
    assert [(item.path.name, item.status) for item in records] == [("completed.md", "archived")]


def test_moving_pending_spec_to_archive_without_acceptance_is_rejected(tmp_path: Path) -> None:
    _write_spec(tmp_path, "archive", "unfinished", "verification-pending", "## Remaining acceptance\n\n1. Device check\n")
    with pytest.raises(ValueError, match="Incorrect lifecycle metadata"):
        inspect_lifecycle(tmp_path)


def test_archival_label_without_acceptance_statement_is_rejected(tmp_path: Path) -> None:
    _write_spec(tmp_path, "archive", "unfinished", "archived", "## Status\n\nImplementation complete.\n")
    with pytest.raises(ValueError, match="lacks recorded acceptance"):
        inspect_lifecycle(tmp_path)


def test_duplicate_spec_in_active_and_archive_is_rejected(tmp_path: Path) -> None:
    _write_spec(tmp_path, "active", "duplicate", "active", "## Status\n\nActive.\n")
    _write_spec(tmp_path, "archive", "duplicate", "archived", "## Status\n\nAccepted.\n")
    with pytest.raises(ValueError, match="Duplicate specification"):
        inspect_lifecycle(tmp_path)


def test_orphaned_implementation_plan_is_rejected(tmp_path: Path) -> None:
    folder = tmp_path / "docs/specs/active/subspecs"
    folder.mkdir(parents=True)
    (folder / "missing-implementation-plan.md").write_text("---\ntype: Implementation Guide\n---\n", encoding="utf-8")
    with pytest.raises(ValueError, match="Orphaned implementation plan"):
        inspect_lifecycle(tmp_path)


def test_pending_spec_must_preserve_specific_acceptance_items(tmp_path: Path) -> None:
    _write_spec(tmp_path, "active", "pending", "verification-pending", "## Status\n\nPending.\n")
    with pytest.raises(ValueError, match="lacks actionable acceptance"):
        inspect_lifecycle(tmp_path)


def test_companion_plan_follows_archived_spec(tmp_path: Path) -> None:
    spec = _write_spec(tmp_path, "archive", "completed", "archived", "## Status\n\nAccepted.\n")
    plan = spec.with_name("completed-implementation-plan.md")
    plan.write_text("---\ntype: Implementation Guide\n---\n", encoding="utf-8")
    assert len(inspect_lifecycle(tmp_path)) == 1
    plan.write_text("---\ntype: Implementation Guide\nspec_status: archived\n---\n", encoding="utf-8")
    with pytest.raises(ValueError, match="independent lifecycle"):
        inspect_lifecycle(tmp_path)


def test_checkpoint_cannot_reference_archived_spec(tmp_path: Path) -> None:
    import json

    _write_spec(tmp_path, "archive", "completed", "archived", "## Status\n\nAccepted.\n")
    path = tmp_path / "docs/specs/STATE.json"
    path.write_text(json.dumps({"workstreams": [{"specPath": "docs/specs/archive/subspecs/completed.md"}]}), encoding="utf-8")
    with pytest.raises(ValueError, match="non-active spec"):
        inspect_lifecycle(tmp_path)
