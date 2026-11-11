package dev.victorc.sunup.feature.wakeup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.learning.CardContent
import dev.victorc.sunup.core.domain.learning.ReviewContext
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.SpacedRepetition
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import dev.victorc.sunup.feature.wakeup.domain.MorningCard
import dev.victorc.sunup.feature.wakeup.domain.WakePlan
import dev.victorc.sunup.feature.wakeup.domain.WakeSessionPlanner
import dev.victorc.sunup.feature.wakeup.domain.gradeFor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class WakeUpViewModel(
    private val alarmId: String,
    private val planner: WakeSessionPlanner,
    private val alarms: WakeAlarmRepository,
    private val reviews: ReviewRepository,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {

    private val _state = MutableStateFlow(WakeUpState())
    val state: StateFlow<WakeUpState> = _state.asStateFlow()

    private val _events = Channel<WakeUpEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var plan: WakePlan? = null
    private var cards: List<MorningCard> = emptyList()
    private var shownAt: Instant = clock.now()

    init {
        viewModelScope.launch {
            // The alarm's settings live in the database, which can be locked right after a reboot.
            val alarm = try {
                alarms.get(alarmId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val loaded = planner.plan(alarm)
            plan = loaded
            startPhase(WakePhase.SILENCING, loaded.silencing)
        }
    }

    fun onAction(action: WakeUpAction) {
        when (action) {
            is WakeUpAction.OnPick -> pick(action.index)
            WakeUpAction.OnContinue -> continueOn()
            WakeUpAction.OnStartBonus -> startPhase(WakePhase.BONUS, plan?.bonus.orEmpty())
            WakeUpAction.OnSkipBonus -> _state.update { it.copy(phase = WakePhase.DONE) }
            WakeUpAction.OnEmergencyStop -> {
                _state.update { it.copy(alarmStopped = true, stoppedWithoutCard = true, phase = WakePhase.DONE) }
                viewModelScope.launch { _events.send(WakeUpEvent.StopAlarm) }
            }
            WakeUpAction.OnFinish -> viewModelScope.launch { _events.send(WakeUpEvent.Close) }
        }
    }

    private fun pick(index: Int) {
        val current = _state.value
        val card = current.card ?: return
        if (card.solved || index in card.wrongPicks) return

        if (index != card.correctIndex) {
            _state.update { it.copy(card = card.copy(wrongPicks = card.wrongPicks + index)) }
            return
        }

        val stopsAlarm = current.phase == WakePhase.SILENCING && current.position == cards.lastIndex
        val learned = card.answer?.let { LearnedItem(card.prompt, it) }
        _state.update {
            it.copy(
                card = card.copy(solved = true),
                alarmStopped = it.alarmStopped || stopsAlarm,
                learned = if (learned != null) it.learned + learned else it.learned,
            )
        }
        record(cards[current.position], wrongAttempts = card.wrongPicks.size, phase = current.phase)
        if (stopsAlarm) viewModelScope.launch { _events.send(WakeUpEvent.StopAlarm) }
    }

    private fun continueOn() {
        val current = _state.value
        if (current.card?.solved != true) return

        val next = current.position + 1
        if (next < cards.size) {
            show(current.phase, next)
            return
        }
        val nextPhase = when (current.phase) {
            WakePhase.SILENCING ->
                if (plan?.mode == WakeMode.NORMAL && !plan?.bonus.isNullOrEmpty()) WakePhase.BONUS_OFFER else WakePhase.DONE
            else -> WakePhase.DONE
        }
        _state.update { it.copy(phase = nextPhase, card = null) }
    }

    private fun startPhase(phase: WakePhase, phaseCards: List<MorningCard>) {
        cards = phaseCards
        if (phaseCards.isEmpty()) {
            _state.update { it.copy(phase = WakePhase.DONE, card = null) }
        } else {
            show(phase, 0)
        }
    }

    private fun show(phase: WakePhase, index: Int) {
        shownAt = clock.now()
        _state.update {
            it.copy(phase = phase, card = cards[index].toUi(), position = index, total = cards.size)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun record(card: MorningCard, wrongAttempts: Int, phase: WakePhase) {
        val note = card.note ?: return
        val state = card.state ?: return
        val now = clock.now()
        val grade = gradeFor(wrongAttempts)
        val today = now.toLocalDateTime(timeZone()).date
        val nextState = SpacedRepetition.review(state, grade, today, plan?.deck?.examDate)
        val record = ReviewRecord(
            id = Uuid.random().toString(),
            noteId = note.id,
            direction = state.direction,
            game = card.content.game,
            grade = grade,
            context = if (phase == WakePhase.SILENCING) ReviewContext.SILENCING else ReviewContext.BONUS,
            reviewedAt = now,
            duration = now - shownAt,
        )
        // A failed write only loses one review; it must never interrupt waking up.
        viewModelScope.launch { reviews.recordReview(record, nextState) }
    }
}

// TODO(M4): move user-facing text to string resources once localization is set up.
private fun MorningCard.toUi(): CardUi = when (val c = content) {
    is CardContent.PickMeaning -> CardUi(
        kind = CardKind.CHOICE,
        label = label,
        prompt = c.prompt,
        options = c.options,
        correctIndex = c.correctIndex,
        answer = c.options[c.correctIndex],
    )
    is CardContent.TrueFalse -> CardUi(
        kind = CardKind.TRUE_FALSE,
        label = label,
        prompt = c.prompt,
        statement = c.shownAnswer,
        options = listOf("True", "False"),
        correctIndex = if (c.isTrue) 0 else 1,
        explanation = c.explanation ?: if (c.isTrue) null else "It means “${c.correctAnswer}”.",
        answer = c.correctAnswer,
    )
    is CardContent.QuickMath -> CardUi(
        kind = CardKind.MATH,
        label = label,
        prompt = c.question,
        options = c.options.map { it.toString() },
        correctIndex = c.correctIndex,
    )
}
