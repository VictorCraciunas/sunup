package dev.victorc.sunup.feature.progress.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.victorc.sunup.core.designsystem.components.ChevronDirection
import dev.victorc.sunup.core.designsystem.components.SunupChevron
import dev.victorc.sunup.core.designsystem.components.SunupIconButton
import dev.victorc.sunup.core.designsystem.motion.popIn
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.wakeup.DayMark
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/** Mockup 11: the Progress tab. */
@Composable
fun ProgressRoot() {
    val viewModel = koinViewModel<ProgressViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressScreen(state, viewModel::onAction)
}

// TODO(M4): move user-facing text to string resources once localization is set up.
@Composable
fun ProgressScreen(state: ProgressState, onAction: (ProgressAction) -> Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Progress",
                style = SunupTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            PeriodToggle(state.period) { onAction(ProgressAction.OnPeriod(it)) }
        }
        state.message?.let {
            Text(it, style = SunupTheme.typography.bodySmall, color = colors.onWrongContainer, modifier = Modifier.background(colors.wrongContainer, SunupTheme.shapes.card).padding(spacing.md))
        }
        if (!state.loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.riseIn(0)) {
                Stat("Streak", if (state.streak == 1) "1 day" else "${state.streak} days", colors.sunSoft, colors.sunOutline, colors.onSunSoft)
                Stat(
                    if (state.period == Period.WEEK) "This week" else "This month",
                    "+${state.learned}",
                    colors.daySky,
                    colors.indigoSoft,
                    colors.indigo,
                )
                Stat("Remembered", state.remembered, colors.correctContainer, colors.correct, colors.onCorrectContainer)
            }

            // A new key per period, so the card rises in again when the toggle changes.
            key(state.period) {
                when (state.period) {
                    Period.WEEK -> WeekCard(state, Modifier.riseIn(1))
                    Period.MONTH -> MonthCard(state, onAction, Modifier.riseIn(1))
                }
            }

            Milestones(state.milestones, Modifier.riseIn(2))
        }
    }
}

@Composable
private fun PeriodToggle(period: Period, onPick: (Period) -> Unit) {
    val colors = SunupTheme.colors
    Row(
        modifier = Modifier
            .background(colors.track, SunupTheme.shapes.button)
            .padding(SunupTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        Period.entries.forEach { option ->
            val on = option == period
            Text(
                if (option == Period.WEEK) "Week" else "Month",
                style = SunupTheme.typography.body,
                color = if (on) colors.ink else colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(SunupTheme.shapes.button)
                    .background(if (on) colors.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(option) }
                    .semantics { selected = on }
                    .wobbleWhenOn(on)
                    .padding(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, face: Color, outline: Color, ink: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(face, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, outline, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.md)
            .clearAndSetSemantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        Text(label.uppercase(), style = SunupTheme.typography.label, color = ink)
        Text(value, style = SunupTheme.typography.headline, color = ink)
    }
}

@Composable
private fun Card(modifier: Modifier, content: @Composable () -> Unit) {
    val colors = SunupTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        content()
    }
}

@Composable
private fun CardHeader(title: String, aside: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = SunupTheme.typography.title, color = SunupTheme.colors.ink, modifier = Modifier.weight(1f))
        aside?.let { Text(it, style = SunupTheme.typography.bodySmall, color = SunupTheme.colors.inkMuted) }
    }
}

/** "Cards per morning": a bar for each day this week, growing up from the bottom. */
@Composable
private fun WeekCard(state: ProgressState, modifier: Modifier) {
    val colors = SunupTheme.colors
    val top = maxOf(state.bars.maxOfOrNull { it.cards } ?: 0, 3)
    // Room for the tallest bar plus its number and day letter.
    val chartHeight = SunupTheme.sizes.pipHero * 1.4f
    Card(modifier) {
        CardHeader("Cards per morning", state.averageWakeUp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .clearAndSetSemantics {
                    contentDescription = "Cards answered each morning this week: " +
                        state.bars.joinToString(", ") { "${it.dayName} ${it.cards}" }
                },
            horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEachIndexed { index, bar ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs, Alignment.Bottom),
                ) {
                    Text(if (bar.cards == 0) "–" else "${bar.cards}", style = SunupTheme.typography.label, color = colors.ink)
                    Bar(
                        fraction = if (bar.cards == 0) 0.06f else 0.12f + 0.68f * bar.cards / top,
                        color = if (bar.cards == 0) colors.dawnHill else colors.sun,
                        index = index,
                    )
                    Text(bar.letter, style = SunupTheme.typography.label, color = colors.inkMuted)
                }
            }
        }
    }
}

/** One bar, growing from the bottom with a little overshoot, one after another. */
@Composable
private fun Bar(fraction: Float, color: Color, index: Int) {
    val still = reduceMotion()
    val grow = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(BAR_DELAY + index * BAR_STAGGER)
        grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val height = SunupTheme.sizes.pipHero * fraction
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleY = grow.value
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .background(color, SunupTheme.shapes.button),
    )
}

