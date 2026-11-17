package dev.victorc.sunup.ios

import dev.victorc.sunup.core.alarm.AlarmStore
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_ID
import dev.victorc.sunup.feature.wakeup.presentation.PREVIEW_ALARM_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which morning the iOS app is showing over everything else, if any. Set by Apple's alarm
 * screen ("Wake up with Pip", through the App Intent in Swift) and by Home's preview.
 */
object IosMorning {
    private val _alarmId = MutableStateFlow<String?>(null)
    val alarmId: StateFlow<String?> = _alarmId.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal var clock: IosPlatformAlarmClock? = null
    internal var store: AlarmStore? = null

    /** Shows the morning flow for [alarmId]. A stop follow-up plays its own alarm's morning. */
    fun show(alarmId: String) {
        _alarmId.value = alarmId.removeSuffix(STOP_FOLLOW_UP_SUFFIX)
    }

    fun close() {
        _alarmId.value = null
    }

    /** The card stopped the alarm: cancel this morning's stop follow-up, set the next one. */
    internal fun answered(alarmId: String) {
        // Previews ring nothing, so there's nothing to cancel.
        if (alarmId.startsWith(PREVIEW_ALARM_PREFIX) || alarmId == PREVIEW_ALARM_ID) return
        val platform = clock ?: return
        scope.launch {
            val alarm = runCatching { store?.alarm(alarmId) }.getOrNull()
            platform.answered(alarmId, alarm)
        }
    }
}
