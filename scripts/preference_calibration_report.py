#!/usr/bin/env python3
"""Report preference metric and score distributions from a generated OsmapDigger dataset."""

from __future__ import annotations

import argparse
import json
import math
import sqlite3
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence

DEFAULT_SATURATION_WARNING_PCT = 90.0


@dataclass(frozen=True)
class PreferenceDefinition:
    """One enabled runtime preference default with presentation metadata."""

    metric_id: str
    group_id: str
    title: str
    unit: str
    direction: str
    target: float
    limit: float
    weight: int
    enabled: bool = True


@dataclass(frozen=True)
class MetricCalibration:
    """Distribution evidence for one enabled preference metric."""

    metric_id: str
    group_id: str
    title: str
    unit: str
    direction: str
    target: float
    limit: float
    weight: int
    enabled: bool
    known_count: int
    total_settlements: int
    known_pct: float
    minimum: float | None
    p10: float | None
    p25: float | None
    median: float | None
    p75: float | None
    p90: float | None
    maximum: float | None
    full_quality_pct_known: float
    transition_pct_known: float
    zero_quality_pct_known: float
    saturation_warning: bool


@dataclass(frozen=True)
class GroupWeightSummary:
    """Enabled preference weight contribution for one metric group."""

    group_id: str
    metric_count: int
    total_weight: int
    weight_pct: float


@dataclass(frozen=True)
class ScoreDistribution:
    """Overall score and weighted-coverage distribution for the dataset."""

    total_settlements: int
    scored_settlements: int
    scored_pct: float
    score_p10: float | None
    score_p25: float | None
    score_median: float | None
    score_p75: float | None
    score_p90: float | None
    coverage_p10: float | None
    coverage_p25: float | None
    coverage_median: float | None
    coverage_p75: float | None
    coverage_p90: float | None


@dataclass(frozen=True)
class CalibrationReport:
    """Complete calibration evidence for one generated dataset database."""

    database: str
    total_settlements: int
    preference_count: int
    enabled_preference_count: int
    total_enabled_weight: int
    metrics: tuple[MetricCalibration, ...]
    groups: tuple[GroupWeightSummary, ...]
    scores: ScoreDistribution


def percentile(sorted_values: Sequence[float], percentile_value: float) -> float | None:
    """Return a deterministic linearly interpolated percentile over sorted finite values."""

    if not sorted_values:
        return None
    if not 0.0 <= percentile_value <= 1.0:
        raise ValueError("percentile_value must be within [0, 1]")
    if len(sorted_values) == 1:
        return sorted_values[0]
    position = (len(sorted_values) - 1) * percentile_value
    lower_index = math.floor(position)
    upper_index = math.ceil(position)
    if lower_index == upper_index:
        return sorted_values[lower_index]
    fraction = position - lower_index
    lower_value = sorted_values[lower_index]
    upper_value = sorted_values[upper_index]
    return lower_value + (upper_value - lower_value) * fraction


def normalized_quality(value: float, preference: PreferenceDefinition) -> float:
    """Apply the shared piecewise-linear LOWER/HIGHER preference formula."""

    if preference.direction == "lower":
        if value <= preference.target:
            return 1.0
        if value >= preference.limit:
            return 0.0
        return (preference.limit - value) / (preference.limit - preference.target)
    if preference.direction == "higher":
        if value >= preference.target:
            return 1.0
        if value <= preference.limit:
            return 0.0
        return (value - preference.limit) / (preference.target - preference.limit)
    raise ValueError(f"Unsupported preference direction: {preference.direction}")


def resolve_database_path(path: Path) -> Path:
    """Resolve either a package directory or a direct georisk.sqlite path."""

    if path.is_dir():
        candidate = path / "georisk.sqlite"
    else:
        candidate = path
    if not candidate.is_file():
        raise ValueError(f"Dataset database does not exist: {candidate}")
    return candidate


