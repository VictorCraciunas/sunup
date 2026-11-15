"""
Builds the country map used by the geography game (Find on the map).

Input:  Natural Earth "Admin 0 - Countries" at 1:50m, as GeoJSON (public domain).
        https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_50m_admin_0_countries.geojson
Output: core/geo/src/commonMain/composeResources/files/geo/countries.json

Usage (from the project root, Python 3.8+, no extra packages):
    python tools/geo/build_countries.py path/to/ne_50m_admin_0_countries.geojson

Only recompute the nearby countries of an existing map file (no download needed):
    python tools/geo/build_countries.py --update-nearby [path/to/countries.json]

The output is small on purpose: outlines are simplified (Douglas-Peucker), tiny islands are
dropped, and coordinates are stored as whole numbers in hundredths of a degree (about 1 km).

Output format:
{
  "scale": 100,
  "countries": [
    {
      "id": "PT",              ISO 3166-1 alpha-2 (Kosovo "XK"); other code if not askable
      "name": "Portugal",
      "continent": "Europe",   Natural Earth continent; open-ocean islands use their UN region
      "area": 91000,           km², rough, used to order cards (biggest first)
      "askable": true,         false for territories that are drawn but never asked
      "small": false,          true: too small to tap, the map draws a circle around it
      "label": [-806, 3970],   label point (lon, lat) × scale
      "near": ["ES", "MA"],    askable countries only: the closest askable countries by
                               border distance, closest first (neighbours, or for islands
                               the nearest coasts). Wrong options for Name the country.
      "rings": [[lon, lat, lon, lat, ...], ...]   × scale; holes are just more rings (even-odd)
    }
  ]
}
"""

import json
import math
import os
import sys

SCALE = 100
TOLERANCE_DEG = 0.03          # simplification; 1:50m data is much finer than a phone needs
MIN_RING_AREA_KM2 = 150.0     # smaller islands are dropped, except a country's biggest ring
SMALL_COUNTRY_KM2 = 3000.0    # Luxembourg and smaller get a tap circle
NEARBY_COUNT = 8              # how many nearby countries each country lists
NEARBY_CELL_DEG = 2.0         # grid cell for the border-distance search
OVERSEAS_GAP_DEG = 25.0       # parts further than this from a country's mainland are "overseas"

# Drawn and askable even though Natural Earth lists them under another sovereign state.
ALWAYS_ASKABLE = {"XK", "TW", "PS", "EH", "GL"}

# Plain everyday names, by country code.
NAMES = {
    "RU": "Russia", "KR": "South Korea", "KP": "North Korea", "LA": "Laos", "BN": "Brunei",
    "CD": "DR Congo", "CG": "Congo", "GM": "Gambia", "CZ": "Czechia", "MK": "North Macedonia",
    "SZ": "Eswatini", "CV": "Cape Verde", "FM": "Micronesia", "VA": "Vatican City",
    "PS": "Palestine", "SY": "Syria", "IR": "Iran", "TZ": "Tanzania", "BO": "Bolivia",
    "VE": "Venezuela", "MD": "Moldova", "VN": "Vietnam", "TW": "Taiwan", "US": "United States",
    "GB": "United Kingdom", "BS": "Bahamas", "CI": "Côte d'Ivoire", "TL": "Timor-Leste",
    "BA": "Bosnia and Herzegovina", "CF": "Central African Republic", "DO": "Dominican Republic",
    "GQ": "Equatorial Guinea", "SS": "South Sudan", "SB": "Solomon Islands", "XK": "Kosovo",
    "EH": "Western Sahara", "MH": "Marshall Islands", "KN": "Saint Kitts and Nevis",
    "VC": "Saint Vincent and the Grenadines", "AG": "Antigua and Barbuda", "LC": "Saint Lucia",
    "ST": "São Tomé and Príncipe", "GL": "Greenland",
}

CONTINENTS = {"Europe", "Asia", "Africa", "North America", "South America", "Oceania"}


def prop(p, *keys):
    for key in keys:
        for variant in (key, key.lower()):
            value = p.get(variant)
            if value not in (None, "", "-99", -99):
                return value
    return None


