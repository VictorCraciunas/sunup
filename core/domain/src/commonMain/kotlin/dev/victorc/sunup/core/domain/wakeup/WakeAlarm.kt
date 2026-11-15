package dev.victorc.sunup.core.domain.wakeup

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * An alarm as the user sets it up in the app. The alarm engine (`:core:alarm`) only gets
 * the time and days; everything else stays here and never blocks the alarm from ringing.
 */
data class WakeAlarm(
    val id: String,
    val time: LocalTime,
    /** Empty means "ring once". */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    /** The deck the cards come from. Null: built-in fallback (e.g. Quick math). */
    val deckId: String? = null,
    val mode: WakeMode = WakeMode.NORMAL,
    /** Soft chime 5 minutes early with the card already open. */
    val preAlarm: Boolean = false,
)

/**
 * The "Try a test alarm" ring from onboarding. Not a saved alarm: the morning flow plays it
 * as Gentle (one card, no wake-up check) with the first deck, or Quick math.
 */
const val TEST_ALARM_ID = "test-alarm"

enum class WakeMode {
    /** 1 card stops the alarm. */
    GENTLE,

    /** 1 card, a 60-second wake-up check, then an optional quiet bonus round. */
    NORMAL,

    /** 3 cards, and it rings again if the phone goes idle. */
    HEAVY_SLEEPER,
}
