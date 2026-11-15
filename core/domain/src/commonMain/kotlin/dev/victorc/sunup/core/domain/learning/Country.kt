package dev.victorc.sunup.core.domain.learning

/** A country the geography game can ask for. Shapes live in :core:geo; this is just the facts. */
data class Country(
    /** ISO 3166-1 alpha-2, e.g. "PT" (Kosovo: "XK"). Stored as the note's back. */
    val id: String,
    val name: String,
    /** Natural Earth continent, e.g. "Europe". See [GeoRegion.includes]. */
    val continent: String,
    val areaKm2: Long,
)
