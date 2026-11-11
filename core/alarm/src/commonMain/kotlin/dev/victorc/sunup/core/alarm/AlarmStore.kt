package dev.victorc.sunup.core.alarm

/**
 * The engine's own copy of every alarm, plus the ring log.
 *
 * On Android it lives in device-protected storage, so it can be read right after a reboot,
 * before the phone is unlocked. The app's database (with decks, modes and so on) is the
 * source of truth for the UI and keeps this copy up to date.
 */
interface AlarmStore {
    suspend fun alarms(): List<Alarm>
    suspend fun alarm(id: String): Alarm?
    suspend fun save(alarm: Alarm)
    suspend fun delete(id: String)

    /** Oldest first. Capped, so old events drop off. */
    suspend fun events(): List<RingEvent>
    suspend fun record(event: RingEvent)
    suspend fun clearEvents()
}
