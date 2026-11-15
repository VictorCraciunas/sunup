package dev.victorc.sunup.core.domain.settings

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow

/** Small app-wide settings that aren't alarms or decks. Never needed on the ring path. */
interface AppSettingsRepository {

    /** True once the first-run screens were finished. False when it can't be read. */
    fun observeOnboardingDone(): Flow<Boolean>

    suspend fun setOnboardingDone(): EmptyResult<DataError.Local>
}
