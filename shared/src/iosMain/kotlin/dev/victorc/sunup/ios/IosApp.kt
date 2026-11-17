package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.AlarmScheduler
import dev.victorc.sunup.core.alarm.AlarmService
import dev.victorc.sunup.core.alarm.AlarmStore
import dev.victorc.sunup.core.alarm.DefaultAlarmScheduler
import dev.victorc.sunup.core.alarm.FileAlarmStore
import dev.victorc.sunup.core.alarm.IosAlarmAuthorization
import dev.victorc.sunup.core.alarm.PlatformAlarmClock
import dev.victorc.sunup.core.data.SETTINGS_FILE_NAME
import dev.victorc.sunup.core.data.createSettingsDataStore
import dev.victorc.sunup.core.data.dataModule
import dev.victorc.sunup.core.data.morningInfoModule
import dev.victorc.sunup.core.database.SunupDatabase
import dev.victorc.sunup.core.database.buildSunupDatabase
import dev.victorc.sunup.core.database.iosDatabaseBuilder
import dev.victorc.sunup.feature.alarms.presentation.alarmsModule
import dev.victorc.sunup.feature.decks.presentation.decksModule
import dev.victorc.sunup.feature.onboarding.presentation.onboardingModule
import dev.victorc.sunup.feature.progress.presentation.progressModule
import dev.victorc.sunup.feature.wakeup.presentation.wakeUpModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module

private var started = false

/**
 * Starts Sunup on iOS. Called once by `iOSApp.swift` before the first screen, with the Swift
 * AlarmKit bridge. Registers every saved alarm with AlarmKit again (it turns off a one-off
 * alarm whose time has passed), like Android does after a reboot.
 */
fun startSunup(alarmKit: AlarmKitBridge) {
    if (started) return
    started = true

    val platform = IosPlatformAlarmClock(alarmKit)
    IosAlarmAuthorization.isAuthorized = { alarmKit.isAuthorized() }
    IosAlarmAuthorization.request = { onResult -> alarmKit.requestAuthorization(onResult) }

    val koin = startKoin {
        modules(
            iosAlarmModule(platform),
            iosDatabaseModule,
            iosSettingsStoreModule,
            morningInfoModule,
            dataModule,
            alarmsModule,
            decksModule,
            onboardingModule,
            progressModule,
            wakeUpModule,
        )
    }.koin

    val store = koin.get<AlarmStore>()
    IosMorning.clock = platform
    IosMorning.store = store
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        runCatching { koin.get<AlarmScheduler>().rescheduleAll(reason = "app started") }
    }
}

/** The alarm engine (Android's alarmModule): a JSON file in Application Support, and AlarmKit. */
private fun iosAlarmModule(platform: IosPlatformAlarmClock) = module {
    single<AlarmStore> { FileAlarmStore(IosStateFile(applicationSupportPath(ALARM_FILE_NAME))) }
    single<PlatformAlarmClock> { platform }
    single<AlarmScheduler> { DefaultAlarmScheduler(store = get(), platform = get()) }
    factory { AlarmService(alarms = get(), scheduler = get(), engine = get(), clock = get()) }
}

/** Android's databaseModule. Opened on first use. */
private val iosDatabaseModule = module {
    single { buildSunupDatabase(iosDatabaseBuilder()) }
    single { get<SunupDatabase>().deckDao() }
    single { get<SunupDatabase>().noteDao() }
    single { get<SunupDatabase>().reviewStateDao() }
    single { get<SunupDatabase>().reviewLogDao() }
    single { get<SunupDatabase>().alarmDao() }
    single { get<SunupDatabase>().wakeSessionDao() }
}

/** Android's settingsStoreModule: the small settings file, in Documents. */
private val iosSettingsStoreModule = module {
    single { createSettingsDataStore(documentsPath(SETTINGS_FILE_NAME)) }
}

private const val ALARM_FILE_NAME = "alarms.json"
