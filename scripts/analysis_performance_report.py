#!/usr/bin/env python3
"""Summarize Desktop ranked-analysis performance diagnostics for acceptance checks."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

PERFORMANCE_RE = re.compile(
    r"^(?P<timestamp>\S+)\s+\S+\s+\[[^]]+\]\s+\[analysis\.performance\]\s+"
    r"candidates=(?P<candidates>\d+)\s+"
    r"exactEligible=(?P<exact_eligible>\d+)\s+"
    r"scoringMetrics=(?P<scoring_metrics>\d+)\s+"
    r"results=(?P<results>\d+)\s+"
    r"retrievalMs=(?P<retrieval_ms>\d+(?:\.\d+)?)\s+"
    r"sharedMs=(?P<shared_ms>\d+(?:\.\d+)?)\s+"
    r"totalMs=(?P<total_ms>\d+(?:\.\d+)?)\s*$"
)


@dataclass(frozen=True)
class AnalysisPerformanceSample:
    """One completed current-generation ranked-analysis diagnostic sample."""

    timestamp: str
    candidates: int
    exact_eligible: int
    scoring_metrics: int
    results: int
    retrieval_ms: float
    shared_ms: float
    total_ms: float


def parse_samples(lines: Iterable[str]) -> list[AnalysisPerformanceSample]:
    """Parse valid ranked-analysis diagnostics while ignoring unrelated Desktop log lines."""

    samples: list[AnalysisPerformanceSample] = []
    for raw_line in lines:
        match = PERFORMANCE_RE.match(raw_line.strip())
        if match is None:
            continue
        values = match.groupdict()
        sample = AnalysisPerformanceSample(
            timestamp=values["timestamp"],
            candidates=int(values["candidates"]),
            exact_eligible=int(values["exact_eligible"]),
            scoring_metrics=int(values["scoring_metrics"]),
            results=int(values["results"]),
            retrieval_ms=float(values["retrieval_ms"]),
            shared_ms=float(values["shared_ms"]),
            total_ms=float(values["total_ms"]),
        )
        if sample.exact_eligible > sample.candidates:
            raise ValueError(
                "Invalid analysis.performance sample: exactEligible exceeds candidates"
            )
        samples.append(sample)
    return samples


def select_representative_sample(
    samples: Iterable[AnalysisPerformanceSample],
    *,
    min_candidates: int,
    require_scoring: bool = False,
) -> AnalysisPerformanceSample | None:
    """Return the latest representative sample, preferring scored runs when available."""

    if min_candidates < 1:
        raise ValueError("min_candidates must be positive")
    eligible = [sample for sample in samples if sample.candidates >= min_candidates]
    if not eligible:
        return None

    scored = [sample for sample in eligible if sample.scoring_metrics > 0]
    if scored:
        return scored[-1]
    if require_scoring:
        return None
    return eligible[-1]


def render_markdown(sample: AnalysisPerformanceSample, *, min_candidates: int) -> str:
    """Render a compact copy-ready acceptance record."""

    mode = "ranked-scoring" if sample.scoring_metrics > 0 else "filter-only"
    lines = [
            "### Ranked-analysis performance acceptance sample",
            "",
            f"- Mode: `{mode}`",
            f"- Timestamp: `{sample.timestamp}`",
            f"- Candidate threshold: `>= {min_candidates}`",
            f"- Candidates: `{sample.candidates}`",
            f"- Exact-radius eligible: `{sample.exact_eligible}`",
            f"- Enabled scoring metrics: `{sample.scoring_metrics}`",
            f"- Visible results: `{sample.results}`",
            f"- Batch retrieval: `{sample.retrieval_ms:.3f} ms`",
            f"- Shared radius/scoring/sort: `{sample.shared_ms:.3f} ms`",
            f"- End-to-end analysis: `{sample.total_ms:.3f} ms`",
        ]
    if sample.scoring_metrics == 0:
        lines.extend(
            [
                "",
                "> Note: this is a representative filter/radius run with no enabled scoring metrics. ",
                "> Full ranked-scoring acceptance is still pending; rebuild/load a dataset with ",
                "> preference defaults or enable at least one Preference, rerun analysis, then use ",
                "> `make analysis-acceptance-strict`.",
            ]
        )
    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Read Desktop analysis.performance diagnostics and emit the latest "
            "representative country-scale acceptance sample."
        )
    )
    parser.add_argument(
        "--log",
        type=Path,
        required=True,
        help="Path to the Desktop diagnostics log.",
    )
    parser.add_argument(
        "--min-candidates",
        type=int,
        default=1000,
        help="Minimum batch candidate count accepted as representative (default: 1000).",
    )
    parser.add_argument(
        "--require-scoring",
        action="store_true",
        help="Fail unless the representative sample has at least one scoring metric.",
    )
    parser.add_argument(
        "--format",
        choices=("markdown", "json"),
        default="markdown",
        help="Output format (default: markdown).",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.min_candidates < 1:
        raise SystemExit("--min-candidates must be positive")
    if not args.log.is_file():
        raise SystemExit(f"Desktop diagnostics log does not exist: {args.log}")

    samples = parse_samples(args.log.read_text(encoding="utf-8").splitlines())
    if not samples:
        raise SystemExit(
            "No analysis.performance samples found. Run Desktop ranked analysis first."
        )

    selected = select_representative_sample(
        samples,
        min_candidates=args.min_candidates,
        require_scoring=args.require_scoring,
    )
    if selected is None:
        largest = max(sample.candidates for sample in samples)
        large_samples = [sample for sample in samples if sample.candidates >= args.min_candidates]
        if args.require_scoring and large_samples:
            raise SystemExit(
                "Representative country-scale samples exist, but none has scoringMetrics > 0. "
                f"Largest observed candidate count is {largest}. Rebuild/load a dataset with "
                "preference defaults or enable at least one Preference, rerun analysis, then "
                "retry strict acceptance. Use `make analysis-acceptance` for a non-strict "
                "filter-only performance report."
            )
        raise SystemExit(
            "No representative analysis.performance sample found: "
            f"need candidates >= {args.min_candidates}; "
            f"largest observed candidate count is {largest}."
        )

    if args.format == "json":
        payload = {
            "minimumCandidateThreshold": args.min_candidates,
            "sample": asdict(selected),
        }
        print(json.dumps(payload, indent=2, sort_keys=True))
    else:
        print(render_markdown(selected, min_candidates=args.min_candidates))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
