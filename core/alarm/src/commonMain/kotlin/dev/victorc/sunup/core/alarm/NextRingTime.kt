package dev.victorc.sunup.core.alarm

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

object NextRingTime {

    /**
     * The first moment strictly after [now] when [alarm] should ring, in [zone].
     *
     * A time that doesn't exist because the clocks go forward (e.g. 03:30 on the night of
     * the spring change) rings at the same wall-clock distance after the jump, so the
     * user is still woken up. Returns null when the alarm is off.
     */
    fun after(alarm: Alarm, now: Instant, zone: TimeZone): Instant? {
        if (!alarm.enabled) return null

        val today = now.toLocalDateTime(zone).date
        // 8 days covers "today, but the time has passed" for a weekly alarm.
        for (dayOffset in 0..7) {
            val date = today.plus(dayOffset, DateTimeUnit.DAY)
            if (!alarm.isOneOff && date.dayOfWeek !in alarm.repeatDays) continue

            val candidate = date.atTime(alarm.time).toInstant(zone)
            if (candidate > now) return candidate
        }
        return null
    }
}
