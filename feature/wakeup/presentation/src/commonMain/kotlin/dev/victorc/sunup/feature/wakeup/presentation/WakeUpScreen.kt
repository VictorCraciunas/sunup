package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The whole morning flow for one ringing alarm.
 *
 * @param ringing whether an alarm is ringing right now (the first ring, or the wake-up check's soft re-ring)
 * @param onStopAlarm silence the alarm (a card was answered, "I'm up", or the emergency stop)
 * @param onClose the user is done; close the screen
 */
@Composable
fun WakeUpRoot(
    alarmId: String,
    ringing: Boolean,
    onStopAlarm: () -> Unit,
    onClose: () -> Unit,
) {
    val viewModel = koinViewModel<WakeUpViewModel> { parametersOf(alarmId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stopAlarm by rememberUpdatedState(onStopAlarm)
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WakeUpEvent.StopAlarm -> stopAlarm()
                WakeUpEvent.Close -> close()
            }
        }
    }
    LaunchedEffect(ringing) {
        viewModel.onAction(WakeUpAction.OnRingingChanged(ringing))
    }

    Box(
        Modifier
            .fillMaxSize()
            // Watch every touch without taking it from the buttons underneath.
            .pointerInput(viewModel) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        viewModel.onAction(WakeUpAction.OnUserActive)
                    }
                }
            },
    ) {
        WakeUpScreen(state = state, onAction = viewModel::onAction)
    }
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun WakeUpScreen(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    if (state.ringingAgain) {
        StillWithMe(onAction)
        return
    }
    when (state.phase) {
        WakePhase.LOADING -> Page(Palette.night()) {
            Spacer(Modifier.weight(1f))
            Pip(PipMood.SLEEPY, modifier = Modifier.align(Alignment.CenterHorizontally), size = 140.dp)
            Spacer(Modifier.weight(1f))
        }
        WakePhase.SILENCING -> CardPage(state, Palette.night(), onAction)
        WakePhase.BONUS_OFFER -> BonusOffer(state, onAction)
        WakePhase.BONUS -> CardPage(state, Palette.dawn(), onAction)
        WakePhase.DONE -> Done(state, onAction)
    }
}

/** The screen brightens as you wake: night for the silencing card, dawn for the bonus, day at the end. */
@Immutable
private data class Palette(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
) {
    companion object {
        @Composable
        fun night() = with(SunupTheme.colors) {
            Palette(nightBackground, nightSurface, nightBorder, onNight, onNightMuted)
        }

        @Composable
        fun dawn() = with(SunupTheme.colors) { Palette(dawn, surface, Color(0xFFF0CFA8), ink, inkMuted) }

        @Composable
        fun day() = with(SunupTheme.colors) { Palette(background, surface, border, ink, inkMuted) }
    }
}

@Composable
private fun Page(palette: Palette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeContentPadding()
            .padding(SunupTheme.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
        content = content,
    )
}

