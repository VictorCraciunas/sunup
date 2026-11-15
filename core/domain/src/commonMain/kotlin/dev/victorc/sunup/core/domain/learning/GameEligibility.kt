package dev.victorc.sunup.core.domain.learning

/** Which games exist for cards, and how geography decks pick theirs. */
object GameEligibility {

    /** The games a user can pick for a card, in the order the picker shows them. */
    val CARD_GAMES: List<Game> = listOf(
        Game.PICK_MEANING,
        Game.TRUE_FALSE,
        Game.TYPE_IT,
        Game.FILL_THE_GAP,
        Game.MATCH_PAIRS,
    )

    /** Pick from 4 and Type it can also be asked the other way round (answer → question). */
    fun canReverse(game: Game): Boolean = game == Game.PICK_MEANING || game == Game.TYPE_IT

    /**
     * Geography: a country that's new or was just missed is named from four; once known,
     * it's found on the map with no help. [allowed] is the deck's allowed games.
     */
    fun mapGame(state: ReviewState, allowed: Set<Game>): Game? {
        val preferred = if (state.level == MasteryLevel.RECOGNIZE) Game.NAME_ON_MAP else Game.FIND_ON_MAP
        val other = if (preferred == Game.NAME_ON_MAP) Game.FIND_ON_MAP else Game.NAME_ON_MAP
        return listOf(preferred, other).firstOrNull { it in allowed }
    }
}