def load_preferences(connection: sqlite3.Connection) -> list[PreferenceDefinition]:
    """Load all runtime preference defaults from the generated dataset contract."""

    rows = connection.execute(
        """
        SELECT
            p.metric_id,
            d.group_id,
            d.title,
            d.unit,
            p.direction,
            p.target_value,
            p.limit_value,
            p.weight,
            p.default_enabled
        FROM metric_preference_default AS p
        JOIN metric_definition AS d ON d.metric_id = p.metric_id
        ORDER BY d.sort_order, p.metric_id
        """
    ).fetchall()
    return [
        PreferenceDefinition(
            metric_id=str(row[0]),
            group_id=str(row[1]),
            title=str(row[2]),
            unit=str(row[3]),
            direction=str(row[4]),
            target=float(row[5]),
            limit=float(row[6]),
            weight=int(row[7]),
            enabled=bool(row[8]),
        )
        for row in rows
    ]


def build_metric_calibration(
    connection: sqlite3.Connection,
    preference: PreferenceDefinition,
    *,
    total_settlements: int,
    saturation_warning_pct: float,
) -> MetricCalibration:
    """Calculate value percentiles and score-saturation shares for one preference."""

    values = [
        float(row[0])
        for row in connection.execute(
            "SELECT value FROM settlement_metric WHERE metric_id = ? ORDER BY value",
            (preference.metric_id,),
        )
    ]
    qualities = [normalized_quality(value, preference) for value in values]
    known_count = len(values)
    full_count = sum(quality >= 1.0 for quality in qualities)
    zero_count = sum(quality <= 0.0 for quality in qualities)
    transition_count = known_count - full_count - zero_count

    def known_share(count: int) -> float:
        return 100.0 * count / known_count if known_count else 0.0

    full_pct = known_share(full_count)
    zero_pct = known_share(zero_count)
    return MetricCalibration(
        metric_id=preference.metric_id,
        group_id=preference.group_id,
        title=preference.title,
        unit=preference.unit,
        direction=preference.direction,
        target=preference.target,
        limit=preference.limit,
        weight=preference.weight,
        enabled=preference.enabled,
        known_count=known_count,
        total_settlements=total_settlements,
        known_pct=(100.0 * known_count / total_settlements if total_settlements else 0.0),
        minimum=percentile(values, 0.0),
        p10=percentile(values, 0.10),
        p25=percentile(values, 0.25),
        median=percentile(values, 0.50),
        p75=percentile(values, 0.75),
        p90=percentile(values, 0.90),
        maximum=percentile(values, 1.0),
        full_quality_pct_known=full_pct,
        transition_pct_known=known_share(transition_count),
        zero_quality_pct_known=zero_pct,
        saturation_warning=max(full_pct, zero_pct) >= saturation_warning_pct,
    )


def build_group_summaries(preferences: Iterable[PreferenceDefinition]) -> tuple[GroupWeightSummary, ...]:
    """Summarize enabled weight by persisted conceptual metric group."""

    grouped: dict[str, tuple[int, int]] = {}
    preference_list = [preference for preference in preferences if preference.enabled]
    total_weight = sum(preference.weight for preference in preference_list)
    for preference in preference_list:
        count, weight = grouped.get(preference.group_id, (0, 0))
        grouped[preference.group_id] = (count + 1, weight + preference.weight)
    return tuple(
        GroupWeightSummary(
            group_id=group_id,
            metric_count=count,
            total_weight=weight,
            weight_pct=(100.0 * weight / total_weight if total_weight else 0.0),
        )
        for group_id, (count, weight) in sorted(grouped.items())
    )


