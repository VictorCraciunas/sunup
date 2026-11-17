package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.runtime.Composable

// TODO(M5): weather and calendar on iOS (Core Location, EventKit).
@Composable
actual fun rememberMorningPermissions(): MorningPermissions = MorningPermissions(
    supported = false,
    locationAllowed = false,
    calendarAllowed = false,
    askLocation = { it(false) },
    askCalendar = { it(false) },
)
