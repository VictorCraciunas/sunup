package dev.victorc.sunup.core.alarm

import dev.victorc.sunup.core.domain.Error
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone

enum class AlarmError : Error {
    /** Android 12: "Alarms & reminders" is off for Sunup, so it can't ring on time. */
    EXACT_ALARMS_NOT_ALLOWED,

    /** The phone refused the alarm for another reason. */
    NOT_SCHEDULED,

    /** It will ring, but the app couldn't save its settings (deck, mode). */
    NOT_SAVED,
}

/** An alarm and when it rings next. */
data class NextAlarm(val alarm: WakeAlarm, val at: Instant)

/**
 * The one place alarms are changed. Two copies exist: the app's database (deck, mode, all
 * settings, what the screens show) and the alarm engine's small store (time, days, on/off),
 * which is what actually rings and is readable before the first unlock.
 *
 * Rule #1: the engine is always told first, so a failed database write can never cost an alarm.
 */
class AlarmService(
    private val alarms: WakeAlarmRepository,
    private val scheduler: AlarmScheduler,
    private val engine: AlarmStore,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    fun observe(): Flow<List<WakeAlarm>> = alarms.observeAll()

    suspend fun get(id: String): WakeAlarm? = alarms.get(id)

    /** Schedules [alarm] and saves it. Success holds the next ring time (null when it's off). */
    suspend fun save(alarm: WakeAlarm): Result<Instant?, AlarmError> {
        val next = when (val scheduled = scheduler.schedule(alarm.toEngine())) {
            is Result.Success -> scheduled.data
            is Result.Error -> return Result.Error(scheduled.error.toAlarmError())
        }
        return when (alarms.save(alarm)) {
            is Result.Success -> Result.Success(next)
            is Result.Error -> Result.Error(AlarmError.NOT_SAVED)
        }
    }

    suspend fun setEnabled(alarm: WakeAlarm, enabled: Boolean): Result<Instant?, AlarmError> =
        save(alarm.copy(enabled = enabled))

    /** Stops it ringing first, then removes it from the app. */
    suspend fun delete(id: String) {
        scheduler.cancel(id)
        alarms.delete(id)
    }

    /**
     * Brings the app's copy in line with the engine, e.g. when the alarm list opens:
     * - a one-off alarm that rang was switched off by the engine: show it as off;
     * - an alarm the engine has but the app doesn't (its save failed): add it, so it can be
     *   seen and turned off;
     * - an alarm that's on in the app but missing from the engine: schedule it again.
     */
    suspend fun syncWithEngine() {
        val inEngine = engine.alarms().associateBy { it.id }
        val inApp = alarms.observeAll().first()
        val appIds = inApp.map { it.id }.toSet()

        for (alarm in inApp) {
            val engineCopy = inEngine[alarm.id]
            when {
                engineCopy == null && alarm.enabled -> scheduler.schedule(alarm.toEngine())
                engineCopy != null && engineCopy.enabled != alarm.enabled -> alarms.save(alarm.copy(enabled = engineCopy.enabled))
            }
        }
        inEngine.values
            .filter { it.id !in appIds }
            .forEach { orphan ->
                alarms.save(
                    WakeAlarm(
                        id = orphan.id,
                        time = orphan.time,
                        repeatDays = orphan.repeatDays,
                        enabled = orphan.enabled,
                        label = orphan.label,
                    ),
                )
            }
    }

    /** The alarm that rings soonest, or null when none are on. */
    fun next(list: List<WakeAlarm>, now: Instant = clock.now()): NextAlarm? = list
        .mapNotNull { alarm -> NextRingTime.after(alarm.toEngine(), now, timeZone())?.let { NextAlarm(alarm, it) } }
        .minByOrNull { it.at }

    fun now(): Instant = clock.now()
}

/** The engine only needs time, days and on/off. */
internal fun WakeAlarm.toEngine() = Alarm(
    id = id,
    time = time,
    repeatDays = repeatDays,
    enabled = enabled,
    label = label,
)

private fun ScheduleError.toAlarmError(): AlarmError = when (this) {
    ScheduleError.EXACT_ALARMS_NOT_ALLOWED -> AlarmError.EXACT_ALARMS_NOT_ALLOWED
    ScheduleError.UNKNOWN -> AlarmError.NOT_SCHEDULED
}
