package dev.victorc.sunup.feature.wakeup.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.victorc.sunup.core.designsystem.components.Pip
import dev.victorc.sunup.core.designsystem.components.PipMood
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupRaised
import dev.victorc.sunup.core.designsystem.motion.bob
import dev.victorc.sunup.core.designsystem.motion.reduceMotion
import dev.victorc.sunup.core.designsystem.motion.riseIn
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * Improvement A3: the quiet bonus round, offered on a warm dawn screen. Lists the cards
 * waiting (game, new or practice, a hint), then Let's go or Not today.
 */
@Composable
internal fun BonusRoundScreen(state: WakeUpState, onAction: (WakeUpAction) -> Unit) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.dawn),
    ) {
        // The hill along the bottom.
        Canvas(Modifier.fillMaxSize()) {
            val hill = size.height * 0.3f
            drawOval(colors.dawnHill, topLeft = Offset(-size.width * 0.1f, size.height - hill), size = Size(size.width * 1.2f, hill * 2))
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = spacing.screen, vertical = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            state.wakeCheckSecondsLeft?.let { WakeCheckChip(it) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                FloatingPip()
                Text(
                    if (state.bonusItems.size == 2) "Quiet now. Two more while you stretch?" else "Quiet now. A few more while you stretch?",
                    style = SunupTheme.typography.body,
                    color = colors.ink,
                    modifier = Modifier
                        .background(colors.surface, SunupTheme.shapes.card)
                        .padding(spacing.md),
                )
            }

            Text(
                "Bonus round",
                style = SunupTheme.typography.headline,
                color = colors.ink,
                modifier = Modifier
                    .riseIn(0)
                    .semantics { heading() },
            )
            state.bonusItems.forEachIndexed { index, item ->
                BonusRow(item, Modifier.riseIn(index + 1))
            }

            Spacer(Modifier.weight(1f))

            Text(
                "No sound. No ads. Skip anytime.",
                style = SunupTheme.typography.bodySmall,
                color = colors.onSunSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SunupButton(text = "Let's go", onClick = { onAction(WakeUpAction.OnStartBonus) })
            TextButton(onClick = { onAction(WakeUpAction.OnSkipBonus) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Not today", style = SunupTheme.typography.body, color = colors.onSunSoft)
            }
        }
    }
}

/** Pip awake in a warm circle, floating, rays slowly turning. */
@Composable
private fun FloatingPip() {
    val colors = SunupTheme.colors
    val size = SunupTheme.sizes.pipHero * 0.85f
    val still = reduceMotion()
    val rays = if (still) 0f else rememberInfiniteTransition(label = "bonus pip").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(RAYS_MILLIS, easing = LinearEasing)),
        label = "rays",
    ).value
    Box(
        modifier = Modifier
            .size(size)
            .bob()
            .background(colors.sun.copy(alpha = 0.4f), SunupTheme.shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Pip(PipMood.AWAKE, size = size * 0.93f, rayTurn = rays, contentDescription = "Pip the sun, awake and smiling")
    }
}

@Composable
private fun BonusRow(item: BonusItemUi, modifier: Modifier) {
    val colors = SunupTheme.colors
    val spacing = SunupTheme.spacing
    SunupRaised(
        face = colors.surface,
        edge = colors.dawnEdge,
        outline = colors.dawnEdge,
        contentPadding = PaddingValues(spacing.lg),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(
                modifier = Modifier
                    .size(SunupTheme.sizes.pipBadge)
                    .background(if (item.isNew) colors.correctContainer else colors.daySky, SunupTheme.shapes.button),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (item.isNew) "⭐" else "🔁", style = SunupTheme.typography.title)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    if (item.isNew) "NEW CARD" else "PRACTICE",
                    style = SunupTheme.typography.label,
                    color = if (item.isNew) colors.onCorrectContainer else colors.indigo,
                    modifier = Modifier
                        .background(if (item.isNew) colors.correctContainer else colors.daySky, SunupTheme.shapes.pill)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs / 2),
                )
                Text(item.game, style = SunupTheme.typography.title, color = colors.ink)
                if (item.hint.isNotBlank()) {
                    Text(item.hint, style = SunupTheme.typography.bodySmall, color = colors.inkMuted, maxLines = 1)
                }
            }
        }
    }
}

private const val RAYS_MILLIS = 16_000
