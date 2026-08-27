"""Settlement extraction, canonicalization, and searchable-name normalization."""

from __future__ import annotations

from dataclasses import dataclass
import json
from itertools import combinations
import math
from typing import Any

import geopandas as gpd
import pandas as pd
from shapely.geometry.base import BaseGeometry

from .models import SettlementNameRecord, SettlementRecord


_NAME_KIND_PRIORITY = {
    "primary": 0,
    "localized": 1,
    "official": 2,
    "alternate": 3,
}

def _is_missing(value: Any) -> bool:
    if value is None:
        return True
    try:
        result = pd.isna(value)
        return result if isinstance(result, bool) else False
    except (TypeError, ValueError):
        return False


def _tags(row: pd.Series) -> dict[str, Any]:
    value = row.get("tags")
    if isinstance(value, dict):
        return value
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            return parsed if isinstance(parsed, dict) else {}
        except json.JSONDecodeError:
            return {}
    return {}


def extract_tag(row: pd.Series, key: str) -> Any:
    """Read one OSM tag from a dedicated Pyrosm column or catch-all tags."""
    value = row.get(key)
    return _tags(row).get(key) if _is_missing(value) else value


def tag_series(frame: gpd.GeoDataFrame, key: str) -> pd.Series:
    """Return one logical OSM tag as a Series independent of Pyrosm storage shape."""
    if key in frame.columns:
        direct = frame[key].copy()
        if "tags" not in frame.columns or direct.notna().all():
            return direct
        missing = direct.isna()
        if missing.any():
            direct.loc[missing] = frame.loc[missing].apply(
                lambda row: extract_tag(row, key),
                axis=1,
            )
        return direct
    return frame.apply(lambda row: extract_tag(row, key), axis=1)


def normalize_settlement_name(value: str) -> str:
    """Normalize a settlement name using rules reproduced by shared Kotlin search."""
    lowered = value.strip().lower().replace("ё", "е")
    parts: list[str] = []
    previous_space = False
    for char in lowered:
        if char.isalnum():
            parts.append(char)
            previous_space = False
        elif not previous_space:
            parts.append(" ")
            previous_space = True
    return "".join(parts).strip()


def _name_kind(tag: str) -> str:
    if tag == "name":
        return "primary"
    if tag.startswith("name:"):
        return "localized"
    if tag == "official_name":
        return "official"
    return "alternate"


def _name_language(tag: str) -> str | None:
    return tag.split(":", 1)[1] if tag.startswith("name:") else None


def _name_values(tag: str, raw: Any) -> list[str]:
    if _is_missing(raw):
        return []
    text = str(raw).strip()
    if not text:
        return []
    if tag in {"alt_name", "short_name", "old_name"}:
        return [item.strip() for item in text.split(";") if item.strip()]
    return [text]


def settlement_names(
    row: pd.Series,
    name_tags: tuple[str, ...],
) -> tuple[SettlementNameRecord, ...]:
    """Extract deterministic unique searchable names from configured OSM tags."""
    by_normalized: dict[str, SettlementNameRecord] = {}
    for tag in name_tags:
        for value in _name_values(tag, extract_tag(row, tag)):
            normalized = normalize_settlement_name(value)
            if not normalized:
                continue
            candidate = SettlementNameRecord(
                name=value,
                normalized_name=normalized,
                language=_name_language(tag),
                kind=_name_kind(tag),
            )
            current = by_normalized.get(normalized)
            if (
                current is None
                or _NAME_KIND_PRIORITY[candidate.kind] < _NAME_KIND_PRIORITY[current.kind]
            ):
                by_normalized[normalized] = candidate
    return tuple(
        sorted(
            by_normalized.values(),
            key=lambda item: (
                _NAME_KIND_PRIORITY[item.kind],
                item.language or "",
                item.name.lower(),
            ),
        )
    )


