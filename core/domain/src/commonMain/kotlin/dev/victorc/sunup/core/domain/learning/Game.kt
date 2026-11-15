package dev.victorc.sunup.core.domain.learning

/**
 * The nine card games. Same as the PRD, except Fill the gap replaces Flip & rate:
 * every game now has a real right answer instead of rating yourself.
 */
enum class Game(
    val isPlus: Boolean,
    /** Fast and easy enough to stop a ringing alarm. */
    val canSilenceAlarm: Boolean,
    /** Built from the user's notes. Quick math is generated instead. */
    val usesNotes: Boolean = true,
) {
    PICK_MEANING(isPlus = false, canSilenceAlarm = true),
    TRUE_FALSE(isPlus = false, canSilenceAlarm = true),
    /** Pick the missing word in the note's example sentence. */
    FILL_THE_GAP(isPlus = false, canSilenceAlarm = false),
    TYPE_IT(isPlus = false, canSilenceAlarm = false),
    MATCH_PAIRS(isPlus = false, canSilenceAlarm = false),

    /** Fallback silencing card when a deck has nothing known yet (e.g. the first mornings). */
    QUICK_MATH(isPlus = false, canSilenceAlarm = true, usesNotes = false),

    /** Geography decks: tap the named country on an interactive map. */
    FIND_ON_MAP(isPlus = false, canSilenceAlarm = true),

    BUILD_THE_SENTENCE(isPlus = true, canSilenceAlarm = false),
    LISTEN_AND_PICK(isPlus = true, canSilenceAlarm = false),

    /** Always offers "Can't speak now". */
    SAY_IT(isPlus = true, canSilenceAlarm = false),
}
