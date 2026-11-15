package dev.victorc.sunup.core.domain.wakeup

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How a day looks in the streak. */
enum class DayMark {
    /** Woke up by answering. */
    SUNRISE,

    /** An alarm rang but wasn't answered (Emergency stop, or left ringing). */
    MISSED,

    /** No alarm rang. Never breaks a streak. */
    NO_ALARM,

    /** Today, before a sunrise. Can't break the streak yet. */
    TODAY,

    /** A day still to come this week. */
    LATER,
}

/**
 * The streak rules (docs/HOW_IT_WORKS.md, "Streaks"):
 * - a day with at least one sunrise adds 1;
 * - a day where an alarm rang and none was answered resets it to 0;
 * - a day without an alarm is skipped, so weekends off never break it;
 * - today only counts once it has a sunrise; until then it can't break the streak either.
 */
object Streaks {

    fun markFor(day: LocalDate, sessions: List<WakeSession>, today: LocalDate): DayMark {
        val onDay = sessions.filter { it.date == day }
        return when {
            onDay.any { it.outcome == WakeOutcome.UP } -> DayMark.SUNRISE
            day > today -> DayMark.LATER
            day == today -> DayMark.TODAY
            onDay.isNotEmpty() -> DayMark.MISSED
            else -> DayMark.NO_ALARM
        }
    }

    /** Sunrises in a row up to today, skipping days without an alarm. */
    fun current(sessions: List<WakeSession>, today: LocalDate): Int {
        val byDay = sessions.groupBy { it.date }
        val oldest = byDay.keys.minOrNull() ?: return 0
        var streak = 0
        var day = today
        while (day >= oldest) {
            when (markFor(day, byDay[day].orEmpty(), today)) {
                DayMark.SUNRISE -> streak++
                DayMark.MISSED -> return streak
                DayMark.NO_ALARM, DayMark.TODAY, DayMark.LATER -> Unit
            }
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }

    /** Monday to Sunday of the week [today] is in. */
    fun week(sessions: List<WakeSession>, today: LocalDate): List<Pair<LocalDate, DayMark>> {
        val monday = today.minus(today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal, DateTimeUnit.DAY)
        return (0 until 7).map { offset ->
            val day = monday.plus(offset, DateTimeUnit.DAY)
            day to markFor(day, sessions, today)
        }
    }

    /** Cards answered in every morning, bonus rounds included. */
    fun cardsAnswered(sessions: List<WakeSession>): Int = sessions.sumOf { it.silencingCards + it.bonusCards }
}
