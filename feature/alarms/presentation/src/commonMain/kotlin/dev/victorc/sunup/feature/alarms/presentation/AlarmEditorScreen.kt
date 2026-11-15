package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
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
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            IconButton(description = "Back to alarms", onClick = { onAction(AlarmEditorAction.OnBack) }) {
                Chevron(direction = ChevronDirection.LEFT, color = colors.inkMuted)
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
                    DayChip(day = day.letter(), name = day.fullName(), on = day in state.days) {
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
        TimeDialog(
            hour = state.hour,
            minute = state.minute,
            onPick = { hour, minute ->
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
                Stepper(
                    value = state.hour.toString().padStart(2, '0'),
                    upLabel = "Later hour",
                    downLabel = "Earlier hour",
                    onUp = { onAction(AlarmEditorAction.OnHourStep(1)) },
                    onDown = { onAction(AlarmEditorAction.OnHourStep(-1)) },
                    onTapValue = onPickTime,
                )
                Text(":", style = SunupTheme.typography.clock, color = colors.sunOutline)
                Stepper(
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
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    upLabel: String,
    downLabel: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTapValue: () -> Unit,
) {
    val muted = SunupTheme.colors.inkMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(description = upLabel, onClick = onUp) { Chevron(ChevronDirection.UP, muted) }
        val still = reduceMotion()
        // Each new value drops in with a little bounce, like the mockups' ticking digits.
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val change = if (still) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (slideInVertically(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) { -it / 5 } +
                        fadeIn(tween(durationMillis = 150))) togetherWith fadeOut(tween(durationMillis = 90))
                }
                change using SizeTransform(clip = false)
            },
            label = "digit",
            modifier = Modifier
                .clip(SunupTheme.shapes.button)
                .clickable(onClickLabel = "Type a time", role = Role.Button, onClick = onTapValue)
                .padding(horizontal = SunupTheme.spacing.sm),
        ) { shown ->
            Text(text = shown, style = SunupTheme.typography.clock, color = SunupTheme.colors.ink)
        }
        IconButton(description = downLabel, onClick = onDown) { Chevron(ChevronDirection.DOWN, muted) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(picker.hour, picker.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = picker) },
    )
}

@Composable
private fun Section(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
        Text(title.uppercase(), style = SunupTheme.typography.label, color = SunupTheme.colors.inkMuted)
        content()
    }
}

@Composable
private fun DayChip(day: String, name: String, on: Boolean, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = if (on) colors.indigo else colors.surface,
        edge = if (on) colors.indigoShadow else colors.border,
        outline = if (on) null else colors.border,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        role = Role.Checkbox,
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .wobbleWhenOn(on)
            .semantics {
                contentDescription = name
                selected = on
            },
    ) {
        Text(day, style = SunupTheme.typography.body, color = if (on) colors.onIndigo else colors.inkMuted)
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

@Composable
private fun IconButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .clip(SunupTheme.shapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private enum class ChevronDirection { UP, DOWN, LEFT }

@Composable
private fun Chevron(direction: ChevronDirection, color: Color) {
    val stroke = SunupTheme.sizes.border
    Canvas(Modifier.size(SunupTheme.spacing.xl)) {
        val w = size.width
        val h = size.height
        val points = when (direction) {
            ChevronDirection.UP -> listOf(Offset(w * 0.2f, h * 0.65f), Offset(w * 0.5f, h * 0.35f), Offset(w * 0.8f, h * 0.65f))
            ChevronDirection.DOWN -> listOf(Offset(w * 0.2f, h * 0.35f), Offset(w * 0.5f, h * 0.65f), Offset(w * 0.8f, h * 0.35f))
            ChevronDirection.LEFT -> listOf(Offset(w * 0.65f, h * 0.2f), Offset(w * 0.35f, h * 0.5f), Offset(w * 0.65f, h * 0.8f))
        }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
        }
        drawPath(path, color, style = Stroke(width = stroke.toPx() * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
