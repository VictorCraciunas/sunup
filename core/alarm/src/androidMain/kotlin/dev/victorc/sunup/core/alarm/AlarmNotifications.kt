package dev.victorc.sunup.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

internal object AlarmNotifications {

    const val NOTIFICATION_ID = 7_001
    private const val CHANNEL_ID = "alarm_ringing"

    // TODO(M4): move user-facing text to string resources once localization is set up.
    private const val CHANNEL_NAME = "Ringing alarms"
    private const val TITLE = "Good morning!"
    private const val TEXT = "Tap to wake up with Pip"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            // The service plays sound and vibration itself, on the alarm stream.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun ringing(context: Context, alarmId: String): Notification {
        val screen = ringingScreenIntent(context, alarmId)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ringingScreenIntent(context: Context, alarmId: String): PendingIntent {
        val intent = Intent(AlarmIntents.ACTION_SHOW_RINGING)
            .setPackage(context.packageName)
            .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
