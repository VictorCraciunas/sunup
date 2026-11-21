package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.Alarm
import dev.victorc.sunup.core.alarm.NextRingTime
import dev.victorc.sunup.core.alarm.PlatformAlarmClock
import dev.victorc.sunup.core.alarm.RingOptions
import dev.victorc.sunup.core.alarm.ScheduleError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber

/** Start of the suffix of the alarms that follow each ring on iOS, in case Apple's Stop was used instead of the card. */
const val STOP_FOLLOW_UP_SUFFIX = ":stop"

/**
 * How far apart the follow-ups set ahead with every ring are, and how long "Wake up with Pip"
 * gives to unlock and answer the card before the alarm comes back.
 */
private val STOP_FOLLOW_UP_EVERY = 3.minutes

/** After Apple's Stop the alarm comes back this fast, again and again: stopping isn't worth it. */
private val AFTER_STOP_EVERY = 30.seconds

/**
 * How many follow-ups are set ahead. Every dismissal sets them again from that moment
 * ([IosPlatformAlarmClock.dismissedWithoutCard]), so they never run out; the ones already
 * waiting are the safety net if that code doesn't get to run.
 */
private const val STOP_FOLLOW_UPS_AHEAD = 5

private val STOP_FOLLOW_UP_PATTERN = Regex(":stop[0-9]*$")

/** ":stop", ":stop2" … ":stop5". The first has no number, so one set by an earlier build is still found and cancelled. */
private fun stopFollowUpId(alarmId: String, index: Int): String =
    alarmId + STOP_FOLLOW_UP_SUFFIX + if (index == 0) "" else "${index + 1}"

/** The alarm a ring belongs to: a stop follow-up's id without its suffix, any other id as it is. */
fun alarmIdOfRing(ringId: String): String = ringId.replace(STOP_FOLLOW_UP_PATTERN, "")

/**
 * The alarm engine's platform clock on iOS: AlarmKit through [AlarmKitBridge].
 *
 * Apple's alarm screen always has a Stop button, and the app isn't told when an alarm rings.
 * So every alarm also gets follow-ups (PRD, "Android vs iOS"): a few are set ahead, 3 minutes
 * apart; every dismissal sets them again from that moment ([dismissedWithoutCard]), 30 seconds
 * apart after Stop and 3 minutes apart after "Wake up with Pip"; only answering the card cancels
 * them ([answered]). Dismissing just brings the alarm back. Weekly alarms are left to AlarmKit
 * to repeat, so they keep ringing even if the app isn't opened for days.
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
        repeat(STOP_FOLLOW_UPS_AHEAD) { index ->
            alarmKit.scheduleOnce(stopFollowUpId(alarmId, index), followUpTime(next, index), soft = true)
        }
    }

    /**
     * The alarm or one of its follow-ups was dismissed on Apple's alarm screen and the card isn't
     * answered yet. The follow-ups start over from now, so dismissing never runs out of rings.
     *
     * [stopped]: Apple's Stop was used, so the alarm is back in 30 seconds, and every 30 seconds
     * after that. Otherwise it was "Wake up with Pip": 3 minutes, enough to unlock and answer.
     */
    fun dismissedWithoutCard(alarmId: String, stopped: Boolean) {
        if (!alarmKit.isAuthorized()) return
        val now = clock.now()
        val every = if (stopped) AFTER_STOP_EVERY else STOP_FOLLOW_UP_EVERY
        repeat(STOP_FOLLOW_UPS_AHEAD) { index ->
            alarmKit.scheduleOnce(stopFollowUpId(alarmId, index), followUpTime(now, index, every), soft = true)
        }
    }

    private fun cancelStopFollowUps(alarmId: String) {
        repeat(STOP_FOLLOW_UPS_AHEAD) { index -> alarmKit.cancel(stopFollowUpId(alarmId, index)) }
    }

    /** Follow-up number [index] (0 = the first), counted from [from]: a ring, or the moment one was dismissed. */
    private fun followUpTime(from: Instant, index: Int, every: Duration = STOP_FOLLOW_UP_EVERY): Long =
        (from + every * (index + 1)).toEpochMilliseconds()

    /**
     * The follow-ups for the ring at [next]. One that's already waiting for an earlier ring (it
     * rang, the card wasn't answered yet) is kept, so opening and closing the app can't skip it.
     */
    private fun scheduleStopFollowUps(alarmId: String, next: Instant) {
        val now = clock.now().toEpochMilliseconds()
        repeat(STOP_FOLLOW_UPS_AHEAD) { index ->
            val id = stopFollowUpId(alarmId, index)
            val at = followUpTime(next, index)
            val waiting = alarmKit.scheduledAt(id)
            if (waiting > now && waiting < at) return@repeat
            alarmKit.scheduleOnce(id, at, soft = true)
        }
    }
}
