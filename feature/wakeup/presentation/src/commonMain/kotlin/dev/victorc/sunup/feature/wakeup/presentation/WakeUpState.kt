package dev.victorc.sunup.feature.wakeup.presentation

import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.geo.GeoAtlas

/** Opening the morning flow with this id plays every game once, without an alarm, and saves nothing. */
const val PREVIEW_ALARM_ID = "preview"

data class WakeUpState(
    val phase: WakePhase = WakePhase.LOADING,
    val card: CardUi? = null,
    /** 0-based position of [card] in the current phase. */
    val position: Int = 0,
    val total: Int = 0,
    val alarmStopped: Boolean = false,
    /** The alarm was stopped without answering a card. */
    val stoppedWithoutCard: Boolean = false,
    /** Words answered this morning, for the "You're up!" screen. */
    val learned: List<LearnedItem> = emptyList(),
    /** Seconds of stillness left before the soft re-ring; null when no wake-up check is running. */
    val wakeCheckSecondsLeft: Int? = null,
    /** The wake-up check fired: the alarm is ringing softly again. */
    val ringingAgain: Boolean = false,
    /** Country outlines, loaded only when this morning has a geography card. */
    val atlas: GeoAtlas? = null,
)

enum class WakePhase { LOADING, SILENCING, BONUS_OFFER, BONUS, DONE }

/** One card on screen. Each game keeps its own progress. */
sealed interface CardUi {
    val label: String
    val solved: Boolean

    /** Needed another try, or gave up: the feedback is gentler and the card comes back soon. */
    val struggled: Boolean

    /** Shown once solved, e.g. the right meaning after a false statement. */
    val explanation: String?

    /** Pick the meaning, True or false, Fill the gap, Quick math: tap one option. */
    data class Choice(
        val kind: ChoiceKind,
        override val label: String,
        /** The word or question; for Fill the gap, the sentence with the gap. */
        val prompt: String,
        /** True or false: the answer being claimed. Fill the gap: the sentence's translation. */
        val statement: String? = null,
        val options: List<String>,
        val correctIndex: Int,
        override val explanation: String? = null,
        /** What goes on the "You're up!" list. Null for Quick math. */
        val learned: LearnedItem? = null,
        val wrongPicks: Set<Int> = emptySet(),
        override val solved: Boolean = false,
    ) : CardUi {
        override val struggled: Boolean get() = wrongPicks.isNotEmpty()
    }

    /** Type the answer. */
    data class Typing(
        override val label: String,
        val prompt: String,
        val answers: List<String>,
        val language: String?,
        val text: String = "",
        val wrongAttempts: Int = 0,
        val result: TypingResult? = null,
    ) : CardUi {
        override val solved: Boolean get() = result != null
        override val struggled: Boolean get() = wrongAttempts > 0 || result == TypingResult.REVEALED
        override val explanation: String?
            get() = when (result) {
                TypingResult.CLOSE_ACCENTS -> "Almost! Watch the accents: “${answers.first()}”."
                TypingResult.CLOSE_TYPO -> "Almost! It's spelled “${answers.first()}”."
                TypingResult.REVEALED -> "It's “${answers.first()}”."
                else -> null
            }
    }

    /** Find on the map: tap [countryName] on the map of [region]. */
    data class FindOnMap(
        override val label: String,
        val countryId: String,
        val countryName: String,
        val region: GeoRegion,
        val wrongAttempts: Int = 0,
        /** The last wrong tap, shown in orange with its name until the next tap. */
        val lastWrongId: String? = null,
        val lastWrongName: String? = null,
        override val solved: Boolean = false,
    ) : CardUi {
        override val struggled: Boolean get() = wrongAttempts > 0
        override val explanation: String? get() = null

        /** After a few misses the country starts to glow, so nobody is stuck while the alarm rings. */
        val showHint: Boolean get() = !solved && wrongAttempts >= HINT_AFTER_MISSES

        companion object {
            const val HINT_AFTER_MISSES = 3
        }
    }

    /** Four prompts, four answers: tap a prompt, then its answer. */
    data class Match(
        override val label: String,
        val prompts: List<String>,
        /** answers[i] belongs to prompts[i]. */
        val answers: List<String>,
        /** Right column order: slot s shows answers[answerOrder[s]]. */
        val answerOrder: List<Int>,
        val matched: Set<Int> = emptySet(),
        val selectedPrompt: Int? = null,
        /** A wrong pair to shake for a moment: prompt index and answer slot. */
        val wrongPair: Pair<Int, Int>? = null,
        val mistakes: Map<Int, Int> = emptyMap(),
    ) : CardUi {
        override val solved: Boolean get() = matched.size == prompts.size
        override val struggled: Boolean get() = mistakes.values.any { it > 0 }
        override val explanation: String? get() = null
    }
}

enum class ChoiceKind { MEANING, TRUE_FALSE, GAP, MATH }

enum class TypingResult { CORRECT, CLOSE_ACCENTS, CLOSE_TYPO, REVEALED }

data class LearnedItem(val prompt: String, val answer: String)

sealed interface WakeUpAction {
    data class OnPick(val index: Int) : WakeUpAction
    data class OnTypingChange(val text: String) : WakeUpAction
    data object OnCheckTyping : WakeUpAction
    data object OnRevealTyping : WakeUpAction
    data class OnMatchPrompt(val index: Int) : WakeUpAction
    data class OnMatchAnswer(val slot: Int) : WakeUpAction
    data class OnMapTap(val countryId: String, val countryName: String) : WakeUpAction

    data object OnContinue : WakeUpAction
    data object OnStartBonus : WakeUpAction
    data object OnSkipBonus : WakeUpAction
    data object OnEmergencyStop : WakeUpAction
    data object OnFinish : WakeUpAction

    /** Any touch on the screen. Resets the wake-up check. */
    data object OnUserActive : WakeUpAction

    /** Whether an alarm is ringing right now, as the platform reports it. */
    data class OnRingingChanged(val ringing: Boolean) : WakeUpAction

    /** "I'm up" on the soft re-ring. */
    data object OnStillAwake : WakeUpAction
}

sealed interface WakeUpEvent {
    data object StopAlarm : WakeUpEvent
    data object Close : WakeUpEvent
}
