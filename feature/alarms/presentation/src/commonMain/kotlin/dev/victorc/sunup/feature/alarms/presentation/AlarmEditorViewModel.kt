package dev.victorc.sunup.feature.alarms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import dev.victorc.sunup.core.alarm.AlarmService
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** A new alarm starts at 07:00 on weekdays, as in the mockups. */
internal val DEFAULT_TIME = LocalTime(7, 0)

data class AlarmEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val hour: Int = DEFAULT_TIME.hour,
    val minute: Int = DEFAULT_TIME.minute,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val mode: WakeMode = WakeMode.NORMAL,
    /** Null: any deck (the morning flow picks the first one). */
    val deckId: String? = null,
    val decks: List<DeckOption> = emptyList(),
    /** "Rings in 8 h 19 min" for the time and days as they are now. */
    val ringsIn: String? = null,
    val saving: Boolean = false,
    val confirmingDelete: Boolean = false,
    val message: String? = null,
) {
    val timeText: String get() = timeText(LocalTime(hour, minute))
}

data class DeckOption(val id: String?, val name: String)

sealed interface AlarmEditorAction {
    data class OnHourStep(val by: Int) : AlarmEditorAction
    data class OnMinuteStep(val by: Int) : AlarmEditorAction
    data class OnTimePicked(val hour: Int, val minute: Int) : AlarmEditorAction
    data class OnToggleDay(val day: DayOfWeek) : AlarmEditorAction
    data class OnPickMode(val mode: WakeMode) : AlarmEditorAction
    data class OnPickDeck(val deckId: String?) : AlarmEditorAction
    data object OnSave : AlarmEditorAction
    data object OnDelete : AlarmEditorAction
    data object OnConfirmDelete : AlarmEditorAction
    data object OnCancelDelete : AlarmEditorAction
    data object OnBack : AlarmEditorAction
    data object OnDismissMessage : AlarmEditorAction
}

sealed interface AlarmEditorEvent {
    data object Close : AlarmEditorEvent
}

/** @param alarmId null for a new alarm */
class AlarmEditorViewModel(
    private val alarmId: String?,
    private val service: AlarmService,
    decks: DeckRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditorState(isNew = alarmId == null))
    val state: StateFlow<AlarmEditorState> = _state.asStateFlow()

    private val _events = Channel<AlarmEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The alarm as it was, so fields this screen doesn't show (label, pre-alarm) are kept. */
    private var original: WakeAlarm? = null

    @OptIn(ExperimentalUuidApi::class)
    private val id: String = alarmId ?: Uuid.random().toString()

    init {
        viewModelScope.launch {
            val existing = alarmId?.let { id ->
                try {
                    service.get(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            original = existing
            _state.update { state ->
                val loaded = if (existing == null) {
                    state
                } else {
                    state.copy(
                        isNew = false,
                        hour = existing.time.hour,
                        minute = existing.time.minute,
                        days = existing.repeatDays,
                        mode = existing.mode,
                        deckId = existing.deckId,
                    )
                }
                loaded.copy(loading = false).withRingsIn()
            }
        }

        decks.observeDecks()
            .onEach { list ->
                val options = listOf(DeckOption(null, ANY_DECK)) + list.map { DeckOption(it.id, it.name) }
                _state.update { it.copy(decks = options) }
            }
            .catch { _state.update { it.copy(decks = listOf(DeckOption(null, ANY_DECK))) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: AlarmEditorAction) {
        when (action) {
            is AlarmEditorAction.OnHourStep -> change { it.copy(hour = (it.hour + action.by).mod(24)) }
            is AlarmEditorAction.OnMinuteStep -> change { it.copy(minute = (it.minute + action.by).mod(60)) }
            is AlarmEditorAction.OnTimePicked -> change { it.copy(hour = action.hour, minute = action.minute) }
            is AlarmEditorAction.OnToggleDay -> change {
                it.copy(days = if (action.day in it.days) it.days - action.day else it.days + action.day)
            }
            is AlarmEditorAction.OnPickMode -> change { it.copy(mode = action.mode) }
            is AlarmEditorAction.OnPickDeck -> change { it.copy(deckId = action.deckId) }
            AlarmEditorAction.OnSave -> save()
            AlarmEditorAction.OnDelete -> _state.update { it.copy(confirmingDelete = true) }
            AlarmEditorAction.OnCancelDelete -> _state.update { it.copy(confirmingDelete = false) }
            AlarmEditorAction.OnConfirmDelete -> delete()
            AlarmEditorAction.OnBack -> viewModelScope.launch { _events.send(AlarmEditorEvent.Close) }
            AlarmEditorAction.OnDismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun change(edit: (AlarmEditorState) -> AlarmEditorState) {
        _state.update { edit(it).withRingsIn() }
    }

    private fun toAlarm(state: AlarmEditorState): WakeAlarm {
        val base = original ?: WakeAlarm(id = id, time = DEFAULT_TIME)
        return base.copy(
            time = LocalTime(state.hour, state.minute),
            repeatDays = state.days,
            mode = state.mode,
            deckId = state.deckId,
            // Saving means "I want this alarm": it's switched on.
            enabled = true,
        )
    }

    private fun AlarmEditorState.withRingsIn(): AlarmEditorState {
        val next = service.next(listOf(toAlarm(this)))
        return copy(ringsIn = next?.let { ringsInText(it.at - service.now()) })
    }

    private fun save() {
        val current = _state.value
        if (current.saving || current.loading) return
        val alarm = toAlarm(current)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = service.save(alarm)) {
                is Result.Success -> _events.send(AlarmEditorEvent.Close)
                is Result.Error -> _state.update { it.copy(saving = false, message = result.error.message()) }
            }
        }
    }

    private fun delete() {
        val id = alarmId ?: return
        _state.update { it.copy(confirmingDelete = false) }
        viewModelScope.launch {
            service.delete(id)
            _events.send(AlarmEditorEvent.Close)
        }
    }
}
