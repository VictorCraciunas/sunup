package dev.victorc.sunup.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.victorc.sunup.core.alarm.AlarmError
import dev.victorc.sunup.core.alarm.AlarmScheduler
import dev.victorc.sunup.core.alarm.AlarmService
import dev.victorc.sunup.core.alarm.ScheduleError
import dev.victorc.sunup.core.data.GeoDeckSeeder
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.settings.AppSettingsRepository
import dev.victorc.sunup.core.domain.wakeup.TEST_ALARM_ID
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import dev.victorc.sunup.core.geo.GeoAtlasSource
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
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
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** What the first alarm wakes the user up with. */
enum class LearnGoal {
    /** Adds a map pack; the alarm uses it. */
    GEOGRAPHY,

    /** Opens the Decks tab afterwards, to make or import a deck. */
    OWN_CARDS,

    /** No deck: the alarm falls back to Quick math. */
    MATH,
}

enum class TestAlarmState { IDLE, SET, NEEDS_EXACT_ALARMS, FAILED }

data class OnboardingState(
    val goal: LearnGoal? = null,
    val region: GeoRegion = GeoRegion.EUROPE,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val promised: Boolean = false,
    val mode: WakeMode = WakeMode.NORMAL,
    val testAlarm: TestAlarmState = TestAlarmState.IDLE,
    val finishing: Boolean = false,
    val problem: String? = null,
)

sealed interface OnboardingAction {
    data class OnPickGoal(val goal: LearnGoal) : OnboardingAction
    data class OnPickRegion(val region: GeoRegion) : OnboardingAction
    data class OnHourStep(val by: Int) : OnboardingAction
    data class OnMinuteStep(val by: Int) : OnboardingAction
    data class OnTimePicked(val hour: Int, val minute: Int) : OnboardingAction
    data class OnToggleDay(val day: DayOfWeek) : OnboardingAction
    data object OnPromise : OnboardingAction
    data object OnTestAlarm : OnboardingAction
    data class OnPickMode(val mode: WakeMode) : OnboardingAction
    data object OnFinish : OnboardingAction
    data object OnDismissProblem : OnboardingAction
}

sealed interface OnboardingEvent {
    /** Onboarding is over. [openDecks]: the user wants to make their own cards first. */
    data class Finished(val openDecks: Boolean) : OnboardingEvent
}

/**
 * Holds the answers from every first-run screen (one ViewModel for the whole flow) and, at
 * the end, adds the map pack, saves the first alarm and remembers that onboarding is done.
 */
class OnboardingViewModel(
    private val alarms: AlarmService,
    private val scheduler: AlarmScheduler,
    private val settings: AppSettingsRepository,
    private val atlasSource: GeoAtlasSource,
    private val geoSeeder: GeoDeckSeeder,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @OptIn(ExperimentalUuidApi::class)
    private val alarmId = Uuid.random().toString()
    private var testResetJob: Job? = null

    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.OnPickGoal -> _state.update { it.copy(goal = action.goal) }
            is OnboardingAction.OnPickRegion -> _state.update { it.copy(region = action.region) }
            is OnboardingAction.OnHourStep -> _state.update { it.copy(hour = (it.hour + action.by).mod(24)) }
            is OnboardingAction.OnMinuteStep -> _state.update { it.copy(minute = (it.minute + action.by).mod(60)) }
            is OnboardingAction.OnTimePicked -> _state.update { it.copy(hour = action.hour, minute = action.minute) }
            is OnboardingAction.OnToggleDay -> _state.update {
                it.copy(days = if (action.day in it.days) it.days - action.day else it.days + action.day)
            }
            OnboardingAction.OnPromise -> _state.update { it.copy(promised = true) }
            OnboardingAction.OnTestAlarm -> ringTestAlarm()
            is OnboardingAction.OnPickMode -> _state.update { it.copy(mode = action.mode) }
            OnboardingAction.OnFinish -> finish()
            OnboardingAction.OnDismissProblem -> _state.update { it.copy(problem = null) }
        }
    }

    /** A soft ring in 10 seconds, played as one Gentle card. Nothing is saved. */
    private fun ringTestAlarm() {
        testResetJob?.cancel()
        testResetJob = viewModelScope.launch {
            val result = scheduler.scheduleFollowUp(TEST_ALARM_ID, clock.now() + TEST_ALARM_DELAY)
            val outcome = when (result) {
                is Result.Success -> TestAlarmState.SET
                is Result.Error -> when (result.error) {
                    ScheduleError.EXACT_ALARMS_NOT_ALLOWED -> TestAlarmState.NEEDS_EXACT_ALARMS
                    ScheduleError.UNKNOWN -> TestAlarmState.FAILED
                }
            }
            _state.update { it.copy(testAlarm = outcome) }
            // Then the button can be used again.
            delay(TEST_ALARM_DELAY + 5.seconds)
            _state.update { it.copy(testAlarm = TestAlarmState.IDLE) }
        }
    }

    private fun finish() {
        val current = _state.value
        if (current.finishing) return
        _state.update { it.copy(finishing = true, problem = null) }

        viewModelScope.launch {
            val deckId = if (current.goal == LearnGoal.GEOGRAPHY) addMap(current.region) else null
            val alarm = WakeAlarm(
                id = alarmId,
                time = LocalTime(current.hour, current.minute),
                repeatDays = current.days,
                mode = current.mode,
                deckId = deckId,
            )
            val problem = when (val saved = alarms.save(alarm)) {
                is Result.Success -> null
                is Result.Error -> when (saved.error) {
                    AlarmError.EXACT_ALARMS_NOT_ALLOWED -> EXACT_ALARMS_PROBLEM
                    AlarmError.NOT_SCHEDULED -> NOT_SCHEDULED_PROBLEM
                    // It rings; only its settings were lost. Carry on.
                    AlarmError.NOT_SAVED -> null
                }
            }
            if (problem != null) {
                _state.update { it.copy(finishing = false, problem = problem) }
                return@launch
            }
            // If this fails, onboarding shows again next time. The alarm is already set.
            settings.setOnboardingDone()
            _events.send(OnboardingEvent.Finished(openDecks = current.goal == LearnGoal.OWN_CARDS))
        }
    }

    /** The map pack's deck id, or null when the map file can't be read (the alarm then uses Quick math). */
    private suspend fun addMap(region: GeoRegion): String? = try {
        when (val atlas = atlasSource.load()) {
            is Result.Success -> geoSeeder.seed(region, atlas.data.countries(region)).id
            is Result.Error -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

private val TEST_ALARM_DELAY = 10.seconds

// TODO(M4): move user-facing text to string resources once localization is set up.
private const val EXACT_ALARMS_PROBLEM =
    "Sunup isn't allowed to set alarms yet. Go back and allow alarms, then try again."
private const val NOT_SCHEDULED_PROBLEM = "The phone didn't accept the alarm. Please try again."
