import geopandas as gpd
from shapely.geometry import Point, Polygon

from osmapdigger_geo.metrics import calculate_metrics, filter_category
from osmapdigger_geo.settlements import extract_settlements, normalize_settlement_name
from osmapdigger_geo.models import CategoryDefinition, Selector


def category(category_id, selectors, coverage=(), counts=()):
    return CategoryDefinition(
        id=category_id,
        title=category_id.title(),
        group="Test",
        selectors=selectors,
        default_filter=True,
        distance=True,
        coverage_radii_km=coverage,
        count_radii_km=counts,
        preferred_direction="lower",
    )


def test_extract_settlement_uses_geometry_not_lat_lon_columns():
    frame = gpd.GeoDataFrame(
        [{"id": 1, "osm_type": "node", "name": "Test village", "place": "village"}],
        geometry=[Point(1.5, 42.5)],
        crs="EPSG:4326",
    )
    gdf, records = extract_settlements(frame, ["village"], ("name", "name:en"))
    assert len(gdf) == 1
    assert records[0].latitude == 42.5
    assert records[0].longitude == 1.5


def test_filter_category_supports_or_alternatives():
    frame = gpd.GeoDataFrame(
        [
            {"natural": "wood", "landuse": None},
            {"natural": None, "landuse": "forest"},
            {"natural": "water", "landuse": None},
        ],
        geometry=[Point(0, 0), Point(1, 0), Point(2, 0)],
        crs="EPSG:3857",
    )
    spec = category(
        "forest",
        (
            (Selector("natural", ("wood",)),),
            (Selector("landuse", ("forest",)),),
        ),
    )
    assert len(filter_category(frame, spec)) == 2


def test_distance_count_and_coverage_metrics():
    settlements = gpd.GeoDataFrame(
        [{"_sid": 0}],
        geometry=[Point(0, 0)],
        crs="EPSG:3857",
    )
    features = gpd.GeoDataFrame(
        [{"natural": "wood"}, {"natural": "wood"}],
        geometry=[Point(500, 0).buffer(100), Point(1500, 0).buffer(100)],
        crs="EPSG:3857",
    )
    spec = category(
        "forest",
        ((Selector("natural", ("wood",)),),),
        coverage=(1.0,),
        counts=(1.0, 2.0),
    )

    result = calculate_metrics(
        settlements,
        features,
        gpd.GeoDataFrame({"geometry": []}, geometry="geometry", crs="EPSG:3857"),
        [spec],
        "EPSG:3857",
    )

    assert 0.39 <= result[0]["forest.distance_km"] <= 0.41
    assert result[0]["forest.count_1km"] == 1.0
    assert result[0]["forest.count_2km"] == 2.0
    assert result[0]["forest.coverage_pct_1km"] > 0


def test_extract_settlements_merges_place_node_inside_same_named_polygon():
    frame = gpd.GeoDataFrame(
        [
            {
                "id": 10,
                "osm_type": "node",
                "name": "Віцебск",
                "name:ru": "Витебск",
                "name:en": "Vitebsk",
                "place": "city",
                "population": "366299",
            },
            {
                "id": 20,
                "osm_type": "relation",
                "name": "Віцебск",
                "name:ru": "Витебск",
                "place": "city",
            },
        ],
        geometry=[
            Point(30.2049, 55.1904),
            Polygon(
                [
                    (30.0, 55.0),
                    (30.4, 55.0),
                    (30.4, 55.3),
                    (30.0, 55.3),
                    (30.0, 55.0),
                ]
            ),
        ],
        crs="EPSG:4326",
    )

    gdf, records = extract_settlements(
        frame,
        ["city"],
        ("name", "name:be", "name:ru", "name:en", "official_name", "alt_name"),
    )

    assert len(gdf) == 1
    assert len(records) == 1
    assert records[0].settlement_id == "node:10"
    assert records[0].latitude == 55.1904
    assert records[0].longitude == 30.2049
    assert [name.name for name in records[0].names] == ["Віцебск", "Vitebsk", "Витебск"]
    assert records[0].population == 366299


def test_extract_settlements_keeps_distinct_same_named_place_nodes():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Дубровка", "place": "village"},
            {"id": 2, "osm_type": "node", "name": "Дубровка", "place": "village"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.0005, 53.0005)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        deduplication_tolerance_m=250.0,
    )

    assert [record.settlement_id for record in records] == ["node:1", "node:2"]


def test_settlement_name_normalization_matches_runtime_contract():
    assert normalize_settlement_name("  ВИТЁБСК, район! ") == "витебск район"


def test_extract_settlements_merges_same_wikidata_even_when_both_are_points():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Віцебск", "place": "city", "wikidata": "Q1021"},
            {
                "id": 2,
                "osm_type": "relation",
                "name": "Витебск",
                "place": "city",
                "wikidata": "Q1021",
            },
        ],
        geometry=[Point(30.2049, 55.1904), Point(30.2050, 55.1905)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(frame, ["city"], ("name", "name:ru", "name:en"))

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"
    assert {name.name for name in records[0].names} == {"Віцебск", "Витебск"}


def test_extract_settlements_merges_point_near_same_named_area_with_tolerance():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Test", "place": "village"},
            {"id": 2, "osm_type": "relation", "name": "Test", "place": "village"},
        ],
        geometry=[
            Point(27.0020, 53.0),
            Polygon(
                [
                    (27.0, 52.999),
                    (27.0010, 52.999),
                    (27.0010, 53.001),
                    (27.0, 53.001),
                    (27.0, 52.999),
                ]
            ),
        ],
        crs="EPSG:4326",
    )

    _, strict_records = extract_settlements(frame, ["village"], ("name",))
    _, tolerant_records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        deduplication_tolerance_m=250.0,
    )

    assert len(strict_records) == 2
    assert len(tolerant_records) == 1
    assert tolerant_records[0].settlement_id == "node:1"


