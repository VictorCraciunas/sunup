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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.Pip
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
            .safeDrawingPadding()
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
            StreakCard(state.streak, state.bestStreak, Modifier.riseIn(0))
            // As tall as the taller tile: one may wrap to more lines on a narrow phone.
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .riseIn(0),
            ) {
                Stat(
                    value = "+${state.learned}",
                    label = "cards learned",
                    detail = if (state.period == Period.WEEK) "this week" else "this month",
                    face = colors.daySky,
                    ink = colors.indigo,
                )
                Stat(
                    value = state.remembered,
                    label = "remembered",
                    detail = "of cards seen before",
                    face = colors.correctContainer,
                    ink = colors.onCorrectContainer,
                )
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

/** Mockup I1: the streak on its own card, with Pip. */
@Composable
private fun StreakCard(streak: Int, best: Int, modifier: Modifier) {
    val colors = SunupTheme.colors
    val days = if (streak == 1) "1 day" else "$streak days"
    val line = when {
        streak == 0 -> "Answer tomorrow's card to start one"
        streak >= best -> "in a row · your best yet"
        else -> "in a row · best: $best days"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.sunSoft, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.sun, SunupTheme.shapes.card)
            .padding(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.md)
            .clearAndSetSemantics { contentDescription = "Streak: $days. $line" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Pip(if (streak == 0) PipMood.SLEEPY else PipMood.HAPPY, size = SunupTheme.sizes.pipBadge)
        Column {
            Text(if (streak == 0) "No streak yet" else days, style = SunupTheme.typography.display, color = colors.onSunSoft)
            Text(line, style = SunupTheme.typography.body, color = colors.onSunSoft)
        }
    }
}

/** A wide tile: the number, what it is, and over what time. */
@Composable
private fun RowScope.Stat(value: String, label: String, detail: String, face: Color, ink: Color) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(face, SunupTheme.shapes.card)
            .padding(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.md)
            .clearAndSetSemantics { contentDescription = "$value $label, $detail" },
    ) {
        Text(value, style = SunupTheme.typography.headline, color = ink)
        Text(label, style = SunupTheme.typography.body, color = ink)
        Text(detail, style = SunupTheme.typography.bodySmall, color = ink)
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

/** Mockup I1: one row per milestone, saying what it asks and how far along you are. */
@Composable
private fun Milestones(milestones: List<MilestoneUi>, modifier: Modifier) {
    val colors = SunupTheme.colors
    Card(modifier) {
        Text("Milestones", style = SunupTheme.typography.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        Column {
            milestones.forEachIndexed { index, milestone ->
                MilestoneRow(milestone, index)
                if (index < milestones.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = SunupTheme.spacing.sm)
                            .height(SunupTheme.sizes.border)
                            .background(colors.track),
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: MilestoneUi, index: Int) {
    val colors = SunupTheme.colors
    val streak = milestone.kind == MilestoneKind.STREAK
    val ink = if (streak) colors.onSunSoft else colors.indigo
    val fill = if (streak) colors.sun else colors.indigoSoft
    val spoken = if (milestone.earned) {
        "${milestone.name}, earned. ${milestone.goal}."
    } else {
        "${milestone.name}: ${milestone.goal}. ${milestone.progress} of ${milestone.target}."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .popIn(index, MEDAL_DELAY)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Medal(milestone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(milestone.name, style = SunupTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                if (!milestone.earned) {
                    Text("${milestone.progress} / ${milestone.target}", style = SunupTheme.typography.bodySmall, color = ink)
                }
            }
            if (!milestone.earned) {
                ProgressLine(milestone.progress.toFloat() / milestone.target, fill)
            }
            Text(
                if (milestone.earned) "${milestone.goal} · earned" else milestone.goal,
                style = SunupTheme.typography.bodySmall,
                color = colors.inkMuted,
            )
        }
    }
}

/** Earned: a raised sun-yellow medal with a tick. Locked: a dashed outline with its number. */
@Composable
private fun Medal(milestone: MilestoneUi) {
    val colors = SunupTheme.colors
    val size = SunupTheme.sizes.pipBadge
    val stroke = SunupTheme.sizes.border
    val depth = SunupTheme.sizes.pressDepth
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = CornerRadius(this.size.width * 0.3f)
            if (milestone.earned) {
                val face = this.size.copy(height = this.size.height - depth.toPx())
                drawRoundRect(colors.sunShadow, topLeft = Offset(0f, depth.toPx()), size = face, cornerRadius = corner)
                drawRoundRect(colors.sun, size = face, cornerRadius = corner)
                // The tick.
                val w = this.size.width
                val tick = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.28f, w * 0.48f)
                    lineTo(w * 0.44f, w * 0.63f)
                    lineTo(w * 0.72f, w * 0.33f)
                }
                drawPath(tick, colors.onSun, style = Stroke(stroke.toPx() * 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            } else {
                drawRoundRect(colors.track, cornerRadius = corner)
                drawRoundRect(
                    colors.border,
                    cornerRadius = corner,
                    style = Stroke(stroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke.toPx() * 2.5f, stroke.toPx() * 2f))),
                )
            }
        }
        if (!milestone.earned) {
            Text(milestone.badge, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
        }
    }
}

/** A thin bar that fills to [fraction]. */
@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    val colors = SunupTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(SunupTheme.spacing.sm)
            .clip(SunupTheme.shapes.pill)
            .background(colors.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(SunupTheme.shapes.pill)
                .background(color),
        )
    }
}

private const val BAR_DELAY = 150L
private const val BAR_STAGGER = 70L
private const val CELL_STAGGER = 20L
private const val MEDAL_DELAY = 300L
