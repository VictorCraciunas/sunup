package dev.victorc.sunup.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Fired by AlarmManager at the ring time. */
class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: AlarmScheduler by inject()
    private val scope: AlarmScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmIntents.EXTRA_ALARM_ID) ?: return
        val expectedAt = intent.getLongExtra(AlarmIntents.EXTRA_EXPECTED_AT, 0L)
        val soft = intent.getBooleanExtra(AlarmIntents.EXTRA_SOFT, false)

        // Sound first. Nothing below is allowed to delay it.
        AlarmRingingService.start(context, alarmId, expectedAt, soft)

        val pending = goAsync()
        scope.launch {
            try {
                scheduler.onAlarmRang(alarmId)
            } finally {
                pending.finish()
            }
        }
    }
}
