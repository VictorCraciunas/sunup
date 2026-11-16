package dev.victorc.sunup.core.domain.wakeup

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.ReviewContext
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class ProgressTest {

    private val zone = TimeZone.UTC
    // Monday 16 to Sunday 22 November 2026.
    private val week = DayRange.weekOf(LocalDate(2026, 11, 19))

    private fun session(day: Int, outcome: WakeOutcome, cards: Int = 1, bonus: Int = 0, up: String? = null) = WakeSession(
        id = "s$day$outcome",
        alarmId = "a",
        date = LocalDate(2026, 11, day),
        rangAt = Instant.parse("2026-11-${day.toString().padStart(2, '0')}T07:00:00Z"),
        outcome = outcome,
        upAt = up?.let { Instant.parse("2026-11-${day.toString().padStart(2, '0')}T${it}:00Z") },
        silencingCards = cards,
        bonusCards = bonus,
    )

    private fun review(note: String, day: Int, grade: Grade) = ReviewRecord(
        id = "$note$day$grade",
        noteId = note,
        direction = Direction.FORWARD,
        game = Game.PICK_MEANING,
        grade = grade,
        context = ReviewContext.SILENCING,
        reviewedAt = Instant.parse("2026-11-${day.toString().padStart(2, '0')}T07:01:00Z"),
    )

    @Test
    fun weekRunsMondayToSunday() {
        assertThat(week.from).isEqualTo(LocalDate(2026, 11, 16))
        assertThat(week.until).isEqualTo(LocalDate(2026, 11, 22))
    }

    @Test
    fun monthCoversEveryDay() {
        val month = DayRange.monthOf(LocalDate(2026, 2, 10))
        assertThat(month.days.size).isEqualTo(28)
    }

    @Test
    fun bestStreakSkipsDaysOffAndStopsAtMisses() {
        val sessions = listOf(
            session(2, WakeOutcome.UP), session(3, WakeOutcome.UP), session(6, WakeOutcome.UP), // run of 3 (4th, 5th off)
            session(9, WakeOutcome.STOPPED_WITHOUT_CARD),
            session(10, WakeOutcome.UP), session(11, WakeOutcome.UP),
        )
        assertThat(Progress.bestStreak(sessions)).isEqualTo(3)
    }

    @Test
    fun cardsPerDayAddsBonusAndFillsEmptyDays() {
        val sessions = listOf(session(16, WakeOutcome.UP, cards = 1, bonus = 2), session(18, WakeOutcome.UP, cards = 3))
        assertThat(Progress.cardsPerDay(sessions, week).map { it.second }).containsExactly(3, 0, 3, 0, 0, 0, 0)
    }

    @Test
    fun averageWakeUp() {
        val sessions = listOf(session(16, WakeOutcome.UP, up = "07:00"), session(17, WakeOutcome.UP, up = "07:04"))
        assertThat(Progress.averageWakeUp(sessions, week, zone)).isEqualTo(LocalTime(7, 2))
        assertThat(Progress.averageWakeUp(emptyList(), week, zone)).isNull()
    }

    @Test
    fun morningsCountsSunrisesOfAlarmDays() {
        val sessions = listOf(
            session(16, WakeOutcome.UP),
            session(17, WakeOutcome.STOPPED_WITHOUT_CARD),
            session(18, WakeOutcome.RINGING),
            session(18, WakeOutcome.UP),
        )
        assertThat(Progress.mornings(sessions, week)).isEqualTo(2 to 3)
    }

    @Test
    fun learnedCountsFirstRightAnswers() {
        val reviews = listOf(
            review("a", 10, Grade.GOOD), // learned last week
            review("a", 17, Grade.GOOD),
            review("b", 16, Grade.AGAIN),
            review("b", 18, Grade.GOOD), // learned this week, on the second try
            review("c", 19, Grade.GOOD), // learned this week
        )
        assertThat(Progress.learned(reviews, week, zone)).isEqualTo(2)
    }

    @Test
    fun rememberedLooksOnlyAtCardsSeenBefore() {
        val reviews = listOf(
            review("a", 10, Grade.GOOD),
            review("a", 17, Grade.GOOD), // came back: right
            review("b", 11, Grade.GOOD),
            review("b", 18, Grade.AGAIN), // came back: forgot
            review("c", 19, Grade.GOOD), // new: doesn't count
        )
        assertThat(Progress.remembered(reviews, week, zone)).isEqualTo(50)
        assertThat(Progress.remembered(listOf(review("c", 19, Grade.GOOD)), week, zone)).isNull()
    }
}
