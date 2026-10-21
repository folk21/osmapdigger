from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
SPECS = DOCS / "specs"
ACTIVE = SPECS / "active"
PLANNED = SPECS / "planned"
FEATURE_CATALOG = DOCS / "FEATURES.md"
ROADMAP = DOCS / "ROADMAP.md"

FEATURE_ID_RE = re.compile(r"`([A-Z][A-Z0-9_]*(?:\.[A-Z0-9_]+)+)`")
CATALOG_LINE_RE = re.compile(r"^- `([A-Z][A-Z0-9_]*(?:\.[A-Z0-9_]+)+)`\s+—")


def _frontmatter(path: Path) -> dict[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    assert lines and lines[0] == "---", f"Missing YAML frontmatter start: {path}"
    try:
        end = lines.index("---", 1)
    except ValueError as exc:
        raise AssertionError(f"Missing YAML frontmatter end: {path}") from exc

    result: dict[str, str] = {}
    for line in lines[1:end]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        key, separator, value = line.partition(":")
        assert separator, f"Unsupported frontmatter line in {path}: {line!r}"
        result[key.strip()] = value.strip()
    return result


def _active_specs() -> list[Path]:
    return sorted(path for path in ACTIVE.rglob("*.md") if not path.name.endswith("-implementation-plan.md"))


def _planned_specs() -> list[Path]:
    return sorted(path for path in PLANNED.rglob("*.md") if not path.name.endswith("-implementation-plan.md")) if PLANNED.exists() else []


def _current_specs() -> list[Path]:
    return _active_specs() + _planned_specs()


def _catalog_feature_ids() -> set[str]:
    result = {
        match.group(1)
        for line in FEATURE_CATALOG.read_text(encoding="utf-8").splitlines()
        if (match := CATALOG_LINE_RE.match(line))
    }
    assert result, "Feature catalog unexpectedly contains no feature IDs"
    return result


def test_active_spec_metadata_and_navigation_are_consistent() -> None:
    active_specs = _active_specs()
    assert active_specs

    umbrella_specs = []
    for path in active_specs:
        metadata = _frontmatter(path)
        body = path.read_text(encoding="utf-8")

        assert metadata.get("type") == "Specification", f"Unexpected type in {path}"
        assert metadata.get("document_role") in {"umbrella", "subspec"}, f"Missing role in {path}"
        assert metadata.get("spec_status") in {"active", "verification-pending"}, f"Unexpected status in {path}"
        for heading in ("## Status", "## Feature scope", "## Requirement map", "## Validation"):
            assert heading in body, f"Missing {heading!r} in {path}"

        if metadata["document_role"] == "umbrella":
            umbrella_specs.append(path)
            current_focus = metadata.get("current_focus")
            assert current_focus, f"Umbrella spec has no current_focus: {path}"
            assert (path.parent / current_focus).is_file(), f"current_focus does not exist: {current_focus}"
        else:
            parent = metadata.get("parent")
            assert parent, f"Sub-spec has no parent: {path}"
            assert (path.parent / parent).resolve().is_file(), f"Sub-spec parent does not exist: {path} -> {parent}"

    assert len(umbrella_specs) == 1, f"Expected one active umbrella spec, found {len(umbrella_specs)}"


def test_planned_specs_are_non_active_and_navigable() -> None:
    for path in _planned_specs():
        metadata = _frontmatter(path)
        body = path.read_text(encoding="utf-8")

        assert metadata.get("type") == "Specification", f"Unexpected type in {path}"
        assert metadata.get("document_role") == "subspec", f"Planned spec must be a sub-spec: {path}"
        assert metadata.get("spec_status") == "planned", f"Unexpected planned-spec status in {path}"
        parent = metadata.get("parent")
        assert parent, f"Planned sub-spec has no parent: {path}"
        assert (path.parent / parent).resolve().is_file(), f"Planned sub-spec parent does not exist: {path} -> {parent}"
        for heading in ("## Status", "## Feature scope", "## Requirement map", "## Validation"):
            assert heading in body, f"Missing {heading!r} in {path}"

        assert "current_focus" not in metadata, f"Planned sub-spec must not become current_focus implicitly: {path}"


def test_current_specs_use_only_cataloged_feature_ids() -> None:
    catalog = _catalog_feature_ids()
    for path in _current_specs():
        referenced = set(FEATURE_ID_RE.findall(path.read_text(encoding="utf-8")))
        unknown = sorted(referenced - catalog)
        assert not unknown, f"Unknown feature IDs in {path}: {unknown}"


def test_verification_pending_specs_track_acceptance_not_implementation_history() -> None:
    for path in _active_specs():
        metadata = _frontmatter(path)
        body = path.read_text(encoding="utf-8")

        assert "**Implemented:" not in body, f"Active spec contains implementation diary entries: {path}"
        assert "Implemented in this increment" not in body, f"Active spec contains implementation diary entries: {path}"

        if metadata.get("spec_status") == "verification-pending":
            assert "## Remaining acceptance" in body, f"Verification-pending spec lacks remaining acceptance: {path}"
            assert "## Implementation tasks" not in body, f"Verification-pending spec keeps completed implementation tasks: {path}"


def test_roadmap_uses_only_cataloged_feature_ids() -> None:
    catalog = _catalog_feature_ids()
    referenced = set(FEATURE_ID_RE.findall(ROADMAP.read_text(encoding="utf-8")))
    unknown = sorted(referenced - catalog)
    assert not unknown, f"Unknown feature IDs in {ROADMAP}: {unknown}"


def test_feature_oriented_acceptance_navigation_is_present() -> None:
    roadmap = ROADMAP.read_text(encoding="utf-8")
    umbrella = (ACTIVE / "spec-initial-functional-product.md").read_text(encoding="utf-8")

    for heading in ("## Current position", "## P0 — initial functional product acceptance", "### Acceptance map", "### P0 closure order"):
        assert heading in roadmap, f"Roadmap lacks feature-oriented acceptance navigation: {heading}"

    assert "## Acceptance map" in umbrella, "Umbrella spec lacks a feature-oriented acceptance map"
    assert "## Remaining acceptance" in umbrella, "Umbrella spec lacks closure-oriented remaining acceptance"


def test_staged_contributor_spec_has_advisory_companion() -> None:
    spec = ACTIVE / "subspecs/development-agent-pipeline.md"
    plan = spec.with_name("development-agent-pipeline-implementation-plan.md")
    assert spec.is_file() and plan.is_file()
    spec_content = spec.read_text(encoding="utf-8")
    plan_content = plan.read_text(encoding="utf-8")
    assert "spec_status: active" in spec_content
    assert "development-agent-pipeline-implementation-plan.md" in spec_content
    assert "type: Implementation Guide" in plan_content
    assert "document_role: subspec" not in plan_content
    assert "spec_status:" not in plan_content
    assert "development-agent-pipeline.md" in plan_content
    for stage in ("A1", "A2", "A3", "A4", "A5"):
        assert stage in spec_content and stage in plan_content


def test_spec_processing_checkpoint_is_documented_and_not_an_acceptance_receipt() -> None:
    index = (ROOT / "docs/specs/README.md").read_text(encoding="utf-8")
    contract = (ACTIVE / "subspecs/development-agent-pipeline.md").read_text(encoding="utf-8")
    assert (ROOT / "docs/specs/STATE.json").is_file()
    assert (ROOT / "docs/specs/state.schema.json").is_file()
    assert (ROOT / "tools/specs/validate_state.py").is_file()
    assert "python3 tools/specs/validate_state.py" in index
    assert "RUNNING" in index and "ACCEPTED" in index
    assert "**AP-R15:**" in contract and "existing `docs/specs/STATE.json`" in contract
    assert "source-bound" in index and "developer" in index
