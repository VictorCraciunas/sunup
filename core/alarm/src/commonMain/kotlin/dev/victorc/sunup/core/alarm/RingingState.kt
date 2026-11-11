package dev.victorc.sunup.core.alarm

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Ringing(val alarmId: String, val since: Instant)

/** Which alarm is ringing right now, if any. The ringing screen and the app watch this. */
object RingingState {
    private val _current = MutableStateFlow<Ringing?>(null)
    val current: StateFlow<Ringing?> = _current.asStateFlow()

    fun set(ringing: Ringing?) {
        _current.value = ringing
    }
}
