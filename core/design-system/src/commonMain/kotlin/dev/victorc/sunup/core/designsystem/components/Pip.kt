package dev.victorc.sunup.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class PipMood { SLEEPY, AWAKE, HAPPY }

/** Pip, the sun mascot, drawn from the mockups' SVG (a 120 × 120 grid). */
@Composable
fun Pip(
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    contentDescription: String? = null,
    /** Turns the rays, in degrees, so they can spin as in the mockups. */
    rayTurn: Float = 0f,
) {
    val colors = SunupTheme.colors
    val ray = Color(0xFFFFB020)
    val face = colors.ink

    Canvas(
        modifier = modifier
            .size(size)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        val unit = this.size.minDimension / 120f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)

        // Rays
        for (i in 0 until 8) {
            val angle = i * PI / 4 + rayTurn * PI / 180
            val inner = p(60f + 44f * cos(angle).toFloat(), 60f + 44f * sin(angle).toFloat())
            val outer = p(60f + 55f * cos(angle).toFloat(), 60f + 55f * sin(angle).toFloat())
            drawLine(ray, inner, outer, strokeWidth = 7f * unit, cap = StrokeCap.Round)
        }

        // Face
        drawCircle(colors.sun, radius = 38f * unit, center = p(60f, 60f))
        drawCircle(colors.sunOutline, radius = 38f * unit, center = p(60f, 60f), style = Stroke(width = 3f * unit))
        drawCircle(colors.cheek.copy(alpha = 0.55f), radius = 6f * unit, center = p(37f, 68f))
        drawCircle(colors.cheek.copy(alpha = 0.55f), radius = 6f * unit, center = p(83f, 68f))

        when (mood) {
            PipMood.SLEEPY -> {
                closedEye(face, p(47f, 56f), unit, smile = true)
                closedEye(face, p(73f, 56f), unit, smile = true)
                // Yawn
                drawOval(face, topLeft = p(53f, 67f), size = Size(14f * unit, 16f * unit))
            }
            PipMood.AWAKE -> {
                drawOval(face, topLeft = p(41.5f, 49.5f), size = Size(11f * unit, 13f * unit))
                drawOval(face, topLeft = p(67.5f, 49.5f), size = Size(11f * unit, 13f * unit))
                drawCircle(Color.White, radius = 2f * unit, center = p(49f, 53.5f))
                drawCircle(Color.White, radius = 2f * unit, center = p(75f, 53.5f))
                drawArc(
                    face, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                    topLeft = p(49f, 61f), size = Size(22f * unit, 14f * unit),
                    style = Stroke(width = 4f * unit, cap = StrokeCap.Round),
                )
            }
            PipMood.HAPPY -> {
                closedEye(face, p(47f, 57f), unit, smile = false)
                closedEye(face, p(73f, 57f), unit, smile = false)
                drawArc(
                    face, startAngle = 0f, sweepAngle = 180f, useCenter = true,
                    topLeft = p(46f, 61f), size = Size(28f * unit, 22f * unit),
                )
            }
        }
    }
}

/** A curved line: "smile" curves down like a sleeping eye, otherwise up like a laughing one. */
private fun DrawScope.closedEye(color: Color, center: Offset, unit: Float, smile: Boolean) {
    drawArc(
        color,
        startAngle = if (smile) 20f else 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - 6f * unit, center.y - 5f * unit),
        size = Size(12f * unit, 10f * unit),
        style = Stroke(width = 4f * unit, cap = StrokeCap.Round),
    )
}
