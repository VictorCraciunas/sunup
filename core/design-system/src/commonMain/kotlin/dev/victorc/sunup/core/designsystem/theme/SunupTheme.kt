package dev.victorc.sunup.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalSunupColors = staticCompositionLocalOf { SunupColors() }
private val LocalSunupTypography = staticCompositionLocalOf { SunupTypography() }
private val LocalSunupSpacing = staticCompositionLocalOf { SunupSpacing() }
private val LocalSunupShapes = staticCompositionLocalOf { SunupShapes() }
private val LocalSunupSizes = staticCompositionLocalOf { SunupSizes() }

/**
 * Wraps the app in Sunup's tokens. Material components (text fields, switches) are
 * themed from the same colors so they don't look out of place.
 */
@Composable
fun SunupTheme(content: @Composable () -> Unit) {
    val colors = SunupColors()
    val typography = SunupTypography()

    CompositionLocalProvider(
        LocalSunupColors provides colors,
        LocalSunupTypography provides typography,
        LocalSunupSpacing provides SunupSpacing(),
        LocalSunupShapes provides SunupShapes(),
        LocalSunupSizes provides SunupSizes(),
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = colors.sun,
                onPrimary = colors.onSun,
                secondary = colors.indigo,
                onSecondary = colors.onIndigo,
                background = colors.background,
                onBackground = colors.ink,
                surface = colors.surface,
                onSurface = colors.ink,
                onSurfaceVariant = colors.inkMuted,
                outline = colors.border,
                error = colors.wrong,
            ),
            content = content,
        )
    }
}

object SunupTheme {
    val colors: SunupColors
        @Composable @ReadOnlyComposable get() = LocalSunupColors.current
    val typography: SunupTypography
        @Composable @ReadOnlyComposable get() = LocalSunupTypography.current
    val spacing: SunupSpacing
        @Composable @ReadOnlyComposable get() = LocalSunupSpacing.current
    val shapes: SunupShapes
        @Composable @ReadOnlyComposable get() = LocalSunupShapes.current
    val sizes: SunupSizes
        @Composable @ReadOnlyComposable get() = LocalSunupSizes.current
}
