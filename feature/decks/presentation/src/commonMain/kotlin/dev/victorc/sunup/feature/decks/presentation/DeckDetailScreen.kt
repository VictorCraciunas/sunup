package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTextField
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GameEligibility
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Mockup D3: one deck and all its cards. */
@Composable
fun DeckDetailRoot(
    deckId: String,
    onAddCard: (deckId: String) -> Unit,
    onOpenCard: (deckId: String, game: Game, cardId: String) -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = koinViewModel<DeckDetailViewModel>(key = "deck-$deckId") { parametersOf(deckId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addCard by rememberUpdatedState(onAddCard)
    val openCard by rememberUpdatedState(onOpenCard)
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckDetailEvent.AddCard -> addCard(event.deckId)
                is DeckDetailEvent.OpenCard -> openCard(event.deckId, event.game, event.cardId)
                DeckDetailEvent.Close -> close()
            }
        }
    }
    DeckDetailScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun DeckDetailScreen(state: DeckDetailState, onAction: (DeckDetailAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(
            title = state.name,
            onBack = { onAction(DeckDetailAction.OnBack) },
            actions = {
                TextButton(onClick = { onAction(DeckDetailAction.OnOpenSettings) }) { Text("Settings", color = colors.indigo) }
            },
        )

        StatCard(state, Modifier.riseIn(0))

        if (state.isMap) {
            Text(
                "A built-in map deck: Pip asks you to name or find these countries.",
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        } else {
            SunupButton(text = "Add a card", onClick = { onAction(DeckDetailAction.OnAddCard) }, modifier = Modifier.riseIn(1))
        }

        if (state.games.size > 1) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .riseIn(2),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FilterChip("All ${state.cardCount}", on = state.filter == null) { onAction(DeckDetailAction.OnFilter(null)) }
                state.games.forEach { (game, count) ->
                    FilterChip("${game.title()} $count", on = state.filter == game) { onAction(DeckDetailAction.OnFilter(game)) }
                }
            }
        }

        if (!state.loading && state.cards.isEmpty() && !state.isMap) {
            Text(
                "No cards yet. Tap Add a card to make the first one.",
                style = SunupTheme.typography.body,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.xl),
            )
        }

        if (state.cards.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SunupTheme.shapes.card)
                    .background(colors.surface)
                    .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
                    .riseIn(3),
            ) {
                state.cards.forEachIndexed { index, card ->
                    key(card.id) {
                        CardRow(card, clickable = !state.isMap) { onAction(DeckDetailAction.OnOpenCard(card.id)) }
                    }
                    if (index < state.cards.lastIndex) HorizontalDivider(thickness = SunupTheme.sizes.border, color = colors.track)
                }
            }
        }
    }

    state.settings?.let { settings -> SettingsDialog(settings, kind = state.kind, onAction = onAction) }
}

@Composable
private fun StatCard(state: DeckDetailState, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.indigo,
        edge = colors.indigoShadow,
        contentPadding = PaddingValues(SunupTheme.spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            Text(
                text = when (state.cardCount) {
                    1 -> "1 card"
                    else -> "${state.cardCount} cards"
                },
                style = SunupTheme.typography.headline,
                color = colors.onIndigo,
            )
            Text("Pip picks a few at random every morning.", style = SunupTheme.typography.bodySmall, color = colors.onNightMuted)
            if (state.alarms.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
                ) {
                    state.alarms.forEach { alarm ->
                        Text(
                            alarm,
                            style = SunupTheme.typography.label,
                            color = colors.onIndigo,
                            modifier = Modifier
                                .background(colors.indigoShadow, SunupTheme.shapes.pill)
                                .padding(horizontal = SunupTheme.spacing.sm, vertical = SunupTheme.spacing.xs),
                        )
                    }
                }
            } else {
                Text("No alarm uses it yet. Pick it in an alarm's “Cards from”.", style = SunupTheme.typography.bodySmall, color = colors.onNightMuted)
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = SunupTheme.sizes.minTouchTarget)
            .clip(SunupTheme.shapes.button)
            .background(if (on) colors.indigoContainer else colors.surface)
            .border(SunupTheme.sizes.border, if (on) colors.indigoSoft else colors.border, SunupTheme.shapes.button)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = SunupTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SunupTheme.typography.bodySmall, color = if (on) colors.indigo else colors.inkMuted)
    }
}

@Composable
private fun CardRow(card: CardRowUi, clickable: Boolean, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.minTouchTarget + SunupTheme.spacing.md)
            .then(if (clickable) Modifier.clickable(role = Role.Button, onClickLabel = "Edit card", onClick = onClick) else Modifier)
            .padding(horizontal = SunupTheme.spacing.md, vertical = SunupTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        GameBadge(card.game, size = SunupTheme.sizes.pipBadge)
        Column(Modifier.weight(1f)) {
            Text(card.front, style = SunupTheme.typography.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.back.isNotEmpty()) {
                Text(card.back, style = SunupTheme.typography.bodySmall, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!card.playable) {
                Text(
                    "Can't play yet: add wrong answers, or more cards to borrow them from",
                    style = SunupTheme.typography.bodySmall,
                    color = colors.onSunSoft,
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(settings: DeckSettingsUi, kind: DeckKind, onAction: (DeckDetailAction) -> Unit) {
    val colors = SunupTheme.colors
    if (settings.confirmDelete) {
        AlertDialog(
            onDismissRequest = { onAction(DeckDetailAction.OnCloseSettings) },
            title = { Text("Delete this deck?") },
            text = { Text("All its cards go too. Alarms that used it pick another deck. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onAction(DeckDetailAction.OnConfirmDelete) }) { Text("Delete", color = colors.wrong) }
            },
            dismissButton = { TextButton(onClick = { onAction(DeckDetailAction.OnCloseSettings) }) { Text("Keep it") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { onAction(DeckDetailAction.OnCloseSettings) },
        title = { Text("Deck settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                SunupTextField(
                    value = settings.name,
                    onValueChange = { onAction(DeckDetailAction.OnSettingsName(it)) },
                    label = "Name",
                )
                TextButton(
                    onClick = { onAction(DeckDetailAction.OnDeleteDeck) },
                    modifier = Modifier.semantics { contentDescription = "Delete this deck" },
                ) {
                    Text("Delete deck", color = colors.wrong)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAction(DeckDetailAction.OnSaveSettings) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onAction(DeckDetailAction.OnCloseSettings) }) { Text("Cancel") } },
    )
}

/** Mockup D4: pick the card's game first. */
@Composable
fun GamePickerScreen(onPick: (Game) -> Unit, onBack: () -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(title = "Add a card", onBack = onBack)
        Text("Which game should this card be?", style = SunupTheme.typography.headline, color = colors.ink)
        GameEligibility.CARD_GAMES.forEachIndexed { index, game ->
            SunupRaised(
                face = colors.surface,
                edge = colors.border,
                outline = colors.border,
                onClick = { onPick(game) },
                onClickLabel = game.title(),
                contentPadding = PaddingValues(spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .riseIn(index),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    GameBadge(game)
                    Column {
                        Text(game.title(), style = SunupTheme.typography.title, color = colors.ink)
                        Text(game.example(), style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
                    }
                }
            }
        }
        Text(
            "You can mix games in one deck. Pip picks a few cards at random each morning.",
            style = SunupTheme.typography.bodySmall,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
        )
    }
}