@Composable
private fun CardPage(state: WakeUpState, palette: Palette, onAction: (WakeUpAction) -> Unit) {
    val card = state.card ?: return
    val silencing = state.phase == WakePhase.SILENCING

    Page(palette) {
        if (silencing) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(currentTime(), style = SunupTheme.typography.clock, color = palette.text, modifier = Modifier.weight(1f))
                if (state.total > 1) {
                    Text("${state.position + 1} / ${state.total}", style = SunupTheme.typography.title, color = palette.muted)
                }
            }
        } else if (state.total > 1) {
            Text("${state.position + 1} / ${state.total}", style = SunupTheme.typography.title, color = palette.muted)
        }
        state.wakeCheckSecondsLeft?.let { WakeCheckChip(it) }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
            Pip(if (silencing && !state.alarmStopped) PipMood.SLEEPY else PipMood.AWAKE, size = 88.dp)
            Text(
                text = when {
                    !silencing -> "Quiet now. Take your time."
                    state.alarmStopped -> "Alarm off. Good morning!"
                    state.total > 1 -> "Answer these and I'll stop ringing."
                    else -> "Morning! One easy one and I'll stop ringing."
                },
                style = SunupTheme.typography.body,
                color = SunupTheme.colors.ink,
                modifier = Modifier
                    .background(SunupTheme.colors.surface, SunupTheme.shapes.card)
                    .padding(SunupTheme.spacing.md),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface, SunupTheme.shapes.card)
                .border(2.dp, palette.border, SunupTheme.shapes.card)
                .padding(SunupTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
        ) {
            Text(card.label.uppercase(), style = SunupTheme.typography.label, color = palette.muted)
            Text(card.prompt, style = SunupTheme.typography.display.copy(fontSize = 34.sp), color = palette.text)
            if (card.statement != null) {
                Text("means “${card.statement}”?", style = SunupTheme.typography.title, color = palette.text)
            }
        }

        Spacer(Modifier.weight(1f))

        if (card.solved) {
            Feedback(card, stopsAlarm = silencing && state.position == state.total - 1, onAction)
        } else {
            if (card.wrongPicks.isNotEmpty()) {
                Text(
                    "Not quite. Have another go.",
                    style = SunupTheme.typography.body,
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Options(card, palette, onPick = { onAction(WakeUpAction.OnPick(it)) })
            if (silencing && !state.alarmStopped) {
                TextButton(
                    onClick = { onAction(WakeUpAction.OnEmergencyStop) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Emergency stop", color = palette.muted)
                }
            }
        }
    }
}

@Composable
private fun Options(card: CardUi, palette: Palette, onPick: (Int) -> Unit) {
    val rows = if (card.kind == CardKind.TRUE_FALSE) listOf(card.options.indices.toList()) else card.options.indices.chunked(2)
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

private enum class ChoiceState { IDLE, RIGHT, WRONG, DIMMED }

/** Shake path in dp: big first swing, settling back to the middle. */
private val SHAKE_OFFSETS = listOf(-10f, 9f, -7f, 5f, -3f, 0f)

@Composable
private fun Choice(text: String, state: ChoiceState, palette: Palette, onClick: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val shape = SunupTheme.shapes.card
    val (background, border, content) = when (state) {
        ChoiceState.RIGHT -> Triple(colors.correctContainer, colors.correct, colors.onCorrectContainer)
        ChoiceState.WRONG -> Triple(colors.wrongContainer, colors.wrong, colors.onWrongContainer)
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
            .heightIn(min = 76.dp)
            .graphicsLayer {
                translationX = shake.value.dp.toPx()
                scaleX = scale.value
                scaleY = scale.value
            }
            .alpha(if (state == ChoiceState.DIMMED) 0.45f else 1f)
            .clip(shape)
            .background(background)
            .border(2.dp, border, shape)
            .clickable(enabled = state == ChoiceState.IDLE, role = Role.Button, onClick = onClick)
            .padding(SunupTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = SunupTheme.typography.title, color = content, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feedback(card: CardUi, stopsAlarm: Boolean, onAction: (WakeUpAction) -> Unit) {
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
                card.wrongPicks.isEmpty() -> "Nice one!"
                else -> "Got it. It'll come back soon."
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

@Composable
private fun BonusOffer(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val palette = Palette.dawn()
    Page(palette) {
        state.wakeCheckSecondsLeft?.let { WakeCheckChip(it) }
        Spacer(Modifier.weight(1f))
        Pip(PipMood.AWAKE, modifier = Modifier.align(Alignment.CenterHorizontally), size = 140.dp)
        Text("Bonus round", style = SunupTheme.typography.headline, color = palette.text, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            "Quiet now. Two more while you stretch?",
            style = SunupTheme.typography.body,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "No sound. No ads. Skip anytime.",
            style = SunupTheme.typography.bodySmall,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SunupButton(text = "Let's go", onClick = { onAction(WakeUpAction.OnStartBonus) })
        TextButton(onClick = { onAction(WakeUpAction.OnSkipBonus) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Not today")
        }
    }
}

@Composable
private fun Done(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val palette = Palette.day()
    Page(palette) {
        state.wakeCheckSecondsLeft?.let { WakeCheckChip(it) }
        Spacer(Modifier.weight(0.5f))
        Pip(PipMood.HAPPY, modifier = Modifier.align(Alignment.CenterHorizontally), size = 140.dp)
        Text("You're up!", style = SunupTheme.typography.display, color = palette.text, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (state.stoppedWithoutCard) {
            Text(
                "Stopped without a card. That's okay, see you tomorrow.",
                style = SunupTheme.typography.body,
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.learned.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, SunupTheme.shapes.card)
                    .border(2.dp, palette.border, SunupTheme.shapes.card)
                    .padding(SunupTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
            ) {
                Text("THIS MORNING", style = SunupTheme.typography.label, color = palette.muted)
                state.learned.forEach { item ->
                    Row {
                        Text(item.prompt, style = SunupTheme.typography.body, color = palette.text, modifier = Modifier.weight(1f))
                        Text(item.answer, style = SunupTheme.typography.body, color = palette.muted)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        SunupButton(text = "Start my day", onClick = { onAction(WakeUpAction.OnFinish) })
    }
}

/** "Wake-up check · 0:42": time left before a soft re-ring if the phone is left alone. */
@Composable
private fun WakeCheckChip(secondsLeft: Int) {
    Row(
        modifier = Modifier
            .background(SunupTheme.colors.indigoContainer, SunupTheme.shapes.pill)
            .padding(horizontal = SunupTheme.spacing.md, vertical = SunupTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Wake-up check · 0:${secondsLeft.toString().padStart(2, '0')} · tap Start my day when you're up",
            style = SunupTheme.typography.bodySmall,
            color = SunupTheme.colors.indigo,
        )
    }
}

/** The wake-up check fired: soft ring, one big button. */
@Composable
private fun StillWithMe(onAction: (WakeUpAction) -> Unit) {
    val palette = Palette.night()
    Page(palette) {
        Spacer(Modifier.weight(1f))
        Pip(PipMood.SLEEPY, modifier = Modifier.align(Alignment.CenterHorizontally), size = 140.dp)
        Text(
            "Still with me?",
            style = SunupTheme.typography.headline,
            color = palette.text,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "Your phone sat still for a minute, so I'm ringing softly to check you're up.",
            style = SunupTheme.typography.body,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        SunupButton(text = "I'm up", onClick = { onAction(WakeUpAction.OnStillAwake) })
    }
}

@Composable
private fun currentTime(): String = remember {
    val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
