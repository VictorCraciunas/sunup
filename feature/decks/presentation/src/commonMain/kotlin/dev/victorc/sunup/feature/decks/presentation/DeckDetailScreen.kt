package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.components.SunupIconButton
import dev.victorc.sunup.core.designsystem.components.SunupSheet
import dev.victorc.sunup.core.designsystem.components.SunupTextButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTextField
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
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
                SunupIconButton(description = "More for this deck", onClick = { onAction(DeckDetailAction.OnOpenSettings) }) { MoreDots() }
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

    state.settings?.let { settings -> DeckSheet(settings, deckName = state.name, cardCount = state.cardCount, onAction = onAction) }
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

/**
 * Mockup H1: the ⋯ panel. It opens on Rename deck and Delete deck; picking one shows the name
 * field, or asks before deleting, in the same panel.
 */
@Composable
private fun DeckSheet(settings: DeckSettingsUi, deckName: String, cardCount: Int, onAction: (DeckDetailAction) -> Unit) {
    val colors = SunupTheme.colors
    val title = when {
        settings.confirmDelete -> "Delete this deck?"
        settings.renaming -> "Rename deck"
        else -> deckName
    }
    SunupSheet(title = title, onDismiss = { onAction(DeckDetailAction.OnCloseSettings) }) {
        when {
            settings.confirmDelete -> {
                val cards = if (cardCount == 1) "Its 1 card goes too." else "Its $cardCount cards go too."
                Text(
                    "$cards Alarms that used it pick another deck. This can't be undone.",
                    style = SunupTheme.typography.body,
                    color = colors.inkMuted,
                )
                SunupButton(
                    text = "Delete deck",
                    style = SunupButtonStyle.Danger,
                    onClick = { close { onAction(DeckDetailAction.OnConfirmDelete) } },
                )
                SunupTextButton(text = "Keep it", onClick = { close { onAction(DeckDetailAction.OnCloseSettings) } })
            }
            settings.renaming -> {
                SunupTextField(
                    value = settings.name,
                    onValueChange = { onAction(DeckDetailAction.OnSettingsName(it)) },
                    label = "Name",
                )
                SunupButton(text = "Save", onClick = { close { onAction(DeckDetailAction.OnSaveSettings) } })
                SunupTextButton(text = "Cancel", onClick = { close { onAction(DeckDetailAction.OnCloseSettings) } })
            }
            else -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(SunupTheme.shapes.card)
                        .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card),
                ) {
                    MenuRow("Rename deck", MenuIcon.PENCIL, color = colors.ink) { onAction(DeckDetailAction.OnStartRename) }
                    HorizontalDivider(thickness = SunupTheme.sizes.border, color = colors.track)
                    MenuRow("Delete deck", MenuIcon.TRASH, color = colors.wrong) { onAction(DeckDetailAction.OnDeleteDeck) }
                }
                SunupTextButton(text = "Close", onClick = { close { onAction(DeckDetailAction.OnCloseSettings) } })
            }
        }
    }
}

private enum class MenuIcon { PENCIL, TRASH }

@Composable
private fun MenuRow(text: String, icon: MenuIcon, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.minTouchTarget + SunupTheme.spacing.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = SunupTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        LineIcon(icon, if (icon == MenuIcon.PENCIL) SunupTheme.colors.indigo else color)
        Text(text, style = SunupTheme.typography.title, color = color)
    }
}

/** The mockup's pencil and bin, drawn on a 24 × 24 grid. */
@Composable
private fun LineIcon(icon: MenuIcon, color: Color) {
    val stroke = SunupTheme.sizes.border
    Canvas(Modifier.size(SunupTheme.spacing.xxl)) {
        val u = size.width / 24f
        val style = Stroke(width = stroke.toPx() * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            when (icon) {
                MenuIcon.PENCIL -> {
                    moveTo(4 * u, 20 * u); lineTo(8 * u, 20 * u); lineTo(19 * u, 9 * u); lineTo(15 * u, 5 * u); lineTo(4 * u, 16 * u); close()
                    moveTo(13.5f * u, 6.5f * u); lineTo(17.5f * u, 10.5f * u)
                }
                MenuIcon.TRASH -> {
                    moveTo(4 * u, 7 * u); lineTo(20 * u, 7 * u)
                    moveTo(6 * u, 7 * u); lineTo(7 * u, 20 * u); lineTo(17 * u, 20 * u); lineTo(18 * u, 7 * u)
                    moveTo(9 * u, 7 * u); lineTo(9 * u, 4 * u); lineTo(15 * u, 4 * u); lineTo(15 * u, 7 * u)
                    moveTo(10 * u, 11 * u); lineTo(10 * u, 17 * u)
                    moveTo(14 * u, 11 * u); lineTo(14 * u, 17 * u)
                }
            }
        }
        drawPath(path, color, style = style)
    }
}

/** ⋯ in the top bar: opens the deck's panel. */
@Composable
private fun MoreDots() {
    val color = SunupTheme.colors.inkMuted
    Canvas(Modifier.size(SunupTheme.spacing.xxl)) {
        val r = size.width / 11f
        listOf(0.2f, 0.5f, 0.8f).forEach { x -> drawCircle(color, radius = r, center = Offset(size.width * x, size.height / 2)) }
    }
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
