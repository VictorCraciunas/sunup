package dev.victorc.sunup.core.domain.wakeup

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult

interface WakeAlarmRepository {
    suspend fun get(id: String): WakeAlarm?
    suspend fun save(alarm: WakeAlarm): EmptyResult<DataError.Local>
}
