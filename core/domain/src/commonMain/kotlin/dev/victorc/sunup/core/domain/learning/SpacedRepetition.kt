package dev.victorc.sunup.core.domain.learning

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Decides when a card comes back. A simple ladder of intervals: getting it right climbs
 * one rung, "almost" stays on the rung, forgetting goes back to tomorrow.
 *
 * For a card already learned once: AGAIN → tomorrow, HARD → 3 days, GOOD → 8 days
 * (the PRD's Not yet / Almost / Knew it intervals).
 */
object SpacedRepetition {

    /** Days until the next review, per step. Tune here. */
    val INTERVAL_DAYS = listOf(1, 3, 8, 21, 45, 90)

    fun review(
        state: ReviewState,
        grade: Grade,
        today: LocalDate,
        examDate: LocalDate? = null,
    ): ReviewState {
        val step = when (grade) {
            Grade.AGAIN -> 0
            Grade.HARD -> if (state.isNew) 0 else state.step
            Grade.GOOD -> if (state.isNew) 1 else (state.step + 1).coerceAtMost(INTERVAL_DAYS.lastIndex)
        }
        val level = when (grade) {
            Grade.AGAIN -> state.level.previous()
            Grade.HARD -> state.level
            Grade.GOOD -> state.level.next()
        }
        val due = today.plus(INTERVAL_DAYS[step], DateTimeUnit.DAY)

        return state.copy(
            step = step,
            level = level,
            due = capForExam(due, today, examDate),
            reviews = state.reviews + 1,
            lapses = state.lapses + if (grade == Grade.AGAIN && !state.isNew) 1 else 0,
        )
    }

    /**
     * Before an exam, never schedule past the day before it. Halving the time left each
     * review means a card is seen several times before the exam, not just once.
     */
    private fun capForExam(due: LocalDate, today: LocalDate, examDate: LocalDate?): LocalDate {
        if (examDate == null || examDate <= today) return due
        val lastDayBefore = examDate.minus(1, DateTimeUnit.DAY)
        if (due <= lastDayBefore) return due
        val daysLeft = today.daysUntil(lastDayBefore)
        return today.plus(maxOf(1, daysLeft / 2), DateTimeUnit.DAY)
    }

    private fun MasteryLevel.next(): MasteryLevel =
        MasteryLevel.entries[(ordinal + 1).coerceAtMost(MasteryLevel.entries.lastIndex)]

    private fun MasteryLevel.previous(): MasteryLevel =
        MasteryLevel.entries[(ordinal - 1).coerceAtLeast(0)]
}
