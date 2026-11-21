package dev.victorc.sunup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_PREFIX
import dev.victorc.sunup.feature.wakeup.presentation.WakeUpRoot
import dev.victorc.sunup.ios.IosMorning

/**
 * The iOS app's only screen. The app as usual, and the morning flow on top whenever Apple's
 * alarm screen ("Wake up with Pip") or Home's preview opens one. Call `startSunup` first.
 */
fun MainViewController() = ComposeUIViewController {
    val morning by IosMorning.open.collectAsState()
    Box(Modifier.fillMaxSize()) {
        App(onPreviewMorning = { alarmId -> IosMorning.show(PREVIEW_ALARM_PREFIX + alarmId) })
        morning?.let { open -> key(open) { Morning(open.alarmId) } }
    }
}

/** One morning, with its own ViewModel store so the next morning starts fresh (Android: a new activity). */
@Composable
private fun Morning(alarmId: String) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        SunupTheme {
            WakeUpRoot(
                alarmId = alarmId,
                // Only the wake-up check's re-ring uses this. The alarm's sound after "Wake up with
                // Pip" is Sunup's own (IosMorning), until onStopAlarm.
                ringing = false,
                onStopAlarm = { IosMorning.answered(alarmId) },
                onClose = { IosMorning.close() },
            )
        }
    }
}
