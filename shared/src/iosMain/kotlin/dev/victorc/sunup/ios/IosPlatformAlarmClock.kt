package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.Alarm
import dev.victorc.sunup.core.alarm.NextRingTime
import dev.victorc.sunup.core.alarm.PlatformAlarmClock
import dev.victorc.sunup.core.alarm.RingOptions
import dev.victorc.sunup.core.alarm.ScheduleError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber

/** Suffix of the soft alarm that follows each ring on iOS, in case Apple's Stop was used instead of the card. */
const val STOP_FOLLOW_UP_SUFFIX = ":stop"

/** How long after the alarm the follow-up rings if the card wasn't answered. */
private val STOP_FOLLOW_UP_DELAY = 3.minutes

/**
 * The alarm engine's platform clock on iOS: AlarmKit through [AlarmKitBridge].
 *
 * Apple's alarm screen always has a Stop button, and the app isn't told when an alarm rings.
 * So every alarm also gets a soft follow-up 3 minutes later (PRD, "Android vs iOS"); answering
 * the card cancels it ([answered]). Weekly alarms are left to AlarmKit to repeat, so they keep
 * ringing even if the app isn't opened for days.
 */
class IosPlatformAlarmClock(
    private val alarmKit: AlarmKitBridge,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : PlatformAlarmClock {

    override fun set(alarmId: String, at: Instant, options: RingOptions): EmptyResult<ScheduleError> {
        if (!alarmKit.isAuthorized()) return Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED)
        alarmKit.scheduleOnce(alarmId, at.toEpochMilliseconds(), options.soft)
        return Result.Success(Unit)
    }

    override fun cancel(alarmId: String) {
        alarmKit.cancel(alarmId)
        alarmKit.cancel(alarmId + STOP_FOLLOW_UP_SUFFIX)
    }

    override fun setAlarm(alarm: Alarm, next: Instant): EmptyResult<ScheduleError> {
        if (!alarmKit.isAuthorized()) return Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED)
        if (alarm.isOneOff) {
            alarmKit.scheduleOnce(alarm.id, next.toEpochMilliseconds(), soft = false)
        } else {
            alarmKit.scheduleWeekly(alarm.id, alarm.time.hour, alarm.time.minute, alarm.repeatDays.map { it.isoDayNumber }.sorted())
        }
        scheduleStopFollowUp(alarm.id, next)
        return Result.Success(Unit)
    }

    /**
     * The card was answered: this morning's follow-up isn't needed. The next ring gets its own.
     * [alarm] is null for rings that aren't saved alarms (test alarm, wake-up check).
     */
    fun answered(alarmId: String, alarm: Alarm?) {
        alarmKit.cancel(alarmId + STOP_FOLLOW_UP_SUFFIX)
        if (alarm == null || !alarm.enabled) return
        val next = NextRingTime.after(alarm, clock.now(), timeZone()) ?: return
        alarmKit.scheduleOnce(alarmId + STOP_FOLLOW_UP_SUFFIX, (next + STOP_FOLLOW_UP_DELAY).toEpochMilliseconds(), soft = true)
    }

    /**
     * A follow-up for the ring at [next]. One that's already waiting for an earlier ring (it rang,
     * the card wasn't answered yet) is kept, so opening and closing the app can't skip it.
     */
    private fun scheduleStopFollowUp(alarmId: String, next: Instant) {
        val id = alarmId + STOP_FOLLOW_UP_SUFFIX
        val at = (next + STOP_FOLLOW_UP_DELAY).toEpochMilliseconds()
        val waiting = alarmKit.scheduledAt(id)
        if (waiting > clock.now().toEpochMilliseconds() && waiting < at) return
        alarmKit.scheduleOnce(id, at, soft = true)
    }
}
