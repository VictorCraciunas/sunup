package dev.victorc.sunup.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.morning.MorningInfoSettings
import dev.victorc.sunup.core.domain.settings.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** The settings file's name; each platform puts it in its app files folder. */
const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/** Only one DataStore may be open per file, so build it once (a Koin single). */
fun createSettingsDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

class DataStoreAppSettings(private val store: DataStore<Preferences>) : AppSettingsRepository {

    // A broken or unreadable file reads as "nothing saved yet".
    private val values: Flow<Preferences> = store.data.catch { emit(emptyPreferences()) }

    override fun observeOnboardingDone(): Flow<Boolean> = values
        .map { it[ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    override suspend fun setOnboardingDone(): EmptyResult<DataError.Local> =
        safeLocal { store.edit { it[ONBOARDING_DONE] = true } }

    override fun observeMorningInfo(): Flow<MorningInfoSettings> = values
        .map { MorningInfoSettings(weather = it[SHOW_WEATHER] ?: false, calendar = it[SHOW_CALENDAR] ?: false) }
        .distinctUntilChanged()

    override suspend fun setShowWeather(on: Boolean): EmptyResult<DataError.Local> =
        safeLocal { store.edit { it[SHOW_WEATHER] = on } }

    override suspend fun setShowCalendar(on: Boolean): EmptyResult<DataError.Local> =
        safeLocal { store.edit { it[SHOW_CALENDAR] = on } }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val SHOW_CALENDAR = booleanPreferencesKey("show_calendar")
    }
}