@dataclass
class _Candidate:
    source_index: int
    source_geometry: BaseGeometry
    point: BaseGeometry
    names: tuple[SettlementNameRecord, ...]
    place_type: str
    osm_id: int | None
    osm_type: str | None
    population: int | None
    wikidata: str | None
    wikipedia: str | None
    metric_geometry: BaseGeometry | None = None
    metric_point: BaseGeometry | None = None

    @property
    def normalized_names(self) -> set[str]:
        return {name.normalized_name for name in self.names}


def _text_value(value: Any) -> str | None:
    if _is_missing(value):
        return None
    text = str(value).strip()
    return text or None


def _int_value(value: Any) -> int | None:
    if _is_missing(value):
        return None
    try:
        return int(str(value).replace(",", "").replace(" ", ""))
    except ValueError:
        return None


def _candidate(
    index: int,
    row: pd.Series,
    name_tags: tuple[str, ...],
    metric_geometry: BaseGeometry | None = None,
) -> _Candidate | None:
    names = settlement_names(row, name_tags)
    if not names:
        return None
    geometry = row.geometry
    point = geometry if geometry.geom_type == "Point" else geometry.representative_point()
    metric_point = None
    if metric_geometry is not None:
        metric_point = (
            metric_geometry
            if metric_geometry.geom_type == "Point"
            else metric_geometry.representative_point()
        )
    osm_id_raw = row.get("id") if not _is_missing(row.get("id")) else row.get("osm_id")
    osm_id = int(osm_id_raw) if not _is_missing(osm_id_raw) else None
    osm_type_raw = row.get("osm_type") if not _is_missing(row.get("osm_type")) else row.get("type")
    osm_type = str(osm_type_raw) if not _is_missing(osm_type_raw) else None
    place_type_raw = extract_tag(row, "place")
    place_type = str(place_type_raw).strip() if not _is_missing(place_type_raw) else ""
    return _Candidate(
        source_index=index,
        source_geometry=geometry,
        point=point,
        names=names,
        place_type=place_type,
        osm_id=osm_id,
        osm_type=osm_type,
        population=_int_value(extract_tag(row, "population")),
        wikidata=_text_value(extract_tag(row, "wikidata")),
        wikipedia=_text_value(extract_tag(row, "wikipedia")),
        metric_geometry=metric_geometry,
        metric_point=metric_point,
    )


def _same_identity(left: _Candidate, right: _Candidate) -> bool:
    return (
        left.osm_id is not None
        and right.osm_id is not None
        and left.osm_id == right.osm_id
        and left.osm_type == right.osm_type
    )


def _same_wikidata(left: _Candidate, right: _Candidate) -> bool:
    return left.wikidata is not None and left.wikidata == right.wikidata


def _same_wikipedia(left: _Candidate, right: _Candidate) -> bool:
    return left.wikipedia is not None and left.wikipedia == right.wikipedia


def _metric_distance(left: BaseGeometry | None, right: BaseGeometry | None) -> float | None:
    if left is None or right is None:
        return None
    try:
        return float(left.distance(right))
    except Exception:
        return None


