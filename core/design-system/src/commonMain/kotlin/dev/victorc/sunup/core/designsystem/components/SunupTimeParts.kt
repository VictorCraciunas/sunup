package dev.victorc.sunup.core.designsystem.components

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * One half of a clock (hours or minutes) with an arrow above and below. Each new value drops
 * in with a little bounce, like the mockups' ticking digits. Tapping the number calls [onTapValue].
 */
@Composable
fun SunupStepper(
    value: String,
    upLabel: String,
    downLabel: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTapValue: () -> Unit,
) {
    val muted = SunupTheme.colors.inkMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SunupIconButton(description = upLabel, onClick = onUp) { SunupChevron(ChevronDirection.UP, muted) }
        val still = reduceMotion()
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
        SunupIconButton(description = downLabel, onClick = onDown) { SunupChevron(ChevronDirection.DOWN, muted) }
    }
}

/** A day of the week to switch on or off, e.g. "M" for Monday ([name] is read out). */
@Composable
fun SunupDayChip(day: String, name: String, on: Boolean, onClick: () -> Unit) {
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

/** A plain, touch-sized button around an icon. */
@Composable
fun SunupIconButton(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
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

enum class ChevronDirection { UP, DOWN, LEFT }

@Composable
fun SunupChevron(direction: ChevronDirection, color: Color) {
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
