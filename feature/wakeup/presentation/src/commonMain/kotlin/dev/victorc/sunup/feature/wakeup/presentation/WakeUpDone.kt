package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.motion.popIn
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlinx.coroutines.delay

/**
 * Improvement A4 with mockup 8's tiles: "You're up!" in a bright sky. Confetti, Pip jumping,
 * the time and streak day, Correct / Cards / Streak tiles, what was answered, Start my day.
 * Weather, calendar and Share from the mockup aren't built.
 */
@Composable
internal fun YoureUpScreen(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val still = reduceMotion()
    val motion = rememberInfiniteTransition(label = "you're up")

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            Sky(motion, still)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screen, vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                state.wakeCheckSecondsLeft?.let { WakeCheckChip(it) }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "You're up!",
                        style = SunupTheme.typography.display,
                        color = colors.ink,
                        modifier = Modifier
                            .weight(1f)
                            .popIn(delayMillis = TITLE_DELAY)
                            .semantics { heading() },
                    )
                    upLabel(state)?.let {
                        Text(it, style = SunupTheme.typography.title, color = colors.onSunSoft, modifier = Modifier.popIn(delayMillis = TITLE_DELAY))
                    }
                }

                MorningInfo(state)

                val line = when {
                    state.stoppedWithoutCard -> "Stopped without a card. That's okay, see you tomorrow."
                    state.answeredCards == 1 -> "1 card before your feet hit the floor."
                    state.answeredCards > 1 -> "${state.answeredCards} cards before your feet hit the floor."
                    else -> null
                }
                line?.let {
                    Text(it, style = SunupTheme.typography.body, color = colors.inkMuted, modifier = Modifier.popIn(0, TILES_DELAY))
                }

                if (state.answeredCards > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Tile(
                            label = "Correct",
                            value = "${state.rightFirstTime}/${state.answeredCards}",
                            face = colors.correctContainer,
                            outline = colors.correct,
                            ink = colors.onCorrectContainer,
                            index = 1,
                        )
                        Tile(
                            label = "Cards",
                            value = "+${state.answeredCards}",
                            face = colors.daySky,
                            outline = colors.indigoSoft,
                            ink = colors.indigo,
                            index = 2,
                        )
                        state.streakDays?.let { streak ->
                            Tile(
                                label = "Streak",
                                value = "$streak",
                                face = colors.sunSoft,
                                outline = colors.sunOutline,
                                ink = colors.onSunSoft,
                                index = 3,
                                plusOne = true,
                            )
                        }
                    }
                }

                if (state.learned.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .popIn(4, TILES_DELAY)
                            .background(colors.surface, SunupTheme.shapes.card)
                            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
                            .padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Text("THIS MORNING", style = SunupTheme.typography.label, color = colors.inkMuted)
                        state.learned.forEach { item ->
                            Row {
                                Text(item.prompt, style = SunupTheme.typography.body, color = colors.ink, modifier = Modifier.weight(1f))
                                Text(item.answer, style = SunupTheme.typography.body, color = colors.inkMuted)
                            }
                        }
                    }
                }
            }
            SunupButton(
                text = "Start my day",
                onClick = { onAction(WakeUpAction.OnFinish) },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = spacing.screen, vertical = spacing.lg)
                    .popIn(5, TILES_DELAY),
            )
        }
        if (!still && !state.stoppedWithoutCard) Confetti(motion)
    }
}

/** Weather and the next event (A4), each shown once it's loaded and only if switched on in Home. */
@Composable
private fun MorningInfo(state: WakeUpState) {
    val colors = SunupTheme.colors
    val weather = state.weather
    val event = state.nextEvent
    if (weather == null && event == null) return
    Column(verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            weather?.let {
                InfoTile(icon = it.icon, value = it.temperature, valueColor = colors.ink, caption = it.summary, description = "Weather: ${it.temperature}, ${it.summary}")
            }
            event?.let {
                InfoTile(icon = "📅", value = it.time, valueColor = colors.indigo, caption = it.title, description = "Next: ${it.title} at ${it.time}")
            }
        }
        // MET Norway's licence asks for this credit wherever its data is shown.
        if (weather != null) {
            Text("Weather data from MET Norway", style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
        }
    }
}

@Composable
private fun RowScope.InfoTile(icon: String, value: String, valueColor: Color, caption: String, description: String) {
    val colors = SunupTheme.colors
    Column(
        modifier = Modifier
            .weight(1f)
            .popIn()
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(SunupTheme.spacing.md)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.sm)) {
            Text(icon, style = SunupTheme.typography.title)
            Text(value, style = SunupTheme.typography.headline, color = valueColor)
        }
        Text(caption, style = SunupTheme.typography.bodySmall, color = colors.inkMuted, maxLines = 2)
    }
}

/** "07:03 · day 13", "07:03", or null in a preview. */
private fun upLabel(state: WakeUpState): String? {
    val time = state.alarmOffAt ?: return null
    val streak = state.streakDays ?: return time
    return "$time · day $streak"
}

