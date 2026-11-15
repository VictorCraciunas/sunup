package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.components.SunupFieldLabel
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.DeckKind
import org.koin.compose.viewmodel.koinViewModel

/** The Decks tab: My decks and Browse packs (mockup D1). */
@Composable
fun DeckListRoot(onOpenDeck: (deckId: String) -> Unit, onNewDeck: () -> Unit, onImport: () -> Unit) {
    val viewModel = koinViewModel<DeckListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openDeck by rememberUpdatedState(onOpenDeck)
    val newDeck by rememberUpdatedState(onNewDeck)
    val openImport by rememberUpdatedState(onImport)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckListEvent.OpenDeck -> openDeck(event.deckId)
                DeckListEvent.OpenNewDeck -> newDeck()
                DeckListEvent.OpenImport -> openImport()
            }
        }
    }
    DeckListScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun DeckListScreen(state: DeckListState, onAction: (DeckListAction) -> Unit) {
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
        Text("Decks", style = SunupTheme.typography.headline, color = colors.ink)
        Tabs(state.tab, onPick = { onAction(DeckListAction.OnTab(it)) })

        state.message?.let { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.wrongContainer, SunupTheme.shapes.card)
                    .padding(start = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, style = SunupTheme.typography.bodySmall, color = colors.onWrongContainer, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(DeckListAction.OnDismissMessage) }) { Text("OK", color = colors.onWrongContainer) }
            }
        }

        when (state.tab) {
            DeckTab.MINE -> MyDecks(state, onAction)
            DeckTab.BROWSE -> Packs(state, onAction)
        }
    }

    state.confirmRemove?.let { region ->
        AlertDialog(
            onDismissRequest = { onAction(DeckListAction.OnCancelRemove) },
            title = { Text("Remove ${region.title}?") },
            text = { Text("Its cards and what you learned go away. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = { onAction(DeckListAction.OnConfirmRemove) }) { Text("Remove", color = SunupTheme.colors.wrong) }
            },
            dismissButton = { TextButton(onClick = { onAction(DeckListAction.OnCancelRemove) }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Tabs(tab: DeckTab, onPick: (DeckTab) -> Unit) {
    val colors = SunupTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.track, SunupTheme.shapes.button)
            .padding(SunupTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        listOf(DeckTab.MINE to "My decks", DeckTab.BROWSE to "Browse packs").forEach { (value, label) ->
            val on = value == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SunupTheme.sizes.minTouchTarget)
                    .clip(SunupTheme.shapes.button)
                    .background(if (on) colors.surface else colors.track)
                    .clickable(role = Role.Tab) { onPick(value) }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = SunupTheme.typography.body, color = if (on) colors.ink else colors.inkMuted)
            }
        }
    }
}

@Composable
private fun MyDecks(state: DeckListState, onAction: (DeckListAction) -> Unit) {
    val colors = SunupTheme.colors
    if (!state.loading && state.decks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SunupTheme.spacing.xxl)
                .riseIn(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
        ) {
            Pip(PipMood.SLEEPY, size = SunupTheme.sizes.pipHero)
            Text("No decks yet", style = SunupTheme.typography.headline, color = colors.ink)
            Text(
                "Make one with your own cards, or add a ready-made map from Browse packs.",
                style = SunupTheme.typography.body,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    state.decks.forEachIndexed { index, deck ->
        key(deck.id) {
            DeckCard(deck, onClick = { onAction(DeckListAction.OnOpenDeck(deck.id)) }, modifier = Modifier.riseIn(index))
        }
    }
    Row(
        modifier = Modifier.riseIn(state.decks.size),
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
    ) {
        SunupButton(text = "New deck", onClick = { onAction(DeckListAction.OnNewDeck) }, modifier = Modifier.weight(1f))
        SunupButton(
            text = "Import",
            onClick = { onAction(DeckListAction.OnImport) },
            style = SunupButtonStyle.Secondary,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "Import: let ChatGPT or Claude make a whole deck for you.",
        style = SunupTheme.typography.bodySmall,
        color = colors.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeckCard(deck: DeckItemUi, onClick: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (deck.isEmpty) colors.background else colors.surface,
        edge = if (deck.isEmpty) colors.background else colors.border,
        outline = colors.border,
        onClick = onClick,
        onClickLabel = "Open ${deck.name}",
        contentPadding = PaddingValues(SunupTheme.spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
            KindBadge(deck.kind)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
                    Text(deck.name, style = SunupTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                    deck.alarms?.let {
                        Text(
                            it.uppercase(),
                            style = SunupTheme.typography.label,
                            color = colors.onSunSoft,
                            modifier = Modifier
                                .background(colors.sunSoft, SunupTheme.shapes.pill)
                                .padding(horizontal = SunupTheme.spacing.sm, vertical = SunupTheme.spacing.xs),
                        )
                    }
                }
                Text(
                    text = if (deck.isEmpty) "No cards yet · add one so Pip can use it" else deck.cards,
                    style = SunupTheme.typography.bodySmall,
                    color = if (deck.isEmpty) colors.onSunSoft else colors.inkMuted,
                )
            }
        }
    }
}

@Composable
internal fun KindBadge(kind: DeckKind) {
    val colors = SunupTheme.colors
    val (background, content) = when (kind) {
        DeckKind.LANGUAGE -> colors.daySky to colors.indigo
        DeckKind.STUDY -> colors.indigoContainer to colors.indigoSoft
        DeckKind.TRIVIA -> colors.sunSoft to colors.onSunSoft
        DeckKind.GEOGRAPHY -> colors.correctContainer to colors.onCorrectContainer
    }
    Box(
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .background(background, SunupTheme.shapes.button),
        contentAlignment = Alignment.Center,
    ) {
        Text(kind.badge(), style = SunupTheme.typography.label, color = content)
    }
}

@Composable
private fun Packs(state: DeckListState, onAction: (DeckListAction) -> Unit) {
    val colors = SunupTheme.colors
    SunupFieldLabel("Maps · find the country")
    state.packs.forEachIndexed { index, pack ->
        SunupRaised(
            face = colors.surface,
            edge = colors.border,
            outline = colors.border,
            contentPadding = PaddingValues(SunupTheme.spacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .riseIn(index),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                KindBadge(DeckKind.GEOGRAPHY)
                Column(Modifier.weight(1f)) {
                    Text(pack.title, style = SunupTheme.typography.body, color = colors.ink)
                    if (pack.meta.isNotEmpty()) Text(pack.meta, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
                }
                PackButton(pack, onClick = { onAction(DeckListAction.OnTogglePack(pack.region)) })
            }
        }
    }
}

@Composable
private fun PackButton(pack: PackUi, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (pack.added) colors.correctContainer else colors.surface,
        edge = if (pack.added) colors.correct else colors.border,
        outline = if (pack.added) colors.correct else colors.border,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .semantics { contentDescription = if (pack.added) "Remove ${pack.title}" else "Add ${pack.title}" },
    ) {
        Text(
            text = when {
                pack.busy -> "…"
                pack.added -> "✓"
                else -> "+"
            },
            style = SunupTheme.typography.title,
            color = if (pack.added) colors.onCorrectContainer else colors.indigo,
        )
    }
}