def test_extract_settlements_merges_nearby_point_like_different_osm_types_only():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Test", "place": "village"},
            {"id": 2, "osm_type": "relation", "name": "Test", "place": "village"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.0005, 53.0005)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        deduplication_tolerance_m=250.0,
    )

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"


def test_extract_settlements_shared_wikidata_ignores_place_type_difference():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Test", "place": "town", "wikidata": "Q1"},
            {"id": 2, "osm_type": "relation", "name": "Test", "place": "city", "wikidata": "Q1"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.0005, 53.0005)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(frame, ["town", "city"], ("name",))

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"


def test_extract_settlements_merges_nearby_same_named_place_nodes_by_name_distance():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Дубровка", "place": "village"},
            {"id": 2, "osm_type": "node", "name": "Дубровка", "place": "village"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.006, 53.0)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        name_deduplication_distance_m=1000.0,
    )

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"


def test_extract_settlements_keeps_same_named_place_nodes_beyond_name_distance():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Дубровка", "place": "village"},
            {"id": 2, "osm_type": "node", "name": "Дубровка", "place": "village"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.03, 53.0)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        name_deduplication_distance_m=1000.0,
    )

    assert [record.settlement_id for record in records] == ["node:1", "node:2"]


def test_extract_settlements_merges_nearby_same_named_compatible_rural_types():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Test", "place": "village"},
            {"id": 2, "osm_type": "node", "name": "Test", "place": "hamlet"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.004, 53.0)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village", "hamlet"],
        ("name",),
        name_deduplication_distance_m=1000.0,
    )

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"


def test_extract_settlements_merges_nearby_same_named_different_place_types():
    frame = gpd.GeoDataFrame(
        [
            {"id": 1, "osm_type": "node", "name": "Test", "place": "town"},
            {"id": 2, "osm_type": "node", "name": "Test", "place": "village"},
        ],
        geometry=[Point(27.0, 53.0), Point(27.004, 53.0)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["town", "village"],
        ("name",),
        name_deduplication_distance_m=1000.0,
    )

    assert len(records) == 1
    assert records[0].settlement_id == "node:1"


def test_extract_settlements_can_merge_by_shared_localized_alias():
    frame = gpd.GeoDataFrame(
        [
            {
                "id": 1,
                "osm_type": "node",
                "name": "Новае Сяло",
                "name:ru": "Новое Село",
                "place": "village",
            },
            {
                "id": 2,
                "osm_type": "node",
                "name": "Новае сяло",
                "name:ru": "Новое Село",
                "place": "village",
            },
        ],
        geometry=[Point(27.0, 53.0), Point(27.004, 53.0)],
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name", "name:ru"),
        name_deduplication_distance_m=1000.0,
    )

    assert len(records) == 1
    assert {name.name for name in records[0].names} == {"Новае Сяло", "Новое Село"}


def test_settlement_diagnostics_reports_nearby_pairs_outside_name_buckets(capsys):
    frame = gpd.GeoDataFrame(
        {
            "id": [101, 202],
            "osm_type": ["node", "node"],
            "place": ["village", "village"],
            "name": ["Alpha", "Alfa"],
            "geometry": [Point(27.0000, 53.0000), Point(27.0010, 53.0000)],
        },
        geometry="geometry",
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village"],
        ("name",),
        name_deduplication_distance_m=1000.0,
        diagnostics=True,
        diagnostics_limit=10,
    )

    assert len(records) == 2
    output = capsys.readouterr().out
    assert "Settlement diagnostics:" in output
    assert "left=node:101 right=node:202" in output
    assert "shared_normalized=[]" in output
    assert "merge_reason=none" in output
    assert '"normalized":"alpha"' in output
    assert '"normalized":"alfa"' in output


def test_settlement_final_audit_reports_unmerged_same_alias_components(capsys):
    frame = gpd.GeoDataFrame(
        {
            "id": [301, 302],
            "osm_type": ["node", "node"],
            "place": ["village", "locality"],
            "name": ["Duplicate", "Duplicate"],
            "geometry": [Point(27.0000, 53.0000), Point(27.0200, 53.0000)],
        },
        geometry="geometry",
        crs="EPSG:4326",
    )

    _, records = extract_settlements(
        frame,
        ["village", "locality"],
        ("name",),
        name_deduplication_distance_m=1000.0,
        diagnostics=True,
        diagnostics_limit=10,
    )

    assert len(records) == 2
    output = capsys.readouterr().out
    assert "Settlement final audit:" in output
    assert "FINAL DUPLICATE SUSPECT 1:" in output
    assert "shared_normalized=[\"duplicate\"]" in output
    assert "why_not_merged=all_direct_comparisons_rejected" in output
    assert "direct_pairs_checked=1" in output
    assert "mergeable_direct_pairs=0" in output
