import geopandas as gpd
from shapely.geometry import Point

from osmapdigger_geo.metrics import calculate_metrics, extract_settlements, filter_category
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
    gdf, records = extract_settlements(frame, ["village"])
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
