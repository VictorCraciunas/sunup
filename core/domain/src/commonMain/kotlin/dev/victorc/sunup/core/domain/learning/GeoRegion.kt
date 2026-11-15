package dev.victorc.sunup.core.domain.learning

/**
 * A map region for the geography game: which countries can be asked, and the part of the
 * globe the map shows (in degrees). Continents use Natural Earth's names.
 */
enum class GeoRegion(
    val title: String,
    /** Empty means every continent. */
    val continents: Set<String>,
    val lonMin: Double,
    val lonMax: Double,
    val latMin: Double,
    val latMax: Double,
) {
    EUROPE("Europe", setOf("Europe"), lonMin = -25.0, lonMax = 45.0, latMin = 34.0, latMax = 71.5),
    AFRICA("Africa", setOf("Africa"), lonMin = -19.0, lonMax = 53.0, latMin = -36.0, latMax = 38.5),
    ASIA("Asia", setOf("Asia"), lonMin = 25.0, lonMax = 147.0, latMin = -11.0, latMax = 56.0),
    NORTH_AMERICA("North and Central America", setOf("North America"), lonMin = -170.0, lonMax = -50.0, latMin = 6.0, latMax = 75.0),
    SOUTH_AMERICA("South America", setOf("South America"), lonMin = -84.0, lonMax = -33.0, latMin = -56.0, latMax = 13.5),
    OCEANIA("Oceania", setOf("Oceania"), lonMin = 110.0, lonMax = 180.0, latMin = -48.0, latMax = 1.0),
    WORLD("The World", emptySet(), lonMin = -180.0, lonMax = 180.0, latMin = -57.0, latMax = 83.5),
    ;

    fun includes(continent: String): Boolean = continents.isEmpty() || continent in continents

    /** Stored on the deck as [Deck.builtInPackId]. */
    val packId: String get() = "geo-${name.lowercase()}"

    companion object {
        fun fromPackId(packId: String?): GeoRegion? = entries.firstOrNull { it.packId == packId }
    }
}
