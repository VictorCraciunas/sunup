package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import kotlin.coroutines.cancellation.CancellationException

/** Runs a database write and turns any failure into [DataError.Local] instead of throwing. */
internal suspend inline fun safeLocal(block: () -> Unit): EmptyResult<DataError.Local> =
    try {
        block()
        Result.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val full = e.message?.contains("full", ignoreCase = true) == true
        Result.Error(if (full) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
    }
