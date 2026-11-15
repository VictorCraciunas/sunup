package dev.victorc.sunup.feature.decks.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTextField
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import org.koin.compose.viewmodel.koinViewModel

/** Mockup D5. */
@Composable
fun ImportRoot(onCreated: (deckId: String) -> Unit, onClose: () -> Unit) {
    val viewModel = koinViewModel<ImportViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val created by rememberUpdatedState(onCreated)
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Created -> created(event.deckId)
                ImportEvent.Close -> close()
            }
        }
    }
    ImportScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Suppress("DEPRECATION") // LocalClipboardManager: the replacement needs platform code for plain text.
@Composable
fun ImportScreen(state: ImportState, onAction: (ImportAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val clipboard = LocalClipboardManager.current
    val pickFile = rememberTextFilePicker { text -> onAction(ImportAction.OnFile(text)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(title = "Import a deck", onBack = { onAction(ImportAction.OnBack) })

        val preview = state.preview
        if (preview == null) {
            Text(
                "Let ChatGPT, Claude or any AI make the cards, from a topic or your own notes.",
                style = SunupTheme.typography.body,
                color = colors.inkMuted,
            )

            Step(number = 1, title = "Copy the prompt", modifier = Modifier.riseIn(0)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SunupTheme.sizes.pipHero)
                        .background(colors.track, SunupTheme.shapes.button)
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.md),
                ) {
                    Text(state.prompt, style = SunupTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = colors.ink)
                }
                SunupButton(
                    text = if (state.copied) "Copied! Now open your AI" else "Copy prompt",
                    onClick = {
                        clipboard.setText(AnnotatedString(state.prompt))
                        onAction(ImportAction.OnCopied)
                    },
                    style = if (state.copied) SunupButtonStyle.Secondary else SunupButtonStyle.Primary,
                )
            }

            Step(number = 2, title = "Paste it into your AI", modifier = Modifier.riseIn(1)) {
                Text(
                    "Fill in your topic, or add your notes, a PDF or a photo of your notebook.",
                    style = SunupTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }

            Step(number = 3, title = "Bring the deck back", modifier = Modifier.riseIn(2)) {
                SunupTextField(
                    value = state.text,
                    onValueChange = { onAction(ImportAction.OnText(it)) },
                    label = null,
                    placeholder = "Paste the AI's answer here",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8,
                    textStyle = SunupTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    SunupButton(
                        text = "Paste",
                        onClick = { clipboard.getText()?.text?.let { onAction(ImportAction.OnText(it)) } },
                        style = SunupButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    if (pickFile != null) {
                        SunupButton(
                            text = "Upload .json",
                            onClick = pickFile,
                            style = SunupButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SunupButton(text = "Check", onClick = { onAction(ImportAction.OnCheck) }, enabled = state.text.isNotBlank())
            }

            state.problem?.let { Problem(it) }
        } else {
            Preview(preview, state, onAction)
        }
    }
}

@Composable
private fun Step(number: Int, title: String, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        contentPadding = PaddingValues(SunupTheme.spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
                Box(
                    modifier = Modifier
                        .background(colors.indigo, SunupTheme.shapes.pill)
                        .padding(horizontal = SunupTheme.spacing.sm, vertical = SunupTheme.spacing.xs),
                ) {
                    Text("$number", style = SunupTheme.typography.label, color = colors.onIndigo)
                }
                Text(title, style = SunupTheme.typography.body, color = colors.ink)
            }
            content()
        }
    }
}

@Composable
private fun Preview(preview: ImportPreview, state: ImportState, onAction: (ImportAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    SunupRaised(
        face = colors.indigo,
        edge = colors.indigoShadow,
        contentPadding = PaddingValues(spacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .riseIn(0),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("PIP FOUND", style = SunupTheme.typography.label, color = colors.onNightMuted)
            Text(
                text = if (preview.total == 1) "1 card" else "${preview.total} cards",
                style = SunupTheme.typography.headline,
                color = colors.onIndigo,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                preview.games.forEach { (game, count) ->
                    Text(
                        "$count ${game.title().lowercase()}",
                        style = SunupTheme.typography.label,
                        color = colors.onIndigo,
                        modifier = Modifier
                            .background(colors.indigoShadow, SunupTheme.shapes.pill)
                            .padding(horizontal = spacing.sm, vertical = spacing.xs),
                    )
                }
            }
        }
    }

    SunupTextField(
        value = preview.name,
        onValueChange = { onAction(ImportAction.OnName(it)) },
        label = "Deck name",
        modifier = Modifier.riseIn(1),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .riseIn(2),
    ) {
        preview.sample.forEachIndexed { index, card ->
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                GameBadge(card.game, size = SunupTheme.sizes.pipBadge)
                Column(Modifier.weight(1f)) {
                    Text(card.front, style = SunupTheme.typography.body, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (card.back.isNotEmpty()) {
                        Text(card.back, style = SunupTheme.typography.bodySmall, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (index < preview.sample.lastIndex) HorizontalDivider(thickness = SunupTheme.sizes.border, color = colors.track)
        }
        val more = preview.total - preview.sample.size
        if (more > 0) {
            Text(
                "+ $more more",
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm),
            )
        }
    }

    preview.skipped.take(MAX_SKIPPED_SHOWN).forEach { Problem(it) }
    if (preview.skipped.size > MAX_SKIPPED_SHOWN) Problem("…and ${preview.skipped.size - MAX_SKIPPED_SHOWN} more skipped.")
    state.problem?.let { Problem(it) }

    SunupButton(
        text = if (state.creating) "Creating…" else "Create deck",
        onClick = { onAction(ImportAction.OnCreate) },
        enabled = !state.creating,
    )
    SunupButton(
        text = "Paste something else",
        onClick = { onAction(ImportAction.OnEditAgain) },
        style = SunupButtonStyle.Secondary,
    )
}

@Composable
private fun Problem(text: String) {
    val colors = SunupTheme.colors
    Text(
        text,
        style = SunupTheme.typography.bodySmall,
        color = colors.onWrongContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.wrongContainer, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private const val MAX_SKIPPED_SHOWN = 3
