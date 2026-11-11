package dev.victorc.sunup.core.alarm

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

private class FakePlatformAlarmClock : PlatformAlarmClock {
    val registered = mutableMapOf<String, Instant>()
    val soft = mutableSetOf<String>()
    var failWith: ScheduleError? = null

    override fun set(alarmId: String, at: Instant, soft: Boolean): EmptyResult<ScheduleError> {
        failWith?.let { return Result.Error(it) }
        registered[alarmId] = at
        if (soft) this.soft += alarmId else this.soft -= alarmId
        return Result.Success(Unit)
    }

    override fun cancel(alarmId: String) {
        registered.remove(alarmId)
    }
}

private class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

private class MemoryFile : StateFile {
    private var text: String? = null
    override fun read(): String? = text
    override fun write(text: String) {
        this.text = text
    }
}

class DefaultAlarmSchedulerTest {

    private val zone = TimeZone.of("Europe/Bucharest")
    private fun local(text: String): Instant = LocalDateTime.parse(text).toInstant(zone)

    private val store = FileAlarmStore(MemoryFile())
    private val platform = FakePlatformAlarmClock()
    private val clock = FixedClock(local("2026-11-03T06:00"))
    private val scheduler = DefaultAlarmScheduler(store, platform, clock) { zone }

    private val once = Alarm(id = "once", time = LocalTime(7, 0))
    private val weekdays = Alarm(
        id = "weekdays",
        time = LocalTime(7, 0),
        repeatDays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
    )

    @Test
    fun scheduleRegistersTheNextRingAndLogsIt() = runTest {
        val result = scheduler.schedule(once)

        assertThat(result).isEqualTo(Result.Success(local("2026-11-03T07:00")))
        assertThat(platform.registered["once"]).isEqualTo(local("2026-11-03T07:00"))
        assertThat(store.events().map { it.type }).containsExactly(RingEvent.Type.SCHEDULED)
    }

    @Test
    fun failedSchedulingIsReturnedAndLogged() = runTest {
        platform.failWith = ScheduleError.EXACT_ALARMS_NOT_ALLOWED

        val result = scheduler.schedule(once)

        assertThat(result).isEqualTo(Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED))
        assertThat(store.events().single().type).isEqualTo(RingEvent.Type.SCHEDULE_FAILED)
    }

    @Test
    fun oneOffAlarmTurnsOffAfterRinging() = runTest {
        scheduler.schedule(once)
        clock.now = local("2026-11-03T07:00")

        scheduler.onAlarmRang("once")

        assertThat(store.alarm("once")!!.enabled).isFalse()
    }

    @Test
    fun repeatingAlarmRegistersTomorrowAfterRinging() = runTest {
        scheduler.schedule(weekdays)
        clock.now = local("2026-11-03T07:00")

        scheduler.onAlarmRang("weekdays")

        assertThat(platform.registered["weekdays"]).isEqualTo(local("2026-11-04T07:00"))
    }

    @Test
    fun rescheduleAllRegistersEverySavedAlarm() = runTest {
        scheduler.schedule(once)
        scheduler.schedule(weekdays)
        platform.registered.clear()

        scheduler.rescheduleAll("BOOT_COMPLETED")

        assertThat(platform.registered.keys).isEqualTo(setOf("once", "weekdays"))
    }

    @Test
    fun oneOffMissedWhileThePhoneWasOffIsTurnedOffNotMovedToTomorrow() = runTest {
        scheduler.schedule(once)
        platform.registered.clear()
        // Phone was off from 06:30 to 09:00, so 07:00 never rang.
        clock.now = local("2026-11-03T09:00")

        scheduler.rescheduleAll("BOOT_COMPLETED")

        assertThat(store.alarm("once")!!.enabled).isFalse()
        assertThat(platform.registered.containsKey("once")).isFalse()
    }

    @Test
    fun cancelRemovesTheAlarmEverywhere() = runTest {
        scheduler.schedule(weekdays)

        scheduler.cancel("weekdays")

        assertThat(platform.registered.isEmpty()).isTrue()
        assertThat(store.alarms().isEmpty()).isTrue()
    }

    @Test
    fun followUpRingsSoftlyAtTheExactSecondAndIsNotSavedAsAnAlarm() = runTest {
        val at = local("2026-11-03T07:01:23")

        val result = scheduler.scheduleFollowUp("morning:check", at)

        assertThat(result).isEqualTo(Result.Success(at))
        assertThat(platform.registered["morning:check"]).isEqualTo(at)
        assertThat(platform.soft.contains("morning:check")).isTrue()
        assertThat(store.alarms().isEmpty()).isTrue()
    }

    @Test
    fun followUpCanBeMovedAndCancelled() = runTest {
        scheduler.scheduleFollowUp("morning:check", local("2026-11-03T07:01:00"))
        scheduler.scheduleFollowUp("morning:check", local("2026-11-03T07:02:00"))

        assertThat(platform.registered["morning:check"]).isEqualTo(local("2026-11-03T07:02:00"))

        scheduler.cancelFollowUp("morning:check")

        assertThat(platform.registered.containsKey("morning:check")).isFalse()
    }

    @Test
    fun followUpRingingDoesNotTouchSavedAlarms() = runTest {
        scheduler.schedule(weekdays)

        scheduler.onAlarmRang("morning:check")

        assertThat(store.alarm("weekdays")!!.enabled).isTrue()
    }

    @Test
    fun repeatingAlarmIsNeverConsideredMissed() = runTest {
        scheduler.schedule(weekdays)
        clock.now = clock.now + 1.days

        scheduler.rescheduleAll("BOOT_COMPLETED")

        assertThat(store.alarm("weekdays")!!.enabled).isTrue()
    }
}
