package dev.victorc.sunup.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Type scale from the mockups, in Nunito ([SunupTheme] passes it in as [fontFamily]; the
 * default here is only for previews and tests).
 */
@Immutable
data class SunupTypography(
    val fontFamily: FontFamily = FontFamily.Default,
    /** The big alarm time, e.g. "07:00". */
    val clock: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.03).em,
    ),
    /** The ringing screen's time, the biggest text in the app. */
    val clockHero: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 88.sp,
        lineHeight = 88.sp,
        letterSpacing = (-0.04).em,
    ),
    val display: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.02).em,
    ),
    val headline: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
    ),
    val title: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** Small uppercase labels such as "SPANISH · MORNING WORDS". */
    val label: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.06.em,
    ),
    /** Text on chunky buttons; callers upper-case it. */
    val button: TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.06.em,
    ),
)
