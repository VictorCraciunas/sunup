package dev.victorc.sunup.feature.alarms.presentation

import androidx.compose.runtime.Composable

/**
 * What Home's "On You're up!" switches need from the phone: approximate location (weather)
 * and the calendar. Asked only when the user turns a switch on.
 */
class MorningPermissions(
    val locationAllowed: Boolean,
    val calendarAllowed: Boolean,
    /** Shows the system prompt if needed; [onResult] gets whether it's allowed now. */
    val askLocation: (onResult: (Boolean) -> Unit) -> Unit,
    val askCalendar: (onResult: (Boolean) -> Unit) -> Unit,
)

/** Checks again whenever the user comes back to the app (e.g. from Settings). */
@Composable
expect fun rememberMorningPermissions(): MorningPermissions
