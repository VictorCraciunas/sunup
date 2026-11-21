package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Mockup 6: the alarm is ringing. Night sky with twinkling stars, Pip rising from behind a
 * hill with rings pulsing around it, and one big button to the first card. No snooze.
 * Everything holds still with "Remove animations".
 */
@Composable
internal fun RingingScreen(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val still = reduceMotion()
    val motion = rememberInfiniteTransition(label = "ringing")

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.nightBackground),
    ) {
        Stars(motion, still)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = spacing.xxl, vertical = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(currentDay(), style = SunupTheme.typography.title, color = colors.onNightMuted)
            Text(
                currentTime(),
                style = SunupTheme.typography.clockHero,
                color = colors.onNight,
                modifier = Modifier.semantics { heading() },
            )

            Sunrise(motion, still)

            RingingMessage(state, motion, still, Modifier.riseIn(index = 8))

            Spacer(Modifier.weight(1f))

            val nudge = if (still) {
                0f
            } else {
                // A little hop every couple of seconds, so a sleepy eye finds the button.
                motion.animateFloat(
                    initialValue = 0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        keyframes {
                            durationMillis = NUDGE_MILLIS
                            0f at 0
                            0f at (NUDGE_MILLIS * 0.88f).toInt()
                            -1f at (NUDGE_MILLIS * 0.92f).toInt()
                            0f at (NUDGE_MILLIS * 0.96f).toInt()
                        },
                    ),
                    label = "nudge",
                ).value
            }
            val hop = SunupTheme.spacing.sm
            SunupButton(
                text = "I'm up, let's go",
                onClick = { onAction(WakeUpAction.OnWakeUp) },
                modifier = Modifier.graphicsLayer { translationY = nudge * hop.toPx() },
            )
        }
    }
}

/** A few stars that slowly brighten and fade, each at its own pace. */
@Composable
private fun Stars(motion: InfiniteTransition, still: Boolean) {
    val color = SunupTheme.colors.onNight
    val dot = SunupTheme.spacing.xs
    val phase = if (still) {
        0.25f
    } else {
        motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(STAR_MILLIS, easing = LinearEasing)),
            label = "stars",
        ).value
    }
    Canvas(Modifier.fillMaxSize()) {
        STARS.forEachIndexed { index, (x, y) ->
            val t = phase + index * 0.29f
            val alpha = 0.2f + 0.7f * (0.5f - 0.5f * cos(2 * PI * t).toFloat())
            val radius = dot.toPx() * if (index % 2 == 0) 0.75f else 0.5f
            drawCircle(color.copy(alpha = alpha), radius, Offset(size.width * x, size.height * y))
        }
    }
}

/** Pip rising from behind the hill, wiggling, rays turning, rings pulsing outwards. */
@Composable
private fun Sunrise(motion: InfiniteTransition, still: Boolean) {
    val colors = SunupTheme.colors
    val pipSize = SunupTheme.sizes.pipHero * 1.35f
    val ringBase = SunupTheme.sizes.pipHero * 0.72f
    val ringWidth = SunupTheme.sizes.border * 1.5f

    // Rises once, with a little overshoot.
    val rise = remember { Animatable(if (still) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!still) rise.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
    }
    val rings = if (still) 0.35f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RING_MILLIS, easing = LinearEasing)),
        label = "rings",
    ).value
    val wiggle = if (still) 0f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = WIGGLE_MILLIS
                0f at 0
                -7f at (WIGGLE_MILLIS * 0.2f).toInt()
                6f at (WIGGLE_MILLIS * 0.4f).toInt()
                -4f at (WIGGLE_MILLIS * 0.6f).toInt()
                2f at (WIGGLE_MILLIS * 0.8f).toInt()
                0f at WIGGLE_MILLIS
            },
        ),
        label = "wiggle",
    ).value
    val rays = if (still) 0f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(RAYS_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "rays",
    ).value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SunupTheme.sizes.pipHero * 2f)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            repeat(3) { index ->
                val p = (rings + index / 3f) % 1f
                drawCircle(
                    colors.sun.copy(alpha = 0.7f * (1f - p)),
                    radius = ringBase.toPx() * (0.55f + 1.05f * p),
                    center = center,
                    style = Stroke(ringWidth.toPx()),
                )
            }
        }
        Pip(
            mood = PipMood.SLEEPY,
            size = pipSize,
            rayTurn = rays,
            contentDescription = "Pip the sun, waking up",
            modifier = Modifier.graphicsLayer {
                translationY = rise.value * pipSize.toPx()
                rotationZ = wiggle
                // Turn around the bottom, like the mockup's wiggle.
                transformOrigin = TransformOrigin(0.5f, 0.8f)
            },
        )
        // The hill Pip rises from behind.
        Canvas(Modifier.fillMaxSize()) {
            val hill = size.height * 0.36f
            drawOval(
                colors.nightSurface,
                topLeft = Offset(-size.width * 0.1f, size.height - hill),
                size = Size(size.width * 1.2f, hill * 2),
            )
        }
    }
}

@Composable
private fun RingingMessage(state: WakeUpState, motion: InfiniteTransition, still: Boolean, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val cards = state.silencingCount.coerceAtLeast(1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.nightSurface, SunupTheme.shapes.card)
            .padding(horizontal = spacing.xl, vertical = spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            if (cards == 1) "Good morning! 1 quick card and the day is yours." else "Good morning! $cards quick cards and the day is yours.",
            style = SunupTheme.typography.headline,
            color = colors.onNight,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (!state.isPreview) SoundBars(motion, still)
            Text(
                if (state.isPreview) "Preview · no sound, nothing saved" else "Soft at first · louder until you answer",
                style = SunupTheme.typography.bodySmall,
                color = colors.onNightMuted,
            )
        }
    }
}

/** Four little bars bouncing like a sound meter. */
@Composable
private fun SoundBars(motion: InfiniteTransition, still: Boolean) {
    val colors = SunupTheme.colors
    val low = SunupTheme.spacing.sm
    val high = SunupTheme.spacing.xl
    val beat = if (still) 0.5f else motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BARS_MILLIS, easing = LinearEasing)),
        label = "bars",
    ).value
    Row(
        modifier = Modifier.height(high),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.xs / 2),
    ) {
        repeat(4) { index ->
            val t = beat - index * 0.15f
            val level = 0.5f - 0.5f * cos(2 * PI * t).toFloat()
            Box(
                Modifier
                    .width(SunupTheme.spacing.xs)
                    .height(low + (high - low) * level)
                    .background(colors.sun, SunupTheme.shapes.pill),
            )
        }
    }
}

@Composable
private fun currentDay(): String = remember {
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
}

/** Where the stars sit, as fractions of the screen. */
private val STARS = listOf(0.1f to 0.14f, 0.85f to 0.1f, 0.77f to 0.27f, 0.18f to 0.31f)

private const val STAR_MILLIS = 2400
private const val RING_MILLIS = 2400
private const val WIGGLE_MILLIS = 1200
private const val RAYS_MILLIS = 10_000
private const val BARS_MILLIS = 1000
private const val NUDGE_MILLIS = 2200
