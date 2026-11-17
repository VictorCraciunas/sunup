package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.morning.WeatherCondition
import dev.victorc.sunup.core.domain.morning.WeatherToday
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads MET Norway's Locationforecast 2.0 ("compact") answer into today's weather:
 * the temperature now, the sky now, and when rain (or snow) starts later today.
 * https://api.met.no/weatherapi/locationforecast/2.0/documentation
 */
object MetNorwayForecast {

    /**
     * The forecast address for a rough location. Two decimals: about 1 km, enough for the
     * weather (MET asks for no more than four), and all that leaves the phone.
     */
    fun url(latitude: Double, longitude: Double): String =
        "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${twoDecimals(latitude)}&lon=${twoDecimals(longitude)}"

    private fun twoDecimals(value: Double): String {
        val hundredths = (value * 100).roundToInt()
        val sign = if (hundredths < 0) "-" else ""
        val size = abs(hundredths)
        return "$sign${size / 100}.${(size % 100).toString().padStart(2, '0')}"
    }

    /** Null when the answer can't be read. */
    fun parse(json: String, now: Instant, zone: TimeZone): WeatherToday? = try {
        read(json, now, zone)
    } catch (e: Exception) {
        null
    }

    private fun read(json: String, now: Instant, zone: TimeZone): WeatherToday? {
        val series = Json.parseToJsonElement(json).jsonObject["properties"]?.jsonObject
            ?.get("timeseries")?.jsonArray ?: return null
        val points = series.mapNotNull { element ->
            val entry = element.jsonObject
            val time = entry["time"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) } ?: return@mapNotNull null
            val data = entry["data"]?.jsonObject ?: return@mapNotNull null
            Point(
                time = time,
                temperature = data.path("instant", "details")?.get("air_temperature")?.jsonPrimitive?.doubleOrNull,
                symbol = data.path("next_1_hours", "summary")?.get("symbol_code")?.jsonPrimitive?.contentOrNull,
            )
        }.sortedBy { it.time }

        // The hour we're in: the last point at or before now (the forecast starts at the current hour).
        val current = points.lastOrNull { it.time <= now } ?: points.firstOrNull() ?: return null
        val temperature = current.temperature ?: return null
        val today = now.toLocalDateTime(zone).date
        val laterToday = points.filter { it.time > current.time && it.time.toLocalDateTime(zone).date == today }

        val condition = conditionOf(current.symbol ?: laterToday.firstOrNull { it.symbol != null }?.symbol) ?: return null
        val wetLater = if (condition.isWet) null else laterToday.firstOrNull { conditionOf(it.symbol)?.isWet == true }
        return WeatherToday(
            temperatureC = temperature.roundToInt(),
            condition = condition,
            wetFromHour = wetLater?.time?.toLocalDateTime(zone)?.hour,
            wetKind = if (condition.isWet) condition else wetLater?.let { conditionOf(it.symbol) },
            wetNow = condition.isWet,
        )
    }

    /** "partlycloudy_day", "lightrainshowers", "heavysnowandthunder"… → the plain sky. */
    fun conditionOf(symbol: String?): WeatherCondition? {
        val code = symbol?.substringBefore('_') ?: return null
        return when {
            "thunder" in code -> WeatherCondition.THUNDER
            "sleet" in code -> WeatherCondition.SLEET
            "snow" in code -> WeatherCondition.SNOW
            "rain" in code -> WeatherCondition.RAIN
            code == "fog" -> WeatherCondition.FOG
            code == "partlycloudy" -> WeatherCondition.PARTLY_CLOUDY
            code == "cloudy" -> WeatherCondition.CLOUDY
            code == "fair" -> WeatherCondition.FAIR
            code == "clearsky" -> WeatherCondition.CLEAR
            else -> null
        }
    }

    private fun JsonObject.path(vararg keys: String): JsonObject? =
        keys.fold(this as JsonObject?) { obj, key -> obj?.get(key)?.jsonObject }

    private data class Point(val time: Instant, val temperature: Double?, val symbol: String?)
}
