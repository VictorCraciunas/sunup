package dev.victorc.sunup.feature.wakeup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.alarm.AlarmScheduler
import dev.victorc.sunup.core.domain.learning.AnswerCheck
import dev.victorc.sunup.core.domain.learning.AnswerMatcher
import dev.victorc.sunup.core.domain.learning.CardContent
import dev.victorc.sunup.core.domain.learning.CloseReason
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.ReviewContext
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.SpacedRepetition
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.geo.GeoAtlasSource
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import dev.victorc.sunup.feature.wakeup.domain.MorningCard
import dev.victorc.sunup.feature.wakeup.domain.WakePlan
import dev.victorc.sunup.feature.wakeup.domain.WakeSessionPlanner
import dev.victorc.sunup.feature.wakeup.domain.gradeFor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val scheduler: AlarmScheduler,
    private val atlasSource: GeoAtlasSource,
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

    /** Trying the games from the test bench: no alarm, no wake-up check, nothing saved. */
    private val isPreview = alarmId == PREVIEW_ALARM_ID

    // Wake-up check: after the alarm stops, a soft re-ring is always scheduled 60 s ahead.
    // Every touch on this screen pushes it back. If the user leaves Sunup, the alarm engine
    // decides when it fires: busy in another app means awake for now (look again in a minute,
    // until checkUntil); screen off or locked means maybe asleep again, so it rings.
    private val isFollowUpRing = alarmId.endsWith(FOLLOW_UP_SUFFIX)
    private val followUpId = if (isFollowUpRing) alarmId else "$alarmId$FOLLOW_UP_SUFFIX"
    private var checkActive = false
    private var checkUntil: Instant? = null
    private var checkDeadline: Instant? = null
    private var lastScheduledAt: Instant? = null
    private var tickerJob: Job? = null

    init {
        if (isFollowUpRing) {
            // Opened by the wake-up check's soft re-ring after Sunup was closed (e.g. swiped
            // away): no new card, just "Still with me?", then "You're up!".
            _state.update { it.copy(phase = WakePhase.DONE, alarmStopped = true, ringingAgain = true) }
            activateWakeCheck()
        } else {
            loadPlan()
        }
    }

    private fun loadPlan() {
        viewModelScope.launch {
            val loaded = if (isPreview) {
                planner.preview()
            } else {
                // The alarm's settings live in the database, which can be locked right after a reboot.
                val alarm = try {
                    alarms.get(alarmId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                planner.plan(alarm)
            }
            val playable = withMapLoaded(loaded)
            plan = playable
            startPhase(WakePhase.SILENCING, playable.silencing)
        }
    }

    /**
     * Geography cards need the map. If it can't be read, or a country isn't on it, those cards
     * are swapped out so the alarm can still be stopped.
     */
    private suspend fun withMapLoaded(plan: WakePlan): WakePlan {
        val hasMap = (plan.silencing + plan.bonus).any { it.content is CardContent.FindOnMap }
        if (!hasMap) return plan
        val atlas = when (val result = atlasSource.load()) {
            is Result.Success -> result.data
            is Result.Error -> null
        }
        _state.update { it.copy(atlas = atlas) }
        return planner.withoutUnplayableMaps(plan) { atlas?.shape(it.countryId) != null }
    }

    fun onAction(action: WakeUpAction) {
        when (action) {
            is WakeUpAction.OnPick -> pick(action.index)
            is WakeUpAction.OnTypingChange -> {
                updateCard<CardUi.Typing> { if (it.solved) it else it.copy(text = action.text) }
                // Typing on the keyboard doesn't count as a screen touch, so count it here.
                onAction(WakeUpAction.OnUserActive)
            }
            WakeUpAction.OnCheckTyping -> checkTyping()
            WakeUpAction.OnRevealTyping -> revealTyping()
            is WakeUpAction.OnMatchPrompt -> updateCard<CardUi.Match> {
                if (action.index in it.matched) it else it.copy(selectedPrompt = action.index)
            }
            is WakeUpAction.OnMatchAnswer -> matchAnswer(action.slot)
            is WakeUpAction.OnMapTap -> mapTap(action.countryId, action.countryName)

            WakeUpAction.OnContinue -> continueOn()
            WakeUpAction.OnStartBonus -> startPhase(WakePhase.BONUS, plan?.bonus.orEmpty())
            WakeUpAction.OnSkipBonus -> _state.update { it.copy(phase = WakePhase.DONE) }
            WakeUpAction.OnEmergencyStop -> {
                _state.update { it.copy(alarmStopped = true, stoppedWithoutCard = true, phase = WakePhase.DONE) }
                viewModelScope.launch { _events.send(WakeUpEvent.StopAlarm) }
            }
            WakeUpAction.OnFinish -> viewModelScope.launch {
                // Cancel first: the screen (and this scope) goes away right after Close.
                endWakeCheck(reason = "start my day")
                _events.send(WakeUpEvent.Close)
            }
            WakeUpAction.OnUserActive -> if (checkActive && !_state.value.ringingAgain) pushWakeCheck(force = false)
            is WakeUpAction.OnRingingChanged -> if (checkActive) _state.update { it.copy(ringingAgain = action.ringing) }
            WakeUpAction.OnStillAwake -> {
                _state.update { it.copy(ringingAgain = false) }
                viewModelScope.launch { _events.send(WakeUpEvent.StopAlarm) }
                checkUntil = clock.now() + CHECK_WINDOW
                pushWakeCheck(force = true)
            }
        }
    }

    // Games

    private fun pick(index: Int) {
        val card = _state.value.card as? CardUi.Choice ?: return
        if (card.solved || index in card.wrongPicks) return

        if (index != card.correctIndex) {
            setCard(card.copy(wrongPicks = card.wrongPicks + index))
            return
        }
        solve(card.copy(solved = true), grades = listOf(gradeFor(card.wrongPicks.size)), learned = listOfNotNull(card.learned))
    }

    private fun checkTyping() {
        val card = _state.value.card as? CardUi.Typing ?: return
        if (card.solved || card.text.isBlank()) return

        val result = when (val check = AnswerMatcher.check(card.text, card.answers, card.language)) {
            AnswerCheck.Correct -> TypingResult.CORRECT
            is AnswerCheck.Close ->
                if (check.reason == CloseReason.ACCENTS) TypingResult.CLOSE_ACCENTS else TypingResult.CLOSE_TYPO
            AnswerCheck.Wrong -> null
        }
        if (result == null) {
            setCard(card.copy(wrongAttempts = card.wrongAttempts + 1))
            return
        }
        // Right first time is GOOD; close (accents, a typo) is HARD; needing another go is AGAIN.
        val grade = when {
            card.wrongAttempts > 0 -> Grade.AGAIN
            result == TypingResult.CORRECT -> Grade.GOOD
            else -> Grade.HARD
        }
        solve(card.copy(result = result), grades = listOf(grade), learned = listOf(LearnedItem(card.prompt, card.answers.first())))
    }

    private fun revealTyping() {
        val card = _state.value.card as? CardUi.Typing ?: return
        if (card.solved) return
        solve(
            card.copy(result = TypingResult.REVEALED),
            grades = listOf(Grade.AGAIN),
            learned = listOf(LearnedItem(card.prompt, card.answers.first())),
        )
    }

    private fun matchAnswer(slot: Int) {
        val card = _state.value.card as? CardUi.Match ?: return
        val prompt = card.selectedPrompt ?: return
        if (card.solved || prompt in card.matched) return

        val answerIndex = card.answerOrder[slot]
        if (answerIndex in card.matched) return

        if (answerIndex != prompt) {
            val mistakes = card.mistakes + (prompt to (card.mistakes[prompt] ?: 0) + 1)
            val wrong = prompt to slot
            setCard(card.copy(selectedPrompt = null, wrongPair = wrong, mistakes = mistakes))
            viewModelScope.launch {
                delay(WRONG_PAIR_FLASH_MILLIS)
                updateCard<CardUi.Match> { if (it.wrongPair == wrong) it.copy(wrongPair = null) else it }
            }
            return
        }

        val matched = card.copy(matched = card.matched + prompt, selectedPrompt = null, wrongPair = null)
        if (!matched.solved) {
            setCard(matched)
            return
        }
        solve(
            matched,
            grades = card.prompts.indices.map { gradeFor(card.mistakes[it] ?: 0) },
            learned = card.prompts.indices.map { LearnedItem(card.prompts[it], card.answers[it]) },
        )
    }

    private fun mapTap(countryId: String, countryName: String) {
        val card = _state.value.card as? CardUi.FindOnMap ?: return
        if (card.solved) return

        if (countryId != card.countryId) {
            // Tapping the same wrong country twice in a row is one mistake, not two.
            val repeat = countryId == card.lastWrongId
            setCard(
                card.copy(
                    wrongAttempts = if (repeat) card.wrongAttempts else card.wrongAttempts + 1,
                    lastWrongId = countryId,
                    lastWrongName = countryName,
                ),
            )
            return
        }
        // Found first time is GOOD, after one miss HARD, after more AGAIN so it comes back soon.
        val grade = when (card.wrongAttempts) {
            0 -> Grade.GOOD
            1 -> Grade.HARD
            else -> Grade.AGAIN
        }
        solve(
            card.copy(solved = true, lastWrongId = null, lastWrongName = null),
            grades = listOf(grade),
            learned = listOf(LearnedItem(card.countryName, card.region.title)),
        )
    }

    /** A card is done: show it as solved, save the reviews, and stop the alarm if it was the last silencing card. */
    private fun solve(solved: CardUi, grades: List<Grade>, learned: List<LearnedItem>) {
        val current = _state.value
        val stopsAlarm = current.phase == WakePhase.SILENCING && current.position == cards.lastIndex
        _state.update {
            it.copy(
                card = solved,
                alarmStopped = it.alarmStopped || stopsAlarm,
                learned = it.learned + learned,
            )
        }
        record(cards[current.position], grades, current.phase)
        if (stopsAlarm) {
            viewModelScope.launch { _events.send(WakeUpEvent.StopAlarm) }
            startWakeCheck()
        }
    }

    private fun setCard(card: CardUi) {
        _state.update { it.copy(card = card) }
    }

    private inline fun <reified T : CardUi> updateCard(change: (T) -> CardUi) {
        _state.update { current ->
            val card = current.card as? T ?: return@update current
            current.copy(card = change(card))
        }
    }

    // Flow between cards and phases

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
    private fun record(card: MorningCard, grades: List<Grade>, phase: WakePhase) {
        if (isPreview) return
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone()).date
        card.items.zip(grades).forEach { (item, grade) ->
            val nextState = SpacedRepetition.review(item.state, grade, today, plan?.deck?.examDate)
            val record = ReviewRecord(
                id = Uuid.random().toString(),
                noteId = item.note.id,
                direction = item.state.direction,
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

    // Wake-up check

    private fun startWakeCheck() {
        if (isPreview || checkActive || plan?.mode == WakeMode.GENTLE) return
        activateWakeCheck()
        pushWakeCheck(force = true)
    }

    private fun activateWakeCheck() {
        checkActive = true
        checkUntil = clock.now() + CHECK_WINDOW
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (checkActive) {
                val left = checkDeadline?.let { (it - clock.now()).inWholeSeconds.coerceAtLeast(0).toInt() }
                _state.update { it.copy(wakeCheckSecondsLeft = left) }
                delay(1_000)
            }
        }
    }

    private fun pushWakeCheck(force: Boolean) {
        if (!checkActive) return
        val now = clock.now()
        val last = lastScheduledAt
        // Touches come in bursts; rescheduling every few seconds is plenty.
        if (!force && last != null && now - last < RESCHEDULE_EVERY) return
        lastScheduledAt = now
        val at = now + WAKE_CHECK
        checkDeadline = at
        val until = maxOf(checkUntil ?: at, at)
        viewModelScope.launch { scheduler.scheduleFollowUp(followUpId, at, recheckWhileInUseUntil = until) }
    }

    private suspend fun endWakeCheck(reason: String) {
        if (!checkActive) return
        checkActive = false
        tickerJob?.cancel()
        _state.update { it.copy(wakeCheckSecondsLeft = null, ringingAgain = false) }
        scheduler.cancelFollowUp(followUpId, reason)
    }
}

private val WAKE_CHECK = 60.seconds
private val RESCHEDULE_EVERY = 5.seconds

/** How long the wake-up check keeps watching once the user is busy in another app. */
private val CHECK_WINDOW = 5.minutes
private const val FOLLOW_UP_SUFFIX = ":check"
private const val WRONG_PAIR_FLASH_MILLIS = 700L

// TODO(M4): move user-facing text to string resources once localization is set up.
private fun MorningCard.toUi(): CardUi = when (val c = content) {
    is CardContent.PickMeaning -> CardUi.Choice(
        kind = ChoiceKind.MEANING,
        label = label,
        prompt = c.prompt,
        options = c.options,
        correctIndex = c.correctIndex,
        learned = LearnedItem(c.prompt, c.options[c.correctIndex]),
    )
    is CardContent.TrueFalse -> CardUi.Choice(
        kind = ChoiceKind.TRUE_FALSE,
        label = label,
        prompt = c.prompt,
        statement = c.shownAnswer,
        options = listOf("True", "False"),
        correctIndex = if (c.isTrue) 0 else 1,
        explanation = c.explanation ?: if (c.isTrue) null else "It means “${c.correctAnswer}”.",
        learned = LearnedItem(c.prompt, c.correctAnswer),
    )
    is CardContent.FillTheGap -> CardUi.Choice(
        kind = ChoiceKind.GAP,
        label = label,
        prompt = c.sentence,
        statement = c.translation,
        options = c.options,
        correctIndex = c.correctIndex,
        learned = LearnedItem(c.word, c.meaning),
    )
    is CardContent.QuickMath -> CardUi.Choice(
        kind = ChoiceKind.MATH,
        label = label,
        prompt = c.question,
        options = c.options.map { it.toString() },
        correctIndex = c.correctIndex,
    )
    is CardContent.TypeIt -> CardUi.Typing(
        label = label,
        prompt = c.prompt,
        answers = c.answers,
        language = c.language,
    )
    is CardContent.FindOnMap -> CardUi.FindOnMap(
        label = label,
        countryId = c.countryId,
        countryName = c.countryName,
        region = c.region,
    )
    is CardContent.MatchPairs -> CardUi.Match(
        label = label,
        prompts = c.prompts,
        answers = c.answers,
        answerOrder = c.answerOrder,
    )
}
