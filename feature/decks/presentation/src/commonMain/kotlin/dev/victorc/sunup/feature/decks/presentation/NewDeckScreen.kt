package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupFieldLabel
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTextField
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class NewDeckState(
    val name: String = "",
    val kind: DeckKind = DeckKind.LANGUAGE,
    val saving: Boolean = false,
    val message: String? = null,
)

sealed interface NewDeckAction {
    data class OnName(val name: String) : NewDeckAction
    data class OnKind(val kind: DeckKind) : NewDeckAction
    data object OnCreate : NewDeckAction
    data object OnBack : NewDeckAction
}

sealed interface NewDeckEvent {
    data class Created(val deckId: String) : NewDeckEvent
    data object Close : NewDeckEvent
}

/** The kinds a user can make; maps are built in. */
internal val USER_DECK_KINDS = listOf(DeckKind.LANGUAGE, DeckKind.STUDY, DeckKind.TRIVIA)

class NewDeckViewModel(private val decks: DeckRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewDeckState())
    val state: StateFlow<NewDeckState> = _state.asStateFlow()

    private val _events = Channel<NewDeckEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: NewDeckAction) {
        when (action) {
            is NewDeckAction.OnName -> _state.update { it.copy(name = action.name, message = null) }
            is NewDeckAction.OnKind -> _state.update { it.copy(kind = action.kind) }
            NewDeckAction.OnCreate -> create()
            NewDeckAction.OnBack -> viewModelScope.launch { _events.send(NewDeckEvent.Close) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun create() {
        val current = _state.value
        if (current.saving) return
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(message = "Give the deck a name.") }
            return
        }
        val deck = Deck(
            id = Uuid.random().toString(),
            name = name,
            kind = current.kind,
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (decks.saveDeck(deck)) {
                is Result.Success -> _events.send(NewDeckEvent.Created(deck.id))
                is Result.Error -> _state.update { it.copy(saving = false, message = "Couldn't save the deck. Try again.") }
            }
        }
    }
}

/** Mockup D2. */
@Composable
fun NewDeckRoot(onCreated: (deckId: String) -> Unit, onClose: () -> Unit) {
    val viewModel = koinViewModel<NewDeckViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val created by rememberUpdatedState(onCreated)
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NewDeckEvent.Created -> created(event.deckId)
                NewDeckEvent.Close -> close()
            }
        }
    }
    NewDeckScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun NewDeckScreen(state: NewDeckState, onAction: (NewDeckAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(title = "New deck", onBack = { onAction(NewDeckAction.OnBack) })

        SunupTextField(
            value = state.name,
            onValueChange = { onAction(NewDeckAction.OnName(it)) },
            label = "Name",
            placeholder = "e.g. World history",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )

        SunupFieldLabel("What is it?")
        USER_DECK_KINDS.forEach { kind ->
            KindOption(kind, on = kind == state.kind, onClick = { onAction(NewDeckAction.OnKind(kind)) })
        }

        state.message?.let { Text(it, style = SunupTheme.typography.bodySmall, color = colors.wrong) }

        Spacer(Modifier.heightIn(min = spacing.md))
        Text(
            "Next: add cards. Each card plays one game.",
            style = SunupTheme.typography.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SunupButton(
            text = if (state.saving) "Creating…" else "Create deck",
            onClick = { onAction(NewDeckAction.OnCreate) },
            enabled = !state.saving,
        )
    }
}

@Composable
private fun KindOption(kind: DeckKind, on: Boolean, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (on) colors.indigoContainer else colors.surface,
        edge = if (on) colors.indigoSoft else colors.border,
        outline = if (on) colors.indigoSoft else colors.border,
        onClick = onClick,
        role = Role.RadioButton,
        contentPadding = PaddingValues(SunupTheme.spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .wobbleWhenOn(on)
            .semantics { selected = on },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
            KindBadge(kind)
            Column {
                Text(kind.title(), style = SunupTheme.typography.body, color = colors.ink)
                Text(kind.description(), style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
    }
}
