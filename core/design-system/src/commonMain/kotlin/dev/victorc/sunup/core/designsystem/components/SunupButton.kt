package dev.victorc.sunup.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import androidx.compose.ui.tooling.preview.Preview

/** Danger: for the one button that deletes something, after asking (e.g. Delete deck). */
enum class SunupButtonStyle { Primary, Secondary, Danger }

/**
 * The chunky 3D button from the mockups: a colored face over a darker edge.
 * Pressing sinks the face into the edge, which is the button's only press feedback.
 */
@Composable
fun SunupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SunupButtonStyle = SunupButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = SunupTheme.colors
    val shape = SunupTheme.shapes.button
    val depth = SunupTheme.sizes.pressDepth

    val face: Color
    val edge: Color
    val content: Color
    val outline: Color?
    when (style) {
        SunupButtonStyle.Primary -> {
            face = colors.sun
            edge = colors.sunShadow
            content = colors.onSun
            outline = null
        }
        SunupButtonStyle.Secondary -> {
            face = colors.surface
            edge = colors.border
            content = colors.indigo
            outline = colors.border
        }
        SunupButtonStyle.Danger -> {
            face = colors.wrong
            edge = colors.wrongShadow
            content = colors.onWrong
            outline = null
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed && enabled) depth else 0.dp,
        animationSpec = tween(durationMillis = 90),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SunupTheme.sizes.buttonHeight + depth)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .background(edge, shape),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SunupTheme.sizes.buttonHeight)
                .offset { IntOffset(0, sink.roundToPx()) }
                .background(face, shape)
                .then(if (outline != null) Modifier.border(2.dp, outline, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text.uppercase(),
                style = SunupTheme.typography.button,
                color = content,
            )
        }
    }
}

@Preview
@Composable
private fun SunupButtonPreview() {
    SunupTheme {
        Box(Modifier.background(SunupTheme.colors.background).padding(20.dp)) {
            SunupButton(text = "Get started", onClick = {})
        }
    }
}
