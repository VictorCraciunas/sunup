package dev.victorc.sunup.core.data

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The settings file in the app's normal (credential-encrypted) files folder. A lazy single:
 * it's only opened by the screens, never by the alarm receivers after a reboot.
 */
val settingsStoreModule = module {
    single { createSettingsDataStore(androidContext().filesDir.resolve(SETTINGS_FILE_NAME).absolutePath) }
}
