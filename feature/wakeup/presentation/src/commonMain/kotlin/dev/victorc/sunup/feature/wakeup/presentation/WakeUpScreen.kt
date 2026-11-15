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
        WakePhase.RINGING -> RingingScreen(state, onAction)
        WakePhase.SILENCING -> CardPage(state, Palette.night(), onAction)
        WakePhase.WAKE_CHECK -> WakeCheckScreen(state, onAction)
        WakePhase.BONUS_OFFER -> BonusOffer(state, onAction)
        WakePhase.BONUS -> CardPage(state, Palette.dawn(), onAction)
        WakePhase.DONE -> Done(state, onAction)
    }
}

/** The screen brightens as you wake: night for the silencing card, dawn for the bonus, day at the end. */
@Immutable
internal data class Palette(
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
            // The map needs the room, so Pip sits smaller above it.
            Pip(
                if (silencing && !state.alarmStopped) PipMood.SLEEPY else PipMood.AWAKE,
                size = if (card.isMapCard) 56.dp else 88.dp,
            )
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

        PromptCard(card, palette)
        if (card is CardUi.Match) MatchGrid(card, palette, onAction)

        when {
            card is CardUi.FindOnMap -> MapArea(card, state.atlas, palette, onAction, Modifier.weight(1f))
            card is CardUi.Choice && card.map != null -> NameMapArea(card, state.atlas, palette, Modifier.weight(1f))
            else -> Spacer(Modifier.weight(1f))
        }

        if (card.solved) {
            Feedback(card, stopsAlarm = silencing && state.position == state.total - 1, onAction)
        } else {
            GameControls(card, palette, onAction)
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
        state.upLine?.let {
            Text(
                it,
                style = SunupTheme.typography.title,
                color = SunupTheme.colors.onSunSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

private val CardUi.isMapCard: Boolean
    get() = this is CardUi.FindOnMap || (this is CardUi.Choice && map != null)

@Composable
internal fun currentTime(): String = remember {
    val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
