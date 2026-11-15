package dev.victorc.sunup.feature.decks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.feature.decks.domain.CardDraft
import dev.victorc.sunup.feature.decks.domain.CardDrafts
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardEditorState(
    val draft: CardDraft,
    /** False while editing a card that already exists. */
    val isNew: Boolean = true,
    val cardsInDeck: Int = 0,
    /** Why it can't be saved, shown above the buttons. */
    val problem: String? = null,
    /** "Card added. Next one!" for a moment after Save & add another. */
    val saved: Boolean = false,
    val saving: Boolean = false,
    val confirmDelete: Boolean = false,
)

sealed interface CardEditorAction {
    /** Any field changed: the form sends back the whole draft. */
    data class OnDraft(val draft: CardDraft) : CardEditorAction
    /** New cards: save, then an empty form for the next one. Editing: save and close. */
    data object OnSave : CardEditorAction
    data object OnDone : CardEditorAction
    data object OnDelete : CardEditorAction
    data object OnConfirmDelete : CardEditorAction
    data object OnCancelDelete : CardEditorAction
}

sealed interface CardEditorEvent {
    data object Close : CardEditorEvent
}

/** @param cardId null for a new card */
class CardEditorViewModel(
    private val deckId: String,
    private val game: Game,
    private val cardId: String?,
    private val decks: DeckRepository,
    private val notes: NoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardEditorState(draft = CardDraft(game), isNew = cardId == null))
    val state: StateFlow<CardEditorState> = _state.asStateFlow()

    private val _events = Channel<CardEditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var deckKind = DeckKind.LANGUAGE

    init {
        viewModelScope.launch {
            decks.getDeck(deckId)?.let { deckKind = it.kind }
            if (cardId != null) {
                notes.getNote(cardId)?.let { note -> _state.update { it.copy(draft = CardDraft.from(note)) } }
            }
        }
        notes.observeNotesInDeck(deckId)
            .onEach { list -> _state.update { it.copy(cardsInDeck = list.size) } }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun onAction(action: CardEditorAction) {
        when (action) {
            is CardEditorAction.OnDraft -> _state.update { it.copy(draft = action.draft, problem = null, saved = false) }
            CardEditorAction.OnSave -> save()
            CardEditorAction.OnDone -> viewModelScope.launch { _events.send(CardEditorEvent.Close) }
            CardEditorAction.OnDelete -> _state.update { it.copy(confirmDelete = true) }
            CardEditorAction.OnCancelDelete -> _state.update { it.copy(confirmDelete = false) }
            CardEditorAction.OnConfirmDelete -> viewModelScope.launch {
                cardId?.let { notes.deleteNote(it) }
                _events.send(CardEditorEvent.Close)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun save() {
        val current = _state.value
        if (current.saving) return
        val id = cardId ?: Uuid.random().toString()
        val note = when (val result = CardDrafts.toNote(current.draft, id = id, deckId = deckId, deckKind = deckKind)) {
            is Result.Success -> result.data
            is Result.Error -> {
                _state.update { it.copy(problem = result.error.message()) }
                return
            }
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (notes.saveNote(note, bothDirections = false)) {
                is Result.Error -> _state.update { it.copy(saving = false, problem = "Couldn't save the card. Try again.") }
                is Result.Success -> if (cardId != null) {
                    _events.send(CardEditorEvent.Close)
                } else {
                    // Same game, empty form, ready for the next card.
                    _state.update { it.copy(draft = CardDraft(game), saving = false, saved = true) }
                    delay(SAVED_MESSAGE_MILLIS)
                    _state.update { it.copy(saved = false) }
                }
            }
        }
    }

    private companion object {
        const val SAVED_MESSAGE_MILLIS = 2_500L
    }
}
