package dev.victorc.sunup.feature.progress.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.wakeup.DayMark
import dev.victorc.sunup.core.domain.wakeup.DayRange
import dev.victorc.sunup.core.domain.wakeup.Progress
import dev.victorc.sunup.core.domain.wakeup.Streaks
import dev.victorc.sunup.core.domain.wakeup.WakeSession
import dev.victorc.sunup.core.domain.wakeup.WakeSessionRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class Period { WEEK, MONTH }

data class ProgressState(
    val loading: Boolean = true,
    val period: Period = Period.WEEK,
    val streak: Int = 0,
    /** The longest streak ever, this one included. */
    val bestStreak: Int = 0,
    /** New cards answered right for the first time this week / month. */
    val learned: Int = 0,
    /** "91%", or "–" when no card came back yet. */
    val remembered: String = "–",
    /** Week: one bar per day, Monday first. */
    val bars: List<BarUi> = emptyList(),
    /** "Avg wake-up 7:02", or null without a sunrise this week. */
    val averageWakeUp: String? = null,
    /** Month: "November", the grid and "17 of 20 mornings". */
    val monthTitle: String = "",
    val monthCells: List<MonthCellUi> = emptyList(),
    val monthSummary: String = "",
    /** Months back from this one (0 = now); › is hidden at 0. */
    val monthsBack: Int = 0,
    val milestones: List<MilestoneUi> = emptyList(),
    val message: String? = null,
)

data class BarUi(val letter: String, val dayName: String, val cards: Int)

/** One square of the month grid; null [day] is an empty square before the 1st. */
data class MonthCellUi(val day: Int?, val mark: DayMark?)

/** One row of the milestone list (mockup I1). */
data class MilestoneUi(
    /** What's on the medal while it's locked: "100", "30". */
    val badge: String,
    /** "First week", "100 cards", "A month of sunrises". */
    val name: String,
    /** What it asks, in words: "Wake up 30 days in a row". */
    val goal: String,
    /** How far along: the streak now, or cards answered so far (at most [target]). */
    val progress: Int,
    val target: Int,
    val earned: Boolean,
    val kind: MilestoneKind,
)

enum class MilestoneKind { STREAK, CARDS }

sealed interface ProgressAction {
    data class OnPeriod(val period: Period) : ProgressAction
    data object OnEarlierMonth : ProgressAction
    data object OnLaterMonth : ProgressAction
}

class ProgressViewModel(
    sessions: WakeSessionRepository,
    reviews: ReviewRepository,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressState())
    val state: StateFlow<ProgressState> = _state.asStateFlow()

    private val choice = MutableStateFlow(Choice())

    init {
        combine(sessions.observeAll(), reviews.observeAllReviews(), choice) { sessionList, reviewList, picked ->
            build(sessionList, reviewList, picked)
        }
            .catch { _state.update { it.copy(loading = false, message = "Couldn't load your progress. Try again.") } }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ProgressAction) {
        when (action) {
            is ProgressAction.OnPeriod -> choice.update { it.copy(period = action.period) }
            ProgressAction.OnEarlierMonth -> choice.update { it.copy(monthsBack = it.monthsBack + 1) }
            ProgressAction.OnLaterMonth -> choice.update { it.copy(monthsBack = (it.monthsBack - 1).coerceAtLeast(0)) }
        }
    }

    private fun build(sessions: List<WakeSession>, reviews: List<ReviewRecord>, picked: Choice): ProgressState {
        val zone = timeZone()
        val today = clock.now().toLocalDateTime(zone).date
        val week = DayRange.weekOf(today)
        val thisMonth = DayRange.monthOf(today)
        val shownMonth = DayRange.monthOf(thisMonth.from.minus(picked.monthsBack, DateTimeUnit.MONTH))
        // The stat boxes follow the toggle: this week, or the month on screen.
        val statRange = if (picked.period == Period.WEEK) week else shownMonth

        val bars = Progress.cardsPerDay(sessions, week).map { (day, cards) ->
            BarUi(letter = day.dayOfWeek.name.take(1), dayName = day.dayOfWeek.fullName(), cards = cards)
        }
        val (sunrises, alarmDays) = Progress.mornings(sessions, shownMonth)
        val streak = Streaks.current(sessions, today)
        val best = maxOf(Progress.bestStreak(sessions), streak)
        val cards = Streaks.cardsAnswered(sessions)

        return ProgressState(
            loading = false,
            period = picked.period,
            streak = streak,
            bestStreak = best,
            learned = Progress.learned(reviews, statRange, zone),
            remembered = Progress.remembered(reviews, statRange, zone)?.let { "$it%" } ?: "–",
            bars = bars,
            averageWakeUp = Progress.averageWakeUp(sessions, week, zone)?.let {
                "Avg wake-up ${it.hour}:${it.minute.toString().padStart(2, '0')}"
            },
            monthTitle = shownMonth.from.month.name.lowercase().replaceFirstChar { it.uppercase() } +
                if (shownMonth.from.year != today.year) " ${shownMonth.from.year}" else "",
            monthCells = monthCells(sessions, shownMonth, today),
            monthSummary = if (alarmDays == 0) "No alarms yet" else "$sunrises of $alarmDays mornings",
            monthsBack = picked.monthsBack,
            milestones = listOf(
                streakMilestone(days = 7, name = "First week", streak = streak, best = best),
                MilestoneUi(
                    badge = "100",
                    name = "100 cards",
                    goal = "Answer 100 cards in your mornings",
                    progress = cards.coerceAtMost(100),
                    target = 100,
                    earned = cards >= 100,
                    kind = MilestoneKind.CARDS,
                ),
                streakMilestone(days = 30, name = "A month of sunrises", streak = streak, best = best),
            ),
            message = _state.value.message,
        )
    }

    private fun monthCells(sessions: List<WakeSession>, month: DayRange, today: LocalDate): List<MonthCellUi> {
        val blanks = List(month.from.dayOfWeek.ordinal) { MonthCellUi(day = null, mark = null) }
        return blanks + month.days.map { day -> MonthCellUi(day = day.day, mark = Streaks.markFor(day, sessions, today)) }
    }

    /**
     * Earned once the best streak ever reached [days], so a broken streak never takes it back.
     * Until then the bar shows today's streak: that's the one that has to get there.
     */
    private fun streakMilestone(days: Int, name: String, streak: Int, best: Int) = MilestoneUi(
        badge = "$days",
        name = name,
        goal = "Wake up $days days in a row",
        progress = streak.coerceAtMost(days),
        target = days,
        earned = best >= days,
        kind = MilestoneKind.STREAK,
    )

    private data class Choice(val period: Period = Period.WEEK, val monthsBack: Int = 0)
}

private fun kotlinx.datetime.DayOfWeek.fullName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
