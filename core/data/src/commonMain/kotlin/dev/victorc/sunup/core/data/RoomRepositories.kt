package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.database.dao.AlarmDao
import dev.victorc.sunup.core.database.dao.DeckDao
import dev.victorc.sunup.core.database.dao.NoteDao
import dev.victorc.sunup.core.database.dao.ReviewLogDao
import dev.victorc.sunup.core.database.dao.ReviewStateDao
import dev.victorc.sunup.core.database.dao.WakeSessionDao
import dev.victorc.sunup.core.database.mapper.toDomain
import dev.victorc.sunup.core.database.mapper.toEntity
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import dev.victorc.sunup.core.domain.wakeup.WakeSession
import dev.victorc.sunup.core.domain.wakeup.WakeSessionRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomDeckRepository(
    private val dao: DeckDao,
    private val notes: NoteDao,
    private val clock: Clock,
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        dao.observeAll().map { decks -> decks.map { it.toDomain() } }

    override suspend fun getDeck(id: String): Deck? = dao.get(id)?.toDomain()

    override suspend fun saveDeck(deck: Deck): EmptyResult<DataError.Local> = safeLocal {
        val now = clock.now().toEpochMilliseconds()
        val createdAt = dao.get(deck.id)?.createdAt ?: now
        dao.upsert(deck.toEntity(createdAt = createdAt, updatedAt = now))
    }

    override suspend fun deleteDeck(id: String): EmptyResult<DataError.Local> = safeLocal {
        val now = clock.now().toEpochMilliseconds()
        notes.softDeleteInDeck(id, now)
        dao.softDelete(id, now)
    }
}

class RoomNoteRepository(
    private val notes: NoteDao,
    private val states: ReviewStateDao,
    private val clock: Clock,
) : NoteRepository {

    override suspend fun notesInDeck(deckId: String): List<Note> =
        notes.getInDeck(deckId).map { it.toDomain() }

    override fun observeNotesInDeck(deckId: String): Flow<List<Note>> =
        notes.observeInDeck(deckId).map { list -> list.map { it.toDomain() } }

    override suspend fun getNote(id: String): Note? = notes.get(id)?.toDomain()

    override suspend fun deleteNote(id: String): EmptyResult<DataError.Local> = safeLocal {
        notes.softDelete(id, clock.now().toEpochMilliseconds())
    }

    override fun observeCardCounts(): Flow<Map<String, Int>> =
        notes.observeCountsPerDeck().map { counts -> counts.associate { it.deckId to it.cards } }

    override suspend fun saveNote(note: Note, bothDirections: Boolean): EmptyResult<DataError.Local> = safeLocal {
        val now = clock.now().toEpochMilliseconds()
        val createdAt = notes.get(note.id)?.createdAt ?: now
        notes.upsert(note.toEntity(createdAt = createdAt, updatedAt = now))

        val directions = if (bothDirections) Direction.entries else listOf(Direction.FORWARD)
        for (direction in directions) {
            if (states.get(note.id, direction.name) == null) {
                states.upsert(ReviewState(noteId = note.id, direction = direction).toEntity(updatedAt = now))
            }
        }
    }
}

class RoomReviewRepository(
    private val states: ReviewStateDao,
    private val log: ReviewLogDao,
    private val clock: Clock,
) : ReviewRepository {

    override suspend fun statesInDeck(deckId: String): List<ReviewState> =
        states.observeInDeck(deckId).first().map { it.toDomain() }

    override suspend fun dueCards(deckId: String, today: LocalDate): List<ReviewState> =
        states.dueInDeck(deckId, today.toEpochDays()).map { it.toDomain() }

    override suspend fun saveStates(states: List<ReviewState>): EmptyResult<DataError.Local> = safeLocal {
        val now = clock.now().toEpochMilliseconds()
        this.states.upsertAll(states.map { it.toEntity(updatedAt = now) })
    }

    override suspend fun recordReview(record: ReviewRecord, newState: ReviewState): EmptyResult<DataError.Local> =
        safeLocal {
            log.insert(record.toEntity())
            states.upsert(newState.toEntity(updatedAt = clock.now().toEpochMilliseconds()))
        }

    override fun observeAllReviews(): Flow<List<ReviewRecord>> = log.observeAll().map { list -> list.map { it.toDomain() } }
}

class RoomWakeAlarmRepository(
    private val dao: AlarmDao,
    private val clock: Clock,
) : WakeAlarmRepository {

    override fun observeAll(): Flow<List<WakeAlarm>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): WakeAlarm? = dao.get(id)?.toDomain()

    override suspend fun delete(id: String): EmptyResult<DataError.Local> = safeLocal {
        dao.softDelete(id, now = clock.now().toEpochMilliseconds())
    }

    override suspend fun save(alarm: WakeAlarm): EmptyResult<DataError.Local> = safeLocal {
        val now = clock.now().toEpochMilliseconds()
        val createdAt = dao.get(alarm.id)?.createdAt ?: now
        dao.upsert(alarm.toEntity(createdAt = createdAt, updatedAt = now))
    }
}

class RoomWakeSessionRepository(
    private val dao: WakeSessionDao,
    private val clock: Clock,
) : WakeSessionRepository {

    override fun observeAll(): Flow<List<WakeSession>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(session: WakeSession): EmptyResult<DataError.Local> = safeLocal {
        dao.upsert(session.toEntity(updatedAt = clock.now().toEpochMilliseconds()))
    }
}