/** "November sunrises": every day of the month, with ‹ › to look back. */
@Composable
private fun MonthCard(state: ProgressState, onAction: (ProgressAction) -> Unit, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SunupIconButton(description = "Earlier month", onClick = { onAction(ProgressAction.OnEarlierMonth) }) {
                SunupChevron(ChevronDirection.LEFT, colors.inkMuted)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.monthTitle} sunrises", style = SunupTheme.typography.title, color = colors.ink)
                Text(state.monthSummary, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
            }
            if (state.monthsBack > 0) {
                SunupIconButton(description = "Later month", onClick = { onAction(ProgressAction.OnLaterMonth) }) {
                    // A left chevron turned around.
                    Box(Modifier.graphicsLayer { rotationZ = 180f }) { SunupChevron(ChevronDirection.LEFT, colors.inkMuted) }
                }
            } else {
                Box(Modifier.size(SunupTheme.sizes.minTouchTarget))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = SunupTheme.typography.label, color = colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        state.monthCells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                week.forEach { cell -> MonthCell(cell, Modifier.weight(1f)) }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Legend(colors.sun, "Woke up & learned")
            Legend(colors.daySky, "Day off", outline = colors.indigoSoft)
        }
    }
}

@Composable
private fun MonthCell(cell: MonthCellUi, modifier: Modifier) {
    val colors = SunupTheme.colors
    val day = cell.day
    if (day == null || cell.mark == null) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val shape = SunupTheme.shapes.button
    val description = "$day: " + when (cell.mark) {
        DayMark.SUNRISE -> "woke up"
        DayMark.MISSED -> "missed"
        DayMark.NO_ALARM -> "day off"
        DayMark.TODAY -> "today"
        DayMark.LATER -> "still to come"
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .popIn(delayMillis = day * CELL_STAGGER)
            .clearAndSetSemantics { contentDescription = description }
            .then(
                when (cell.mark) {
                    DayMark.SUNRISE -> Modifier.background(colors.sun, shape)
                    DayMark.MISSED -> Modifier.background(colors.track, shape)
                    DayMark.NO_ALARM -> Modifier.background(colors.daySky, shape).border(SunupTheme.sizes.border, colors.indigoSoft, shape)
                    DayMark.TODAY -> Modifier.background(colors.surface, shape).border(SunupTheme.sizes.border * 1.5f, colors.sunOutline, shape)
                    DayMark.LATER -> Modifier.background(colors.track.copy(alpha = 0.5f), shape)
                },
            ),
    )
}

@Composable
private fun Legend(color: Color, label: String, outline: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
        Box(
            Modifier
                .size(SunupTheme.spacing.md)
                .background(color, SunupTheme.shapes.pill)
                .then(if (outline != null) Modifier.border(SunupTheme.sizes.border, outline, SunupTheme.shapes.pill) else Modifier),
        )
        Text(label, style = SunupTheme.typography.bodySmall, color = SunupTheme.colors.inkMuted)
    }
}

@Composable
private fun Milestones(milestones: List<MilestoneUi>, modifier: Modifier) {
    Card(modifier) {
        Text("Milestones", style = SunupTheme.typography.title, color = SunupTheme.colors.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            milestones.forEachIndexed { index, milestone -> Medal(milestone, index) }
        }
    }
}

/** Earned: a raised coloured medal. Locked: a dashed outline and what's left. */
@Composable
private fun Medal(milestone: MilestoneUi, index: Int) {
    val colors = SunupTheme.colors
    val size = SunupTheme.sizes.pipBadge * 1.4f
    val (face, edge, ink) = when {
        !milestone.earned -> Triple(colors.track, colors.border, colors.inkMuted)
        milestone.kind == MilestoneKind.STREAK -> Triple(colors.sun, colors.sunShadow, colors.onSun)
        else -> Triple(colors.indigoSoft, colors.indigo, colors.onIndigo)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm),
        modifier = Modifier
            .popIn(index, MEDAL_DELAY)
            .clearAndSetSemantics {
                contentDescription = if (milestone.earned) "Earned: ${milestone.caption}" else "${milestone.badge}: ${milestone.caption}"
            },
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val stroke = SunupTheme.sizes.border
            val depth = SunupTheme.sizes.pressDepth
            Canvas(Modifier.fillMaxSize()) {
                val corner = CornerRadius(this.size.width * 0.3f)
                if (milestone.earned) {
                    drawRoundRect(edge, topLeft = Offset(0f, depth.toPx()), size = this.size.copy(height = this.size.height - depth.toPx()), cornerRadius = corner)
                    drawRoundRect(face, size = this.size.copy(height = this.size.height - depth.toPx()), cornerRadius = corner)
                } else {
                    drawRoundRect(face, cornerRadius = corner)
                    drawRoundRect(
                        edge,
                        cornerRadius = corner,
                        style = Stroke(stroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke.toPx() * 2.5f, stroke.toPx() * 2f))),
                    )
                }
            }
            Text(milestone.badge, style = SunupTheme.typography.title, color = ink)
        }
        Text(
            milestone.caption,
            style = SunupTheme.typography.label,
            color = if (milestone.earned) colors.ink else colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private const val BAR_DELAY = 150L
private const val BAR_STAGGER = 70L
private const val CELL_STAGGER = 20L
private const val MEDAL_DELAY = 300L
