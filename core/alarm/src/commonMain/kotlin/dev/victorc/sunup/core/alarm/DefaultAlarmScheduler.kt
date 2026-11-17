package dev.victorc.sunup.core.alarm

import dev.victorc.sunup.core.domain.Result
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class DefaultAlarmScheduler(
    private val store: AlarmStore,
    private val platform: PlatformAlarmClock,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : AlarmScheduler {

    override suspend fun schedule(alarm: Alarm): Result<Instant?, ScheduleError> {
        store.save(alarm)
        return register(alarm)
    }

    override suspend fun cancel(alarmId: String) {
        platform.cancel(alarmId)
        store.delete(alarmId)
    }

    override suspend fun onAlarmRang(alarmId: String) {
        val alarm = store.alarm(alarmId) ?: return
        if (alarm.isOneOff) {
            store.save(alarm.copy(enabled = false))
        } else {
            register(alarm)
        }
    }

    override suspend fun rescheduleAll(reason: String) {
        val now = clock.now()
        store.record(RingEvent(alarmId = "*", type = RingEvent.Type.RESCHEDULED_ALL, at = now, note = reason))

        for (alarm in store.alarms()) {
            if (alarm.enabled && wasMissedOneOff(alarm, now)) {
                // Its time passed while the phone was off. Don't ring it tomorrow by surprise.
                store.save(alarm.copy(enabled = false))
                continue
            }
            register(alarm)
        }
    }

    override suspend fun scheduleFollowUp(
        followUpId: String,
        at: Instant,
        recheckWhileInUseUntil: Instant?,
    ): Result<Instant, ScheduleError> {
        val now = clock.now()
        val options = RingOptions(soft = true, recheckWhileInUseUntil = recheckWhileInUseUntil)
        return when (val result = platform.set(followUpId, at, options)) {
            is Result.Success -> {
                store.record(RingEvent(followUpId, RingEvent.Type.SCHEDULED, at = now, expected = at, note = "follow-up"))
                Result.Success(at)
            }
            is Result.Error -> {
                store.record(
                    RingEvent(followUpId, RingEvent.Type.SCHEDULE_FAILED, at = now, expected = at, note = result.error.name),
                )
                result
            }
        }
    }

    override suspend fun cancelFollowUp(followUpId: String, reason: String) {
        platform.cancel(followUpId)
        store.record(RingEvent(followUpId, RingEvent.Type.CANCELLED, at = clock.now(), note = reason))
    }

    private suspend fun register(alarm: Alarm): Result<Instant?, ScheduleError> {
        val now = clock.now()
        val next = NextRingTime.after(alarm, now, timeZone())
        if (next == null) {
            platform.cancel(alarm.id)
            return Result.Success(null)
        }

        return when (val result = platform.setAlarm(alarm, next)) {
            is Result.Success -> {
                store.record(RingEvent(alarm.id, RingEvent.Type.SCHEDULED, at = now, expected = next))
                Result.Success(next)
            }
            is Result.Error -> {
                store.record(
                    RingEvent(alarm.id, RingEvent.Type.SCHEDULE_FAILED, at = now, expected = next, note = result.error.name),
                )
                result
            }
        }
    }

    private suspend fun wasMissedOneOff(alarm: Alarm, now: Instant): Boolean {
        if (!alarm.isOneOff) return false
        val events = store.events().filter { it.alarmId == alarm.id }
        val lastScheduled = events.lastOrNull { it.type == RingEvent.Type.SCHEDULED }?.expected ?: return false
        val rang = events.any { it.type == RingEvent.Type.RANG && it.at >= lastScheduled }
        return !rang && lastScheduled <= now
    }
}
