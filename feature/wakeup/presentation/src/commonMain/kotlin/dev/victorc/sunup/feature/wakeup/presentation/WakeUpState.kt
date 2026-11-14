package dev.victorc.sunup.feature.wakeup.presentation

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
)

enum class WakePhase { LOADING, SILENCING, BONUS_OFFER, BONUS, DONE }

enum class CardKind { CHOICE, TRUE_FALSE, MATH }

data class CardUi(
    val kind: CardKind,
    val label: String,
    val prompt: String,
    /** True or false only: the answer being claimed. */
    val statement: String? = null,
    val options: List<String>,
    val correctIndex: Int,
    /** Shown once answered, e.g. the right meaning after a false statement. */
    val explanation: String? = null,
    /** The right answer in words, for the learned list. Null for Quick math. */
    val answer: String? = null,
    val wrongPicks: Set<Int> = emptySet(),
    val solved: Boolean = false,
)

data class LearnedItem(val prompt: String, val answer: String)

sealed interface WakeUpAction {
    data class OnPick(val index: Int) : WakeUpAction
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
