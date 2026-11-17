package dev.victorc.sunup.ios

/**
 * Apple's AlarmKit (iOS 26+), which only Swift can call. Implemented in
 * `iosApp/iosApp/AlarmKitBridgeImpl.swift` and handed to [startSunup] when the app starts.
 * Kept to plain types so it reads naturally in Swift.
 */
interface AlarmKitBridge {
    /** True once the user allowed Sunup to set alarms. */
    fun isAuthorized(): Boolean

    /** Shows Apple's "Allow alarms?" prompt if it hasn't been answered; [onResult] gets the answer. */
    fun requestAuthorization(onResult: (Boolean) -> Unit)

    /** One ring at [epochMillis]. [soft]: a gentle follow-up (AlarmKit may play it at the normal volume). */
    fun scheduleOnce(alarmId: String, epochMillis: Long, soft: Boolean)

    /** Rings at [hour]:[minute] every week on [weekdays] (1 = Monday … 7 = Sunday). AlarmKit repeats it. */
    fun scheduleWeekly(alarmId: String, hour: Int, minute: Int, weekdays: List<Int>)

    fun cancel(alarmId: String)

    /** When a one-time alarm with this id will ring, in epoch millis; -1 when there's none. */
    fun scheduledAt(alarmId: String): Long
}
