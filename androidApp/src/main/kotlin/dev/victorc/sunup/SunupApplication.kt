package dev.victorc.sunup

import android.app.Application
import dev.victorc.sunup.core.alarm.alarmModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Also starts after a reboot before the phone is unlocked (for the alarm receivers), so
 * nothing here may touch credential-encrypted storage.
 */
class SunupApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SunupApplication)
            modules(alarmModule)
        }
    }
}
