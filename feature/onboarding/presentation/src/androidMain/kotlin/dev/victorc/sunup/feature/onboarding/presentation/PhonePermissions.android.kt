package dev.victorc.sunup.feature.onboarding.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.victorc.sunup.core.alarm.AlarmPermissions

@Composable
actual fun rememberPhonePermissions(): PhonePermissions {
    val context = LocalContext.current
    val checks = remember(context) { AlarmPermissions(context.applicationContext) }
    val autostart = remember(checks) { checks.autostartSettings() }

    var refresh by remember { mutableIntStateOf(0) }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    var autostartOpened by rememberSaveable { mutableStateOf(false) }

    // Coming back from a Settings screen or a prompt: check again.
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val notificationPrompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }

    /** Tries each screen in turn: phone makers move them around. */
    fun open(vararg intents: Intent?): Boolean = intents.filterNotNull().any { intent ->
        try {
            settings.launch(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    val needed = buildList {
        add(PhonePermission.NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(PhonePermission.EXACT_ALARMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(PhonePermission.LOCK_SCREEN)
        add(PhonePermission.BATTERY)
        if (autostart != null) add(PhonePermission.AUTOSTART)
    }
    val granted = remember(refresh, autostartOpened) {
        buildSet {
            if (checks.notificationsAllowed()) add(PhonePermission.NOTIFICATIONS)
            if (checks.exactAlarmsAllowed()) add(PhonePermission.EXACT_ALARMS)
            if (checks.fullScreenAllowed()) add(PhonePermission.LOCK_SCREEN)
            if (checks.batteryUnrestricted()) add(PhonePermission.BATTERY)
            if (autostartOpened) add(PhonePermission.AUTOSTART)
        }
    }

    return PhonePermissions(
        needed = needed,
        granted = granted,
        autostartHint = autostart?.hint.orEmpty(),
        request = { permission ->
            when (permission) {
                PhonePermission.NOTIFICATIONS ->
                    // The system prompt shows at most twice; after that, only Settings can turn them on.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAsked) {
                        notificationsAsked = true
                        notificationPrompt.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        open(checks.notificationSettings())
                    }
                PhonePermission.EXACT_ALARMS -> open(checks.exactAlarmSettings())
                PhonePermission.LOCK_SCREEN -> open(checks.fullScreenSettings())
                PhonePermission.BATTERY -> open(checks.batteryRequest(), checks.batterySettings())
                PhonePermission.AUTOSTART ->
                    if (autostart != null && open(*autostart.screens.toTypedArray())) autostartOpened = true
            }
        },
    )
}
