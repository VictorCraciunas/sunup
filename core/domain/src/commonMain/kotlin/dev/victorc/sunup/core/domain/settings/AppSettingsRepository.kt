package dev.victorc.sunup.core.domain.settings

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.morning.MorningInfoSettings
import kotlinx.coroutines.flow.Flow

/** Small app-wide settings that aren't alarms or decks. Never needed on the ring path. */
interface AppSettingsRepository {

    /** True once the first-run screens were finished. False when it can't be read. */
    fun observeOnboardingDone(): Flow<Boolean>

    suspend fun setOnboardingDone(): EmptyResult<DataError.Local>

    /** Weather and next event on "You're up!". Both off when it can't be read. */
    fun observeMorningInfo(): Flow<MorningInfoSettings>

    suspend fun setShowWeather(on: Boolean): EmptyResult<DataError.Local>

    suspend fun setShowCalendar(on: Boolean): EmptyResult<DataError.Local>
}
