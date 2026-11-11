package dev.victorc.sunup.core.alarm

import co.touchlab.kermit.Logger
import kotlin.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A single text file. Platforms decide where it lives and make [write] atomic. */
interface StateFile {
    /** The file's text, or null if it doesn't exist yet. */
    fun read(): String?
    fun write(text: String)
}

/**
 * [AlarmStore] kept as one small JSON file. Plain files avoid any database setup on the
 * ring path, and a damaged file never crashes the alarm: it reads as empty instead.
 */
class FileAlarmStore(
    private val file: StateFile,
    private val maxEvents: Int = 300,
) : AlarmStore {

    private val mutex = Mutex()
    private var cache: StoredState? = null
    private val log = Logger.withTag("FileAlarmStore")

    override suspend fun alarms(): List<Alarm> = mutex.withLock {
        state().alarms.mapNotNull { it.toAlarm() }
    }

    override suspend fun alarm(id: String): Alarm? = mutex.withLock {
        state().alarms.firstOrNull { it.id == id }?.toAlarm()
    }

    override suspend fun save(alarm: Alarm) = mutex.withLock {
        val stored = alarm.toStored()
        update { state ->
            val others = state.alarms.filterNot { it.id == alarm.id }
            state.copy(alarms = others + stored)
        }
    }

    override suspend fun delete(id: String) = mutex.withLock {
        update { state -> state.copy(alarms = state.alarms.filterNot { it.id == id }) }
    }

    override suspend fun events(): List<RingEvent> = mutex.withLock {
        state().events.mapNotNull { it.toEvent() }
    }

    override suspend fun record(event: RingEvent) = mutex.withLock {
        update { state -> state.copy(events = (state.events + event.toStored()).takeLast(maxEvents)) }
    }

    override suspend fun clearEvents() = mutex.withLock {
        update { state -> state.copy(events = emptyList()) }
    }

    private fun state(): StoredState {
        cache?.let { return it }
        val loaded = try {
            file.read()?.let { json.decodeFromString(StoredState.serializer(), it) } ?: StoredState()
        } catch (e: Exception) {
            log.e(e) { "Alarm store is unreadable; starting empty" }
            StoredState()
        }
        cache = loaded
        return loaded
    }

    private inline fun update(change: (StoredState) -> StoredState) {
        val next = change(state())
        file.write(json.encodeToString(StoredState.serializer(), next))
        cache = next
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

@Serializable
private data class StoredState(
    val version: Int = 1,
    val alarms: List<StoredAlarm> = emptyList(),
    val events: List<StoredEvent> = emptyList(),
)

@Serializable
private data class StoredAlarm(
    val id: String,
    val minuteOfDay: Int,
    /** ISO day numbers, Monday = 1. */
    val repeatDays: List<Int>,
    val enabled: Boolean,
    val label: String = "",
)

@Serializable
private data class StoredEvent(
    val alarmId: String,
    val type: String,
    val atMillis: Long,
    val expectedMillis: Long? = null,
    val note: String = "",
)

private fun Alarm.toStored() = StoredAlarm(
    id = id,
    minuteOfDay = time.hour * 60 + time.minute,
    repeatDays = repeatDays.map { it.isoDayNumber }.sorted(),
    enabled = enabled,
    label = label,
)

private fun StoredAlarm.toAlarm(): Alarm? = runCatching {
    Alarm(
        id = id,
        time = LocalTime(hour = minuteOfDay / 60, minute = minuteOfDay % 60),
        repeatDays = repeatDays.map { DayOfWeek(it) }.toSet(),
        enabled = enabled,
        label = label,
    )
}.getOrNull()

private fun RingEvent.toStored() = StoredEvent(
    alarmId = alarmId,
    type = type.name,
    atMillis = at.toEpochMilliseconds(),
    expectedMillis = expected?.toEpochMilliseconds(),
    note = note,
)

private fun StoredEvent.toEvent(): RingEvent? = runCatching {
    RingEvent(
        alarmId = alarmId,
        type = RingEvent.Type.valueOf(type),
        at = Instant.fromEpochMilliseconds(atMillis),
        expected = expectedMillis?.let { Instant.fromEpochMilliseconds(it) },
        note = note,
    )
}.getOrNull()
