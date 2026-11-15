package dev.victorc.sunup.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
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

    override fun observeOnboardingDone(): Flow<Boolean> = store.data
        // A broken or unreadable file reads as "nothing saved yet".
        .catch { emit(emptyPreferences()) }
        .map { it[ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    override suspend fun setOnboardingDone(): EmptyResult<DataError.Local> =
        safeLocal { store.edit { it[ONBOARDING_DONE] = true } }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
