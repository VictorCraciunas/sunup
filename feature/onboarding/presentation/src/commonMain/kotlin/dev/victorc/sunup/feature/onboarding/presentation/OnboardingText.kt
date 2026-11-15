package dev.victorc.sunup.feature.onboarding.presentation

import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlinx.datetime.DayOfWeek

// TODO(M4): move user-facing text to string resources once localization is set up.

/** Monday first, as on the alarm editor's day chips. */
internal val WEEK: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

internal val WEEKDAYS: Set<DayOfWeek> = WEEK.take(5).toSet()

internal fun DayOfWeek.letter(): String = name.take(1)

internal fun DayOfWeek.fullName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

internal fun daysText(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "Rings once, then switches itself off."
    days.size == 7 -> "Every day"
    days == WEEKDAYS -> "Monday to Friday"
    days == WEEK.drop(5).toSet() -> "Weekends"
    else -> WEEK.filter { it in days }.joinToString(", ") { it.fullName().take(3) }
}

internal fun LearnGoal.title(): String = when (this) {
    LearnGoal.GEOGRAPHY -> "World geography"
    LearnGoal.OWN_CARDS -> "My own cards"
    LearnGoal.MATH -> "Quick mental math"
}

internal fun LearnGoal.subtitle(): String = when (this) {
    LearnGoal.GEOGRAPHY -> "Countries on the map"
    LearnGoal.OWN_CARDS -> "Make them, or let your AI make them"
    LearnGoal.MATH -> "Keep your brain sharp"
}

internal fun WakeMode.title(): String = when (this) {
    WakeMode.GENTLE -> "Gentle"
    WakeMode.NORMAL -> "Normal"
    WakeMode.HEAVY_SLEEPER -> "Heavy sleeper"
}

internal fun WakeMode.hint(): String = when (this) {
    WakeMode.GENTLE -> "1 easy card stops it."
    WakeMode.NORMAL -> "1 card, then a 60-second check that you're really up."
    WakeMode.HEAVY_SLEEPER -> "3 cards to stop it, and it rings again if you go still."
}

/** "Your morning" on the mode screen, step by step. */
internal fun WakeMode.steps(): List<String> = when (this) {
    WakeMode.GENTLE -> listOf("The alarm rings", "Answer 1 card", "It stops. Good morning!")
    WakeMode.NORMAL -> listOf(
        "The alarm rings",
        "Answer 1 card to stop it",
        "60-second wake-up check",
        "2 bonus cards, if you want them",
    )
    WakeMode.HEAVY_SLEEPER -> listOf(
        "The alarm rings",
        "Answer 3 cards to stop it",
        "60-second wake-up check",
        "Rings softly again if you go still",
    )
}
