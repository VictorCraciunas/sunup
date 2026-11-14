package dev.victorc.sunup.core.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Fired by AlarmManager at the ring time. */
class AlarmReceiver : BroadcastReceiver(), KoinComponent {

    private val scheduler: AlarmScheduler by inject()
    private val store: AlarmStore by inject()
    private val scope: AlarmScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmIntents.EXTRA_ALARM_ID) ?: return
        val expectedAt = intent.getLongExtra(AlarmIntents.EXTRA_EXPECTED_AT, 0L)
        val soft = intent.getBooleanExtra(AlarmIntents.EXTRA_SOFT, false)
        val recheckUntil = intent.getLongExtra(AlarmIntents.EXTRA_RECHECK_UNTIL, 0L)

        if (recheckUntil > 0 && isPhoneInUseElsewhere(context)) {
            postpone(alarmId, Instant.fromEpochMilliseconds(recheckUntil))
            return
        }

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

    /**
     * Wake-up check while the user is busy in another app: they're awake right now, so don't
     * ring. Look again in a minute, until the check's window ends.
     */
    private fun postpone(alarmId: String, until: Instant) {
        val pending = goAsync()
        scope.launch {
            try {
                val now = Clock.System.now()
                val next = now + RECHECK_INTERVAL
                if (next <= until) {
                    store.record(RingEvent(alarmId, RingEvent.Type.SKIPPED, at = now, note = "phone in use, checking again"))
                    scheduler.scheduleFollowUp(alarmId, next, recheckWhileInUseUntil = until)
                } else {
                    store.record(RingEvent(alarmId, RingEvent.Type.SKIPPED, at = now, note = "phone in use all along: awake, check ended"))
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Screen on, unlocked, and not on Sunup's wake-up screen: the user is doing something else.
     * Screen off or locked means they may be asleep again, so the check rings.
     */
    private fun isPhoneInUseElsewhere(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            !WakeUpScreenState.isVisible

    private companion object {
        val RECHECK_INTERVAL = 1.minutes
    }
}