def _merge_reason(
    left: _Candidate,
    right: _Candidate,
    deduplication_tolerance_m: float,
    name_deduplication_distance_m: float,
) -> str | None:
    """Return the evidence class for merging two OSM representations, if any."""
    # Stable external/source identities outrank tagging differences. The same Wikidata
    # or Wikipedia object can legitimately carry slightly different place=* values on
    # node and boundary representations.
    if _same_identity(left, right):
        return "same_identity"
    if _same_wikidata(left, right):
        return "shared_wikidata"
    if _same_wikipedia(left, right):
        return "shared_wikipedia"

    if not (left.normalized_names & right.normalized_names):
        return None

    left_point = left.source_geometry.geom_type == "Point"
    right_point = right.source_geometry.geom_type == "Point"
    if left_point != right_point:
        point = left.source_geometry if left_point else right.source_geometry
        area = right.source_geometry if left_point else left.source_geometry
        try:
            if area.covers(point):
                return "point_area_contains"
        except Exception:
            pass

        if deduplication_tolerance_m > 0:
            metric_point = left.metric_point if left_point else right.metric_point
            metric_area = right.metric_geometry if left_point else left.metric_geometry
            distance = _metric_distance(metric_point, metric_area)
            if distance is not None and distance <= deduplication_tolerance_m:
                return "point_area_nearby"

    elif not left_point and not right_point:
        try:
            if left.source_geometry.intersects(right.source_geometry):
                return "area_area_intersects"
        except Exception:
            pass

    # Different source object types can materialize as two points. Preserve the narrow
    # geometry-drift rule for diagnostics, then fall back to the explicit name+distance
    # policy below, which also covers node/node duplicates.
    if (
        left_point
        and right_point
        and deduplication_tolerance_m > 0
        and left.osm_type != right.osm_type
        and left.osm_type is not None
        and right.osm_type is not None
    ):
        distance = _metric_distance(left.metric_point, right.metric_point)
        if distance is not None and distance <= deduplication_tolerance_m:
            return "point_like_nearby"

    if name_deduplication_distance_m > 0:
        distance = _metric_distance(left.metric_point, right.metric_point)
        if distance is not None and distance <= name_deduplication_distance_m:
            return "name_nearby"
    return None




def _candidate_id(candidate: _Candidate) -> str:
    return f"{candidate.osm_type or 'osm'}:{candidate.osm_id if candidate.osm_id is not None else '?'}"


def _diagnostic_name_payload(candidate: _Candidate) -> str:
    values = [
        {
            "name": item.name,
            "normalized": item.normalized_name,
            "language": item.language,
            "kind": item.kind,
        }
        for item in candidate.names
    ]
    return json.dumps(values, ensure_ascii=False, separators=(",", ":"))


def _nearby_candidate_pairs(
    candidates: list[_Candidate],
    radius_m: float,
) -> list[tuple[int, int, float]]:
    """Find nearby candidate points using a deterministic metric grid index."""
    if radius_m <= 0:
        return []

    cell_size = radius_m
    cells: dict[tuple[int, int], list[int]] = {}
    pairs: list[tuple[int, int, float]] = []
    for index, candidate in enumerate(candidates):
        point = candidate.metric_point
        if point is None:
            continue
        cell_x = math.floor(float(point.x) / cell_size)
        cell_y = math.floor(float(point.y) / cell_size)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for other_index in cells.get((cell_x + dx, cell_y + dy), ()):
                    other = candidates[other_index]
                    distance = _metric_distance(point, other.metric_point)
                    if distance is not None and distance <= radius_m:
                        pairs.append((other_index, index, distance))
        cells.setdefault((cell_x, cell_y), []).append(index)

    pairs.sort(key=lambda item: item[2])
    return pairs