def build_score_distribution(
    connection: sqlite3.Connection,
    preferences: Sequence[PreferenceDefinition],
    *,
    total_settlements: int,
) -> ScoreDistribution:
    """Reproduce shared scoring over published metric values for distribution evidence."""

    enabled_preferences = [preference for preference in preferences if preference.enabled]
    total_weight = sum(preference.weight for preference in enabled_preferences)
    by_metric = {preference.metric_id: preference for preference in enabled_preferences}
    weighted_quality: dict[str, float] = {}
    known_weight: dict[str, int] = {}

    if by_metric:
        placeholders = ",".join("?" for _ in by_metric)
        rows = connection.execute(
            f"""
            SELECT settlement_id, metric_id, value
            FROM settlement_metric
            WHERE metric_id IN ({placeholders})
            ORDER BY settlement_id, metric_id
            """,
            tuple(by_metric),
        )
        for settlement_id, metric_id, raw_value in rows:
            preference = by_metric[str(metric_id)]
            value = float(raw_value)
            settlement_key = str(settlement_id)
            weighted_quality[settlement_key] = weighted_quality.get(settlement_key, 0.0) + (
                preference.weight * normalized_quality(value, preference)
            )
            known_weight[settlement_key] = known_weight.get(settlement_key, 0) + preference.weight

    scores: list[float] = []
    coverages: list[float] = []
    for settlement_id, weight in known_weight.items():
        if weight <= 0:
            continue
        scores.append(100.0 * weighted_quality[settlement_id] / weight)
        coverages.append(100.0 * weight / total_weight if total_weight else 0.0)
    scores.sort()
    coverages.sort()

    return ScoreDistribution(
        total_settlements=total_settlements,
        scored_settlements=len(scores),
        scored_pct=(100.0 * len(scores) / total_settlements if total_settlements else 0.0),
        score_p10=percentile(scores, 0.10),
        score_p25=percentile(scores, 0.25),
        score_median=percentile(scores, 0.50),
        score_p75=percentile(scores, 0.75),
        score_p90=percentile(scores, 0.90),
        coverage_p10=percentile(coverages, 0.10),
        coverage_p25=percentile(coverages, 0.25),
        coverage_median=percentile(coverages, 0.50),
        coverage_p75=percentile(coverages, 0.75),
        coverage_p90=percentile(coverages, 0.90),
    )


def build_report(database_path: Path, *, saturation_warning_pct: float) -> CalibrationReport:
    """Build deterministic calibration evidence from one published runtime database."""

    if not 0.0 < saturation_warning_pct <= 100.0:
        raise ValueError("saturation_warning_pct must be within (0, 100]")
    database = resolve_database_path(database_path)
    connection = sqlite3.connect(f"file:{database}?mode=ro", uri=True)
    try:
        total_settlements = int(connection.execute("SELECT COUNT(*) FROM settlement").fetchone()[0])
        preferences = load_preferences(connection)
        if not preferences:
            raise ValueError(
                "Dataset has no metric_preference_default rows; rebuild or select a preference profile "
                "before calibration."
            )
        enabled_preferences = [preference for preference in preferences if preference.enabled]
        metrics = tuple(
            build_metric_calibration(
                connection,
                preference,
                total_settlements=total_settlements,
                saturation_warning_pct=saturation_warning_pct,
            )
            for preference in preferences
        )
        return CalibrationReport(
            database=str(database),
            total_settlements=total_settlements,
            preference_count=len(preferences),
            enabled_preference_count=len(enabled_preferences),
            total_enabled_weight=sum(preference.weight for preference in enabled_preferences),
            metrics=metrics,
            groups=build_group_summaries(preferences),
            scores=build_score_distribution(
                connection,
                preferences,
                total_settlements=total_settlements,
            ),
        )
    finally:
        connection.close()


def format_value(value: float | None, unit: str = "") -> str:
    if value is None:
        return "n/a"
    rendered = f"{value:.3f}".rstrip("0").rstrip(".")
    return f"{rendered} {unit}".strip()


