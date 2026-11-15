package dev.victorc.sunup.core.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * True when the user asked the phone for less motion (Android: "Remove animations";
 * iOS: "Reduce Motion"). Every Sunup animation checks this (CLAUDE.md conventions).
 */
@Composable
expect fun reduceMotion(): Boolean

/** Time between items that rise in one after another. */
const val RISE_STAGGER_MILLIS = 70L

/**
 * Slides up and fades in when first shown, [index] items after the first, like the
 * mockups' `rise` animation. Shows at once with reduced motion.
 */
fun Modifier.riseIn(index: Int = 0): Modifier = composed {
    val still = reduceMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(index * RISE_STAGGER_MILLIS)
        progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        val p = progress.value
        translationY = (1f - p) * 20.dp.toPx()
        alpha = p.coerceIn(0f, 1f)
    }
}

/**
 * Pops in from small with a springy overshoot when first shown, [index] items after the
 * first, like the mockups' `pop` on "You're up!". Shows at once with reduced motion.
 */
fun Modifier.popIn(index: Int = 0, delayMillis: Long = 0): Modifier = composed {
    val still = reduceMotion()
    val progress = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(delayMillis + index * POP_STAGGER_MILLIS)
        progress.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        val p = progress.value
        val scale = 0.4f + 0.6f * p
        scaleX = scale
        scaleY = scale
        alpha = p.coerceIn(0f, 1f)
    }
}

/** Time between items that pop in one after another. */
const val POP_STAGGER_MILLIS = 180L

/**
 * A short squash-and-stretch each time [on] turns true, like the mockups' `jelly` on a
 * picked chip. Nothing with reduced motion.
 */
fun Modifier.wobbleWhenOn(on: Boolean): Modifier = composed {
    val still = reduceMotion()
    val progress = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (!on || still) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 400, easing = LinearEasing))
    }
    graphicsLayer {
        val (x, y) = jelly(progress.value)
        scaleX = x
        scaleY = y
    }
}

/**
 * Floats gently up and down forever, like the mockups' `bob` on Pip. Still with reduced motion.
 *
 * @param height how far up it goes
 */
fun Modifier.bob(height: Dp = 8.dp): Modifier = composed {
    if (reduceMotion()) return@composed Modifier
    val transition = rememberInfiniteTransition(label = "bob")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lift",
    )
    graphicsLayer { translationY = -lift * height.toPx() }
}

/** Keyframes 0% 1/1 · 30% 1.08/0.92 · 60% 0.96/1.04 · 100% 1/1. */
private fun jelly(t: Float): Pair<Float, Float> {
    fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
    return when {
        t < 0.3f -> (t / 0.3f).let { lerp(1f, 1.08f, it) to lerp(1f, 0.92f, it) }
        t < 0.6f -> ((t - 0.3f) / 0.3f).let { lerp(1.08f, 0.96f, it) to lerp(0.92f, 1.04f, it) }
        else -> ((t - 0.6f) / 0.4f).coerceAtMost(1f).let { lerp(0.96f, 1f, it) to lerp(1.04f, 1f, it) }
    }
}
