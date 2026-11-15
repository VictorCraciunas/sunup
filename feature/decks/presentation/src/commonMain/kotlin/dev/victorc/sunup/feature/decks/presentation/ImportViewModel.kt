package dev.victorc.sunup.feature.decks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GameEligibility
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.feature.decks.domain.CardDraft
import dev.victorc.sunup.feature.decks.domain.CardDrafts
import dev.victorc.sunup.feature.decks.domain.DeckImport
import dev.victorc.sunup.feature.decks.domain.ImportError
import dev.victorc.sunup.feature.decks.domain.ImportedDeck
import dev.victorc.sunup.feature.decks.domain.SkipReason
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportState(
    val copied: Boolean = false,
    /** What the user pasted (or the file's contents). */
    val text: String = "",
    val problem: String? = null,
    /** Set once the text was read: the second step, before creating the deck. */
    val preview: ImportPreview? = null,
    val creating: Boolean = false,
) {
    val prompt: String get() = DeckImport.PROMPT
}

data class ImportPreview(
    val name: String,
    val kind: DeckKind,
    val total: Int,
    val games: List<Pair<Game, Int>>,
    /** The first few cards, as the deck screen shows them. */
    val sample: List<CardRowUi>,
    /** "Card 3 was skipped: it's missing something." */
    val skipped: List<String>,
)

sealed interface ImportAction {
    data object OnCopied : ImportAction
    data class OnText(val text: String) : ImportAction
    data object OnCheck : ImportAction
    /** A picked file's contents; null if none was picked or it couldn't be read. */
    data class OnFile(val text: String?) : ImportAction
    data class OnName(val name: String) : ImportAction
    data object OnEditAgain : ImportAction
    data object OnCreate : ImportAction
    data object OnBack : ImportAction
}

sealed interface ImportEvent {
    data class Created(val deckId: String) : ImportEvent
    data object Close : ImportEvent
}

/** Mockup D5: a deck made by the user's own AI, pasted or uploaded as JSON. */
class ImportViewModel(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var imported: ImportedDeck? = null

    fun onAction(action: ImportAction) {
        when (action) {
            ImportAction.OnCopied -> _state.update { it.copy(copied = true) }
            is ImportAction.OnText -> _state.update { it.copy(text = action.text, problem = null) }
            ImportAction.OnCheck -> check(_state.value.text)
            is ImportAction.OnFile -> if (action.text == null) {
                _state.update { it.copy(problem = "That file couldn't be read. Is it the .json file from your AI?") }
            } else {
                _state.update { it.copy(text = action.text) }
                check(action.text)
            }
            is ImportAction.OnName -> _state.update { state -> state.copy(preview = state.preview?.copy(name = action.name)) }
            ImportAction.OnEditAgain -> _state.update { it.copy(preview = null) }
            ImportAction.OnCreate -> create()
            ImportAction.OnBack -> viewModelScope.launch {
                if (_state.value.preview != null) _state.update { it.copy(preview = null) } else _events.send(ImportEvent.Close)
            }
        }
    }

    private fun check(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(problem = "Paste your AI's answer first.") }
            return
        }
        when (val result = DeckImport.parse(text)) {
            is Result.Error -> _state.update {
                it.copy(
                    problem = when (result.error) {
                        ImportError.NOT_JSON -> "Pip couldn't find a deck in that. Paste the AI's whole answer (it starts with { )."
                        ImportError.NO_CARDS -> "Pip found the deck, but no cards it could use. Ask the AI to follow the format in the prompt."
                    },
                )
            }
            is Result.Success -> {
                val deck = result.data
                imported = deck
                val previewNotes = deck.cards.mapIndexedNotNull { index, draft -> noteOf(draft, "preview-$index", deck.kind) }
                _state.update {
                    it.copy(
                        problem = null,
                        preview = ImportPreview(
                            name = deck.name,
                            kind = deck.kind,
                            total = deck.cards.size,
                            games = GameEligibility.CARD_GAMES
                                .map { game -> game to deck.cards.count { card -> card.game == game } }
                                .filter { (_, count) -> count > 0 },
                            sample = previewNotes.take(SAMPLE_SIZE).map { note ->
                                val (front, back) = note.summary()
                                CardRowUi(id = note.id, game = note.game, front = front, back = back, playable = true)
                            },
                            skipped = deck.skipped.map { skip ->
                                when (skip.reason) {
                                    SkipReason.UNKNOWN_GAME -> "Card ${skip.number} skipped: Pip couldn't tell which game it is."
                                    SkipReason.INCOMPLETE -> "Card ${skip.number} skipped: it's missing something."
                                }
                            },
                        ),
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun create() {
        val deck = imported ?: return
        val preview = _state.value.preview ?: return
        if (_state.value.creating) return
        val newDeck = Deck(
            id = Uuid.random().toString(),
            name = preview.name.trim().ifEmpty { deck.name },
            kind = deck.kind,
        )
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            if (decks.saveDeck(newDeck) is Result.Error) {
                _state.update { it.copy(creating = false, problem = "Couldn't save the deck. Try again.") }
                return@launch
            }
            deck.cards.forEach { draft ->
                noteOf(draft, Uuid.random().toString(), deck.kind, deckId = newDeck.id)?.let {
                    notes.saveNote(it, bothDirections = false)
                }
            }
            _events.send(ImportEvent.Created(newDeck.id))
        }
    }

    private fun noteOf(draft: CardDraft, id: String, kind: DeckKind, deckId: String = "preview"): Note? =
        when (val result = CardDrafts.toNote(draft, id = id, deckId = deckId, deckKind = kind)) {
            is Result.Success -> result.data
            is Result.Error -> null
        }

    private companion object {
        const val SAMPLE_SIZE = 4
    }
}