def _print_settlement_diagnostics(
    candidates: list[_Candidate],
    components: list[list[int]],
    deduplication_tolerance_m: float,
    name_deduplication_distance_m: float,
    limit: int,
) -> None:
    """Print bounded raw-pair evidence for diagnosing missed canonicalization."""
    if limit <= 0:
        raise ValueError("settlement diagnostics limit must be > 0")

    diagnostic_radius_m = max(
        500.0,
        deduplication_tolerance_m * 2.0,
        name_deduplication_distance_m * 2.0,
    )
    component_by_candidate: dict[int, int] = {}
    for component_index, component in enumerate(components):
        for candidate_index in component:
            component_by_candidate[candidate_index] = component_index

    nearby = _nearby_candidate_pairs(candidates, diagnostic_radius_m)
    unresolved = [
        pair
        for pair in nearby
        if component_by_candidate[pair[0]] != component_by_candidate[pair[1]]
    ]

    def priority(item: tuple[int, int, float]) -> tuple[int, int, float, str, str]:
        left = candidates[item[0]]
        right = candidates[item[1]]
        shared = left.normalized_names & right.normalized_names
        primary_left = next((n.normalized_name for n in left.names if n.kind == "primary"), "")
        primary_right = next((n.normalized_name for n in right.names if n.kind == "primary"), "")
        return (
            0 if primary_left and primary_left == primary_right else 1,
            0 if shared else 1,
            item[2],
            _candidate_id(left),
            _candidate_id(right),
        )

    unresolved.sort(key=priority)
    print(
        "Settlement diagnostics: "
        f"radius_m={diagnostic_radius_m:g} "
        f"raw_nearby_pairs={len(nearby)} "
        f"unresolved_pairs={len(unresolved)} "
        f"limit={limit}",
        flush=True,
    )

    for ordinal, (left_index, right_index, distance) in enumerate(unresolved[:limit], 1):
        left = candidates[left_index]
        right = candidates[right_index]
        shared = sorted(left.normalized_names & right.normalized_names)
        reason = _merge_reason(
            left,
            right,
            deduplication_tolerance_m,
            name_deduplication_distance_m,
        )
        print(
            f"  diagnostic pair {ordinal}: "
            f"left={_candidate_id(left)} right={_candidate_id(right)} "
            f"distance_m={distance:.1f} "
            f"shared_normalized={json.dumps(shared, ensure_ascii=False)} "
            f"merge_reason={reason or 'none'}",
            flush=True,
        )
        print(
            "    left "
            f"place={left.place_type or '-'} "
            f"geometry={left.source_geometry.geom_type} "
            f"lon={float(left.point.x):.7f} lat={float(left.point.y):.7f} "
            f"wikidata={left.wikidata or '-'} wikipedia={left.wikipedia or '-'} "
            f"names={_diagnostic_name_payload(left)}",
            flush=True,
        )
        print(
            "    right "
            f"place={right.place_type or '-'} "
            f"geometry={right.source_geometry.geom_type} "
            f"lon={float(right.point.x):.7f} lat={float(right.point.y):.7f} "
            f"wikidata={right.wikidata or '-'} wikipedia={right.wikipedia or '-'} "
            f"names={_diagnostic_name_payload(right)}",
            flush=True,
        )
        print(
            "    thresholds "
            f"within_geometry_tolerance={distance <= deduplication_tolerance_m} "
            f"within_name_distance={distance <= name_deduplication_distance_m}",
            flush=True,
        )


def _canonical_priority(candidate: _Candidate) -> tuple[int, int, int, int]:
    geometry_rank = 0 if candidate.source_geometry.geom_type == "Point" else 1
    type_rank = {"node": 0, "relation": 1, "way": 2}.get(candidate.osm_type or "", 3)
    id_rank = candidate.osm_id if candidate.osm_id is not None else 2**63 - 1
    return (geometry_rank, type_rank, id_rank, candidate.source_index)


def _merge_names(candidates: list[_Candidate]) -> tuple[SettlementNameRecord, ...]:
    by_normalized: dict[str, SettlementNameRecord] = {}
    for candidate in sorted(candidates, key=_canonical_priority):
        for name in candidate.names:
            current = by_normalized.get(name.normalized_name)
            if (
                current is None
                or _NAME_KIND_PRIORITY[name.kind] < _NAME_KIND_PRIORITY[current.kind]
            ):
                by_normalized[name.normalized_name] = name
    return tuple(
        sorted(
            by_normalized.values(),
            key=lambda item: (
                _NAME_KIND_PRIORITY[item.kind],
                item.language or "",
                item.name.lower(),
            ),
        )
    )


