package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupBackButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.bob
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.wobbleWhenOn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * One first-run screen: back arrow and progress bar on top, scrolling [content], and the
 * [bottom] buttons always in view.
 *
 * @param progress 0..1; null hides the header (the welcome screen)
 * @param centered puts short content in the middle of the screen instead of at the top
 */
@Composable
internal fun OnboardingFrame(
    progress: Float?,
    onBack: () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = SunupTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SunupTheme.colors.background)
            .safeDrawingPadding()
            .padding(horizontal = spacing.screen, vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        if (progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                SunupBackButton(onBack)
                ProgressBar(progress, Modifier.weight(1f))
            }
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val viewport = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // At least the screen's height, so short content can sit in the middle.
                    .heightIn(min = viewport),
                verticalArrangement = if (centered) {
                    Arrangement.spacedBy(spacing.lg, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(spacing.lg)
                },
                content = content,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md), content = bottom)
    }
}

@Composable
private fun ProgressBar(progress: Float, modifier: Modifier) {
    val colors = SunupTheme.colors
    val still = reduceMotion()
    val shown by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (still) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "progress",
    )
    Box(
        modifier = modifier
            .height(SunupTheme.spacing.lg)
            .clip(SunupTheme.shapes.pill)
            .background(colors.track)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(shown.coerceIn(0f, 1f))
                .clip(SunupTheme.shapes.pill)
                .background(colors.sun),
        )
    }
}

/** Pip floating beside a speech bubble, like the mockups' "What should we wake up with?". */
@Composable
internal fun PipSays(text: String, mood: PipMood = PipMood.AWAKE, size: Dp = SunupTheme.sizes.pipHero / 1.6f) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md)) {
        Pip(mood = mood, size = size, modifier = Modifier.bob())
        Bubble(text, Modifier.weight(1f, fill = false))
    }
}

@Composable
internal fun Bubble(text: String, modifier: Modifier = Modifier) {
    val colors = SunupTheme.colors
    Text(
        text = text,
        style = SunupTheme.typography.title,
        color = colors.ink,
        modifier = modifier
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.md),
    )
}

/**
 * A big tappable option card. Picked: indigo outline and a little wobble.
 *
 * @param icon drawn in a soft square on the left
 * @param badge a small pill after the title, e.g. "Recommended"
 */
@Composable
internal fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
    badge: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    val picked = selected
    SunupRaised(
        face = if (selected) colors.indigoContainer else colors.surface,
        edge = if (selected) colors.indigoSoft else colors.border,
        outline = if (selected) colors.indigoSoft else colors.border,
        onClick = onClick,
        role = role,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .wobbleWhenOn(selected)
            .semantics { this.selected = picked },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            icon?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(title, style = SunupTheme.typography.title, color = colors.ink)
                    badge?.let { Pill(it, face = colors.sunSoft, ink = colors.onSunSoft) }
                }
                Text(subtitle, style = SunupTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
    }
}

/** An emoji in a soft coloured square, the option cards' icon. */
@Composable
internal fun EmojiBadge(emoji: String, tint: Color) {
    Box(
        modifier = Modifier
            .size(SunupTheme.sizes.pipBadge)
            .background(tint, SunupTheme.shapes.button),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, style = SunupTheme.typography.title)
    }
}

@Composable
internal fun Pill(text: String, face: Color, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SunupTheme.typography.label,
        color = ink,
        modifier = modifier
            .background(face, SunupTheme.shapes.pill)
            .padding(horizontal = SunupTheme.spacing.sm, vertical = SunupTheme.spacing.xs),
    )
}

/** A green tick in a soft circle. */
@Composable
internal fun Tick(size: Dp = SunupTheme.spacing.xxl + SunupTheme.spacing.sm) {
    val colors = SunupTheme.colors
    val stroke = SunupTheme.sizes.border
    Box(
        modifier = Modifier
            .size(size)
            .background(colors.correctContainer, SunupTheme.shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size / 2)) {
            val w = this.size.width
            val path = Path().apply {
                moveTo(w * 0.1f, w * 0.55f)
                lineTo(w * 0.4f, w * 0.82f)
                lineTo(w * 0.92f, w * 0.2f)
            }
            drawPath(path, colors.onCorrectContainer, style = Stroke(stroke.toPx() * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** One line of the promise screen: a tick and a sentence, on a white card. */
@Composable
internal fun TickLine(text: String, modifier: Modifier = Modifier) {
    val colors = SunupTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SunupTheme.sizes.minTouchTarget)
            .background(colors.surface, SunupTheme.shapes.card)
            .border(SunupTheme.sizes.border, colors.border, SunupTheme.shapes.card)
            .padding(horizontal = SunupTheme.spacing.lg, vertical = SunupTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
    ) {
        Tick()
        Text(text, style = SunupTheme.typography.body, color = colors.ink)
    }
}
