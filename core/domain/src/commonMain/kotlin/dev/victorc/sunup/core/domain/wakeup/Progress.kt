package dev.victorc.sunup.core.domain.wakeup

import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** A stretch of days, [from] to [until] inclusive (a week or a month). */
data class DayRange(val from: LocalDate, val until: LocalDate) {
    operator fun contains(day: LocalDate): Boolean = day in from..until

    val days: List<LocalDate>
        get() = generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= until }.toList()

    companion object {
        /** Monday to Sunday of [day]'s week. */
        fun weekOf(day: LocalDate): DayRange {
            val monday = day.minus(day.dayOfWeek.ordinal, DateTimeUnit.DAY)
            return DayRange(monday, monday.plus(6, DateTimeUnit.DAY))
        }

        /** The 1st to the last day of [day]'s month. */
        fun monthOf(day: LocalDate): DayRange {
            val first = LocalDate(day.year, day.month, 1)
            return DayRange(first, first.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
        }
    }
}

/**
 * The Progress tab's numbers (docs/HOW_IT_WORKS.md, "Progress"). Built from the morning
 * records ([WakeSession]) and the answer log ([ReviewRecord]).
 */
object Progress {

    /** The longest run of sunrises ever, skipping days without an alarm. For the streak medals. */
    fun bestStreak(sessions: List<WakeSession>): Int {
        val byDay = sessions.groupBy { it.date }
        var best = 0
        var run = 0
        byDay.keys.sorted().forEach { day ->
            if (byDay.getValue(day).any { it.outcome == WakeOutcome.UP }) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    /** Cards answered each morning of [range] (bonus included), in day order. */
    fun cardsPerDay(sessions: List<WakeSession>, range: DayRange): List<Pair<LocalDate, Int>> {
        val byDay = sessions.filter { it.date in range }.groupBy { it.date }
        return range.days.map { day -> day to byDay[day].orEmpty().sumOf { it.silencingCards + it.bonusCards } }
    }

    /** The average time the alarm was answered in [range]; null without a sunrise. */
    fun averageWakeUp(sessions: List<WakeSession>, range: DayRange, zone: TimeZone): LocalTime? {
        val minutes = sessions
            .filter { it.date in range }
            .mapNotNull { it.upAt?.toLocalDateTime(zone)?.time }
            .map { it.hour * 60 + it.minute }
        if (minutes.isEmpty()) return null
        val average = minutes.average().toInt()
        return LocalTime(average / 60, average % 60)
    }

    /** Sunrises in [range], and the days an alarm rang (sunrise or missed). */
    fun mornings(sessions: List<WakeSession>, range: DayRange): Pair<Int, Int> {
        val byDay = sessions.filter { it.date in range }.groupBy { it.date }
        val sunrises = byDay.values.count { day -> day.any { it.outcome == WakeOutcome.UP } }
        return sunrises to byDay.size
    }

    /** New cards answered right for the first time in [range]. */
    fun learned(reviews: List<ReviewRecord>, range: DayRange, zone: TimeZone): Int =
        reviews
            .filter { it.grade == Grade.GOOD }
            .groupBy { it.noteId }
            .values
            .count { answers -> answers.minOf { it.reviewedAt }.toLocalDateTime(zone).date in range }

    /**
     * Of the cards seen before, the share answered right the first time in [range], 0–100.
     * Null when no card came back in that time.
     */
    fun remembered(reviews: List<ReviewRecord>, range: DayRange, zone: TimeZone): Int? {
        val seen = mutableSetOf<String>()
        var again = 0
        var right = 0
        reviews.sortedBy { it.reviewedAt }.forEach { review ->
            val inRange = review.reviewedAt.toLocalDateTime(zone).date in range
            if (inRange && review.noteId in seen) {
                again++
                if (review.grade == Grade.GOOD) right++
            }
            seen += review.noteId
        }
        return if (again == 0) null else (right * 100 + again / 2) / again
    }

    /** When [range] starts, for queries. */
    fun startOf(range: DayRange, zone: TimeZone): Instant = range.from.atStartOfDayIn(zone)
}
