package dev.victorc.sunup.core.domain.wakeup

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * One morning: an alarm rang and the morning screen opened. Streaks and Home are built from
 * these. The test alarm, previews and wake-up-check re-rings never make one.
 */
data class WakeSession(
    val id: String,
    val alarmId: String,
    /** The day it rang, in the phone's time zone then. */
    val date: LocalDate,
    val rangAt: Instant,
    val outcome: WakeOutcome = WakeOutcome.RINGING,
    /** When the last silencing card was answered. Null unless [outcome] is UP. */
    val upAt: Instant? = null,
    /** Cards answered to stop the alarm (Quick math included). */
    val silencingCards: Int = 0,
    val bonusCards: Int = 0,
)

enum class WakeOutcome {
    /** Rang, and the cards weren't answered (yet). Left like this, it's a missed morning. */
    RINGING,

    /** Stopped by answering: a sunrise. */
    UP,

    /** Stopped with Emergency stop: a missed morning. */
    STOPPED_WITHOUT_CARD,
}

interface WakeSessionRepository {
    /** Every morning, newest first. */
    fun observeAll(): Flow<List<WakeSession>>

    /** Adds it, or replaces the one with the same id. */
    suspend fun save(session: WakeSession): EmptyResult<DataError.Local>
}