def _components(
    candidates: list[_Candidate],
    deduplication_tolerance_m: float,
    name_deduplication_distance_m: float,
) -> tuple[list[list[int]], dict[str, int], list[tuple[int, int, float]], dict[tuple[int, int], str | None]]:
    parent = list(range(len(candidates)))

    def find(value: int) -> int:
        while parent[value] != value:
            parent[value] = parent[parent[value]]
            value = parent[value]
        return value

    def union(left: int, right: int) -> None:
        left_root = find(left)
        right_root = find(right)
        if left_root != right_root:
            parent[right_root] = left_root

    buckets: dict[tuple[str, str, str], list[int]] = {}
    for index, candidate in enumerate(candidates):
        for normalized in candidate.normalized_names:
            buckets.setdefault(("name", "", normalized), []).append(index)
        if candidate.wikidata:
            buckets.setdefault(("wikidata", "", candidate.wikidata), []).append(index)
        if candidate.wikipedia:
            buckets.setdefault(("wikipedia", "", candidate.wikipedia), []).append(index)

    checked: set[tuple[int, int]] = set()
    decisions: dict[tuple[int, int], str | None] = {}
    nearby_suspects: list[tuple[int, int, float]] = []
    diagnostic_radius_m = max(
        500.0,
        deduplication_tolerance_m * 2.0,
        name_deduplication_distance_m * 2.0,
    )
    merge_types = {
        "same_identity": 0,
        "shared_wikidata": 0,
        "shared_wikipedia": 0,
        "point_area_contains": 0,
        "point_area_nearby": 0,
        "area_area_intersects": 0,
        "point_like_nearby": 0,
        "name_nearby": 0,
    }
    for indices in buckets.values():
        for left_index, right_index in combinations(indices, 2):
            pair = (min(left_index, right_index), max(left_index, right_index))
            if pair in checked:
                continue
            checked.add(pair)
            left = candidates[left_index]
            right = candidates[right_index]
            reason = _merge_reason(
                left,
                right,
                deduplication_tolerance_m,
                name_deduplication_distance_m,
            )
            decisions[pair] = reason
            if reason is None:
                if left.normalized_names & right.normalized_names:
                    distance = _metric_distance(left.metric_point, right.metric_point)
                    if distance is not None and distance <= diagnostic_radius_m:
                        nearby_suspects.append((left_index, right_index, distance))
                continue
            union(left_index, right_index)
            merge_types[reason] += 1

    grouped: dict[int, list[int]] = {}
    for index in range(len(candidates)):
        grouped.setdefault(find(index), []).append(index)

    # Drop suspects whose candidates became connected transitively through another
    # representation; only unresolved nearby pairs are useful diagnostics.
    unresolved = [
        item
        for item in nearby_suspects
        if find(item[0]) != find(item[1])
    ]
    unresolved.sort(key=lambda item: item[2])
    return list(grouped.values()), merge_types, unresolved, decisions



