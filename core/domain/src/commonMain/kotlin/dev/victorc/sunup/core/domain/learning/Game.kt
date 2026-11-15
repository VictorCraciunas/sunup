package dev.victorc.sunup.core.domain.learning

/**
 * Every game. A card plays one of the first five, picked by the user; any card can stop
 * the alarm. Geography decks play the map games; Quick math is the fallback.
 */
enum class Game(
    val isPlus: Boolean,
    /** Built from the user's cards. Quick math is generated instead. */
    val usesNotes: Boolean = true,
) {
    /** A question and 4 answers: the right one plus 3 wrong (the user's own, or borrowed from other cards). */
    PICK_MEANING(isPlus = false),

    /** A statement the user wrote; tap True or False. */
    TRUE_FALSE(isPlus = false),

    /** A sentence with one word missing; pick it from 4. */
    FILL_THE_GAP(isPlus = false),

    /** Type the answer; checked forgivingly. */
    TYPE_IT(isPlus = false),

    /** One card holds up to 4 pairs; connect each left with its right. */
    MATCH_PAIRS(isPlus = false),

    /** Fallback when no card can be played (no deck, nothing in it, database locked after a reboot). */
    QUICK_MATH(isPlus = false, usesNotes = false),

    /** Geography decks: tap the named country on an interactive map. */
    FIND_ON_MAP(isPlus = false),

    /** Geography decks: one country is highlighted on the map; pick its name from four. */
    NAME_ON_MAP(isPlus = false),

    BUILD_THE_SENTENCE(isPlus = true),
    LISTEN_AND_PICK(isPlus = true),

    /** Always offers "Can't speak now". */
    SAY_IT(isPlus = true),
}
