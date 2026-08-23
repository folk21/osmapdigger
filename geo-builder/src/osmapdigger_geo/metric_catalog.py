"""Expansion of build-time categories into persisted runtime metric definitions."""

from __future__ import annotations

from .models import CategoryDefinition, MetricDefinition


def radius_token(radius_km: float) -> str:
    """Create a stable metric-ID token for a numeric radius."""
    if radius_km.is_integer():
        return f"{int(radius_km)}km"
    return f"{str(radius_km).replace('.', '_')}km"


def build_metric_definitions(
    categories: list[CategoryDefinition],
) -> list[MetricDefinition]:
    """Expand category-level config into deterministic runtime filter definitions.

    Distance metrics inherit ``default_filter`` because they are the primary initial UI
    controls. Count/coverage metrics are still persisted and addable at runtime but start
    disabled to avoid overwhelming the default filter panel.
    """
    result: list[MetricDefinition] = []
    order = 0

    for category in categories:
        if category.distance:
            result.append(
                MetricDefinition(
                    metric_id=f"{category.id}.distance_km",
                    category_id=category.id,
                    group_id=category.group,
                    title=f"Distance to {category.title.lower()}",
                    description=f"Nearest mapped {category.title.lower()} distance",
                    unit="km",
                    measure_type="distance",
                    preferred_direction=category.preferred_direction,
                    default_enabled=category.default_filter,
                    sort_order=order,
                )
            )
            order += 1

        for radius in category.coverage_radii_km:
            token = radius_token(radius)
            result.append(
                MetricDefinition(
                    metric_id=f"{category.id}.coverage_pct_{token}",
                    category_id=category.id,
                    group_id=category.group,
                    title=f"{category.title} coverage within {radius:g} km",
                    description=(
                        f"Share of the {radius:g} km buffer covered by "
                        f"{category.title.lower()}"
                    ),
                    unit="%",
                    measure_type="coverage",
                    preferred_direction="neutral",
                    default_enabled=False,
                    sort_order=order,
                )
            )
            order += 1

        for radius in category.count_radii_km:
            token = radius_token(radius)
            result.append(
                MetricDefinition(
                    metric_id=f"{category.id}.count_{token}",
                    category_id=category.id,
                    group_id=category.group,
                    title=f"{category.title} count within {radius:g} km",
                    description=(
                        f"Number of mapped {category.title.lower()} features "
                        f"within {radius:g} km"
                    ),
                    unit="count",
                    measure_type="count",
                    preferred_direction="neutral",
                    default_enabled=False,
                    sort_order=order,
                )
            )
            order += 1

    return result
