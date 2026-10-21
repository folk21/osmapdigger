"""Deterministic contract tests for the Git-tracked specification checkpoint."""

from __future__ import annotations

import copy
import importlib.util
import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[1]
VALIDATOR_PATH = ROOT / "tools/specs/validate_state.py"
MODULE_SPEC = importlib.util.spec_from_file_location("osmapdigger_spec_state", VALIDATOR_PATH)
assert MODULE_SPEC is not None and MODULE_SPEC.loader is not None
STATE_VALIDATOR = importlib.util.module_from_spec(MODULE_SPEC)
MODULE_SPEC.loader.exec_module(STATE_VALIDATOR)


def _checkpoint(tmp_path: Path) -> tuple[Path, dict]:
    spec_path = tmp_path / "docs/specs/active/subspecs/development-agent-pipeline.md"
    spec_path.parent.mkdir(parents=True)
    spec_path.write_text("**A1 — Foundation**\n### A2 — Execution\n", encoding="utf-8")
    state_path = tmp_path / "docs/specs/STATE.json"
    checkpoint = {
        "schemaVersion": 1,
        "mode": "SINGLE",
        "selectedWorkstreamId": "contributor-agent-pipeline",
        "workstreams": [{
            "id": "contributor-agent-pipeline",
            "specPath": "docs/specs/active/subspecs/development-agent-pipeline.md",
            "stageId": "A1",
            "phase": "DESIGN",
            "runId": None,
            "developerDecision": "UNRECORDED",
        }],
    }
    return state_path, checkpoint


def _validate(path: Path, payload: dict) -> dict:
    path.write_text(json.dumps(payload), encoding="utf-8")
    return STATE_VALIDATOR.validate_state(path, path.parents[2])


def test_checked_in_state_and_schema_are_consistent() -> None:
    assert STATE_VALIDATOR.validate_state()["workstreams"][0]["stageId"] == "A1"
    schema = json.loads((ROOT / "docs/specs/state.schema.json").read_text(encoding="utf-8"))
    assert set(schema["properties"]) == set(schema["required"])
    item = schema["properties"]["workstreams"]["items"]
    assert set(item["properties"]) == set(item["required"])
    assert set(item["properties"]["phase"]["enum"]) == STATE_VALIDATOR.PHASES
    assert schema["properties"]["mode"]["const"] == "SINGLE"


