package dev.victorc.sunup.core.domain.wakeup

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class StreaksTest {

    // A Thursday.
    private val today = LocalDate(2026, 11, 19)

    private fun session(day: Int, outcome: WakeOutcome, cards: Int = 1) = WakeSession(
        id = "s$day-$outcome",
        alarmId = "a",
        date = LocalDate(2026, 11, day),
        rangAt = Instant.fromEpochSeconds(0),
        outcome = outcome,
        silencingCards = if (outcome == WakeOutcome.UP) cards else 0,
    )

    @Test
    fun noMorningsMeansNoStreak() {
        assertThat(Streaks.current(emptyList(), today)).isEqualTo(0)
    }

    @Test
    fun sunrisesInARowCount() {
        val sessions = listOf(session(17, WakeOutcome.UP), session(18, WakeOutcome.UP), session(19, WakeOutcome.UP))
        assertThat(Streaks.current(sessions, today)).isEqualTo(3)
    }

    @Test
    fun daysWithoutAnAlarmAreSkipped() {
        // 14th and 15th: a weekend with no alarm.
        val sessions = listOf(session(13, WakeOutcome.UP), session(16, WakeOutcome.UP), session(18, WakeOutcome.UP))
        assertThat(Streaks.current(sessions, today)).isEqualTo(3)
    }

    @Test
    fun aMissedMorningResetsIt() {
        val sessions = listOf(
            session(15, WakeOutcome.UP),
            session(16, WakeOutcome.STOPPED_WITHOUT_CARD),
            session(17, WakeOutcome.UP),
            session(18, WakeOutcome.UP),
        )
        assertThat(Streaks.current(sessions, today)).isEqualTo(2)
    }

    @Test
    fun anAlarmLeftRingingIsMissed() {
        val sessions = listOf(session(17, WakeOutcome.UP), session(18, WakeOutcome.RINGING))
        assertThat(Streaks.current(sessions, today)).isEqualTo(0)
    }

    @Test
    fun todayCantBreakItBeforeASunrise() {
        val sessions = listOf(session(18, WakeOutcome.UP), session(19, WakeOutcome.RINGING))
        assertThat(Streaks.current(sessions, today)).isEqualTo(1)
    }

    @Test
    fun anyAnsweredAlarmMakesTheDayASunrise() {
        val sessions = listOf(session(18, WakeOutcome.STOPPED_WITHOUT_CARD), session(18, WakeOutcome.UP))
        assertThat(Streaks.current(sessions, today)).isEqualTo(1)
    }

    @Test
    fun weekRunsMondayToSunday() {
        val sessions = listOf(session(16, WakeOutcome.UP), session(17, WakeOutcome.STOPPED_WITHOUT_CARD))
        assertThat(Streaks.week(sessions, today).map { it.second }).containsExactly(
            DayMark.SUNRISE, // Mon 16
            DayMark.MISSED, // Tue 17
            DayMark.NO_ALARM, // Wed 18
            DayMark.TODAY, // Thu 19
            DayMark.LATER,
            DayMark.LATER,
            DayMark.LATER,
        )
    }

    @Test
    fun cardsAnsweredAddsBonusCards() {
        val sessions = listOf(session(18, WakeOutcome.UP, cards = 3).copy(bonusCards = 2), session(19, WakeOutcome.UP))
        assertThat(Streaks.cardsAnswered(sessions)).isEqualTo(6)
    }
}
