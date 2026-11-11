package dev.victorc.sunup.core.alarm

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class NextRingTimeTest {

    private val zone = TimeZone.of("Europe/Bucharest")

    private fun local(text: String): Instant = LocalDateTime.parse(text).toInstant(zone)

    private fun alarm(
        time: String,
        repeatDays: Set<DayOfWeek> = emptySet(),
        enabled: Boolean = true,
    ) = Alarm(id = "a", time = LocalTime.parse(time), repeatDays = repeatDays, enabled = enabled)

    @Test
    fun oneOffLaterToday() {
        // Tuesday 3 Nov 2026, 06:00
        val now = local("2026-11-03T06:00")

        assertThat(NextRingTime.after(alarm("07:00"), now, zone))
            .isEqualTo(local("2026-11-03T07:00"))
    }

    @Test
    fun oneOffAlreadyPassedRingsTomorrow() {
        val now = local("2026-11-03T08:00")

        assertThat(NextRingTime.after(alarm("07:00"), now, zone))
            .isEqualTo(local("2026-11-04T07:00"))
    }

    @Test
    fun exactlyNowRingsTomorrowSoAFiredAlarmIsNotScheduledTwice() {
        val now = local("2026-11-03T07:00")

        assertThat(NextRingTime.after(alarm("07:00"), now, zone))
            .isEqualTo(local("2026-11-04T07:00"))
    }

    @Test
    fun weekdayAlarmOnFridayEveningSkipsTheWeekend() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        // Friday 6 Nov 2026, 20:00
        val now = local("2026-11-06T20:00")

        assertThat(NextRingTime.after(alarm("07:00", weekdays), now, zone))
            .isEqualTo(local("2026-11-09T07:00"))
    }

    @Test
    fun weeklyAlarmWhoseTimePassedTodayRingsNextWeek() {
        // Tuesday 3 Nov 2026, 08:00; only rings on Tuesdays
        val now = local("2026-11-03T08:00")

        assertThat(NextRingTime.after(alarm("07:00", setOf(DayOfWeek.TUESDAY)), now, zone))
            .isEqualTo(local("2026-11-10T07:00"))
    }

    @Test
    fun disabledAlarmNeverRings() {
        val now = local("2026-11-03T06:00")

        assertThat(NextRingTime.after(alarm("07:00", enabled = false), now, zone)).isNull()
    }

    @Test
    fun timeSkippedBySpringForwardStillRings() {
        // Romania moves 03:00 -> 04:00 on Sunday 29 March 2026, so 03:30 doesn't exist.
        val now = local("2026-03-28T22:00")

        val ring = NextRingTime.after(alarm("03:30"), now, zone)

        // 01:30 UTC is 04:30 local, an hour of wall-clock time after the skipped 03:30.
        assertThat(ring).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"))
    }

    @Test
    fun usualMorningOnTheDayClocksGoForwardIsUnaffected() {
        val now = local("2026-03-28T22:00")

        assertThat(NextRingTime.after(alarm("07:00"), now, zone))
            .isEqualTo(Instant.parse("2026-03-29T04:00:00Z"))
    }
}
