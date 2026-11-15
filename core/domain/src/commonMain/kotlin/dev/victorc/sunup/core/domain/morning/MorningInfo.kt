package dev.victorc.sunup.core.domain.morning

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.Result
import kotlinx.datetime.LocalTime

/**
 * Extras for "You're up!": today's weather and the next calendar event. Both are optional,
 * switched on by the user on Home, and only fetched after the alarm has stopped (Rule #1).
 */
data class WeatherToday(
    val temperatureC: Int,
    val condition: WeatherCondition,
    /** The first hour later today with rain, snow or sleet; null when it stays dry (or it's wet already). */
    val wetFromHour: Int? = null,
    /** What falls from [wetFromHour] (or now, when [wetNow]). */
    val wetKind: WeatherCondition? = null,
    val wetNow: Boolean = false,
)

enum class WeatherCondition {
    CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SLEET, SNOW, THUNDER;

    /** Rain, sleet, snow or thunder. */
    val isWet: Boolean get() = this == RAIN || this == SLEET || this == SNOW || this == THUNDER
}

data class CalendarEvent(val title: String, val start: LocalTime)

interface WeatherSource {
    /** Today's weather where the phone roughly is. An error when it's off, offline or the location is unknown. */
    suspend fun today(): Result<WeatherToday, DataError.Network>
}

interface CalendarSource {
    /** The next timed event later today, or null (none, or no calendar access). */
    suspend fun nextEventToday(): CalendarEvent?
}

/** Which extras the user switched on. */
data class MorningInfoSettings(val weather: Boolean = false, val calendar: Boolean = false)
