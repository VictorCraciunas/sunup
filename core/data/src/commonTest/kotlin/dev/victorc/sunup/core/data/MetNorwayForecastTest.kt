package dev.victorc.sunup.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.victorc.sunup.core.domain.morning.WeatherCondition
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class MetNorwayForecastTest {

    private val zone = TimeZone.UTC
    private val now = Instant.parse("2026-11-16T07:03:00Z")

    private fun forecast(vararg hours: Triple<String, Double, String>) = """
        {"type":"Feature","properties":{"meta":{"updated_at":"2026-11-16T06:00:00Z"},"timeseries":[
        ${hours.joinToString(",") { (time, temp, symbol) ->
            """{"time":"$time","data":{"instant":{"details":{"air_temperature":$temp}},"next_1_hours":{"summary":{"symbol_code":"$symbol"}}}}"""
        }}
        ]}}
    """.trimIndent()

    @Test
    fun readsTemperatureSkyAndRainLater() {
        val json = forecast(
            Triple("2026-11-16T07:00:00Z", 13.6, "clearsky_day"),
            Triple("2026-11-16T08:00:00Z", 14.2, "fair_day"),
            Triple("2026-11-16T18:00:00Z", 11.0, "lightrain"),
            Triple("2026-11-16T19:00:00Z", 10.0, "rain"),
        )
        val weather = MetNorwayForecast.parse(json, now, zone)

        assertThat(weather).isNotNull()
        assertThat(weather!!.temperatureC).isEqualTo(14)
        assertThat(weather.condition).isEqualTo(WeatherCondition.CLEAR)
        assertThat(weather.wetFromHour).isEqualTo(18)
        assertThat(weather.wetKind).isEqualTo(WeatherCondition.RAIN)
        assertThat(weather.wetNow).isFalse()
    }

    @Test
    fun dryAllDayHasNoWetHour() {
        val json = forecast(
            Triple("2026-11-16T07:00:00Z", 5.0, "partlycloudy_day"),
            Triple("2026-11-16T12:00:00Z", 8.0, "cloudy"),
            // Tomorrow's rain doesn't count.
            Triple("2026-11-17T09:00:00Z", 6.0, "rain"),
        )
        val weather = MetNorwayForecast.parse(json, now, zone)!!

        assertThat(weather.condition).isEqualTo(WeatherCondition.PARTLY_CLOUDY)
        assertThat(weather.wetFromHour).isNull()
    }

    @Test
    fun alreadyRaining() {
        val json = forecast(Triple("2026-11-16T07:00:00Z", 3.0, "heavyrainshowers_day"))
        val weather = MetNorwayForecast.parse(json, now, zone)!!

        assertThat(weather.wetNow).isTrue()
        assertThat(weather.wetKind).isEqualTo(WeatherCondition.RAIN)
        assertThat(weather.wetFromHour).isNull()
    }

    @Test
    fun snowAndThunderAreRecognised() {
        assertThat(MetNorwayForecast.conditionOf("lightsnowshowers_night")).isEqualTo(WeatherCondition.SNOW)
        assertThat(MetNorwayForecast.conditionOf("heavyrainandthunder")).isEqualTo(WeatherCondition.THUNDER)
        assertThat(MetNorwayForecast.conditionOf("sleet")).isEqualTo(WeatherCondition.SLEET)
        assertThat(MetNorwayForecast.conditionOf("fog")).isEqualTo(WeatherCondition.FOG)
    }

    @Test
    fun brokenAnswerGivesNothing() {
        assertThat(MetNorwayForecast.parse("not json", now, zone)).isNull()
        assertThat(MetNorwayForecast.parse("""{"properties":{}}""", now, zone)).isNull()
    }
}