def _print_final_settlement_diagnostics(
    candidates: list[_Candidate],
    components: list[list[int]],
    decisions: dict[tuple[int, int], str | None],
    deduplication_tolerance_m: float,
    name_deduplication_distance_m: float,
    limit: int,
) -> None:
    """Audit duplicate-looking pairs after canonicalization has finished."""
    if limit <= 0:
        raise ValueError("settlement diagnostics limit must be > 0")

    radius_m = max(
        500.0,
        deduplication_tolerance_m * 2.0,
        name_deduplication_distance_m * 2.0,
    )
    canonical_candidates = [
        min((candidates[index] for index in component), key=_canonical_priority)
        for component in components
    ]
    canonical_aliases = [
        {name.normalized_name for name in _merge_names([candidates[index] for index in component])}
        for component in components
    ]
    nearby = _nearby_candidate_pairs(canonical_candidates, radius_m)
    suspects = [
        (left, right, distance)
        for left, right, distance in nearby
        if canonical_aliases[left] & canonical_aliases[right]
    ]
    print(
        "Settlement final audit: "
        f"canonical={len(components)} radius_m={radius_m:g} "
        f"duplicate_suspects={len(suspects)} limit={limit}",
        flush=True,
    )

    for ordinal, (left_component, right_component, distance) in enumerate(suspects[:limit], 1):
        left_members = components[left_component]
        right_members = components[right_component]
        left = canonical_candidates[left_component]
        right = canonical_candidates[right_component]
        shared = sorted(canonical_aliases[left_component] & canonical_aliases[right_component])
        direct: list[tuple[str, str, str | None]] = []
        for left_index in left_members:
            for right_index in right_members:
                pair = (min(left_index, right_index), max(left_index, right_index))
                if pair in decisions:
                    direct.append((
                        _candidate_id(candidates[left_index]),
                        _candidate_id(candidates[right_index]),
                        decisions[pair],
                    ))
        mergeable = [item for item in direct if item[2] is not None]
        if not direct:
            why = "candidates_never_compared"
        elif mergeable:
            why = "merge_reason_seen_but_components_separate"
        else:
            why = "all_direct_comparisons_rejected"

        print(
            f"  FINAL DUPLICATE SUSPECT {ordinal}: "
            f"left={_candidate_id(left)} right={_candidate_id(right)} "
            f"distance_m={distance:.1f} shared_normalized={json.dumps(shared, ensure_ascii=False)} "
            f"why_not_merged={why}",
            flush=True,
        )
        print(
            "    left "
            f"place={left.place_type or '-'} lon={float(left.point.x):.7f} lat={float(left.point.y):.7f} "
            f"members={json.dumps([_candidate_id(candidates[index]) for index in left_members])} "
            f"names={json.dumps(sorted(canonical_aliases[left_component]), ensure_ascii=False)}",
            flush=True,
        )
        print(
            "    right "
            f"place={right.place_type or '-'} lon={float(right.point.x):.7f} lat={float(right.point.y):.7f} "
            f"members={json.dumps([_candidate_id(candidates[index]) for index in right_members])} "
            f"names={json.dumps(sorted(canonical_aliases[right_component]), ensure_ascii=False)}",
            flush=True,
        )
        print(
            "    decision_history "
            f"direct_pairs_checked={len(direct)} "
            f"mergeable_direct_pairs={len(mergeable)} "
            f"pairs={json.dumps(direct, ensure_ascii=False)}",
            flush=True,
        )


