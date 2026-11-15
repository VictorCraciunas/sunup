package dev.victorc.sunup.feature.alarms.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
actual fun rememberMorningPermissions(): MorningPermissions {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val prompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
        refresh++
    }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose {}
    }

    fun allowed(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun ask(permission: String, onResult: (Boolean) -> Unit) {
        if (allowed(permission)) {
            onResult(true)
        } else {
            pending = onResult
            prompt.launch(permission)
        }
    }

    val location = remember(refresh) { allowed(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val calendar = remember(refresh) { allowed(Manifest.permission.READ_CALENDAR) }
    return MorningPermissions(
        locationAllowed = location,
        calendarAllowed = calendar,
        askLocation = { ask(Manifest.permission.ACCESS_COARSE_LOCATION, it) },
        askCalendar = { ask(Manifest.permission.READ_CALENDAR, it) },
    )
}
