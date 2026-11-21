package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.ChevronDirection
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupChevron
import dev.victorc.sunup.core.designsystem.components.SunupDayChip
import dev.victorc.sunup.core.designsystem.components.SunupIconButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupStepper
import dev.victorc.sunup.core.designsystem.components.SunupTimeSheet
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** @param alarmId null for a new alarm */
@Composable
fun AlarmEditorRoot(alarmId: String?, onClose: () -> Unit) {
    val viewModel = koinViewModel<AlarmEditorViewModel>(key = alarmId ?: "new") { parametersOf(alarmId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val close by rememberUpdatedState(onClose)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AlarmEditorEvent.Close -> close()
            }
        }
    }
    AlarmEditorScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun AlarmEditorScreen(state: AlarmEditorState, onAction: (AlarmEditorAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    var pickingTime by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            SunupIconButton(description = "Back to alarms", onClick = { onAction(AlarmEditorAction.OnBack) }) {
                SunupChevron(direction = ChevronDirection.LEFT, color = colors.inkMuted)
            }
            Text(
                text = if (state.isNew) "New alarm" else "Edit alarm",
                style = SunupTheme.typography.headline,
                color = colors.ink,
            )
        }

        state.message?.let { Message(it, onDismiss = { onAction(AlarmEditorAction.OnDismissMessage) }) }

        TimeCard(state, onAction, onPickTime = { pickingTime = true }, modifier = Modifier.riseIn(index = 0))

        Section("Repeat on", Modifier.riseIn(index = 1)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                WEEK.forEach { day ->
                    SunupDayChip(day = day.letter(), name = day.fullName(), on = day in state.days) {
                        onAction(AlarmEditorAction.OnToggleDay(day))
                    }
                }
            }
            Text(
                text = if (state.days.isEmpty()) "Rings once, then switches itself off." else repeatText(state.days),
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }

        Section("Wake-up mode", Modifier.riseIn(index = 2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                WakeMode.entries.forEach { mode ->
                    Segment(
                        text = if (mode == WakeMode.HEAVY_SLEEPER) "Heavy" else mode.title(),
                        on = mode == state.mode,
                        onClick = { onAction(AlarmEditorAction.OnPickMode(mode)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(state.mode.hint(), style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
        }

        Section("Cards from", Modifier.riseIn(index = 3)) {
            DeckList(state.decks, selected = state.deckId, onPick = { onAction(AlarmEditorAction.OnPickDeck(it)) })
        }

        Spacer(Modifier.heightIn(min = spacing.sm))
        SunupButton(
            text = if (state.saving) "Saving…" else "Save alarm",
            onClick = { onAction(AlarmEditorAction.OnSave) },
            enabled = !state.saving && !state.loading,
        )
        if (!state.isNew) {
            TextButton(
                onClick = { onAction(AlarmEditorAction.OnDelete) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Delete alarm", color = colors.wrong)
            }
        }
    }

    if (pickingTime) {
        SunupTimeSheet(
            hour = state.hour,
            minute = state.minute,
            onDone = { hour, minute ->
                pickingTime = false
                onAction(AlarmEditorAction.OnTimePicked(hour, minute))
            },
            onDismiss = { pickingTime = false },
        )
    }
    if (state.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { onAction(AlarmEditorAction.OnCancelDelete) },
            title = { Text("Delete this alarm?") },
            text = { Text("It won't ring again. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onAction(AlarmEditorAction.OnConfirmDelete) }) { Text("Delete", color = colors.wrong) }
            },
            dismissButton = {
                TextButton(onClick = { onAction(AlarmEditorAction.OnCancelDelete) }) { Text("Keep it") }
            },
        )
    }
}

/** Hours step by 1, minutes by 5; tap the time to type an exact one. */
@Composable
private fun TimeCard(
    state: AlarmEditorState,
    onAction: (AlarmEditorAction) -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier,
) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        contentPadding = PaddingValues(SunupTheme.spacing.sm),
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
                SunupStepper(
                    value = state.hour.toString().padStart(2, '0'),
                    upLabel = "Later hour",
                    downLabel = "Earlier hour",
                    onUp = { onAction(AlarmEditorAction.OnHourStep(1)) },
                    onDown = { onAction(AlarmEditorAction.OnHourStep(-1)) },
                    onTapValue = onPickTime,
                )
                Text(":", style = SunupTheme.typography.clock, color = colors.sunOutline)
                SunupStepper(
                    value = state.minute.toString().padStart(2, '0'),
                    upLabel = "Five minutes later",
                    downLabel = "Five minutes earlier",
                    onUp = { onAction(AlarmEditorAction.OnMinuteStep(5)) },
                    onDown = { onAction(AlarmEditorAction.OnMinuteStep(-5)) },
                    onTapValue = onPickTime,
                )
            }
            state.ringsIn?.let {
                Text(it, style = SunupTheme.typography.bodySmall, color = colors.indigo, textAlign = TextAlign.Center)
            }
            Text("Tap the time to pick any minute", style = SunupTheme.typography.bodySmall, color = colors.inkMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
        Text(title.uppercase(), style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted)
        content()
    }
}

@Composable
private fun Segment(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (on) colors.indigoContainer else colors.surface,
        edge = if (on) colors.indigoSoft else colors.border,
        outline = if (on) colors.indigoSoft else colors.border,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        role = Role.RadioButton,
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = SunupTheme.sizes.minTouchTarget + SunupTheme.sizes.pressDepth)
            .wobbleWhenOn(on)
            .semantics { selected = on },
    ) {
        Text(text, style = SunupTheme.typography.body, color = if (on) colors.indigo else colors.ink)
    }
}

@Composable
private fun DeckList(decks: List<DeckOption>, selected: String?, onPick: (String?) -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.surface,
        edge = colors.border,
        outline = colors.border,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            decks.forEach { deck ->
                val on = deck.id == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = SunupTheme.sizes.minTouchTarget)
                        .clickable(role = Role.RadioButton, onClick = { onPick(deck.id) })
                        .semantics { this.selected = on }
                        .padding(horizontal = SunupTheme.spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deck.name,
                        style = SunupTheme.typography.body,
                        color = if (on) colors.indigo else colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    RadioDot(on)
                }
            }
        }
    }
}

@Composable
private fun RadioDot(on: Boolean) {
    val colors = SunupTheme.colors
    val ring = SunupTheme.sizes.border
    Canvas(Modifier.size(SunupTheme.spacing.xl)) {
        val radius = size.minDimension / 2
        drawCircle(if (on) colors.indigo else colors.border, radius - ring.toPx(), style = Stroke(ring.toPx() * 1.5f))
        if (on) drawCircle(colors.indigo, radius / 2.5f)
    }
}

