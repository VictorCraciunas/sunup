package dev.victorc.sunup.core.alarm

import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Error
import dev.victorc.sunup.core.domain.Result
import kotlin.time.Instant

/** The shared alarm API from the PRD. Android and iOS each plug in a [PlatformAlarmClock]. */
interface AlarmScheduler {
    /** Saves [alarm] and registers its next ring time. Success holds that time, or null when the alarm is off. */
    suspend fun schedule(alarm: Alarm): Result<Instant?, ScheduleError>

    suspend fun cancel(alarmId: String)

    /** Called once an alarm has started ringing: turns a one-off alarm off, or registers the next ring of a repeating one. */
    suspend fun onAlarmRang(alarmId: String)

    /** Registers every saved alarm again, e.g. after a reboot or a time zone change. */
    suspend fun rescheduleAll(reason: String)
}

enum class ScheduleError : Error {
    /** Android 12: the user turned off "Alarms & reminders" for the app. */
    EXACT_ALARMS_NOT_ALLOWED,
    UNKNOWN,
}

/** The thin native part: hand one exact ring time to the OS, or take it back. */
interface PlatformAlarmClock {
    fun set(alarmId: String, at: Instant): EmptyResult<ScheduleError>
    fun cancel(alarmId: String)
}
