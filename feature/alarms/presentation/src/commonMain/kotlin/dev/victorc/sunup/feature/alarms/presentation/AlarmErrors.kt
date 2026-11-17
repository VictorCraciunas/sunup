package dev.victorc.sunup.feature.alarms.presentation

import dev.victorc.sunup.core.alarm.AlarmError

// TODO(M4): move user-facing text to string resources once localization is set up.
internal fun AlarmError.message(): String = when (this) {
    AlarmError.EXACT_ALARMS_NOT_ALLOWED ->
        "Sunup isn't allowed to set alarms. Allow alarms for Sunup in the phone's settings " +
            "(Android: \"Alarms & reminders\")."
    AlarmError.NOT_SCHEDULED -> "The phone didn't accept this alarm. Try again."
    AlarmError.NOT_SAVED -> "The alarm will ring, but its settings didn't save. Open it and save again."
}
