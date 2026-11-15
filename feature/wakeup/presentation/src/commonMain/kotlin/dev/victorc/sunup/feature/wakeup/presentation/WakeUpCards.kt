package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.geo.CountryMap
import dev.victorc.sunup.core.geo.GeoAtlas

// TODO(M4): move user-facing text to string resources once localization is set up.

/** The card itself: label and prompt. */
@Composable
internal fun PromptCard(card: CardUi, palette: Palette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, SunupTheme.shapes.card)
            .border(2.dp, palette.border, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        Text(card.label.uppercase(), style = SunupTheme.typography.label, color = palette.muted)
        when (card) {
            is CardUi.Choice -> when (card.kind) {
                ChoiceKind.GAP -> {
                    // A whole sentence: smaller than a single word, still easy to read half asleep.
                    Text(card.prompt, style = SunupTheme.typography.title.copy(fontSize = 24.sp, lineHeight = 30.sp), color = palette.text)
                    card.statement?.let { Text(it, style = SunupTheme.typography.body, color = palette.muted) }
                }
                ChoiceKind.TRUE_FALSE -> {
                    Text(card.prompt, style = promptStyle(), color = palette.text)
                    card.statement?.let {
                        Text("means “$it”?", style = SunupTheme.typography.title, color = palette.text)
                    }
                }
                else -> Text(card.prompt, style = promptStyle(), color = palette.text)
            }
            is CardUi.Typing -> {
                Text(card.prompt, style = promptStyle(), color = palette.text)
                Text("Type it", style = SunupTheme.typography.body, color = palette.muted)
            }
            is CardUi.Match -> Text("Match the pairs", style = SunupTheme.typography.title, color = palette.text)
            is CardUi.FindOnMap -> Text(card.countryName, style = promptStyle(), color = palette.text)
        }
    }
}

/** Find on the map: the map fills the space between the card and the controls. */
@Composable
internal fun MapArea(card: CardUi.FindOnMap, atlas: GeoAtlas?, palette: Palette, onAction: (WakeUpAction) -> Unit, modifier: Modifier) {
    val shape = SunupTheme.shapes.card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(2.dp, palette.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (atlas == null) {
            // The ViewModel swaps map cards out when the map can't load, so this is only a flash.
            Text("Unfolding the map…", style = SunupTheme.typography.body, color = palette.muted)
        } else {
            CountryMap(
                atlas = atlas,
                region = card.region,
                found = if (card.solved) setOf(card.countryId) else emptySet(),
                wrong = card.lastWrongId,
                hint = card.countryId.takeIf { card.showHint },
                description = "Map of ${card.region.title}. Find ${card.countryName}.",
                onTap = { tapped -> onAction(WakeUpAction.OnMapTap(tapped.id, tapped.name)) },
                modifier = Modifier.matchParentSize(),
                resetKey = card.countryId,
            )
        }
    }
}

@Composable
private fun promptStyle(): TextStyle = SunupTheme.typography.display.copy(fontSize = 34.sp, lineHeight = 38.sp)

