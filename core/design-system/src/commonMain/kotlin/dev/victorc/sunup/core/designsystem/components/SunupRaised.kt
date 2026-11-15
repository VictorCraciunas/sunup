package dev.victorc.sunup.core.designsystem.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * A face over a darker edge, the mockups' chunky 3D look (`box-shadow: 0 4px 0 <edge>`).
 * Tappable ones sink into their edge when pressed, like [SunupButton].
 *
 * The edge is part of the size: a 48 dp chip is a 44 dp face over a 4 dp edge, so it
 * still meets the touch target and needs no extra spacing.
 *
 * @param outline drawn around the face, e.g. for white cards on the cream background
 * @param onClick null: not tappable, no press effect
 */
@Composable
fun SunupRaised(
    face: Color,
    edge: Color,
    modifier: Modifier = Modifier,
    shape: Shape = SunupTheme.shapes.card,
    outline: Color? = null,
    depth: Dp = SunupTheme.sizes.pressDepth,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentAlignment: Alignment = Alignment.TopStart,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed && onClick != null) depth else 0.dp,
        animationSpec = tween(durationMillis = 90),
    )

    Box(
        modifier = modifier.then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick,
                )
            },
        ),
        // The face fills whatever size the caller gives the whole thing.
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .background(edge, shape),
        )
        Box(
            modifier = Modifier
                // Sinking moves the face down by the same amount the edge below it shrinks.
                .padding(top = sink, bottom = depth - sink)
                .clip(shape)
                .background(face)
                .then(if (outline != null) Modifier.border(SunupTheme.sizes.border, outline, shape) else Modifier)
                .padding(contentPadding),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}
