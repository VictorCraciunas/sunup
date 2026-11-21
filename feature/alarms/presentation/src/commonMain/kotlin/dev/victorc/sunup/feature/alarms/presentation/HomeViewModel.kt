package dev.victorc.sunup.feature.alarms.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.alarm.AlarmService
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.settings.AppSettingsRepository
import dev.victorc.sunup.core.domain.wakeup.DayMark
import dev.victorc.sunup.core.domain.wakeup.Streaks
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeSession
import dev.victorc.sunup.core.domain.wakeup.WakeSessionRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class HomeState(
    val loading: Boolean = true,
    /** Sunrises in a row. */
    val streak: Int = 0,
    val cardsAnswered: Int = 0,
    /** The big alarm card. Null when there are no alarms at all. */
    val alarm: HomeAlarmUi? = null,
    /** How many alarms there are, on or off: the card's "All 3 ›". */
    val alarmCount: Int = 0,
    /** The deck the next alarm uses. Null when there are no decks yet. */
    val deck: DeckProgressUi? = null,
    val week: List<WeekDayUi> = emptyList(),
    val sunrisesThisWeek: Int = 0,
    val message: String? = null,
    /** "You're up!" extras the user switched on (the phone's permission is checked on screen). */
    val showWeather: Boolean = false,
    val showCalendar: Boolean = false,
)

data class HomeAlarmUi(
    val id: String,
    /** "Today", "Tomorrow", "Wednesday", or "Off". */
    val day: String,
    val time: String,
    /** "Mon–Fri · Geography · Europe · Normal", or the off message. */
    val details: String,
    val enabled: Boolean,
)

data class DeckProgressUi(
    val id: String,
    val name: String,
    val isMap: Boolean,
    val learned: Int,
    val total: Int,
)

data class WeekDayUi(
    /** "M", "T"… */
    val letter: String,
    /** "Monday", for screen readers. */
    val name: String,
    val mark: DayMark,
)

sealed interface HomeAction {
    data class OnToggleAlarm(val enabled: Boolean) : HomeAction
    data object OnOpenAlarms : HomeAction
    data object OnAddAlarm : HomeAction
    data object OnOpenDeck : HomeAction
    data object OnOpenDecks : HomeAction
    data object OnPreview : HomeAction
    data object OnDismissMessage : HomeAction

    /** Home went out of sight: the card goes back to showing the next alarm. */
    data object OnLeave : HomeAction

    /** Already allowed by the phone when [on]; the screen asks for permission first. */
    data class OnSetWeather(val on: Boolean) : HomeAction
    data class OnSetCalendar(val on: Boolean) : HomeAction

    /** The phone said no to location ([forWeather]) or the calendar. */
    data class OnPermissionDenied(val forWeather: Boolean) : HomeAction
}

sealed interface HomeEvent {
    data object OpenAlarms : HomeEvent
    data object NewAlarm : HomeEvent
    data class OpenDeck(val deckId: String) : HomeEvent
    data object OpenDecks : HomeEvent
    data class Preview(val alarmId: String) : HomeEvent
}

