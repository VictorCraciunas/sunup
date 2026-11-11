package dev.victorc.sunup.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import kotlin.time.Instant

/**
 * Uses `setAlarmClock()`: the system never delays these, leaves Doze to deliver them,
 * and shows the alarm icon in the status bar.
 */
internal class AndroidPlatformAlarmClock(private val context: Context) : PlatformAlarmClock {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun set(alarmId: String, at: Instant, soft: Boolean): EmptyResult<ScheduleError> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED)
        }
        return try {
            val info = AlarmManager.AlarmClockInfo(at.toEpochMilliseconds(), openAppIntent())
            alarmManager.setAlarmClock(info, ringIntent(alarmId, at, soft))
            Result.Success(Unit)
        } catch (e: SecurityException) {
            Result.Error(ScheduleError.EXACT_ALARMS_NOT_ALLOWED)
        }
    }

    override fun cancel(alarmId: String) {
        alarmManager.cancel(ringIntent(alarmId, at = null, soft = false))
    }

    /** One PendingIntent per alarm: the data URI makes them distinct, extras don't. */
    private fun ringIntent(alarmId: String, at: Instant?, soft: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setData(Uri.parse("sunup://alarm/${Uri.encode(alarmId)}"))
            .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmIntents.EXTRA_SOFT, soft)
        if (at != null) intent.putExtra(AlarmIntents.EXTRA_EXPECTED_AT, at.toEpochMilliseconds())
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Where tapping the status-bar alarm icon's info takes the user. */
    private fun openAppIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