def render_markdown(report: CalibrationReport, *, saturation_warning_pct: float) -> str:
    """Render copy-ready calibration evidence for specs/reviews."""

    lines = [
        "### Preference calibration report",
        "",
        f"- Database: `{report.database}`",
        f"- Settlements: `{report.total_settlements}`",
        f"- Preference defaults: `{report.preference_count}`",
        f"- Enabled by default: `{report.enabled_preference_count}`",
        f"- Total enabled weight: `{report.total_enabled_weight}`",
        f"- Saturation warning threshold: `>= {saturation_warning_pct:.1f}%` at full or zero quality",
        "",
        "#### Enabled weight by group",
        "",
        "| Group | Metrics | Weight | Share |",
        "|---|---:|---:|---:|",
    ]
    for group in report.groups:
        lines.append(
            f"| `{group.group_id}` | {group.metric_count} | {group.total_weight} | {group.weight_pct:.1f}% |"
        )

    lines.extend(
        [
            "",
            "#### Metric distributions",
            "",
            "| Metric | Enabled | Known | p10 | p25 | p50 | p75 | p90 | Target / limit | Full | Transition | Zero |",
            "|---|:---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|",
        ]
    )
    for metric in report.metrics:
        warning = " ⚠" if metric.saturation_warning else ""
        target_limit = (
            f"{metric.direction}: {format_value(metric.target, metric.unit)} / "
            f"{format_value(metric.limit, metric.unit)}"
        )
        lines.append(
            f"| `{metric.metric_id}`{warning} | {'yes' if metric.enabled else 'no'} | "
            f"{metric.known_pct:.1f}% ({metric.known_count}/{metric.total_settlements}) | "
            f"{format_value(metric.p10, metric.unit)} | "
            f"{format_value(metric.p25, metric.unit)} | "
            f"{format_value(metric.median, metric.unit)} | "
            f"{format_value(metric.p75, metric.unit)} | "
            f"{format_value(metric.p90, metric.unit)} | {target_limit} | "
            f"{metric.full_quality_pct_known:.1f}% | {metric.transition_pct_known:.1f}% | "
            f"{metric.zero_quality_pct_known:.1f}% |"
        )

    scores = report.scores
    lines.extend(
        [
            "",
            "#### Overall score distribution",
            "",
            f"- Settlements with at least one known enabled Preference: `{scores.scored_settlements}` "
            f"(`{scores.scored_pct:.1f}%`)",
            f"- Score p10 / p25 / p50 / p75 / p90: "
            f"`{format_value(scores.score_p10)} / {format_value(scores.score_p25)} / "
            f"{format_value(scores.score_median)} / {format_value(scores.score_p75)} / "
            f"{format_value(scores.score_p90)}`",
            f"- Coverage p10 / p25 / p50 / p75 / p90: "
            f"`{format_value(scores.coverage_p10, '%')} / {format_value(scores.coverage_p25, '%')} / "
            f"{format_value(scores.coverage_median, '%')} / {format_value(scores.coverage_p75, '%')} / "
            f"{format_value(scores.coverage_p90, '%')}`",
        ]
    )

    warnings = [metric for metric in report.metrics if metric.saturation_warning]
    if warnings:
        lines.extend(
            [
                "",
                "#### Calibration warnings",
                "",
                *[
                    f"- `{metric.metric_id}`: {max(metric.full_quality_pct_known, metric.zero_quality_pct_known):.1f}% "
                    "of known values saturate at one scoring endpoint; review the fixed target/limit "
                    "against product intent before changing defaults."
                    for metric in warnings
                ],
            ]
        )
    else:
        lines.extend(
            [
                "",
                "> No enabled metric crosses the configured saturation warning threshold. This is "
                "distribution evidence only; it does not by itself prove that product-default weights "
                "or thresholds are optimal.",
            ]
        )
    return "\n".join(lines)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Inspect a generated OsmapDigger georisk.sqlite and report enabled Preference "
            "metric/score distributions for calibration review."
        )
    )
    parser.add_argument(
        "--dataset",
        type=Path,
        required=True,
        help="Generated package directory or direct georisk.sqlite path.",
    )
    parser.add_argument(
        "--saturation-warning-pct",
        type=float,
        default=DEFAULT_SATURATION_WARNING_PCT,
        help=(
            "Warn when at least this percentage of known values saturates at full or zero quality "
            f"(default: {DEFAULT_SATURATION_WARNING_PCT:g})."
        ),
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
    try:
        report = build_report(
            args.dataset,
            saturation_warning_pct=args.saturation_warning_pct,
        )
    except (sqlite3.Error, ValueError) as error:
        raise SystemExit(str(error)) from error

    if args.format == "json":
        print(json.dumps(asdict(report), indent=2, sort_keys=True))
    else:
        print(render_markdown(report, saturation_warning_pct=args.saturation_warning_pct))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
