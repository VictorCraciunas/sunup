package dev.victorc.sunup.core.domain.learning

import kotlinx.datetime.LocalDate

/** How well the user knows one note in one direction, and when it's due next. */
data class ReviewState(
    val noteId: String,
    val direction: Direction,
    /** Index into [SpacedRepetition.INTERVAL_DAYS]; [NEW_STEP] until the first review. */
    val step: Int = NEW_STEP,
    val level: MasteryLevel = MasteryLevel.RECOGNIZE,
    /** Null until the first review. */
    val due: LocalDate? = null,
    val reviews: Int = 0,
    /** Times the user forgot it after having learned it. */
    val lapses: Int = 0,
) {
    val isNew: Boolean get() = step == NEW_STEP

    /** Learned at least once and not just forgotten: safe to use as the alarm-silencing card. */
    val isKnown: Boolean get() = step >= 1

    companion object {
        const val NEW_STEP = -1
    }
}

/** The level-up path from the PRD: a word starts as Pick the meaning, then Match pairs, then Type it. */
enum class MasteryLevel { RECOGNIZE, MATCH, RECALL }

/** How a review went: right first time (GOOD), close (HARD), or needed another go (AGAIN). */
enum class Grade { AGAIN, HARD, GOOD }
