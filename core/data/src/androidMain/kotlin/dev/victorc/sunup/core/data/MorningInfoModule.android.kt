package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.morning.CalendarSource
import dev.victorc.sunup.core.domain.morning.WeatherSource
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Weather and calendar for "You're up!". Needs Clock (dataModule). */
val morningInfoModule = module {
    factory<WeatherSource> { MetNorwayWeatherSource(context = androidContext(), clock = get()) }
    factory<CalendarSource> { AndroidCalendarSource(context = androidContext(), clock = get()) }
}
