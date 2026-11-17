package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.morning.CalendarSource
import dev.victorc.sunup.core.domain.morning.WeatherSource
import org.koin.dsl.module

/** Weather and calendar for "You're up!" on iOS. Needs Clock (dataModule). */
val morningInfoModule = module {
    factory<WeatherSource> { IosWeatherSource(clock = get()) }
    factory<CalendarSource> { IosCalendarSource(clock = get()) }
}
