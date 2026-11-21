package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.components.SunupTopBar
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every alarm, opened from Home's alarm card.
 *
 * @param onOpenEditor null id: a new alarm
 * @param onBack back to Home
 * @param onOpenTestBench shows a link to the debug test bench when set (Android debug only)
 */
@Composable
fun AlarmListRoot(
    onOpenEditor: (alarmId: String?) -> Unit,
    onBack: () -> Unit,
    onOpenTestBench: (() -> Unit)? = null,
) {
    val viewModel = koinViewModel<AlarmListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openEditor by rememberUpdatedState(onOpenEditor)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AlarmListEvent.OpenEditor -> openEditor(event.alarmId)
            }
        }
    }
    AlarmListScreen(state, viewModel::onAction, onBack, onOpenTestBench)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun AlarmListScreen(
    state: AlarmListState,
    onAction: (AlarmListAction) -> Unit,
    onBack: () -> Unit,
    onOpenTestBench: (() -> Unit)? = null,
) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SunupTopBar(title = "Alarms", onBack = onBack) {
            AddButton(onClick = { onAction(AlarmListAction.OnAdd) })
        }

        state.message?.let { Message(it, onDismiss = { onAction(AlarmListAction.OnDismissMessage) }) }

        when {
            state.loading -> Spacer(Modifier.weight(1f))
            state.alarms.isEmpty() -> EmptyAlarms(onAdd = { onAction(AlarmListAction.OnAdd) }, modifier = Modifier.weight(1f))
            else -> {
                NextAlarmBanner(state.next, Modifier.riseIn(index = 0))
                // A plain scrolling column: few alarms, and each card rises in once, as in the mockups.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    state.alarms.forEachIndexed { index, alarm ->
                        key(alarm.id) {
                            AlarmCard(
                                alarm = alarm,
                                onToggle = { onAction(AlarmListAction.OnToggle(alarm.id, it)) },
                                onEdit = { onAction(AlarmListAction.OnEdit(alarm.id)) },
                                modifier = Modifier.riseIn(index = index + 1),
                            )
                        }
                    }
                }
            }
        }

        if (onOpenTestBench != null) {
            TextButton(onClick = onOpenTestBench, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Test bench", color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = SunupTheme.colors
    val stroke = SunupTheme.sizes.pressDepth
    SunupRaised(
        face = colors.sun,
        edge = colors.sunShadow,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(SunupTheme.sizes.minTouchTarget)
            .semantics { contentDescription = "Add alarm" },
    ) {
        Canvas(Modifier.size(SunupTheme.spacing.xxl)) {
            val width = stroke.toPx() * 0.8f
            drawLine(colors.onSun, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), width, StrokeCap.Round)
            drawLine(colors.onSun, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), width, StrokeCap.Round)
        }
    }
}

@Composable
private fun NextAlarmBanner(next: String?, modifier: Modifier) {
    val colors = SunupTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.sunSoft, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Pip(if (next != null) PipMood.AWAKE else PipMood.SLEEPY, size = SunupTheme.sizes.pipBadge)
        Text(
            text = next ?: "No alarms on. Enjoy the sleep-in.",
            style = SunupTheme.typography.body,
            color = colors.onSunSoft,
        )
    }
}

@Composable
private fun AlarmCard(alarm: AlarmItemUi, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val on = alarm.enabled
    val text = if (on) colors.onIndigo else colors.inkMuted
    val tagBackground = if (on) colors.indigoShadow else colors.track

    // On: indigo over its dark edge. Off: a white card, quieter, as in the mockups.
    SunupRaised(
        face = if (on) colors.indigo else colors.surface,
        edge = if (on) colors.indigoShadow else colors.border,
        outline = if (on) null else colors.border,
        onClick = onEdit,
        onClickLabel = "Edit alarm",
        contentPadding = PaddingValues(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.md),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(alarm.time, style = SunupTheme.typography.display, color = if (on) colors.onIndigo else colors.ink, modifier = Modifier.weight(1f))
                Switch(
                    checked = on,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.sun,
                        checkedThumbColor = colors.surface,
                        checkedBorderColor = colors.sun,
                        uncheckedTrackColor = colors.track,
                        uncheckedThumbColor = colors.surface,
                        uncheckedBorderColor = colors.border,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Alarm ${alarm.time}" },
                )
            }
            Text(alarm.details, style = SunupTheme.typography.body, color = text)
            Text(
                text = alarm.mode,
                style = SunupTheme.typography.label,
                color = text,
                modifier = Modifier
                    .background(tagBackground, SunupTheme.shapes.pill)
                    .padding(horizontal = SunupTheme.spacing.sm, vertical = SunupTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun EmptyAlarms(onAdd: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .riseIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md, Alignment.CenterVertically),
    ) {
        Pip(PipMood.SLEEPY, size = SunupTheme.sizes.pipHero)
        Text("No alarms yet", style = SunupTheme.typography.headline, color = SunupTheme.colors.ink)
        Text(
            "Set one, and Pip wakes you with a quick card instead of a snooze button.",
            style = SunupTheme.typography.body,
            color = SunupTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        SunupButton(text = "Add an alarm", onClick = onAdd)
    }
}

@Composable
internal fun Message(text: String, onDismiss: () -> Unit) {
    val colors = SunupTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.wrongContainer, SunupTheme.shapes.card)
            .padding(start = SunupTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = SunupTheme.typography.bodySmall, color = colors.onWrongContainer, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("OK", color = colors.onWrongContainer) }
    }
}
