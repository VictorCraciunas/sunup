package dev.victorc.sunup.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.victorc.sunup.core.alarm.AlarmRingingService
import dev.victorc.sunup.core.alarm.RingingState
import dev.victorc.sunup.core.designsystem.components.SunupButton
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The screen shown while an alarm rings. For now it only has a Stop button;
 * in M3 the silencing card takes its place.
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        setContent {
            val ringing by RingingState.current.collectAsState()
            // Opened from an old notification, or stopped elsewhere: nothing to show.
            LaunchedEffect(ringing) {
                if (ringing == null) finish()
            }
            // No escaping with Back while it rings.
            BackHandler {}

            SunupTheme {
                RingingScreen(onStop = { AlarmRingingService.stop(this) })
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun RingingScreen(onStop: () -> Unit) {
    val now = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SunupTheme.colors.nightBackground)
            .safeContentPadding()
            .padding(SunupTheme.spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SunupTheme.spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = now,
            style = SunupTheme.typography.clock,
            color = SunupTheme.colors.onNight,
        )
        Text(
            text = "Good morning!",
            style = SunupTheme.typography.headline,
            color = SunupTheme.colors.onNight,
        )
        Text(
            text = "Your wake-up card goes here soon.",
            style = SunupTheme.typography.body,
            color = SunupTheme.colors.onNightMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        SunupButton(text = "Stop", onClick = onStop)
    }
}
