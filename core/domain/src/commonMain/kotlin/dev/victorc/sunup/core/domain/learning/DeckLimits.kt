package dev.victorc.sunup.core.domain.learning

/**
 * The free plan's deck limit (ROADMAP "Free limit = number of decks"). Made or imported decks
 * count; built-in packs (maps, the sample deck) don't. Plus will lift it; not built yet.
 */
object DeckLimits {
    const val FREE_DECKS = 5

    /** The user's own decks: everything that isn't a built-in pack. */
    fun ownDecks(decks: List<Deck>): Int = decks.count { it.builtInPackId == null }

    fun canAddDeck(decks: List<Deck>): Boolean = ownDecks(decks) < FREE_DECKS
}
