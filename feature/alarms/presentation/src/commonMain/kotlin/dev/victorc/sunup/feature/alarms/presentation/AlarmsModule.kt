package dev.victorc.sunup.feature.alarms.presentation

import dev.victorc.sunup.feature.alarms.domain.AlarmService
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Needs the alarm engine (alarmModule) and the repositories and Clock (dataModule). */
val alarmsModule = module {
    factory { AlarmService(alarms = get(), scheduler = get(), engine = get(), clock = get()) }
    viewModel { AlarmListViewModel(service = get(), decks = get()) }
    viewModel { params -> AlarmEditorViewModel(alarmId = params.getOrNull(), service = get(), decks = get()) }
}
