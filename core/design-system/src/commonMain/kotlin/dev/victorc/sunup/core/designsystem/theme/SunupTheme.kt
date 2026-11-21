package dev.victorc.sunup.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.victorc.sunup.core.designsystem.resources.Res
import dev.victorc.sunup.core.designsystem.resources.nunito
import org.jetbrains.compose.resources.Font

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
    val nunito = nunito()
    val typography = SunupTypography(fontFamily = nunito)

    CompositionLocalProvider(
        LocalSunupColors provides colors,
        LocalSunupTypography provides typography,
        LocalSunupSpacing provides SunupSpacing(),
        LocalSunupShapes provides SunupShapes(),
        LocalSunupSizes provides SunupSizes(),
    ) {
        MaterialTheme(
            // Indigo, not sun, drives Material components: yellow text buttons, cursors and
            // focus lines on white fail contrast. Sun stays on SunupButton, with dark text on it.
            colorScheme = lightColorScheme(
                primary = colors.indigo,
                onPrimary = colors.onIndigo,
                primaryContainer = colors.indigoContainer,
                onPrimaryContainer = colors.indigo,
                secondary = colors.sun,
                onSecondary = colors.onSun,
                background = colors.background,
                onBackground = colors.ink,
                surface = colors.surface,
                onSurface = colors.ink,
                onSurfaceVariant = colors.inkMuted,
                outline = colors.border,
                error = colors.wrong,
            ),
            // Material's own text (text field placeholders, dialogs) in Nunito too.
            typography = Typography().withFont(nunito),
            content = content,
        )
    }
}

/**
 * The mockups' font. The file is Google Fonts' variable Nunito: one file, every weight;
 * each weight below picks its point on the file's weight axis. While the file loads (a moment
 * on iOS), text shows in the phone's own font.
 */
@Composable
private fun nunito(): FontFamily = FontFamily(
    Font(Res.font.nunito, FontWeight.SemiBold),
    Font(Res.font.nunito, FontWeight.Bold),
    Font(Res.font.nunito, FontWeight.ExtraBold),
    Font(Res.font.nunito, FontWeight.Black),
)

private fun Typography.withFont(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

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
