package dev.victorc.sunup.core.domain.wakeup

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow

interface WakeAlarmRepository {
    /** Every alarm, earliest time of day first. */
    fun observeAll(): Flow<List<WakeAlarm>>
    suspend fun get(id: String): WakeAlarm?
    suspend fun save(alarm: WakeAlarm): EmptyResult<DataError.Local>
    suspend fun delete(id: String): EmptyResult<DataError.Local>
}
