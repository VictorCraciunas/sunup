package dev.victorc.sunup.feature.alarms.presentation

import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

// TODO(M4): move user-facing text to string resources once localization is set up.

/** Monday first, as on the editor's day chips. */
internal val WEEK: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

internal val WEEKDAYS: Set<DayOfWeek> = WEEK.take(5).toSet()
private val WEEKEND: Set<DayOfWeek> = WEEK.drop(5).toSet()

internal fun DayOfWeek.short(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

internal fun DayOfWeek.letter(): String = short().take(1)

internal fun DayOfWeek.fullName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** "Every day", "Mon–Fri", "Sat–Sun", "Mon, Wed, Fri", or "Once" for no repeat days. */
internal fun repeatText(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "Once"
    days.size == 7 -> "Every day"
    days == WEEKDAYS -> "Mon–Fri"
    days == WEEKEND -> "Sat–Sun"
    else -> {
        val ordered = WEEK.filter { it in days }
        val run = WEEK.indexOf(ordered.last()) - WEEK.indexOf(ordered.first()) + 1 == ordered.size
        if (run && ordered.size >= 3) "${ordered.first().short()}–${ordered.last().short()}" else ordered.joinToString(", ") { it.short() }
    }
}

internal fun timeText(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

/** "Rings in 8 h 19 min", "Rings in 45 min", "Rings in under a minute". */
internal fun ringsInText(left: Duration): String {
    val minutes = (left.inWholeSeconds + 59) / 60 // round up: 30 s left is "1 min", not "0 min"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        left.inWholeSeconds < 60 -> "Rings in under a minute"
        hours == 0L -> "Rings in $rest min"
        rest == 0L -> "Rings in $hours h"
        else -> "Rings in $hours h $rest min"
    }
}

internal fun WakeMode.title(): String = when (this) {
    WakeMode.GENTLE -> "Gentle"
    WakeMode.NORMAL -> "Normal"
    WakeMode.HEAVY_SLEEPER -> "Heavy sleeper"
}

/** The one-line rule for each mode, as in the mockups. */
internal fun WakeMode.hint(): String = when (this) {
    WakeMode.GENTLE -> "1 easy card stops it."
    WakeMode.NORMAL -> "1 card, a 60-second wake-up check, then a quiet bonus round if you want it."
    WakeMode.HEAVY_SLEEPER -> "3 cards to stop it, and it rings again if you go still."
}

internal const val ANY_DECK = "Any deck"
