package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.AlarmStore
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_ID
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_PREFIX
import dev.victorc.sunup.feature.wakeup.presentation.WAKE_CHECK_SUFFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A morning on screen. [run] changes whenever a morning starts over, so the same alarm ringing
 * again tomorrow gets a fresh flow (Android: a new activity).
 */
data class OpenMorning(val alarmId: String, val run: Int)

/**
 * Which morning the iOS app is showing over everything else, if any. Set by Apple's alarm
 * screen ("Wake up with Pip", through the App Intent in Swift) and by Home's preview.
 * Everything here is called on the main thread.
 */
object IosMorning {
    private val _open = MutableStateFlow<OpenMorning?>(null)
    val open: StateFlow<OpenMorning?> = _open.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal var clock: IosPlatformAlarmClock? = null
    internal var store: AlarmStore? = null
    internal var sound: MorningSoundBridge? = null

    private var runs = 0

    /** The open morning's alarm was already stopped (card answered, emergency stop). */
    private var alarmStopped = false

    /**
     * Shows the morning flow for [alarmId]. A stop follow-up plays its own alarm's morning: if
     * that morning is open and its card isn't answered yet, it carries on where it was.
     *
     * A real alarm also starts Sunup's own sound (Apple's went quiet when its button was
     * tapped); it rings until the card is answered.
     */
    fun show(alarmId: String) {
        val morning = alarmIdOfRing(alarmId)
        if (_open.value?.alarmId != morning || alarmStopped) {
            runs++
            alarmStopped = false
            _open.value = OpenMorning(morning, runs)
        }
        if (!isPreview(morning) && !morning.endsWith(WAKE_CHECK_SUFFIX)) sound?.start()
    }

    fun close() {
        sound?.stop()
        alarmStopped = false
        _open.value = null
    }

    /** The alarm was stopped in Sunup: quiet now, cancel this morning's stop follow-ups, set the next ones. */
    internal fun answered(alarmId: String) {
        sound?.stop()
        alarmStopped = true
        // Previews ring nothing, so there's nothing to cancel.
        if (isPreview(alarmId)) return
        val platform = clock ?: return
        scope.launch {
            val alarm = runCatching { store?.alarm(alarmId) }.getOrNull()
            platform.answered(alarmId, alarm)
        }
    }

    /**
     * Apple's Stop (or "slide to stop") was used on [ringId], the alarm or one of its follow-ups.
     * The card isn't answered, so the alarm comes back in 30 seconds, and every 30 seconds after.
     * Called by the stop intent in Swift, usually in the background; it returns once every new
     * ring has been handed to AlarmKit.
     */
    suspend fun stopped(ringId: String) = dismissed(ringId, stopped = true)

    /**
     * "Wake up with Pip" was tapped on [ringId]. The card isn't answered yet: if it still isn't
     * in 3 minutes, the alarm comes back. Called by the open intent in Swift.
     */
    suspend fun wakingUp(ringId: String) = dismissed(ringId, stopped = false)

    private suspend fun dismissed(ringId: String, stopped: Boolean) {
        val platform = clock ?: return
        // Only saved alarms come back. The test alarm and the wake-up check stop for good.
        val alarm = runCatching { store?.alarm(alarmIdOfRing(ringId)) }.getOrNull() ?: return
        runCatching { platform.dismissedWithoutCard(alarm.id, stopped) }
    }

    private fun isPreview(alarmId: String) = alarmId.startsWith(PREVIEW_ALARM_PREFIX) || alarmId == PREVIEW_ALARM_ID
}
