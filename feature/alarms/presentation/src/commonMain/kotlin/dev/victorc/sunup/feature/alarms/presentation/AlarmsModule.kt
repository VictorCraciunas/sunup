package dev.victorc.sunup.feature.alarms.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Needs AlarmService (alarmModule) and the repositories (dataModule). */
val alarmsModule = module {
    viewModel { HomeViewModel(service = get(), decks = get(), notes = get(), reviews = get(), sessions = get(), settings = get()) }
    viewModel { AlarmListViewModel(service = get(), decks = get()) }
    viewModel { params -> AlarmEditorViewModel(alarmId = params.getOrNull(), service = get(), decks = get()) }
}
