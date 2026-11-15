package dev.victorc.sunup.core.domain.learning

import kotlinx.datetime.LocalDate

data class Deck(
    val id: String,
    val name: String,
    val kind: DeckKind,
    /** Language being learned, e.g. "es". Language decks only. */
    val targetLanguage: String? = null,
    /** The user's own language, e.g. "en". */
    val nativeLanguage: String? = null,
    /** No longer used: cards are always asked as written. Kept so old saved decks still load. */
    val bothDirections: Boolean = false,
    val newCardsPerDay: Int = DEFAULT_NEW_CARDS_PER_DAY,
    /** Which games Pip may use for this deck. */
    val allowedGames: Set<Game> = Game.entries.toSet(),
    /** Study decks: Pip spaces cards so each is seen again before this date. */
    val examDate: LocalDate? = null,
    /** Set when the deck came from a built-in pack. */
    val builtInPackId: String? = null,
) {
    companion object {
        const val DEFAULT_NEW_CARDS_PER_DAY = 3
    }
}

enum class DeckKind {
    LANGUAGE,
    STUDY,
    TRIVIA,

    /**
     * Built-in: one note per country (front = name, back = country code), played as Name the
     * country and Find on the map. The region is the deck's [Deck.builtInPackId], see [GeoRegion.packId].
     */
    GEOGRAPHY,
}
