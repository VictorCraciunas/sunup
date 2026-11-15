package dev.victorc.sunup.feature.onboarding.presentation

import androidx.compose.runtime.Composable

// TODO(M5): AlarmKit asks for its own permission; add it here with the iOS alarm engine.
@Composable
actual fun rememberPhonePermissions(): PhonePermissions =
    PhonePermissions(needed = emptyList(), granted = emptySet(), autostartHint = "", request = {})