/** The bright sky band: drifting clouds and Pip jumping for joy, rays turning. */
@Composable
private fun Sky(motion: InfiniteTransition, still: Boolean) {
    val colors = SunupTheme.colors
    val pipSize = SunupTheme.sizes.pipHero
    val jump = SunupTheme.spacing.xxl
    val drift = SunupTheme.spacing.lg

    val hop = if (still) 0f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = JUMP_MILLIS
                0f at 0
                1f at (JUMP_MILLIS * 0.35f).toInt()
                0f at (JUMP_MILLIS * 0.65f).toInt()
                0f at JUMP_MILLIS
            },
        ),
        label = "jump",
    ).value
    val rays = if (still) 0f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(RAYS_MILLIS, easing = LinearEasing)),
        label = "rays",
    ).value
    val sway = if (still) 0f else motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CLOUD_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "clouds",
    ).value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.daySky)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(pipSize * 1.3f)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shift = sway * drift.toPx()
            cloud(Offset(size.width * 0.18f + shift, size.height * 0.3f), size.height * 0.11f, colors.surface)
            cloud(Offset(size.width * 0.82f - shift, size.height * 0.5f), size.height * 0.08f, colors.surface)
        }
        Pip(
            mood = PipMood.HAPPY,
            size = pipSize,
            rayTurn = rays,
            contentDescription = "Pip the sun, cheering",
            modifier = Modifier.graphicsLayer {
                translationY = -hop * jump.toPx()
                // Squash at the bottom, stretch at the top.
                scaleX = 1f + 0.04f * hop
                scaleY = 1f - 0.04f * hop
            },
        )
    }
}

/** A puffy cloud: three circles on a flat base. */
private fun DrawScope.cloud(center: Offset, r: Float, color: Color) {
    drawCircle(color, r, Offset(center.x - r, center.y + r * 0.3f))
    drawCircle(color, r * 1.4f, Offset(center.x, center.y))
    drawCircle(color, r, Offset(center.x + r * 1.1f, center.y + r * 0.3f))
    drawRect(color, topLeft = Offset(center.x - r, center.y + r * 0.3f), size = Size(r * 2.1f, r))
}

@Composable
private fun RowScope.Tile(
    label: String,
    value: String,
    face: Color,
    outline: Color,
    ink: Color,
    index: Int,
    plusOne: Boolean = false,
) {
    Box(
        Modifier
            .weight(1f)
            .popIn(index, TILES_DELAY),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(face, SunupTheme.shapes.card)
                .border(SunupTheme.sizes.border, outline, SunupTheme.shapes.card)
                .padding(vertical = SunupTheme.spacing.md)
                .clearAndSetSemantics { contentDescription = "$label: $value" },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs),
        ) {
            Text(label.uppercase(), style = SunupTheme.typography.label, color = ink)
            Text(value, style = SunupTheme.typography.headline, color = ink)
        }
        if (plusOne) PlusOne(ink, Modifier.align(Alignment.TopEnd))
    }
}

/** "+1" floating up off the streak tile, once. */
@Composable
private fun PlusOne(ink: Color, modifier: Modifier) {
    if (reduceMotion()) return
    val rise = SunupTheme.spacing.xxl
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(PLUS_ONE_DELAY)
        progress.animateTo(1f, tween(PLUS_ONE_MILLIS, easing = FastOutSlowInEasing))
    }
    Text(
        "+1",
        style = SunupTheme.typography.title,
        color = ink,
        modifier = modifier
            .padding(end = SunupTheme.spacing.md)
            .graphicsLayer {
                val p = progress.value
                translationY = -p * rise.toPx()
                alpha = when {
                    p == 0f -> 0f
                    p < 0.3f -> p / 0.3f
                    else -> 1f - (p - 0.3f) / 0.7f
                }
            },
    )
}

/** Paper pieces falling and turning, over the whole screen. Not drawn with reduced motion. */
@Composable
private fun Confetti(motion: InfiniteTransition) {
    val colors = SunupTheme.colors
    val pieceColors = listOf(colors.sun, colors.indigoSoft, colors.daySky, colors.cheek, colors.correct)
    val piece = SunupTheme.spacing.md
    val base = motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CONFETTI_CYCLE_MILLIS, easing = LinearEasing)),
        label = "confetti",
    ).value
    Canvas(Modifier.fillMaxSize()) {
        val w = piece.toPx() * 0.65f
        val h = piece.toPx()
        CONFETTI.forEachIndexed { index, (x, offset) ->
            // Whole numbers of falls per cycle, so the loop never jumps.
            val falls = if (index % 3 == 0) 3 else 4
            val p = (base * falls + offset) % 1f
            val center = Offset(size.width * x, -h + p * (size.height + 2 * h))
            rotate(degrees = p * 540f, pivot = center) {
                drawRoundRect(
                    pieceColors[index % pieceColors.size],
                    topLeft = Offset(center.x - w / 2, center.y - h / 2),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 3),
                )
            }
        }
    }
}

/** Where each piece falls (fraction of the width) and how far along it starts. */
private val CONFETTI = listOf(
    0.06f to 0.0f, 0.18f to 0.55f, 0.31f to 0.2f, 0.44f to 0.75f, 0.56f to 0.35f,
    0.68f to 0.9f, 0.79f to 0.1f, 0.9f to 0.6f, 0.25f to 0.4f, 0.62f to 0.05f,
)

private const val TITLE_DELAY = 200L
private const val TILES_DELAY = 500L
private const val PLUS_ONE_DELAY = 1300L
private const val PLUS_ONE_MILLIS = 1600
private const val JUMP_MILLIS = 1100
private const val RAYS_MILLIS = 8000
private const val CLOUD_MILLIS = 4500
private const val CONFETTI_CYCLE_MILLIS = 12_000
