from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "scripts" / "analysis_performance_report.py"
SPEC = importlib.util.spec_from_file_location("analysis_performance_report", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)

AnalysisPerformanceSample = MODULE.AnalysisPerformanceSample
parse_samples = MODULE.parse_samples
render_markdown = MODULE.render_markdown
select_representative_sample = MODULE.select_representative_sample


def performance_line(
    *,
    timestamp: str = "2026-08-29T09:00:00+04:00",
    candidates: int = 2500,
    exact_eligible: int | None = None,
    scoring_metrics: int = 6,
    results: int = 100,
    retrieval_ms: float = 42.125,
    shared_ms: float = 10.5,
    total_ms: float = 52.625,
) -> str:
    if exact_eligible is None:
        exact_eligible = min(candidates, 1200)
    return (
        f"{timestamp} INFO [com.permieware.osmapdigger.desktop] "
        f"[analysis.performance] candidates={candidates} "
        f"exactEligible={exact_eligible} scoringMetrics={scoring_metrics} "
        f"results={results} retrievalMs={retrieval_ms:.3f} "
        f"sharedMs={shared_ms:.3f} totalMs={total_ms:.3f}"
    )


def test_parse_samples_ignores_unrelated_lines() -> None:
    samples = parse_samples(
        [
            "2026-08-29T09:00:00+04:00 INFO [logger] [startup] ready",
            performance_line(),
        ]
    )

    assert samples == [
        AnalysisPerformanceSample(
            timestamp="2026-08-29T09:00:00+04:00",
            candidates=2500,
            exact_eligible=1200,
            scoring_metrics=6,
            results=100,
            retrieval_ms=42.125,
            shared_ms=10.5,
            total_ms=52.625,
        )
    ]


def test_select_representative_sample_uses_latest_qualifying_run() -> None:
    samples = parse_samples(
        [
            performance_line(timestamp="2026-08-29T08:00:00+04:00", candidates=5000),
            performance_line(
                timestamp="2026-08-29T08:10:00+04:00",
                candidates=9000,
                scoring_metrics=0,
            ),
            performance_line(timestamp="2026-08-29T08:20:00+04:00", candidates=700),
            performance_line(timestamp="2026-08-29T08:30:00+04:00", candidates=3000),
        ]
    )

    selected = select_representative_sample(samples, min_candidates=1000)

    assert selected is not None
    assert selected.timestamp == "2026-08-29T08:30:00+04:00"
    assert selected.candidates == 3000


def test_select_representative_sample_falls_back_to_latest_unscored_run() -> None:
    samples = parse_samples(
        [
            performance_line(candidates=999),
            performance_line(candidates=5000, scoring_metrics=0),
        ]
    )

    selected = select_representative_sample(samples, min_candidates=1000)

    assert selected is not None
    assert selected.candidates == 5000
    assert selected.scoring_metrics == 0


def test_select_representative_sample_strict_mode_rejects_unscored_runs() -> None:
    samples = parse_samples(
        [
            performance_line(candidates=999),
            performance_line(candidates=5000, scoring_metrics=0),
        ]
    )

    assert (
        select_representative_sample(
            samples,
            min_candidates=1000,
            require_scoring=True,
        )
        is None
    )


def test_render_markdown_contains_acceptance_fields() -> None:
    sample = parse_samples([performance_line()])[0]

    report = render_markdown(sample, min_candidates=1000)

    assert "Candidates: `2500`" in report
    assert "Enabled scoring metrics: `6`" in report
    assert "Batch retrieval: `42.125 ms`" in report
    assert "End-to-end analysis: `52.625 ms`" in report


def test_render_markdown_marks_filter_only_sample_as_pending_scoring_acceptance() -> None:
    sample = parse_samples([performance_line(scoring_metrics=0)])[0]

    report = render_markdown(sample, min_candidates=1000)

    assert "Mode: `filter-only`" in report
    assert "Full ranked-scoring acceptance is still pending" in report
    assert "make analysis-acceptance-strict" in report
