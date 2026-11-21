package dev.victorc.sunup.core.data

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
import platform.EventKit.EKAuthorizationStatusFullAccess
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

/**
 * The next timed event later today from the phone's calendars (EventKit, full access).
 * Read on the phone; nothing is sent anywhere.
 */
class IosCalendarSource(private val clock: Clock) : CalendarSource {

    override suspend fun nextEventToday(): CalendarEvent? = withContext(Dispatchers.Default) {
        if (EKEventStore.authorizationStatusForEntityType(EKEntityType.EKEntityTypeEvent) != EKAuthorizationStatusFullAccess) {
            return@withContext null
        }
        val zone = TimeZone.currentSystemDefault()
        val now = clock.now()
        val endOfDay = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
        val store = EKEventStore()
        val today = store.predicateForEventsWithStartDate(now.toNSDate(), endDate = endOfDay.toNSDate(), calendars = null)
        store.eventsMatchingPredicate(today)
            .filterIsInstance<EKEvent>()
            .filter { !it.allDay }
            .mapNotNull { event -> event.startDate?.toInstant()?.let { it to event } }
            .filter { (start, _) -> start >= now }
            .minByOrNull { (start, _) -> start }
            ?.let { (start, event) ->
                CalendarEvent(
                    title = event.title.orEmpty().ifBlank { "Busy" },
                    start = start.toLocalDateTime(zone).time,
                )
            }
    }
}

private fun Instant.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(toEpochMilliseconds() / 1000.0)

private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000).toLong())