def test_valid_workstream_and_optional_bound_run_id(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    assert _validate(path, state)["workstreams"][0]["phase"] == "DESIGN"
    state["workstreams"][0].update(phase="READY_FOR_REVIEW", runId="stage.A1-2026_01")
    assert _validate(path, state)["workstreams"][0]["runId"] == "stage.A1-2026_01"
    state["workstreams"][0]["phase"] = "AWAITING_DEVELOPER_DECISION"
    _validate(path, state)


@pytest.mark.parametrize("field,value", [
    ("schemaVersion", True), ("schemaVersion", "1"), ("mode", "MULTI"),
    ("selectedWorkstreamId", "../escape"), ("selectedWorkstreamId", "other"),
])
def test_invalid_root_values_fail(tmp_path: Path, field: str, value: object) -> None:
    path, state = _checkpoint(tmp_path)
    state[field] = value
    with pytest.raises(ValueError):
        _validate(path, state)


def test_unknown_duplicate_and_missing_fields_fail(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    state["unrecognized"] = "bypass"
    with pytest.raises(ValueError, match="fields"):
        _validate(path, state)
    del state["unrecognized"]
    del state["workstreams"][0]["runId"]
    with pytest.raises(ValueError, match="fields"):
        _validate(path, state)
    path.write_text('{"schemaVersion": 1, "schemaVersion": 1}', encoding="utf-8")
    with pytest.raises(ValueError, match="Duplicate key"):
        STATE_VALIDATOR.validate_state(path, tmp_path)


@pytest.mark.parametrize("spec_path", [
    "../escaped.md",
    "docs/specs/planned/subspecs/later.md",
    "docs/specs/archive/subspecs/archived.md",
    "docs/specs/active/subspecs/missing.md",
])
def test_foreign_missing_and_non_active_spec_paths_fail(tmp_path: Path, spec_path: str) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["specPath"] = spec_path
    with pytest.raises(ValueError):
        _validate(path, state)


def test_stage_reference_must_be_an_actual_declared_stage(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["stageId"] = "A10"
    with pytest.raises(ValueError, match="not found"):
        _validate(path, state)
    state["workstreams"][0]["stageId"] = "A2"
    _validate(path, state)


def test_symlink_state_or_spec_is_rejected(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    _validate(path, state)
    other = tmp_path / "elsewhere.md"
    other.write_text("**A1 — Not an active spec**\n", encoding="utf-8")
    spec_path = tmp_path / state["workstreams"][0]["specPath"]
    spec_path.unlink()
    spec_path.symlink_to(other)
    with pytest.raises(ValueError, match="unsafe"):
        _validate(path, state)
    spec_path.unlink()
    spec_path.write_text("**A1 — Foundation**\n", encoding="utf-8")
    path.unlink()
    actual_state = tmp_path / "checkpoint.json"
    actual_state.write_text(json.dumps(state), encoding="utf-8")
    path.symlink_to(actual_state)
    with pytest.raises(ValueError, match="regular file"):
        STATE_VALIDATOR.validate_state(path, tmp_path)


@pytest.mark.parametrize("phase", ["RUNNING", "ACCEPTED", "unexpected"])
def test_live_and_accepted_are_not_checkpoint_phases(tmp_path: Path, phase: str) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["phase"] = phase
    with pytest.raises(ValueError, match="phase"):
        _validate(path, state)


@pytest.mark.parametrize("value", ["ACCEPTED", "APPROVED", "PASS"])
def test_checkpoint_cannot_assert_developer_acceptance(tmp_path: Path, value: str) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["developerDecision"] = value
    with pytest.raises(ValueError, match="developerDecision"):
        _validate(path, state)


def test_rejection_requires_blocked_or_paused(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["developerDecision"] = "REJECTED"
    with pytest.raises(ValueError, match="REJECTED"):
        _validate(path, state)
    state["workstreams"][0]["phase"] = "BLOCKED"
    _validate(path, state)


@pytest.mark.parametrize("phase", ["READY_FOR_REVIEW", "AWAITING_DEVELOPER_DECISION"])
def test_review_phase_requires_bound_run_id(tmp_path: Path, phase: str) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["phase"] = phase
    with pytest.raises(ValueError, match="requires runId"):
        _validate(path, state)


@pytest.mark.parametrize("run_id", ["", "..", "../outside", "bad/run", "x" * 129, True, 1])
def test_invalid_run_ids_fail(tmp_path: Path, run_id: object) -> None:
    path, state = _checkpoint(tmp_path)
    state["workstreams"][0]["runId"] = run_id
    with pytest.raises(ValueError, match="runId"):
        _validate(path, state)


def test_single_mode_rejects_concurrent_progress(tmp_path: Path) -> None:
    path, state = _checkpoint(tmp_path)
    second = tmp_path / "docs/specs/active/subspecs/other.md"
    second.write_text("## A2 — Second stage\n", encoding="utf-8")
    other = copy.deepcopy(state["workstreams"][0])
    other.update(id="other-workstream", specPath="docs/specs/active/subspecs/other.md", stageId="A2")
    state["workstreams"].append(other)
    with pytest.raises(ValueError, match="SINGLE mode"):
        _validate(path, state)
    other["phase"] = "PAUSED"
    _validate(path, state)
    state["workstreams"].append(copy.deepcopy(other))
    with pytest.raises(ValueError, match="duplicate"):
        _validate(path, state)


def test_missing_checkpoint_fails(tmp_path: Path) -> None:
    path, _ = _checkpoint(tmp_path)
    with pytest.raises(ValueError, match="existing regular file"):
        STATE_VALIDATOR.validate_state(path, tmp_path)
