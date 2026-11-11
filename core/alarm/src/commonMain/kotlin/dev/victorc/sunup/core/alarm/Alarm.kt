package dev.victorc.sunup.core.alarm

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One alarm as the engine sees it. Deliberately small: decks, wake-up modes and the rest
 * live in the app's database and never block the alarm from ringing.
 *
 * @param repeatDays empty means "ring once", then the alarm turns itself off.
 */
data class Alarm(
    val id: String,
    val time: LocalTime,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
) {
    val isOneOff: Boolean get() = repeatDays.isEmpty()
}
