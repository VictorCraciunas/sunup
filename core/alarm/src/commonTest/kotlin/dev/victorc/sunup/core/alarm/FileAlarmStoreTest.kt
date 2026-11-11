package dev.victorc.sunup.core.alarm

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

private class InMemoryStateFile(var text: String? = null) : StateFile {
    override fun read(): String? = text
    override fun write(text: String) {
        this.text = text
    }
}

class FileAlarmStoreTest {

    private val weekdayAlarm = Alarm(
        id = "morning",
        time = LocalTime(7, 0),
        repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        label = "Spanish",
    )

    @Test
    fun savedAlarmSurvivesARestart() = runTest {
        val file = InMemoryStateFile()
        FileAlarmStore(file).save(weekdayAlarm)

        val reopened = FileAlarmStore(file)

        assertThat(reopened.alarms()).containsExactly(weekdayAlarm)
    }

    @Test
    fun savingTheSameIdReplacesIt() = runTest {
        val store = FileAlarmStore(InMemoryStateFile())
        store.save(weekdayAlarm)

        store.save(weekdayAlarm.copy(time = LocalTime(6, 30)))

        assertThat(store.alarms()).containsExactly(weekdayAlarm.copy(time = LocalTime(6, 30)))
    }

    @Test
    fun deleteRemovesTheAlarm() = runTest {
        val store = FileAlarmStore(InMemoryStateFile())
        store.save(weekdayAlarm)

        store.delete("morning")

        assertThat(store.alarm("morning")).isNull()
    }

    @Test
    fun eventLogKeepsOnlyTheNewestEvents() = runTest {
        val store = FileAlarmStore(InMemoryStateFile(), maxEvents = 2)

        repeat(3) { i ->
            store.record(RingEvent("morning", RingEvent.Type.RANG, Instant.fromEpochMilliseconds(i * 1000L)))
        }

        assertThat(store.events().map { it.at.toEpochMilliseconds() }).isEqualTo(listOf(1000L, 2000L))
    }

    @Test
    fun ringEventRoundTripsWithItsExpectedTime() = runTest {
        val file = InMemoryStateFile()
        val event = RingEvent(
            alarmId = "morning",
            type = RingEvent.Type.RANG,
            at = Instant.fromEpochMilliseconds(61_000),
            expected = Instant.fromEpochMilliseconds(60_000),
            note = "boot",
        )
        FileAlarmStore(file).record(event)

        val reloaded = FileAlarmStore(file).events().single()

        assertThat(reloaded).isEqualTo(event)
        assertThat(reloaded.delay?.inWholeSeconds).isEqualTo(1L)
    }

    @Test
    fun damagedFileReadsAsEmptyInsteadOfCrashing() = runTest {
        val store = FileAlarmStore(InMemoryStateFile("{ not json"))

        assertThat(store.alarms()).isEmpty()
        assertThat(store.events()).isEmpty()
    }
}
