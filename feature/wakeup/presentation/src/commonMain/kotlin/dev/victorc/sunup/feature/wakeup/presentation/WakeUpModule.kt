package dev.victorc.sunup.feature.wakeup.presentation

import dev.victorc.sunup.core.domain.learning.CardBuilder
import dev.victorc.sunup.core.geo.BundledGeoAtlasSource
import dev.victorc.sunup.core.geo.GeoAtlasSource
import dev.victorc.sunup.feature.wakeup.domain.WakeSessionPlanner
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Needs the repositories and Clock from the data module. */
val wakeUpModule = module {
    single { CardBuilder() }
    // One copy of the map in memory, read on first use.
    single<GeoAtlasSource> { BundledGeoAtlasSource() }
    factory { WakeSessionPlanner(decks = get(), notes = get(), reviews = get(), cards = get(), clock = get()) }
    viewModel { params ->
        WakeUpViewModel(
            alarmId = params.get(),
            planner = get(),
            alarms = get(),
            reviews = get(),
            sessions = get(),
            scheduler = get(),
            atlasSource = get(),
            clock = get(),
        )
    }
}
