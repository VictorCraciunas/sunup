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
    /** Outline of cards, chips and inputs. */
    val border: Dp = 2.dp,
    /** Soft drop shadow under a button that floats over the screen (Home's New alarm). */
    val floatShadow: Dp = 12.dp,
    /** Pip next to a line of text, e.g. the "next alarm" banner. */
    val pipBadge: Dp = 44.dp,
    /** Pip as the main picture of a screen, e.g. an empty list. */
    val pipHero: Dp = 140.dp,
    /** Geography map: circle drawn around countries too small to tap. */
    val mapSmallCountry: Dp = 9.dp,
    /** Geography map: a tap this close to a small country's circle counts for it. */
    val mapTapRadius: Dp = 18.dp,
    val mapBorder: Dp = 0.8.dp,
)
