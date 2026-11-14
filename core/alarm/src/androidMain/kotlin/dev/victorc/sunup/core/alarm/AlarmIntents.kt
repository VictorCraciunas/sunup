package dev.victorc.sunup.core.alarm

object AlarmIntents {
    const val EXTRA_ALARM_ID = "dev.victorc.sunup.extra.ALARM_ID"
    const val EXTRA_EXPECTED_AT = "dev.victorc.sunup.extra.EXPECTED_AT"
    const val EXTRA_SOFT = "dev.victorc.sunup.extra.SOFT"
    const val EXTRA_RECHECK_UNTIL = "dev.victorc.sunup.extra.RECHECK_UNTIL"

    /** The app declares an activity for this action: the screen shown while an alarm rings. */
    const val ACTION_SHOW_RINGING = "dev.victorc.sunup.action.SHOW_RINGING"
}
