package dev.victorc.sunup.feature.decks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.data.GeoDeckSeeder
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.geo.GeoAtlas
import dev.victorc.sunup.core.geo.GeoAtlasSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeckTab { MINE, BROWSE }

data class DeckListState(
    val tab: DeckTab = DeckTab.MINE,
    val loading: Boolean = true,
    val decks: List<DeckItemUi> = emptyList(),
    val packs: List<PackUi> = GeoRegion.entries.map { PackUi(it, it.title, meta = "", added = false) },
    /** A pack the user asked to remove, waiting for "Remove". */
    val confirmRemove: GeoRegion? = null,
    val message: String? = null,
)

data class DeckItemUi(
    val id: String,
    val name: String,
    val kind: DeckKind,
    /** "60 cards", "No cards yet". */
    val cards: String,
    val isEmpty: Boolean,
    /** "2 alarms", or null when no alarm uses it. */
    val alarms: String?,
)

data class PackUi(
    val region: GeoRegion,
    val title: String,
    /** "45 countries". */
    val meta: String,
    val added: Boolean,
    val busy: Boolean = false,
)

sealed interface DeckListAction {
    data class OnTab(val tab: DeckTab) : DeckListAction
    data class OnOpenDeck(val deckId: String) : DeckListAction
    data object OnNewDeck : DeckListAction
    data object OnImport : DeckListAction
    data class OnTogglePack(val region: GeoRegion) : DeckListAction
    data object OnConfirmRemove : DeckListAction
    data object OnCancelRemove : DeckListAction
    data object OnDismissMessage : DeckListAction
}

sealed interface DeckListEvent {
    data class OpenDeck(val deckId: String) : DeckListEvent
    data object OpenNewDeck : DeckListEvent
    data object OpenImport : DeckListEvent
}

class DeckListViewModel(
    private val decks: DeckRepository,
    notes: NoteRepository,
    alarms: WakeAlarmRepository,
    private val atlasSource: GeoAtlasSource,
    private val geoSeeder: GeoDeckSeeder,
) : ViewModel() {

    private val _state = MutableStateFlow(DeckListState())
    val state: StateFlow<DeckListState> = _state.asStateFlow()

    private val _events = Channel<DeckListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var atlas: GeoAtlas? = null

    init {
        combine(decks.observeDecks(), notes.observeCardCounts(), alarms.observeAll()) { deckList, counts, alarmList ->
            val addedPacks = deckList.mapNotNull { GeoRegion.fromPackId(it.builtInPackId) }.toSet()
            val items = deckList.map { deck ->
                val count = counts[deck.id] ?: 0
                val using = alarmList.count { it.deckId == deck.id }
                DeckItemUi(
                    id = deck.id,
                    name = deck.name,
                    kind = deck.kind,
                    cards = when (count) {
                        0 -> "No cards yet"
                        1 -> "1 card"
                        else -> "$count cards"
                    },
                    isEmpty = count == 0,
                    alarms = when (using) {
                        0 -> null
                        1 -> "1 alarm"
                        else -> "$using alarms"
                    },
                )
            }
            items to addedPacks
        }
            .onEach { (items, addedPacks) ->
                _state.update { state ->
                    state.copy(
                        loading = false,
                        decks = items,
                        packs = state.packs.map { it.copy(added = it.region in addedPacks) },
                    )
                }
            }
            .catch { _state.update { it.copy(loading = false, message = "Couldn't load your decks. Try again.") } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DeckListAction) {
        when (action) {
            is DeckListAction.OnTab -> {
                _state.update { it.copy(tab = action.tab) }
                if (action.tab == DeckTab.BROWSE) loadPackSizes()
            }
            is DeckListAction.OnOpenDeck -> viewModelScope.launch { _events.send(DeckListEvent.OpenDeck(action.deckId)) }
            DeckListAction.OnNewDeck -> viewModelScope.launch { _events.send(DeckListEvent.OpenNewDeck) }
            DeckListAction.OnImport -> viewModelScope.launch { _events.send(DeckListEvent.OpenImport) }
            is DeckListAction.OnTogglePack -> togglePack(action.region)
            DeckListAction.OnConfirmRemove -> removePack()
            DeckListAction.OnCancelRemove -> _state.update { it.copy(confirmRemove = null) }
            DeckListAction.OnDismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    /** "45 countries" under each map, read from the bundled map once. */
    private fun loadPackSizes() {
        if (atlas != null) return
        viewModelScope.launch {
            val loaded = loadAtlas() ?: return@launch
            atlas = loaded
            _state.update { state ->
                state.copy(packs = state.packs.map { it.copy(meta = "${loaded.countries(it.region).size} countries") })
            }
        }
    }

    private fun togglePack(region: GeoRegion) {
        val pack = _state.value.packs.firstOrNull { it.region == region } ?: return
        if (pack.busy) return
        if (pack.added) {
            _state.update { it.copy(confirmRemove = region) }
            return
        }
        setBusy(region, true)
        viewModelScope.launch {
            val loaded = atlas ?: loadAtlas()
            if (loaded == null) {
                _state.update { it.copy(message = "The map isn't available on this phone.") }
            } else {
                atlas = loaded
                geoSeeder.seed(region, loaded.countries(region))
            }
            setBusy(region, false)
        }
    }

    private fun removePack() {
        val region = _state.value.confirmRemove ?: return
        _state.update { it.copy(confirmRemove = null) }
        viewModelScope.launch { decks.deleteDeck(region.packId) }
    }

    private suspend fun loadAtlas(): GeoAtlas? = when (val result = atlasSource.load()) {
        is Result.Success -> result.data
        is Result.Error -> null
    }

    private fun setBusy(region: GeoRegion, busy: Boolean) {
        _state.update { state -> state.copy(packs = state.packs.map { if (it.region == region) it.copy(busy = busy) else it }) }
    }
}
