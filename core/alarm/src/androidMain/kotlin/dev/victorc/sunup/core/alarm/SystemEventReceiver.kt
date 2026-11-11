package dev.victorc.sunup.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Registers every alarm again when the OS forgets them (reboot) or their times shift
 * (clock or time zone change). Only reschedules: starting a foreground service from
 * BOOT_COMPLETED is restricted on Android 15.
 */
class SystemEventReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: AlarmScheduler by inject()
    private val scope: AlarmScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rescheduleAll(reason = action.substringAfterLast('.'))
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
