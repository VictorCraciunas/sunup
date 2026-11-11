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

    /**
     * A one-time, soft ring at an exact moment, e.g. the 60-second wake-up check.
     * Not saved as an alarm, so it doesn't come back after a reboot. Calling it again with
     * the same [followUpId] moves it.
     */
    suspend fun scheduleFollowUp(followUpId: String, at: Instant): Result<Instant, ScheduleError>

    suspend fun cancelFollowUp(followUpId: String)
}

enum class ScheduleError : Error {
    /** Android 12: the user turned off "Alarms & reminders" for the app. */
    EXACT_ALARMS_NOT_ALLOWED,
    UNKNOWN,
}

/** The thin native part: hand one exact ring time to the OS, or take it back. */
interface PlatformAlarmClock {
    /** @param soft ring quietly, e.g. for the wake-up check after the user is already up. */
    fun set(alarmId: String, at: Instant, soft: Boolean = false): EmptyResult<ScheduleError>
    fun cancel(alarmId: String)
}
