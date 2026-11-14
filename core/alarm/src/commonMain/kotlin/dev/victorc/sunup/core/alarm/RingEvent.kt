package dev.victorc.sunup.core.alarm

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * One line of the ring log. The log is how we measure reliability on real phones:
 * every SCHEDULED should be followed by a RANG close to its [expected] time.
 */
data class RingEvent(
    val alarmId: String,
    val type: Type,
    val at: Instant,
    /** When the alarm was supposed to ring (SCHEDULED, RANG). */
    val expected: Instant? = null,
    val note: String = "",
) {
    enum class Type {
        SCHEDULED,
        SCHEDULE_FAILED,
        RANG,
        STOPPED,
        TIMED_OUT,
        RESCHEDULED_ALL,

        /** A follow-up (e.g. the wake-up check) was taken back before it rang. */
        CANCELLED,

        /** A follow-up fired but didn't ring, e.g. the user was busy in another app. */
        SKIPPED,
    }

    /** How late the alarm rang. Only meaningful for [Type.RANG]. */
    val delay: Duration? get() = expected?.let { at - it }
}
