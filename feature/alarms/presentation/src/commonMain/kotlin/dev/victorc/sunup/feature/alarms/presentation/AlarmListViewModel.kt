package dev.victorc.sunup.feature.alarms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.feature.alarms.domain.AlarmService
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlarmListState(
    val loading: Boolean = true,
    val alarms: List<AlarmItemUi> = emptyList(),
    /** "Rings in 8 h 19 min", or null when no alarm is on. */
    val next: String? = null,
    val message: String? = null,
)

data class AlarmItemUi(
    val id: String,
    val time: String,
    /** "Mon–Fri · Spanish · Morning words" */
    val details: String,
    val mode: String,
    val enabled: Boolean,
)

sealed interface AlarmListAction {
    data object OnAdd : AlarmListAction
    data class OnEdit(val alarmId: String) : AlarmListAction
    data class OnToggle(val alarmId: String, val enabled: Boolean) : AlarmListAction
    data object OnDismissMessage : AlarmListAction
}

sealed interface AlarmListEvent {
    /** Null: a new alarm. */
    data class OpenEditor(val alarmId: String?) : AlarmListEvent
}

class AlarmListViewModel(
    private val service: AlarmService,
    decks: DeckRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmListState())
    val state: StateFlow<AlarmListState> = _state.asStateFlow()

    private val _events = Channel<AlarmListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var alarms: List<WakeAlarm> = emptyList()

    init {
        viewModelScope.launch {
            // A one-off alarm that rang was switched off by the engine; show it that way.
            try {
                service.syncWithEngine()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Showing the list matters more than a perfect sync; it runs again next time.
            }
        }

        combine(service.observe(), decks.observeDecks(), everyMinute()) { list, deckList, _ ->
            alarms = list
            val deckNames = deckList.associate { it.id to it.name }
            val next = service.next(list)
            AlarmListState(
                loading = false,
                alarms = list.map { it.toUi(deckNames) },
                next = next?.let { ringsInText(it.at - service.now()) },
                message = _state.value.message,
            )
        }
            .catch { _state.update { it.copy(loading = false, message = "Couldn't load your alarms. Try again.") } }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AlarmListAction) {
        when (action) {
            AlarmListAction.OnAdd -> viewModelScope.launch { _events.send(AlarmListEvent.OpenEditor(null)) }
            is AlarmListAction.OnEdit -> viewModelScope.launch { _events.send(AlarmListEvent.OpenEditor(action.alarmId)) }
            is AlarmListAction.OnToggle -> toggle(action.alarmId, action.enabled)
            AlarmListAction.OnDismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun toggle(alarmId: String, enabled: Boolean) {
        val alarm = alarms.firstOrNull { it.id == alarmId } ?: return
        viewModelScope.launch {
            val result = service.setEnabled(alarm, enabled)
            if (result is Result.Error) _state.update { it.copy(message = result.error.message()) }
        }
    }

    /** Ticks once a minute so "Rings in…" stays right while the list is open. */
    private fun everyMinute(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}

private fun WakeAlarm.toUi(deckNames: Map<String, String>) = AlarmItemUi(
    id = id,
    time = timeText(time),
    details = listOfNotNull(
        repeatText(repeatDays),
        deckId?.let { deckNames[it] } ?: ANY_DECK,
        label.takeIf { it.isNotBlank() },
    ).joinToString(" · "),
    mode = mode.title(),
    enabled = enabled,
)
