package dev.victorc.sunup.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors taken from the mockups (Wake & Learn Alarm canvas).
 *
 * The morning flow moves from [nightBackground] through [firstLight] and [dawn]
 * to the daytime [background], so the screen brightens as the user wakes up.
 */
@Immutable
data class SunupColors(
    // Pip and primary actions
    val sun: Color = Color(0xFFFFC23D),
    val sunShadow: Color = Color(0xFFD99A12),
    val sunOutline: Color = Color(0xFFE89B0C),
    val onSun: Color = Color(0xFF3A2400),
    val cheek: Color = Color(0xFFFF8A6B),

    // Secondary brand color
    val indigo: Color = Color(0xFF3B3F9E),
    val indigoShadow: Color = Color(0xFF252870),
    val indigoContainer: Color = Color(0xFFEEEFFF),
    val onIndigo: Color = Color(0xFFFFFFFF),

    // Daytime screens
    val background: Color = Color(0xFFFFFBF3),
    val surface: Color = Color(0xFFFFFFFF),
    val border: Color = Color(0xFFE6DCCB),
    val track: Color = Color(0xFFEFE6D6),
    val ink: Color = Color(0xFF2A2340),
    val inkMuted: Color = Color(0xFF6B6680),

    // Night screens (alarm ringing, tuck-in)
    val nightBackground: Color = Color(0xFF1F2156),
    val nightSurface: Color = Color(0xFF2C2F78),
    val nightBorder: Color = Color(0xFF4045A0),
    val nightShadow: Color = Color(0xFF15173F),
    val onNight: Color = Color(0xFFFFFFFF),
    val onNightMuted: Color = Color(0xFFB9BCEB),

    // Sunrise steps between night and day
    val firstLight: Color = Color(0xFF3B3478),
    val dawn: Color = Color(0xFFFFE6CC),
    val daySky: Color = Color(0xFFDDEFFF),

    // Geography map: calm sea, warm sand land, white borders. Found countries use [correct].
    val mapSea: Color = Color(0xFFCFE7F8),
    val mapLand: Color = Color(0xFFF6E2B8),
    /** Land outside the region being played: drawn for context, can't be asked. */
    val mapLandMuted: Color = Color(0xFFE7E0D2),
    val mapBorder: Color = Color(0xFFFFFFFF),

    // Answer feedback. Never shame: "wrong" is a warm orange, not red.
    val correct: Color = Color(0xFF2F9E44),
    val correctShadow: Color = Color(0xFF1E6E2E),
    val correctContainer: Color = Color(0xFFD8F5DF),
    val onCorrectContainer: Color = Color(0xFF1B6B2E),
    val wrong: Color = Color(0xFFC2410C),
    val wrongShadow: Color = Color(0xFF8A2E08),
    val wrongContainer: Color = Color(0xFFFFE4D6),
    val onWrongContainer: Color = Color(0xFF8A2E08),
)
