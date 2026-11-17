package dev.victorc.sunup.core.alarm

/**
 * Whether Sunup may set alarms on iOS (AlarmKit), and how to ask. Filled in by the app at
 * start-up (shared's `startSunup`), which holds the Swift side; read by onboarding's checklist.
 */
object IosAlarmAuthorization {
    var isAuthorized: () -> Boolean = { false }
    var request: (onResult: (Boolean) -> Unit) -> Unit = { it(false) }
}