def country_code(p):
    code = prop(p, "ISO_A2_EH", "ISO_A2")
    if isinstance(code, str) and len(code) == 2:
        return code.upper()
    return None


def continent_of(p):
    continent = prop(p, "CONTINENT")
    if continent in CONTINENTS:
        return continent
    region = prop(p, "REGION_UN")
    if region == "Americas":
        return "South America" if prop(p, "SUBREGION") == "South America" else "North America"
    return region if region in CONTINENTS else continent


def ring_area_km2(ring):
    """Shoelace on a local equirectangular projection; good enough to rank countries."""
    if len(ring) < 3:
        return 0.0
    mean_lat = sum(lat for _, lat in ring) / len(ring)
    kx = 111.32 * math.cos(math.radians(mean_lat))
    ky = 110.57
    total = 0.0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        total += (x1 * kx) * (y2 * ky) - (x2 * kx) * (y1 * ky)
    return abs(total) / 2.0


def perpendicular_distance(point, start, end):
    (x, y), (x1, y1), (x2, y2) = point, start, end
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return math.hypot(x - x1, y - y1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)))
    return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy))


def simplify(points, tolerance):
    """Douglas-Peucker, iterative so big rings don't hit the recursion limit."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        best, index = 0.0, None
        for i in range(first + 1, last):
            d = perpendicular_distance(points[i], points[first], points[last])
            if d > best:
                best, index = d, i
        if index is not None and best > tolerance:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [p for p, k in zip(points, keep) if k]


def polygons_of(geometry):
    if geometry is None:
        return []
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"]]
    if geometry["type"] == "MultiPolygon":
        return geometry["coordinates"]
    return []


def encode(ring):
    flat = []
    previous = None
    for lon, lat in ring:
        point = (round(lon * SCALE), round(lat * SCALE))
        if point != previous:
            flat.extend(point)
            previous = point
    return flat


def build(features):
    countries = []
    for feature in features:
        p = feature.get("properties", {})
        continent = continent_of(p)
        if continent is None or continent == "Antarctica":
            continue

        code = country_code(p)
        admin = prop(p, "ADMIN", "NAME_LONG", "NAME")
        sovereign = prop(p, "SOVEREIGNT")
        askable = code is not None and (sovereign == admin or code in ALWAYS_ASKABLE)
        name = NAMES.get(code) or prop(p, "NAME_LONG", "NAME", "ADMIN")

        rings = []
        total_area = 0.0
        for polygon in polygons_of(feature.get("geometry")):
            for i, ring in enumerate(polygon):
                points = [(float(x), float(y)) for x, y in ring[:-1]]  # GeoJSON repeats the first point
                area = ring_area_km2(points)
                if i == 0:
                    total_area += area
                else:
                    total_area -= area
                rings.append((area, points))
        if not rings:
            continue

        biggest = max(area for area, _ in rings)
        kept = []
        for area, points in rings:
            if area < MIN_RING_AREA_KM2 and area < biggest:
                continue
            simplified = simplify(points, TOLERANCE_DEG)
            if len(simplified) < 3:
                simplified = points
            encoded = encode(simplified)
            if len(encoded) >= 6:
                kept.append(encoded)

        label_x = prop(p, "LABEL_X")
        label_y = prop(p, "LABEL_Y")
        if label_x is None or label_y is None:
            _, points = max(rings, key=lambda r: r[0])
            label_x = sum(x for x, _ in points) / len(points)
            label_y = sum(y for _, y in points) / len(points)

        # Territories can share their country's code (Ashmore and Cartier is "AU" too),
        # so only askable countries use the two-letter code.
        other_id = prop(p, "ADM0_A3", "SOV_A3") or name
        countries.append({
            "id": code if askable else other_id,
            "name": name,
            "continent": continent,
            "area": round(max(total_area, 0.0)),
            "askable": askable,
            "small": total_area < SMALL_COUNTRY_KM2,
            "label": [round(float(label_x) * SCALE), round(float(label_y) * SCALE)],
            "rings": kept,
        })

    countries.sort(key=lambda c: -c["area"])
    return {"scale": SCALE, "countries": countries}


def add_nearby(atlas):
    """
    For every askable country, the askable countries closest to its border, closest first.
    Distance is between outline points (longitude scaled by latitude), found with a grid so
    it runs in seconds. Countries with nothing close (far islands) fall back to the distance
    between label points, so every country gets a full list.
    """
    scale = atlas["scale"]
    countries = [c for c in atlas["countries"] if c["askable"]]

    def bbox(ring):
        xs, ys = ring[0::2], ring[1::2]
        return min(xs) / scale, max(xs) / scale, min(ys) / scale, max(ys) / scale

    def points_of(c):
        # Far-away overseas parts don't make neighbours: France's Caribbean islands shouldn't
        # put France next to Saint Kitts. Parts within OVERSEAS_GAP_DEG of the biggest one count.
        boxes = [bbox(ring) for ring in c["rings"]]
        main = max(boxes, key=lambda b: (b[1] - b[0]) * (b[3] - b[2]))
        for ring, b in zip(c["rings"], boxes):
            gap_lon = max(0.0, main[0] - b[1], b[0] - main[1])
            gap_lat = max(0.0, main[2] - b[3], b[2] - main[3])
            if max(gap_lon, gap_lat) > OVERSEAS_GAP_DEG:
                continue
            for i in range(0, len(ring), 2):
                yield ring[i] / scale, ring[i + 1] / scale

    grid = {}
    for index, c in enumerate(countries):
        for lon, lat in points_of(c):
            key = (math.floor(lon / NEARBY_CELL_DEG), math.floor(lat / NEARBY_CELL_DEG))
            grid.setdefault(key, []).append((index, lon, lat))

    for index, c in enumerate(countries):
        best = {}
        for lon, lat in points_of(c):
            kx = math.cos(math.radians(lat))
            cx, cy = math.floor(lon / NEARBY_CELL_DEG), math.floor(lat / NEARBY_CELL_DEG)
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for other, olon, olat in grid.get((cx + dx, cy + dy), ()):
                        if other == index:
                            continue
                        d = math.hypot((olon - lon) * kx, olat - lat)
                        if d < best.get(other, float("inf")):
                            best[other] = d

        lx, ly = c["label"][0] / scale, c["label"][1] / scale
        kx = math.cos(math.radians(ly))
        far = {
            other: math.hypot((o["label"][0] / scale - lx) * kx, o["label"][1] / scale - ly) + 100.0
            for other, o in enumerate(countries)
            if other != index and other not in best
        }
        ranked = sorted({**far, **best}.items(), key=lambda item: item[1])
        c["near"] = [countries[other]["id"] for other, _ in ranked[:NEARBY_COUNT]]


def write(atlas, target):
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with open(target, "w", encoding="utf-8") as f:
        json.dump(atlas, f, ensure_ascii=False, separators=(",", ":"))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    default_target = os.path.join(
        root, "core", "geo", "src", "commonMain", "composeResources", "files", "geo", "countries.json")

    if sys.argv[1] == "--update-nearby":
        target = sys.argv[2] if len(sys.argv) > 2 else default_target
        with open(target, encoding="utf-8") as f:
            atlas = json.load(f)
        add_nearby(atlas)
        write(atlas, target)
        print(f"Updated nearby countries in {target}")
        return

    source = sys.argv[1]
    target = sys.argv[2] if len(sys.argv) > 2 else default_target

    with open(source, encoding="utf-8") as f:
        data = json.load(f)
    atlas = build(data["features"])
    add_nearby(atlas)

    write(atlas, target)

    askable = [c for c in atlas["countries"] if c["askable"]]
    by_continent = {}
    for c in askable:
        by_continent[c["continent"]] = by_continent.get(c["continent"], 0) + 1
    points = sum(len(r) // 2 for c in atlas["countries"] for r in c["rings"])
    print(f"Wrote {target}")
    print(f"  {len(atlas['countries'])} shapes, {len(askable)} askable, {points} points, "
          f"{os.path.getsize(target) // 1024} KB")
    for continent, count in sorted(by_continent.items()):
        print(f"  {continent}: {count}")


if __name__ == "__main__":
    main()
