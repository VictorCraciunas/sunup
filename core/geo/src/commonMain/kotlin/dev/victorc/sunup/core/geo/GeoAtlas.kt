package dev.victorc.sunup.core.geo

import dev.victorc.sunup.core.domain.learning.Country
import dev.victorc.sunup.core.domain.learning.GeoRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One outline on the map: a country, or a territory that is drawn but never asked. */
class GeoShape(
    val id: String,
    val name: String,
    val continent: String,
    val areaKm2: Long,
    /** False for territories (Faroe Islands, Puerto Rico…): drawn, tappable, never asked. */
    val askable: Boolean,
    /** Too small to tap: the map draws a circle around [labelLon]/[labelLat]. */
    val small: Boolean,
    val labelLon: Float,
    val labelLat: Float,
    /** Each ring is lon, lat, lon, lat… in degrees. Holes are just more rings (even-odd). */
    val rings: List<FloatArray>,
    /** Closest askable countries by border, closest first. Empty for territories. */
    val nearby: List<String> = emptyList(),
) {
    val minLon: Float
    val maxLon: Float
    val minLat: Float
    val maxLat: Float

    init {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        rings.forEach { ring ->
            for (i in ring.indices step 2) {
                minX = minOf(minX, ring[i])
                maxX = maxOf(maxX, ring[i])
                minY = minOf(minY, ring[i + 1])
                maxY = maxOf(maxY, ring[i + 1])
            }
        }
        if (rings.isEmpty()) {
            minX = labelLon; maxX = labelLon; minY = labelLat; maxY = labelLat
        }
        minLon = minX
        maxLon = maxX
        minLat = minY
        maxLat = maxY
    }

    /**
     * Bounds of the biggest ring only, for zooming to a country: France without French Guiana,
     * the US without Hawaii. [minLon, maxLon, minLat, maxLat].
     */
    val mainBounds: FloatArray by lazy {
        rings
            .map { ring ->
                var minX = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (i in ring.indices step 2) {
                    minX = minOf(minX, ring[i])
                    maxX = maxOf(maxX, ring[i])
                    minY = minOf(minY, ring[i + 1])
                    maxY = maxOf(maxY, ring[i + 1])
                }
                floatArrayOf(minX, maxX, minY, maxY)
            }
            .maxByOrNull { (it[1] - it[0]) * (it[3] - it[2]) }
            ?: floatArrayOf(labelLon, labelLon, labelLat, labelLat)
    }

    val country: Country
        get() = Country(id = id, name = name, continent = continent, areaKm2 = areaKm2, nearby = nearby)

    /** Even-odd test over every ring, so holes (Lesotho inside South Africa) work. */
    fun contains(lon: Float, lat: Float): Boolean {
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) return false
        var inside = false
        rings.forEach { ring ->
            val points = ring.size / 2
            var j = points - 1
            for (i in 0 until points) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                    inside = !inside
                }
                j = i
            }
        }
        return inside
    }
}

/** Every outline, loaded once from the bundled map file. */
class GeoAtlas(val shapes: List<GeoShape>) {

    private val byId = shapes.associateBy { it.id }
    private val smallestFirst = shapes.sortedBy { it.areaKm2 }

    fun shape(id: String): GeoShape? = byId[id]

    /** Countries the game may ask in [region], biggest first (the easy ones come first). */
    fun countries(region: GeoRegion): List<Country> = shapes
        .filter { it.askable && region.includes(it.continent) }
        .sortedByDescending { it.areaKm2 }
        .map { it.country }

    /**
     * What's under a tap at [lon]/[lat]. Small countries win when the tap lands within their
     * circle (an ellipse of [radiusLon] × [radiusLat] degrees, because the map is stretched),
     * then outlines, smallest first so an enclave beats the country around it.
     */
    fun shapeAt(lon: Float, lat: Float, radiusLon: Float = 0f, radiusLat: Float = 0f): GeoShape? {
        if (radiusLon > 0f && radiusLat > 0f) {
            val nearSmall = shapes
                .filter { it.small }
                .map { it to ellipseDistance(it, lon, lat, radiusLon, radiusLat) }
                .filter { (_, distance) -> distance <= 1f }
                .minByOrNull { (_, distance) -> distance }
            if (nearSmall != null) return nearSmall.first
        }
        return smallestFirst.firstOrNull { it.contains(lon, lat) }
    }

    private fun ellipseDistance(shape: GeoShape, lon: Float, lat: Float, rx: Float, ry: Float): Float {
        val dx = (shape.labelLon - lon) / rx
        val dy = (shape.labelLat - lat) / ry
        return dx * dx + dy * dy
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses the file written by tools/geo/build_countries.py. */
        fun parse(text: String): GeoAtlas {
            val file = json.decodeFromString<AtlasFile>(text)
            val scale = file.scale.toFloat()
            val shapes = file.countries.map { dto ->
                GeoShape(
                    id = dto.id,
                    name = dto.name,
                    continent = dto.continent,
                    areaKm2 = dto.area,
                    askable = dto.askable,
                    small = dto.small,
                    labelLon = dto.label[0] / scale,
                    labelLat = dto.label[1] / scale,
                    rings = dto.rings.map { ring -> FloatArray(ring.size) { ring[it] / scale } },
                    nearby = dto.near,
                )
            }
            return GeoAtlas(shapes)
        }
    }
}

@Serializable
private class AtlasFile(
    val scale: Int,
    val countries: List<CountryDto>,
)

@Serializable
private class CountryDto(
    val id: String,
    val name: String,
    val continent: String,
    val area: Long,
    val askable: Boolean,
    val small: Boolean,
    val label: List<Int>,
    val rings: List<List<Int>>,
    val near: List<String> = emptyList(),
)
