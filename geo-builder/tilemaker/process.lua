-- OsmapDigger tilemaker profile.
--
-- This intentionally generates only the vector layers consumed by the current
-- offline MapLibre style. Analytical metrics come from georisk.sqlite and are
-- not encoded into the basemap.

function Set(values)
    local result = {}
    for _, value in ipairs(values) do
        result[value] = true
    end
    return result
end

local landcoverValues = Set {
    "forest", "wood", "wetland", "grass", "grassland", "meadow",
    "farmland", "orchard", "vineyard", "scrub", "heath", "beach"
}

local parkValues = Set {
    "park", "garden", "nature_reserve", "recreation_ground"
}

local majorRoadValues = Set {
    "motorway", "trunk", "primary", "secondary"
}

local regionalRoadValues = Set {
    "tertiary", "unclassified"
}

local localRoadValues = Set {
    "residential", "living_street", "service", "track"
}

local railwayValues = Set {
    "rail", "light_rail", "narrow_gauge", "tram"
}

local linearWaterways = Set {
    "river", "stream", "canal", "drain", "ditch"
}

local function setName()
    local name = Find("name")
    if name ~= "" then
        Attribute("name", name)
    end
end

local function placeMinZoom(place)
    if place == "country" then return 2 end
    if place == "state" or place == "province" then return 4 end
    if place == "city" then return 5 end
    if place == "town" then return 8 end
    if place == "village" then return 10 end
    if place == "hamlet" or place == "isolated_dwelling" then return 12 end
    return 13
end

function node_function()
    local place = Find("place")
    if place ~= "" then
        Layer("place", false)
        Attribute("class", place)
        setName()
        MinZoom(placeMinZoom(place))
    end
end

function relation_scan_function()
    if Find("type") == "boundary" and Find("boundary") == "administrative" then
        Accept()
    end
end

function way_function()
    local isClosed = IsClosed()
    local highway = Find("highway")
    local railway = Find("railway")
    local waterway = Find("waterway")
    local natural = Find("natural")
    local landuse = Find("landuse")
    local leisure = Find("leisure")
    local boundary = Find("boundary")
    local building = Find("building")
    local place = Find("place")

    -- Administrative boundaries can be mapped directly on a way or inherited
    -- from an accepted boundary relation.
    local adminLevel = tonumber(Find("admin_level")) or 99
    local isAdministrativeBoundary = boundary == "administrative"
    while true do
        local relation = NextRelation()
        if not relation then break end
        isAdministrativeBoundary = true
        adminLevel = math.min(adminLevel, tonumber(FindInRelation("admin_level")) or 99)
    end
    if isAdministrativeBoundary then
        Layer("boundary", false)
        Attribute("admin_level", tostring(adminLevel))
        if adminLevel <= 4 then MinZoom(4)
        elseif adminLevel <= 6 then MinZoom(7)
        elseif adminLevel <= 8 then MinZoom(10)
        else MinZoom(12) end
    end

    if highway ~= "" then
        local minZoom = 14
        if majorRoadValues[highway] then minZoom = 5
        elseif regionalRoadValues[highway] then minZoom = 9
        elseif localRoadValues[highway] then minZoom = 12 end

        Layer("transportation", false)
        Attribute("class", highway)
        setName()
        MinZoom(minZoom)
    end

    if railwayValues[railway] then
        Layer("transportation", false)
        Attribute("class", "rail")
        Attribute("subclass", railway)
        setName()
        if railway == "rail" then MinZoom(8) else MinZoom(11) end
    end

    if linearWaterways[waterway] and not isClosed then
        Layer("waterway", false)
        Attribute("class", waterway)
        setName()
        if waterway == "river" then MinZoom(8) else MinZoom(11) end
    end

    local isWater =
        natural == "water" or
        landuse == "reservoir" or
        landuse == "basin" or
        (waterway == "riverbank" and isClosed)
    if isWater and isClosed then
        Layer("water", true)
        Attribute("class", waterway == "riverbank" and "river" or "lake")
        setName()
    end

    if building ~= "" and isClosed then
        Layer("building", true)
        Attribute("class", building)
        MinZoom(13)
    end

    local landClass = landuse
    if landClass == "" then landClass = natural end
    if landClass == "" then landClass = leisure end

    if landcoverValues[landClass] and isClosed then
        Layer("landcover", true)
        Attribute("class", landClass)
    elseif landuse ~= "" and not isWater and isClosed then
        Layer("landuse", true)
        Attribute("class", landuse)
    end

    if parkValues[leisure] and isClosed then
        Layer("park", true)
        Attribute("class", leisure)
        setName()
    end

    -- Some settlements are mapped as polygons rather than nodes. Render a
    -- centroid so they are still visible in the basemap.
    if place ~= "" and isClosed then
        LayerAsCentroid("place")
        Attribute("class", place)
        setName()
        MinZoom(placeMinZoom(place))
    end
end
