package dev.victorc.sunup

import android.app.Application
import dev.victorc.sunup.core.alarm.alarmModule
import dev.victorc.sunup.core.data.dataModule
import dev.victorc.sunup.core.data.morningInfoModule
import dev.victorc.sunup.core.data.settingsStoreModule
import dev.victorc.sunup.core.database.databaseModule
import dev.victorc.sunup.feature.alarms.presentation.alarmsModule
import dev.victorc.sunup.feature.decks.presentation.decksModule
import dev.victorc.sunup.feature.onboarding.presentation.onboardingModule
import dev.victorc.sunup.feature.wakeup.presentation.wakeUpModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Also starts after a reboot before the phone is unlocked (for the alarm receivers), so
 * nothing here may touch credential-encrypted storage. Koin singletons are lazy, so the
 * database is only opened once something asks for it.
 */
class SunupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SunupApplication)
            modules(
                alarmModule,
                databaseModule,
                settingsStoreModule,
                morningInfoModule,
                dataModule,
                alarmsModule,
                decksModule,
                onboardingModule,
                wakeUpModule,
            )
        }
    }
}
