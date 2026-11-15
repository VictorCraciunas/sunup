package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.runtime.Composable

/** Something the phone has to allow so the alarm rings on time and shows its card. */
enum class PhonePermission {
    NOTIFICATIONS,
    EXACT_ALARMS,

    /** Android 14+: the card opens over the lock screen instead of only a notification. */
    LOCK_SCREEN,
    BATTERY,

    /** Xiaomi, Samsung and similar phones only. The app can't check it, so it counts once opened. */
    AUTOSTART,
}

/**
 * What this phone needs, what it already allows, and how to ask for the rest.
 *
 * @param needed only the ones this phone has, in the order to show them
 * @param autostartHint where to look on the Autostart screen, for this phone's maker
 */
class PhonePermissions(
    val needed: List<PhonePermission>,
    val granted: Set<PhonePermission>,
    val autostartHint: String,
    val request: (PhonePermission) -> Unit,
)

/** Checks again whenever the user comes back from a prompt or a Settings screen. */
@Composable
expect fun rememberPhonePermissions(): PhonePermissions
