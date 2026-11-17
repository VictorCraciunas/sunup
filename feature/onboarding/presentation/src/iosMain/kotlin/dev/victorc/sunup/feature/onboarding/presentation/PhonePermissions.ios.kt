package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.victorc.sunup.core.alarm.IosAlarmAuthorization

/** iOS needs one thing: AlarmKit's permission. Notifications, battery and autostart are Android's. */
@Composable
actual fun rememberPhonePermissions(): PhonePermissions {
    var refresh by remember { mutableIntStateOf(0) }
    // Coming back from Settings: check again.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }
    val allowed = remember(refresh) { IosAlarmAuthorization.isAuthorized() }
    return PhonePermissions(
        needed = listOf(PhonePermission.ALARMS),
        granted = if (allowed) setOf(PhonePermission.ALARMS) else emptySet(),
        autostartHint = "",
        request = { IosAlarmAuthorization.request { refresh++ } },
    )
}
