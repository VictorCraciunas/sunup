package dev.victorc.sunup.feature.alarms.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

class AlarmTextTest {

    @Test
    fun repeatDaysReadNaturally() {
        assertThat(repeatText(emptySet())).isEqualTo("Once")
        assertThat(repeatText(WEEK.toSet())).isEqualTo("Every day")
        assertThat(repeatText(WEEKDAYS)).isEqualTo("Mon–Fri")
        assertThat(repeatText(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))).isEqualTo("Sat–Sun")
        assertThat(repeatText(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)))
            .isEqualTo("Mon–Thu")
        assertThat(repeatText(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))).isEqualTo("Mon, Wed, Fri")
        assertThat(repeatText(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))).isEqualTo("Mon, Tue")
    }

    @Test
    fun timesHaveTwoDigits() {
        assertThat(timeText(LocalTime(7, 5))).isEqualTo("07:05")
    }

    @Test
    fun countdownRoundsUpToTheMinute() {
        assertThat(ringsInText(8.hours + 18.minutes + 30.seconds)).isEqualTo("Rings in 8 h 19 min")
        assertThat(ringsInText(45.minutes)).isEqualTo("Rings in 45 min")
        assertThat(ringsInText(2.hours)).isEqualTo("Rings in 2 h")
        assertThat(ringsInText(20.seconds)).isEqualTo("Rings in under a minute")
    }
}
