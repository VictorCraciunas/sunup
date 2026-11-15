package dev.victorc.sunup.feature.alarms.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Needs AlarmService (alarmModule) and the DeckRepository (dataModule). */
val alarmsModule = module {
    viewModel { AlarmListViewModel(service = get(), decks = get()) }
    viewModel { params -> AlarmEditorViewModel(alarmId = params.getOrNull(), service = get(), decks = get()) }
}