def extract_settlements(
    frame: gpd.GeoDataFrame,
    settlement_places: list[str],
    name_tags: tuple[str, ...],
    scope_geometry=None,
    deduplication_tolerance_m: float = 0.0,
    name_deduplication_distance_m: float = 0.0,
    diagnostics: bool = False,
    diagnostics_limit: int = 20,
) -> tuple[gpd.GeoDataFrame, list[SettlementRecord]]:
    """Extract and canonicalize named OSM settlements into one runtime point each.

    OSM commonly maps one settlement through more than one place object. Shared source,
    Wikidata, or Wikipedia identity is strong merge evidence. Otherwise candidates need
    a shared normalized name. Point/area pairs may use a
    small geometry tolerance; a separate configured name-distance threshold can merge
    nearby same-name representations regardless of source geometry, including node/node.
    """
    places = tag_series(frame, "place")
    raw = frame[places.isin(settlement_places)].copy()
    if scope_geometry is not None:
        raw = raw[raw.geometry.intersects(scope_geometry)].copy()
    if raw.empty:
        return raw, []

    if deduplication_tolerance_m < 0:
        raise ValueError("deduplication_tolerance_m must be >= 0")
    if name_deduplication_distance_m < 0:
        raise ValueError("name_deduplication_distance_m must be >= 0")

    raw = raw.reset_index(drop=True)
    metric_geometries: list[BaseGeometry | None] = [None] * len(raw)
    if deduplication_tolerance_m > 0 or name_deduplication_distance_m > 0:
        metric_crs = raw.estimate_utm_crs()
        if metric_crs is None:
            raise RuntimeError("Cannot determine metric CRS for settlement deduplication")
        projected = raw.to_crs(metric_crs)
        metric_geometries = list(projected.geometry)

    candidates = [
        candidate
        for index, (_, row) in enumerate(raw.iterrows())
        if (
            candidate := _candidate(
                index,
                row,
                name_tags,
                metric_geometries[index],
            )
        ) is not None
    ]
    if not candidates:
        return gpd.GeoDataFrame({"geometry": []}, geometry="geometry", crs=raw.crs), []

    components, merge_types, nearby_suspects, decisions = _components(
        candidates,
        deduplication_tolerance_m,
        name_deduplication_distance_m,
    )
    if diagnostics:
        _print_settlement_diagnostics(
            candidates,
            components,
            deduplication_tolerance_m,
            name_deduplication_distance_m,
            diagnostics_limit,
        )
    canonical_rows: list[dict[str, Any]] = []
    records: list[SettlementRecord] = []

    for sid, component in enumerate(components):
        members = [candidates[index] for index in component]
        canonical = min(members, key=_canonical_priority)
        names = _merge_names(members)
        population_values = [
            member.population
            for member in members
            if member.population is not None
        ]
        population = max(population_values) if population_values else None
        stable_id = (
            f"{canonical.osm_type or 'osm'}:{canonical.osm_id}"
            if canonical.osm_id is not None
            else f"generated:{sid}"
        )
        primary = next((name for name in names if name.kind == "primary"), names[0])
        english = next((name.name for name in names if name.language == "en"), None)

        records.append(
            SettlementRecord(
                settlement_id=stable_id,
                osm_id=canonical.osm_id,
                osm_type=canonical.osm_type,
                name=primary.name,
                name_local=primary.name,
                name_en=english,
                place_type=canonical.place_type or None,
                population=population,
                latitude=float(canonical.point.y),
                longitude=float(canonical.point.x),
                names=names,
            )
        )
        canonical_rows.append({"_sid": sid, "geometry": canonical.point})

    if diagnostics:
        _print_final_settlement_diagnostics(
            candidates,
            components,
            decisions,
            deduplication_tolerance_m,
            name_deduplication_distance_m,
            diagnostics_limit,
        )

    merged = len(candidates) - len(records)
    print(
        "Settlement extraction: "
        f"raw={len(candidates)} canonical={len(records)} merged={merged} "
        f"geometry_tolerance_m={deduplication_tolerance_m:g} "
        f"name_distance_m={name_deduplication_distance_m:g} "
        f"same_identity={merge_types['same_identity']} "
        f"shared_wikidata={merge_types['shared_wikidata']} "
        f"shared_wikipedia={merge_types['shared_wikipedia']} "
        f"point_area_contains={merge_types['point_area_contains']} "
        f"point_area_nearby={merge_types['point_area_nearby']} "
        f"area_area_intersects={merge_types['area_area_intersects']} "
        f"point_like_nearby={merge_types['point_like_nearby']} "
        f"name_nearby={merge_types['name_nearby']}",
        flush=True,
    )
    if nearby_suspects:
        diagnostic_radius_m = max(
            500.0,
            deduplication_tolerance_m * 2.0,
            name_deduplication_distance_m * 2.0,
        )
        print(
            "Settlement deduplication suspects: "
            f"remaining_nearby={len(nearby_suspects)} "
            f"diagnostic_radius_m={diagnostic_radius_m:g}",
            flush=True,
        )
        for left_index, right_index, distance in nearby_suspects[:5]:
            left = candidates[left_index]
            right = candidates[right_index]
            left_id = f"{left.osm_type or 'osm'}:{left.osm_id or '?'}"
            right_id = f"{right.osm_type or 'osm'}:{right.osm_id or '?'}"
            print(
                "  unresolved pair "
                f"left={left_id} right={right_id} "
                f"distance_m={distance:.1f} place={left.place_type} "
                f"geometry={left.source_geometry.geom_type}/{right.source_geometry.geom_type}",
                flush=True,
            )
    return gpd.GeoDataFrame(canonical_rows, geometry="geometry", crs=raw.crs), records
