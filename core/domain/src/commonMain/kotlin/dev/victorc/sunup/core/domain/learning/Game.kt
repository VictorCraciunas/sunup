package dev.victorc.sunup.core.domain.learning

/** The nine card games from the PRD. */
enum class Game(
    val isPlus: Boolean,
    /** Fast and easy enough to stop a ringing alarm. */
    val canSilenceAlarm: Boolean,
    /** Built from the user's notes. Quick math is generated instead. */
    val usesNotes: Boolean = true,
) {
    PICK_MEANING(isPlus = false, canSilenceAlarm = true),
    TRUE_FALSE(isPlus = false, canSilenceAlarm = true),
    FLIP_AND_RATE(isPlus = false, canSilenceAlarm = false),
    TYPE_IT(isPlus = false, canSilenceAlarm = false),
    MATCH_PAIRS(isPlus = false, canSilenceAlarm = false),

    /** Fallback silencing card when a deck has nothing known yet (e.g. the first mornings). */
    QUICK_MATH(isPlus = false, canSilenceAlarm = true, usesNotes = false),

    BUILD_THE_SENTENCE(isPlus = true, canSilenceAlarm = false),
    LISTEN_AND_PICK(isPlus = true, canSilenceAlarm = false),

    /** Always offers "Can't speak now". */
    SAY_IT(isPlus = true, canSilenceAlarm = false),
}
