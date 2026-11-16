package dev.victorc.sunup.feature.progress.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Needs the WakeSession and Review repositories and Clock (dataModule). */
val progressModule = module {
    viewModel { ProgressViewModel(sessions = get(), reviews = get(), clock = get()) }
}
