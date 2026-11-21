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

/** Start of the suffix of the alarms that follow each ring on iOS, in case Apple's Stop was used instead of the card. */
const val STOP_FOLLOW_UP_SUFFIX = ":stop"

/** How long after the alarm each follow-up rings if the card wasn't answered. Only the card cancels them. */
private val STOP_FOLLOW_UP_DELAYS = listOf(3.minutes, 6.minutes, 9.minutes)

private val STOP_FOLLOW_UP_PATTERN = Regex(":stop[0-9]*$")

/** ":stop", ":stop2", ":stop3". The first has no number, so one set by an earlier build is still found and cancelled. */
private fun stopFollowUpId(alarmId: String, index: Int): String =
    alarmId + STOP_FOLLOW_UP_SUFFIX + if (index == 0) "" else "${index + 1}"

/** The alarm a ring belongs to: a stop follow-up's id without its suffix, any other id as it is. */
fun alarmIdOfRing(ringId: String): String = ringId.replace(STOP_FOLLOW_UP_PATTERN, "")

/**
 * The alarm engine's platform clock on iOS: AlarmKit through [AlarmKitBridge].
 *
 * Apple's alarm screen always has a Stop button, and the app isn't told when an alarm rings.
 * So every alarm also gets follow-ups 3, 6 and 9 minutes later (PRD, "Android vs iOS"); only
 * answering the card cancels them ([answered]), so Stop just brings the alarm back. Weekly
 * alarms are left to AlarmKit to repeat, so they keep ringing even if the app isn't opened for days.
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
        cancelStopFollowUps(alarmId)
    }

    override fun setAlarm(alarm: Alarm, next: Instant): EmptyResult<ScheduleError> {
        if (!alarmKit.isAuthorized()) return Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED)
        if (alarm.isOneOff) {
            alarmKit.scheduleOnce(alarm.id, next.toEpochMilliseconds(), soft = false)
        } else {
            alarmKit.scheduleWeekly(alarm.id, alarm.time.hour, alarm.time.minute, alarm.repeatDays.map { it.isoDayNumber }.sorted())
        }
        scheduleStopFollowUps(alarm.id, next)
        return Result.Success(Unit)
    }

    /**
     * The card was answered: this morning's follow-ups aren't needed. The next ring gets its own.
     * [alarm] is null for rings that aren't saved alarms (test alarm, wake-up check). A one-off
     * alarm has no next ring, so it gets none (they would ring on their own tomorrow).
     */
    fun answered(alarmId: String, alarm: Alarm?) {
        cancelStopFollowUps(alarmId)
        if (alarm == null || !alarm.enabled || alarm.isOneOff) return
        val next = NextRingTime.after(alarm, clock.now(), timeZone()) ?: return
        STOP_FOLLOW_UP_DELAYS.forEachIndexed { index, delay ->
            alarmKit.scheduleOnce(stopFollowUpId(alarmId, index), (next + delay).toEpochMilliseconds(), soft = true)
        }
    }

    private fun cancelStopFollowUps(alarmId: String) {
        STOP_FOLLOW_UP_DELAYS.indices.forEach { index -> alarmKit.cancel(stopFollowUpId(alarmId, index)) }
    }

    /**
     * The follow-ups for the ring at [next]. One that's already waiting for an earlier ring (it
     * rang, the card wasn't answered yet) is kept, so opening and closing the app can't skip it.
     */
    private fun scheduleStopFollowUps(alarmId: String, next: Instant) {
        val now = clock.now().toEpochMilliseconds()
        STOP_FOLLOW_UP_DELAYS.forEachIndexed { index, delay ->
            val id = stopFollowUpId(alarmId, index)
            val at = (next + delay).toEpochMilliseconds()
            val waiting = alarmKit.scheduledAt(id)
            if (waiting > now && waiting < at) return@forEachIndexed
            alarmKit.scheduleOnce(id, at, soft = true)
        }
    }
}
