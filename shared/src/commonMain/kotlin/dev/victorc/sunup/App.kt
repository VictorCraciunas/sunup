package dev.victorc.sunup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.components.SunupButtonStyle
import dev.victorc.sunup.core.designsystem.theme.SunupTheme

/**
 * Root of the app. A placeholder screen until navigation and the first features land.
 *
 * @param extraContent shown below the placeholder; Android uses it for the alarm test bench.
 */
@Composable
@Preview
fun App(extraContent: @Composable () -> Unit = {}) {
    SunupTheme {
        var taps by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SunupTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .safeContentPadding()
                .padding(SunupTheme.spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.md),
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = "sunup",
                style = SunupTheme.typography.display,
                color = SunupTheme.colors.ink,
            )
            Text(
                text = "Wake up for you. Learn a little something every single morning.",
                style = SunupTheme.typography.body,
                color = SunupTheme.colors.inkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            SunupButton(
                text = if (taps == 0) "Get started" else "Pressed $taps×",
                onClick = { taps++ },
            )
            SunupButton(
                text = "I already have an account",
                onClick = {},
                style = SunupButtonStyle.Secondary,
            )
            extraContent()
        }
    }
}