/** What you tap or type to answer, below the card. */
@Composable
internal fun GameControls(card: CardUi, palette: Palette, onAction: (WakeUpAction) -> Unit) {
    when (card) {
        is CardUi.Choice -> {
            if (card.wrongPicks.isNotEmpty()) TryAgain(palette)
            ChoiceOptions(card, palette, onPick = { onAction(WakeUpAction.OnPick(it)) })
        }
        is CardUi.Typing -> TypingInput(card, palette, onAction)
        is CardUi.Match -> Text(
            "Tap a word, then its meaning.",
            style = SunupTheme.typography.body,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        is CardUi.FindOnMap -> Text(
            text = when {
                card.lastWrongName != null && card.showHint -> "That's ${card.lastWrongName}. Look for the glow."
                card.lastWrongName != null -> "That's ${card.lastWrongName}. Have another go."
                card.showHint -> "Look for the glow."
                else -> "Pinch to zoom, then tap the country."
            },
            style = SunupTheme.typography.body,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TryAgain(palette: Palette) {
    Text(
        "Not quite. Have another go.",
        style = SunupTheme.typography.body,
        color = palette.muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChoiceOptions(card: CardUi.Choice, palette: Palette, onPick: (Int) -> Unit) {
    val rows = if (card.kind == ChoiceKind.TRUE_FALSE) listOf(card.options.indices.toList()) else card.options.indices.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                row.forEach { index ->
                    Choice(
                        text = card.options[index],
                        state = when {
                            card.solved && index == card.correctIndex -> ChoiceState.RIGHT
                            card.solved -> ChoiceState.DIMMED
                            index in card.wrongPicks -> ChoiceState.WRONG
                            else -> ChoiceState.IDLE
                        },
                        palette = palette,
                        onClick = { onPick(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Two columns: words on the left, meanings (shuffled) on the right. */
@Composable
internal fun MatchGrid(card: CardUi.Match, palette: Palette, onAction: (WakeUpAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            card.prompts.forEachIndexed { index, text ->
                Choice(
                    text = text,
                    state = when {
                        index in card.matched -> ChoiceState.RIGHT
                        card.wrongPair?.first == index -> ChoiceState.WRONG
                        card.selectedPrompt == index -> ChoiceState.SELECTED
                        else -> ChoiceState.IDLE
                    },
                    palette = palette,
                    onClick = { onAction(WakeUpAction.OnMatchPrompt(index)) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 56.dp,
                    textStyle = SunupTheme.typography.body,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            card.answerOrder.forEachIndexed { slot, answerIndex ->
                Choice(
                    text = card.answers[answerIndex],
                    state = when {
                        answerIndex in card.matched -> ChoiceState.RIGHT
                        card.wrongPair?.second == slot -> ChoiceState.WRONG
                        else -> ChoiceState.IDLE
                    },
                    palette = palette,
                    onClick = { onAction(WakeUpAction.OnMatchAnswer(slot)) },
                    modifier = Modifier.fillMaxWidth(),
                    minHeight = 56.dp,
                    textStyle = SunupTheme.typography.body,
                )
            }
        }
    }
}

@Composable
private fun TypingInput(card: CardUi.Typing, palette: Palette, onAction: (WakeUpAction) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(card.prompt) { focus.requestFocus() }

    // A wrong answer shakes the field, like a wrong option.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(card.wrongAttempts) {
        if (card.wrongAttempts > 0) SHAKE_OFFSETS.forEach { shake.animateTo(it, tween(durationMillis = 45)) }
    }

    if (card.wrongAttempts > 0) TryAgain(palette)
    OutlinedTextField(
        value = card.text,
        onValueChange = { onAction(WakeUpAction.OnTypingChange(it)) },
        singleLine = true,
        placeholder = { Text("Your answer") },
        textStyle = SunupTheme.typography.title,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAction(WakeUpAction.OnCheckTyping) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedContainerColor = palette.surface,
            unfocusedContainerColor = palette.surface,
            focusedBorderColor = SunupTheme.colors.indigo,
            unfocusedBorderColor = palette.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = shake.value.dp.toPx() }
            .focusRequester(focus),
    )

    val accents = accentKeys(card.language)
    if (accents.isNotEmpty()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
        ) {
            accents.forEach { letter ->
                Box(
                    modifier = Modifier
                        .size(SunupTheme.sizes.minTouchTarget)
                        .clip(SunupTheme.shapes.button)
                        .background(palette.surface)
                        .border(2.dp, palette.border, SunupTheme.shapes.button)
                        .clickable(role = Role.Button) { onAction(WakeUpAction.OnTypingChange(card.text + letter)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = SunupTheme.typography.title, color = palette.text)
                }
            }
        }
    }

    SunupButton(
        text = "Check",
        onClick = { onAction(WakeUpAction.OnCheckTyping) },
        enabled = card.text.isNotBlank(),
    )
    if (card.wrongAttempts > 0) {
        TextButton(onClick = { onAction(WakeUpAction.OnRevealTyping) }, modifier = Modifier.fillMaxWidth()) {
            Text("Show answer", color = palette.muted)
        }
    }
}

/** Letters that are hard to reach on a phone keyboard, for the language being typed. */
private fun accentKeys(language: String?): List<String> = when (language) {
    "es" -> listOf("á", "é", "í", "ó", "ú", "ñ", "ü", "¿", "¡")
    "fr" -> listOf("é", "è", "ê", "à", "â", "ç", "ô", "û", "ù", "î", "ï", "ë", "œ")
    "de" -> listOf("ä", "ö", "ü", "ß")
    "it" -> listOf("à", "è", "é", "ì", "ò", "ù")
    "pt" -> listOf("á", "â", "ã", "à", "ç", "é", "ê", "í", "ó", "ô", "õ", "ú")
    else -> emptyList()
}

internal enum class ChoiceState { IDLE, SELECTED, RIGHT, WRONG, DIMMED }

/** Shake path in dp: big first swing, settling back to the middle. */
private val SHAKE_OFFSETS = listOf(-10f, 9f, -7f, 5f, -3f, 0f)

@Composable
private fun Choice(
    text: String,
    state: ChoiceState,
    palette: Palette,
    onClick: () -> Unit,
    modifier: Modifier,
    minHeight: Dp = 76.dp,
    textStyle: TextStyle = SunupTheme.typography.title,
) {
    val colors = SunupTheme.colors
    val shape = SunupTheme.shapes.card
    val (background, border, content) = when (state) {
        ChoiceState.RIGHT -> Triple(colors.correctContainer, colors.correct, colors.onCorrectContainer)
        ChoiceState.WRONG -> Triple(colors.wrongContainer, colors.wrong, colors.onWrongContainer)
        ChoiceState.SELECTED -> Triple(colors.indigoContainer, colors.indigo, colors.indigo)
        else -> Triple(palette.surface, palette.border, palette.text)
    }

    // Wrong answers shake gently, right ones bounce (PRD: never shame).
    // Compose skips these when the phone's "Remove animations" setting is on.
    val shake = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state) {
        when (state) {
            ChoiceState.WRONG -> SHAKE_OFFSETS.forEach { shake.animateTo(it, tween(durationMillis = 45)) }
            ChoiceState.RIGHT -> {
                scale.animateTo(1.08f, tween(durationMillis = 110))
                scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            }
            else -> Unit
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .graphicsLayer {
                translationX = shake.value.dp.toPx()
                scaleX = scale.value
                scaleY = scale.value
            }
            .alpha(if (state == ChoiceState.DIMMED) 0.45f else 1f)
            .clip(shape)
            .background(background)
            .border(2.dp, border, shape)
            .clickable(
                enabled = state == ChoiceState.IDLE || state == ChoiceState.SELECTED,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(SunupTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = textStyle, color = content, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun Feedback(card: CardUi, stopsAlarm: Boolean, onAction: (WakeUpAction) -> Unit) {
    // Slides up from below, like the sheet in the mockups.
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        rise.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                translationY = rise.value * 120.dp.toPx()
                alpha = 1f - rise.value
            }
            .fillMaxWidth()
            .background(SunupTheme.colors.correctContainer, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Text(
            text = when {
                stopsAlarm -> "Alarm off. Good morning!"
                card is CardUi.Typing && card.result != TypingResult.CORRECT && card.result != TypingResult.REVEALED ->
                    "Close enough!"
                card.struggled -> "Got it. It'll come back soon."
                else -> "Nice one!"
            },
            style = SunupTheme.typography.title,
            color = SunupTheme.colors.onCorrectContainer,
        )
        card.explanation?.let {
            Text(it, style = SunupTheme.typography.body, color = SunupTheme.colors.onCorrectContainer)
        }
        SunupButton(text = "Continue", onClick = { onAction(WakeUpAction.OnContinue) })
    }
}
