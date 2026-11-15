package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * Improvement A2, "first light": right after the alarm stops (Normal and Heavy sleeper).
 * Pip peeks over the horizon, and a ring counts down the wake-up check: if the phone is left
 * alone that long, it rings again softly. Any touch on the screen starts the minute again.
 */
@Composable
internal fun WakeCheckScreen(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.firstLight)
            .safeContentPadding()
            .padding(horizontal = spacing.screen, vertical = spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        AlarmOffPill(state.alarmOffAt)
        Horizon()
        Text(
            "Stay with me for a minute",
            style = SunupTheme.typography.headline,
            color = colors.onNight,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .riseIn(1)
                .semantics { heading() },
        )
        Text(
            "If your phone sits still for 60 seconds, I'll ring again, softly.",
            style = SunupTheme.typography.body,
            color = colors.onNightMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.riseIn(2),
        )
        Countdown(state.wakeCheckSecondsLeft, Modifier.riseIn(3))
        Text(
            "Tapping anything starts the minute again.",
            style = SunupTheme.typography.bodySmall,
            color = colors.onNightMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        if (state.bonusCount > 0) {
            SunupButton(
                text = if (state.bonusCount == 1) "Play 1 bonus card" else "Play ${state.bonusCount} bonus cards",
                onClick = { onAction(WakeUpAction.OnStartBonus) },
            )
        }
        NightButton(text = if (state.bonusCount > 0) "I'm up, skip" else "I'm up", onClick = { onAction(WakeUpAction.OnSkipBonus) })
    }
}

@Composable
private fun AlarmOffPill(at: String?) {
    val colors = SunupTheme.colors
    Text(
        text = if (at != null) "✓  Alarm off · $at" else "✓  Alarm off",
        style = SunupTheme.typography.label,
        color = colors.correctContainer,
        modifier = Modifier
            .background(colors.correctShadow, SunupTheme.shapes.pill)
            .padding(horizontal = SunupTheme.spacing.md, vertical = SunupTheme.spacing.sm)
            .riseIn(0),
    )
}

/** Pip peeking over the hill, a warm glow breathing behind it. */
@Composable
private fun Horizon() {
    val colors = SunupTheme.colors
    val still = reduceMotion()
    val pipSize = SunupTheme.sizes.pipHero

    val peek = remember { Animatable(if (still) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!still) peek.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow))
    }
    val glow = if (still) {
        0.45f
    } else {
        rememberInfiniteTransition(label = "first light").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(GLOW_MILLIS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        ).value
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pipSize * 1.2f)
            .clipToBounds(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(colors.cheek.copy(alpha = glow), radius = size.height * 0.85f, center = Offset(size.width / 2, size.height * 1.1f))
        }
        Pip(
            mood = PipMood.AWAKE,
            size = pipSize,
            contentDescription = "Pip the sun, peeking over the horizon",
            modifier = Modifier.graphicsLayer { translationY = pipSize.toPx() * (0.25f + 0.35f * peek.value) },
        )
        Canvas(Modifier.fillMaxSize()) {
            val hill = size.height * 0.4f
            drawOval(
                colors.indigoShadow,
                topLeft = Offset(-size.width * 0.1f, size.height - hill),
                size = Size(size.width * 1.2f, hill * 2),
            )
        }
    }
}

/** A ring that empties as the minute runs out; "0:42 · until I check" inside. */
@Composable
private fun Countdown(secondsLeft: Int?, modifier: Modifier) {
    val colors = SunupTheme.colors
    val left = (secondsLeft ?: CHECK_SECONDS).coerceIn(0, CHECK_SECONDS)
    val still = reduceMotion()
    val shown by animateFloatAsState(
        targetValue = left / CHECK_SECONDS.toFloat(),
        animationSpec = if (still) snap() else tween(durationMillis = 1000, easing = LinearEasing),
        label = "countdown",
    )
    val ringSize = SunupTheme.sizes.pipHero * 1.15f
    val thickness = SunupTheme.spacing.md + SunupTheme.spacing.xs / 2
    val time = if (left >= CHECK_SECONDS) "1:00" else "0:${left.toString().padStart(2, '0')}"

    Box(
        modifier = modifier
            .size(ringSize)
            // Not a live region: reading it out every second would drown everything else.
            .clearAndSetSemantics { contentDescription = "$left seconds until Pip checks on you" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.nightBorder, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                colors.sun,
                startAngle = -90f,
                sweepAngle = 360f * shown,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, style = SunupTheme.typography.display, color = colors.onNight)
            Text(
                if (left == 0) "ringing softly" else "until I check",
                style = SunupTheme.typography.bodySmall,
                color = colors.onNightMuted,
            )
        }
    }
}

/** The darker second button from the mockup. */
@Composable
private fun NightButton(text: String, onClick: () -> Unit) {
    val colors = SunupTheme.colors
    SunupRaised(
        face = colors.nightBorder,
        edge = colors.nightShadow,
        shape = SunupTheme.shapes.button,
        onClick = onClick,
        contentAlignment = Alignment.Center,
        contentPadding = PaddingValues(horizontal = SunupTheme.spacing.lg),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.buttonHeight),
    ) {
        Row {
            Text(text.uppercase(), style = SunupTheme.typography.button, color = colors.onNight)
        }
    }
}

private const val CHECK_SECONDS = 60
private const val GLOW_MILLIS = 3000
