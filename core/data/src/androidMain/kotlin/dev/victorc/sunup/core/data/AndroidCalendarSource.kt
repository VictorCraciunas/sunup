package dev.victorc.sunup.core.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import dev.victorc.sunup.core.domain.morning.CalendarEvent
import dev.victorc.sunup.core.domain.morning.CalendarSource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** The next timed event later today from the phone's calendars. Read on the phone; nothing is sent anywhere. */
class AndroidCalendarSource(
    private val context: Context,
    private val clock: Clock,
) : CalendarSource {

    override suspend fun nextEventToday(): CalendarEvent? = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        val endOfDay = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now.toEpochMilliseconds())
            ContentUris.appendId(it, endOfDay.toEpochMilliseconds())
        }.build()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= ?",
                arrayOf(now.toEpochMilliseconds().toString()),
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                CalendarEvent(
                    title = cursor.getString(0).orEmpty().ifBlank { "Busy" },
                    start = Instant.fromEpochMilliseconds(cursor.getLong(1)).toLocalDateTime(zone).time,
                )
            }
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
