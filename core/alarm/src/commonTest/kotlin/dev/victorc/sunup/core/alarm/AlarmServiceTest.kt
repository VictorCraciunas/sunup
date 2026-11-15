package dev.victorc.sunup.core.alarm

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

private class FakeAlarms(var failSaves: Boolean = false) : WakeAlarmRepository {
    val saved = MutableStateFlow<Map<String, WakeAlarm>>(emptyMap())
    val calls = mutableListOf<String>()

    override fun observeAll(): Flow<List<WakeAlarm>> = saved.map { it.values.sortedBy { a -> a.time } }
    override suspend fun get(id: String): WakeAlarm? = saved.value[id]
    override suspend fun save(alarm: WakeAlarm): EmptyResult<DataError.Local> {
        calls += "db save ${alarm.id}"
        if (failSaves) return Result.Error(DataError.Local.UNKNOWN)
        saved.value = saved.value + (alarm.id to alarm)
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): EmptyResult<DataError.Local> {
        calls += "db delete $id"
        saved.value = saved.value - id
        return Result.Success(Unit)
    }
}

/** Records calls in the same list as [FakeAlarms], so the order can be checked. */
private class FakeEngine(private val calls: MutableList<String>, var error: ScheduleError? = null) : AlarmScheduler, AlarmStore {
    val stored = mutableMapOf<String, Alarm>()

    override suspend fun schedule(alarm: Alarm): Result<Instant?, ScheduleError> {
        calls += "engine schedule ${alarm.id}"
        error?.let { return Result.Error(it) }
        stored[alarm.id] = alarm
        return Result.Success(NextRingTime.after(alarm, FixedClock.now(), TimeZone.UTC))
    }
    override suspend fun cancel(alarmId: String) {
        calls += "engine cancel $alarmId"
        stored.remove(alarmId)
    }
    override suspend fun onAlarmRang(alarmId: String) = Unit
    override suspend fun rescheduleAll(reason: String) = Unit
    override suspend fun scheduleFollowUp(followUpId: String, at: Instant, recheckWhileInUseUntil: Instant?) =
        Result.Success(at)
    override suspend fun cancelFollowUp(followUpId: String, reason: String) = Unit

    override suspend fun alarms(): List<Alarm> = stored.values.toList()
    override suspend fun alarm(id: String): Alarm? = stored[id]
    override suspend fun save(alarm: Alarm) { stored[alarm.id] = alarm }
    override suspend fun delete(id: String) { stored.remove(id) }
    override suspend fun events(): List<RingEvent> = emptyList()
    override suspend fun record(event: RingEvent) = Unit
    override suspend fun clearEvents() = Unit
}

private object FixedClock : Clock {
    // Tuesday 3 Nov 2026, 06:00 UTC
    override fun now(): Instant = Instant.parse("2026-11-03T06:00:00Z")
}

class AlarmServiceTest {

    private val db = FakeAlarms()
    private val engine = FakeEngine(db.calls)
    private val service = AlarmService(db, scheduler = engine, engine = engine, clock = FixedClock, timeZone = { TimeZone.UTC })

    private val weekdays = WakeAlarm(
        id = "a",
        time = LocalTime(7, 0),
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        deckId = "es",
        mode = WakeMode.HEAVY_SLEEPER,
    )

    @Test
    fun savingTellsTheEngineFirst() = runTest {
        val result = service.save(weekdays)

        assertThat(db.calls).containsExactly("engine schedule a", "db save a")
        assertThat((result as Result.Success).data).isEqualTo(Instant.parse("2026-11-03T07:00:00Z"))
        assertThat(engine.stored["a"]?.time).isEqualTo(LocalTime(7, 0))
    }

    @Test
    fun anAlarmThePhoneRefusesIsNotSaved() = runTest {
        engine.error = ScheduleError.EXACT_ALARMS_NOT_ALLOWED

        val result = service.save(weekdays)

        assertThat(result).isEqualTo(Result.Error(AlarmError.EXACT_ALARMS_NOT_ALLOWED))
        assertThat(db.saved.value.isEmpty()).isTrue()
    }

    @Test
    fun aFailedDatabaseWriteStillRings() = runTest {
        db.failSaves = true

        val result = service.save(weekdays)

        assertThat(result).isEqualTo(Result.Error(AlarmError.NOT_SAVED))
        assertThat(engine.stored["a"]).isNotNull()
    }

    @Test
    fun deletingStopsTheRingFirst() = runTest {
        service.save(weekdays)
        db.calls.clear()

        service.delete("a")

        assertThat(db.calls).containsExactly("engine cancel a", "db delete a")
    }

    @Test
    fun syncShowsARungOneOffAlarmAsOff() = runTest {
        val once = weekdays.copy(id = "once", repeatDays = emptySet())
        service.save(once)
        // It rang: the engine switched it off on its own.
        engine.stored["once"] = engine.stored.getValue("once").copy(enabled = false)

        service.syncWithEngine()

        assertThat(db.saved.value.getValue("once").enabled).isFalse()
        assertThat(db.saved.value.getValue("once").deckId).isEqualTo("es")
    }

    @Test
    fun syncAddsAlarmsTheAppLostAndReschedulesOnesTheEngineLost() = runTest {
        engine.stored["orphan"] = Alarm(id = "orphan", time = LocalTime(6, 30), label = "Test")
        db.saved.value = mapOf("lost" to weekdays.copy(id = "lost"))

        service.syncWithEngine()

        assertThat(db.saved.value["orphan"]?.time).isEqualTo(LocalTime(6, 30))
        assertThat(engine.stored["lost"]).isNotNull()
    }

    @Test
    fun nextIsTheSoonestAlarmThatIsOn() {
        val later = weekdays.copy(id = "later", time = LocalTime(9, 0))
        val off = weekdays.copy(id = "off", time = LocalTime(6, 30), enabled = false)

        val next = service.next(listOf(later, weekdays, off))

        assertThat(next?.alarm?.id).isEqualTo("a")
        assertThat(service.next(listOf(off))).isNull()
    }

    @Test
    fun turningAnAlarmOffKeepsItsSettings() = runTest {
        service.save(weekdays)

        val result = service.setEnabled(weekdays, enabled = false)

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(db.saved.value.getValue("a").enabled).isFalse()
        assertThat(db.saved.value.getValue("a").mode).isEqualTo(WakeMode.HEAVY_SLEEPER)
    }
}
