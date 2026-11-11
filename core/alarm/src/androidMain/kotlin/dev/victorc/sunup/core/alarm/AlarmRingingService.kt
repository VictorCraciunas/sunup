package dev.victorc.sunup.core.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the alarm ringing until it's stopped: sound, vibration, and a full-screen
 * notification that opens the ringing screen, even over the lock screen.
 */
class AlarmRingingService : Service(), KoinComponent {

    private val store: AlarmStore by inject()
    private val scope: AlarmScope by inject()

    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRinging(timedOut = true) }

    private var sound: AlarmSound? = null
    private var vibration: AlarmVibration? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging(timedOut = false)
            return START_NOT_STICKY
        }

        val alarmId = intent?.getStringExtra(AlarmIntents.EXTRA_ALARM_ID)
        if (intent == null || alarmId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val expectedAt = intent.getLongExtra(AlarmIntents.EXTRA_EXPECTED_AT, 0L)
        val soft = intent.getBooleanExtra(AlarmIntents.EXTRA_SOFT, false)
        startRinging(alarmId, expectedAt, soft, redelivered = (flags and START_FLAG_REDELIVERY) != 0)

        // If the system kills us mid-ring, start again and keep ringing.
        return START_REDELIVER_INTENT
    }

    private fun startRinging(alarmId: String, expectedAt: Long, soft: Boolean, redelivered: Boolean) {
        val now = Clock.System.now()
        // Before the notification goes up, so the ringing screen sees it straight away.
        RingingState.set(Ringing(alarmId, now))

        AlarmNotifications.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.NOTIFICATION_ID,
            AlarmNotifications.ringing(this, alarmId),
            foregroundServiceType(),
        )
        acquireWakeLock()

        sound?.stop()
        sound = AlarmSound(this, maxVolume = if (soft) SOFT_MAX_VOLUME else 1f).also { it.start() }
        vibration?.stop()
        vibration = AlarmVibration(this).also { it.start() }

        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, MAX_RING_MILLIS)

        val note = (listOfNotNull("soft".takeIf { soft }) + ringProblems(redelivered)).joinToString(", ")
        scope.launch {
            store.record(
                RingEvent(
                    alarmId = alarmId,
                    type = RingEvent.Type.RANG,
                    at = now,
                    expected = expectedAt.takeIf { it > 0 }?.let { Instant.fromEpochMilliseconds(it) },
                    note = note,
                ),
            )
        }
    }

    /** Anything that stops the ringing screen from showing, written to the ring log. */
    private fun ringProblems(redelivered: Boolean): List<String> {
        val permissions = AlarmPermissions(this)
        return buildList {
            if (redelivered) add("restarted by system")
            if (!permissions.notificationsAllowed()) add("notifications off: rang without a screen")
            if (!permissions.fullScreenAllowed()) add("full-screen off: notification only")
        }
    }

    private fun stopRinging(timedOut: Boolean) {
        handler.removeCallbacks(timeout)
        sound?.stop()
        sound = null
        vibration?.stop()
        vibration = null

        val ringing = RingingState.current.value
        RingingState.set(null)
        if (ringing != null) {
            val type = if (timedOut) RingEvent.Type.TIMED_OUT else RingEvent.Type.STOPPED
            scope.launch { store.record(RingEvent(ringing.alarmId, type, at = Clock.System.now())) }
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        sound?.stop()
        vibration?.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sunup:ringing").apply {
            acquire(MAX_RING_MILLIS + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }

    companion object {
        private const val ACTION_STOP = "dev.victorc.sunup.action.STOP_RINGING"

        /** Safety net: stop on our own after this long, and log it as TIMED_OUT. */
        private const val MAX_RING_MILLIS = 15 * 60 * 1000L

        /** A soft ring (the wake-up check) never gets louder than this. */
        private const val SOFT_MAX_VOLUME = 0.45f

        fun start(context: Context, alarmId: String, expectedAt: Long, soft: Boolean = false) {
            val intent = Intent(context, AlarmRingingService::class.java)
                .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmIntents.EXTRA_EXPECTED_AT, expectedAt)
                .putExtra(AlarmIntents.EXTRA_SOFT, soft)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Call from a visible screen, e.g. the ringing screen once the card is answered. */
        fun stop(context: Context) {
            if (RingingState.current.value == null) return
            context.startService(Intent(context, AlarmRingingService::class.java).setAction(ACTION_STOP))
        }
    }
}
