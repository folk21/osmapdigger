#!/usr/bin/env python3
"""Validate specification lifecycle structure without granting acceptance."""
from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUBSPEC_ROOTS = {
    "planned": "planned",
    "active": "active",
    "archive": "archived",
}
PLAN_SUFFIX = "-implementation-plan.md"


@dataclass(frozen=True)
class SpecRecord:
    path: Path
    status: str
    remaining_acceptance: bool


def _frontmatter(content: str, path: Path) -> dict[str, str]:
    lines = content.splitlines()
    if not lines or lines[0] != "---":
        raise ValueError(f"Missing frontmatter: {path}")
    try:
        end = lines.index("---", 1)
    except ValueError as exc:
        raise ValueError(f"Unterminated frontmatter: {path}") from exc
    fields: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip():
            continue
        key, sep, value = line.partition(":")
        if not sep or key.strip() in fields:
            raise ValueError(f"Invalid/duplicate frontmatter field: {path}")
        fields[key.strip()] = value.strip()
    return fields


def _regular_file(path: Path) -> bool:
    return path.is_file() and not path.is_symlink()


def inspect_lifecycle(root: Path = ROOT) -> list[SpecRecord]:
    """Return current sub-spec statuses; raise on unsafe or inconsistent moves.

    This is a structural check only. It cannot establish device/manual acceptance,
    developer approval, or that evidence cited by a specification actually exists.
    """
    specs_root = root / "docs/specs"
    records: list[SpecRecord] = []
    names: set[str] = set()
    for directory, target_status in SUBSPEC_ROOTS.items():
        location = specs_root / directory / "subspecs"
        if not location.exists():
            continue
        if not location.is_dir() or location.is_symlink():
            raise ValueError(f"Invalid specification directory: {location}")
        markdown = sorted(location.glob("*.md"))
        for path in markdown:
            if not _regular_file(path):
                raise ValueError(f"Unsafe specification file: {path}")
            if path.name.endswith(PLAN_SUFFIX):
                owner = location / (path.name[: -len(PLAN_SUFFIX)] + ".md")
                if not _regular_file(owner):
                    raise ValueError(f"Orphaned implementation plan: {path}")
                plan_fields = _frontmatter(path.read_text(encoding="utf-8"), path)
                if "spec_status" in plan_fields or "document_role" in plan_fields:
                    raise ValueError(f"Implementation plan has independent lifecycle: {path}")
                continue
            if path.name in names:
                raise ValueError(f"Duplicate specification across lifecycle directories: {path.name}")
            names.add(path.name)
            content = path.read_text(encoding="utf-8")
            fields = _frontmatter(content, path)
            status = fields.get("spec_status", "")
            allowed = {"active", "verification-pending"} if target_status == "active" else {target_status}
            if fields.get("type") != "Specification" or fields.get("document_role") != "subspec" or status not in allowed:
                raise ValueError(f"Incorrect lifecycle metadata: {path} ({status})")
            if status == "verification-pending":
                match = re.search(r"^## Remaining acceptance\s*$\n(.*?)(?=^## |\Z)", content, flags=re.M | re.S)
                if not match or not re.search(r"(?m)^\d+\.\s+\S", match.group(1)):
                    raise ValueError(f"Pending spec lacks actionable acceptance items: {path}")
            if status == "archived":
                status_section = re.search(r"^## Status\s*$\n(.*?)(?=^## |\Z)", content, flags=re.M | re.S)
                if not status_section or not re.search(r"\baccepted\b", status_section.group(1), re.I):
                    raise ValueError(f"Archived spec lacks recorded acceptance statement: {path}")
            companion = location / (path.stem + PLAN_SUFFIX)
            if companion.exists() and not _regular_file(companion):
                raise ValueError(f"Unsafe companion plan: {companion}")
            records.append(SpecRecord(path.relative_to(root), status, status == "verification-pending"))
    state = specs_root / "STATE.json"
    if _regular_file(state):
        import json
        current = json.loads(state.read_text(encoding="utf-8"))
        for workstream in current.get("workstreams", []):
            path = root / workstream["specPath"]
            if not _regular_file(path) or not path.resolve().is_relative_to((specs_root / "active/subspecs").resolve()):
                raise ValueError(f"Checkpoint references a non-active spec: {workstream['specPath']}")
    return records


def main() -> int:
    try:
        records = inspect_lifecycle()
    except (ValueError, OSError, UnicodeError) as exc:
        print(f"Spec lifecycle validation FAILED: {exc}", file=sys.stderr)
        return 1
    counts = {status: sum(item.status == status for item in records) for status in ("planned", "active", "verification-pending", "archived")}
    print("Spec lifecycle structure PASS (not an acceptance decision)")
    print(" ".join(f"{status}={count}" for status, count in counts.items()))
    for item in records:
        if item.remaining_acceptance:
            print(f"Pending acceptance: {item.path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
