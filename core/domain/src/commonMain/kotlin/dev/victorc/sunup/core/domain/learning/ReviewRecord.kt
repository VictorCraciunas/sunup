package dev.victorc.sunup.core.domain.learning

import kotlin.time.Duration
import kotlin.time.Instant

/** One answered card, kept forever for stats and streaks. */
data class ReviewRecord(
    val id: String,
    val noteId: String,
    val direction: Direction,
    val game: Game,
    val grade: Grade,
    val context: ReviewContext,
    val reviewedAt: Instant,
    val duration: Duration? = null,
)

enum class ReviewContext {
    /** The card that stopped the alarm. */
    SILENCING,

    /** The optional quiet cards after waking up. */
    BONUS,

    /** "Practice now", outside the alarm. */
    PRACTICE,
}
