package dev.victorc.sunup.feature.onboarding.presentation

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Needs AlarmService and AlarmScheduler (alarmModule), the settings, GeoDeckSeeder and Clock
 * (dataModule, settingsStoreModule) and GeoAtlasSource (wakeUpModule).
 */
val onboardingModule = module {
    viewModel { OnboardingGateViewModel(settings = get()) }
    viewModel {
        OnboardingViewModel(
            alarms = get(),
            scheduler = get(),
            settings = get(),
            atlasSource = get(),
            geoSeeder = get(),
            clock = get(),
        )
    }
}
