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
     *
     * @param recheckWhileInUseUntil if set and, when it fires, the user is busy in another app
     *   (screen on, unlocked), don't ring yet: look again a minute later, until this moment.
     */
    suspend fun scheduleFollowUp(
        followUpId: String,
        at: Instant,
        recheckWhileInUseUntil: Instant? = null,
    ): Result<Instant, ScheduleError>

    /** @param reason written to the ring log, so a missing re-ring can be explained. */
    suspend fun cancelFollowUp(followUpId: String, reason: String = "")
}

enum class ScheduleError : Error {
    /** Android 12: the user turned off "Alarms & reminders" for the app. */
    EXACT_ALARMS_NOT_ALLOWED,
    UNKNOWN,
}

/** How a scheduled alarm should behave when it fires. */
data class RingOptions(
    /** Ring quietly, e.g. the wake-up check after the user is already up. */
    val soft: Boolean = false,
    /** See [AlarmScheduler.scheduleFollowUp]. */
    val recheckWhileInUseUntil: Instant? = null,
)

/** The thin native part: hand one exact ring time to the OS, or take it back. */
interface PlatformAlarmClock {
    fun set(alarmId: String, at: Instant, options: RingOptions = RingOptions()): EmptyResult<ScheduleError>
    fun cancel(alarmId: String)

    /**
     * Registers a saved alarm whose next ring is [next]. Android: one ring at a time (the
     * receiver registers the next). iOS: AlarmKit repeats weekly alarms itself, because the
     * app isn't woken when an alarm rings, so it gets the whole alarm.
     */
    fun setAlarm(alarm: Alarm, next: Instant): EmptyResult<ScheduleError> = set(alarm.id, next)
}

/**
 * Whether the wake-up screen is showing right now. The platform's ringing screen keeps this
 * up to date; a follow-up that fires while it's visible always rings, because the morning
 * flow's own touch tracking already decided the user went quiet.
 */
object WakeUpScreenState {
    @kotlin.concurrent.Volatile
    var isVisible: Boolean = false
}
