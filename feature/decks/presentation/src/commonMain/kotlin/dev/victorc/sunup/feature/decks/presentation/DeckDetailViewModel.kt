package dev.victorc.sunup.feature.decks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.learning.CardBuilder
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GameEligibility
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeckDetailState(
    val loading: Boolean = true,
    val name: String = "",
    val kind: DeckKind = DeckKind.LANGUAGE,
    /** Built-in map decks: cards come from the map, not the user. */
    val isMap: Boolean = false,
    val cardCount: Int = 0,
    /** "07:00 Mon–Fri" for each alarm using this deck. */
    val alarms: List<String> = emptyList(),
    /** Games in this deck with how many cards each, for the filter chips. */
    val games: List<Pair<Game, Int>> = emptyList(),
    val filter: Game? = null,
    val cards: List<CardRowUi> = emptyList(),
    val settings: DeckSettingsUi? = null,
)

data class CardRowUi(
    val id: String,
    val game: Game,
    val front: String,
    val back: String,
    /** False when the card can't be played yet, e.g. a Pick from 4 with nothing to borrow wrong answers from. */
    val playable: Boolean,
)

/** The settings sheet while it's open. */
data class DeckSettingsUi(
    val name: String,
    val confirmDelete: Boolean = false,
)

sealed interface DeckDetailAction {
    data class OnFilter(val game: Game?) : DeckDetailAction
    data object OnAddCard : DeckDetailAction
    data class OnOpenCard(val cardId: String) : DeckDetailAction
    data object OnBack : DeckDetailAction
    data object OnOpenSettings : DeckDetailAction
    data class OnSettingsName(val name: String) : DeckDetailAction
    data object OnSaveSettings : DeckDetailAction
    data object OnCloseSettings : DeckDetailAction
    data object OnDeleteDeck : DeckDetailAction
    data object OnConfirmDelete : DeckDetailAction
}

sealed interface DeckDetailEvent {
    data class AddCard(val deckId: String) : DeckDetailEvent
    data class OpenCard(val deckId: String, val game: Game, val cardId: String) : DeckDetailEvent
    data object Close : DeckDetailEvent
}

class DeckDetailViewModel(
    private val deckId: String,
    private val decks: DeckRepository,
    notes: NoteRepository,
    alarms: WakeAlarmRepository,
    private val cards: CardBuilder,
) : ViewModel() {

    private val _state = MutableStateFlow(DeckDetailState())
    val state: StateFlow<DeckDetailState> = _state.asStateFlow()

    private val _events = Channel<DeckDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var deck: Deck? = null
    private var allCards: List<CardRowUi> = emptyList()

    init {
        val deckFlow = decks.observeDecks().map { list -> list.firstOrNull { it.id == deckId } }
        combine(deckFlow, notes.observeNotesInDeck(deckId), alarms.observeAll()) { found, noteList, alarmList ->
            Triple(found, noteList, alarmList)
        }
            .onEach { (found, noteList, alarmList) ->
                if (found == null) {
                    // Deleted (here or elsewhere): nothing to show.
                    if (deck != null) _events.send(DeckDetailEvent.Close)
                    return@onEach
                }
                deck = found
                allCards = noteList.map { note ->
                    val (front, back) = note.summary()
                    CardRowUi(
                        id = note.id,
                        // A map's countries are saved without a game of their own (each morning picks
                        // Name the country or Find on the map), so they'd show the default "1/4" badge.
                        game = if (found.kind == DeckKind.GEOGRAPHY) Game.NAME_ON_MAP else note.game,
                        front = front,
                        back = back,
                        playable = found.kind == DeckKind.GEOGRAPHY ||
                            cards.build(note, Direction.FORWARD, noteList) != null,
                    )
                }
                val games = GameEligibility.CARD_GAMES
                    .map { game -> game to allCards.count { it.game == game } }
                    .filter { it.second > 0 }
                _state.update { state ->
                    state.copy(
                        loading = false,
                        name = found.name,
                        kind = found.kind,
                        isMap = found.kind == DeckKind.GEOGRAPHY,
                        cardCount = noteList.size,
                        alarms = alarmList.filter { it.deckId == deckId }.map { it.shortText() },
                        games = games,
                        cards = filtered(state.filter),
                    )
                }
            }
            .catch { _state.update { it.copy(loading = false) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DeckDetailAction) {
        when (action) {
            is DeckDetailAction.OnFilter -> _state.update { it.copy(filter = action.game, cards = filtered(action.game)) }
            DeckDetailAction.OnAddCard -> viewModelScope.launch { _events.send(DeckDetailEvent.AddCard(deckId)) }
            is DeckDetailAction.OnOpenCard -> {
                val card = allCards.firstOrNull { it.id == action.cardId } ?: return
                if (_state.value.isMap) return
                viewModelScope.launch { _events.send(DeckDetailEvent.OpenCard(deckId, card.game, card.id)) }
            }
            DeckDetailAction.OnBack -> viewModelScope.launch { _events.send(DeckDetailEvent.Close) }
            DeckDetailAction.OnOpenSettings -> _state.update {
                it.copy(settings = DeckSettingsUi(name = it.name))
            }
            is DeckDetailAction.OnSettingsName -> updateSettings { it.copy(name = action.name) }
            DeckDetailAction.OnSaveSettings -> saveSettings()
            DeckDetailAction.OnCloseSettings -> _state.update { it.copy(settings = null) }
            DeckDetailAction.OnDeleteDeck -> updateSettings { it.copy(confirmDelete = true) }
            DeckDetailAction.OnConfirmDelete -> viewModelScope.launch {
                _state.update { it.copy(settings = null) }
                decks.deleteDeck(deckId)
                _events.send(DeckDetailEvent.Close)
            }
        }
    }

    private fun filtered(game: Game?): List<CardRowUi> = if (game == null) allCards else allCards.filter { it.game == game }

    private fun updateSettings(change: (DeckSettingsUi) -> DeckSettingsUi) {
        _state.update { state -> state.copy(settings = state.settings?.let(change)) }
    }

    private fun saveSettings() {
        val settings = _state.value.settings ?: return
        val current = deck ?: return
        val name = settings.name.trim().ifEmpty { current.name }
        _state.update { it.copy(settings = null) }
        viewModelScope.launch {
            decks.saveDeck(current.copy(name = name))
        }
    }
}
