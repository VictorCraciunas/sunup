package dev.victorc.sunup.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SunupSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    /** Side padding of full screens. */
    val screen: Dp = 20.dp,
)

@Immutable
data class SunupShapes(
    val button: RoundedCornerShape = RoundedCornerShape(16.dp),
    val card: RoundedCornerShape = RoundedCornerShape(22.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
)

@Immutable
data class SunupSizes(
    /** Height of the chunky buttons, not counting the 3D edge. */
    val buttonHeight: Dp = 54.dp,
    /** How far the 3D edge shows below a button or card, and how far it sinks when pressed. */
    val pressDepth: Dp = 4.dp,
    /** Smallest touch target (PRD: at least 44). */
    val minTouchTarget: Dp = 48.dp,
)