/** Home (the original mockup): streak, next alarm, its deck's progress, and this week. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val service: AlarmService,
    decks: DeckRepository,
    private val notes: NoteRepository,
    private val reviews: ReviewRepository,
    sessions: WakeSessionRepository,
    private val settings: AppSettingsRepository,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var alarms: List<WakeAlarm> = emptyList()

    /**
     * An alarm switched off from Home's card stays on the card (as "Off") instead of the card
     * jumping to another alarm under the user's finger. Only until Home is left.
     */
    private val heldAlarmId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            // A one-off alarm that rang was switched off by the engine; show it that way.
            try {
                service.syncWithEngine()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Home matters more than a perfect sync; it runs again next time.
            }
        }

        combine(
            service.observe(),
            decks.observeDecks(),
            sessions.observeAll(),
            // Card counts change when cards are added; reviews change with the mornings (sessions).
            notes.observeCardCounts(),
            everyMinute(),
        ) { alarmList, deckList, sessionList, _, _ -> Triple(alarmList, deckList, sessionList) }
            .combine(heldAlarmId) { lists, held -> lists to held }
            .mapLatest { (lists, held) -> build(lists.first, lists.second, lists.third, held) }
            .catch { _state.update { it.copy(loading = false, message = "Couldn't load Home. Try again.") } }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)

        settings.observeMorningInfo()
            .onEach { info -> _state.update { it.copy(showWeather = info.weather, showCalendar = info.calendar) } }
            .launchIn(viewModelScope)
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnToggleAlarm -> toggle(action.enabled)
            HomeAction.OnOpenAlarms -> send(HomeEvent.OpenAlarms)
            HomeAction.OnAddAlarm -> send(HomeEvent.NewAlarm)
            HomeAction.OnOpenDeck -> _state.value.deck?.let { send(HomeEvent.OpenDeck(it.id)) }
            HomeAction.OnOpenDecks -> send(HomeEvent.OpenDecks)
            HomeAction.OnPreview -> _state.value.alarm?.let { send(HomeEvent.Preview(it.id)) }
            HomeAction.OnDismissMessage -> _state.update { it.copy(message = null) }
            HomeAction.OnLeave -> heldAlarmId.value = null
            is HomeAction.OnSetWeather -> viewModelScope.launch { settings.setShowWeather(action.on) }
            is HomeAction.OnSetCalendar -> viewModelScope.launch { settings.setShowCalendar(action.on) }
            is HomeAction.OnPermissionDenied -> _state.update {
                it.copy(
                    message = if (action.forWeather) {
                        "Sunup can't see your approximate location. You can allow it for Sunup in the phone's settings."
                    } else {
                        "Sunup can't see your calendar. You can allow it for Sunup in the phone's settings."
                    },
                )
            }
        }
    }

    private fun send(event: HomeEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun toggle(enabled: Boolean) {
        val alarm = alarms.firstOrNull { it.id == _state.value.alarm?.id } ?: return
        heldAlarmId.value = alarm.id
        viewModelScope.launch {
            val result = service.setEnabled(alarm, enabled)
            if (result is Result.Error) _state.update { it.copy(message = result.error.message()) }
        }
    }

    private suspend fun build(
        alarmList: List<WakeAlarm>,
        deckList: List<Deck>,
        sessionList: List<WakeSession>,
        held: String?,
    ): HomeState {
        alarms = alarmList
        val now = service.now()
        val today = now.toLocalDateTime(timeZone()).date
        val next = service.next(alarmList, now)

        // The one just switched off here (until Home is left); else the next to ring; else the earliest one (off).
        val shown = alarmList.firstOrNull { it.id == held && !it.enabled }
            ?: next?.alarm
            ?: alarmList.firstOrNull()
        val deckNames = deckList.associate { it.id to it.name }
        val alarmUi = shown?.let { alarm ->
            val ringsOn = next?.takeIf { it.alarm.id == alarm.id }?.at?.toLocalDateTime(timeZone())?.date
            HomeAlarmUi(
                id = alarm.id,
                day = when {
                    !alarm.enabled || ringsOn == null -> "Off"
                    ringsOn == today -> "Today"
                    ringsOn == today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
                    else -> ringsOn.dayOfWeek.fullName()
                },
                time = timeText(alarm.time),
                details = if (alarm.enabled) {
                    listOfNotNull(
                        repeatText(alarm.repeatDays),
                        alarm.deckId?.let { deckNames[it] } ?: ANY_DECK,
                        alarm.mode.title(),
                    ).joinToString(" · ")
                } else {
                    "Alarm off · enjoy the sleep-in"
                },
                enabled = alarm.enabled,
            )
        }

        // "Any deck" means the first deck, as in the morning planner.
        val deck = shown?.deckId?.let { id -> deckList.firstOrNull { it.id == id } } ?: deckList.firstOrNull()
        val week = Streaks.week(sessionList, today).map { (day, mark) ->
            WeekDayUi(letter = day.dayOfWeek.letter(), name = day.dayOfWeek.fullName(), mark = mark)
        }

        return HomeState(
            loading = false,
            streak = Streaks.current(sessionList, today),
            cardsAnswered = Streaks.cardsAnswered(sessionList),
            alarm = alarmUi,
            alarmCount = alarmList.size,
            deck = deck?.let { progress(it) },
            week = week,
            sunrisesThisWeek = week.count { it.mark == DayMark.SUNRISE },
            message = _state.value.message,
            showWeather = _state.value.showWeather,
            showCalendar = _state.value.showCalendar,
        )
    }

    /** Learned: answered right, and not forgotten since. */
    private suspend fun progress(deck: Deck): DeckProgressUi {
        val total = notes.notesInDeck(deck.id).size
        val learned = reviews.statesInDeck(deck.id).filter { it.isKnown }.map { it.noteId }.distinct().size
        return DeckProgressUi(
            id = deck.id,
            name = deck.name,
            isMap = deck.kind == DeckKind.GEOGRAPHY,
            learned = learned.coerceAtMost(total),
            total = total,
        )
    }

    /** Ticks once a minute, so "Today" turns into "Tomorrow" and the week moves on at midnight. */
    private fun everyMinute(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}
