package dev.victorc.sunup.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.victorc.sunup.core.database.entity.AlarmEntity
import dev.victorc.sunup.core.database.entity.DeckEntity
import dev.victorc.sunup.core.database.entity.NoteEntity
import dev.victorc.sunup.core.database.entity.ReviewLogEntity
import dev.victorc.sunup.core.database.entity.ReviewStateEntity
import dev.victorc.sunup.core.database.entity.WakeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Upsert
    suspend fun upsert(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): DeckEntity?

    @Query("UPDATE decks SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface NoteDao {
    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE deckId = :deckId AND deletedAt IS NULL ORDER BY createdAt")
    fun observeInDeck(deckId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deckId = :deckId AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun getInDeck(deckId: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes WHERE deckId = :deckId AND deletedAt IS NULL")
    suspend fun countInDeck(deckId: String): Int

    @Query("SELECT deckId, COUNT(*) AS cards FROM notes WHERE deletedAt IS NULL GROUP BY deckId")
    fun observeCountsPerDeck(): Flow<List<DeckCardCount>>

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE deckId = :deckId AND deletedAt IS NULL")
    suspend fun softDeleteInDeck(deckId: String, now: Long)
}

/** How many cards a deck has, for the deck list. */
data class DeckCardCount(val deckId: String, val cards: Int)

@Dao
interface ReviewStateDao {
    @Upsert
    suspend fun upsert(state: ReviewStateEntity)

    @Upsert
    suspend fun upsertAll(states: List<ReviewStateEntity>)

    @Query("SELECT * FROM review_states WHERE noteId = :noteId AND direction = :direction AND deletedAt IS NULL")
    suspend fun get(noteId: String, direction: String): ReviewStateEntity?

    /** Cards due today or earlier, most overdue first. */
    @Query(
        """
        SELECT rs.* FROM review_states rs
        INNER JOIN notes n ON n.id = rs.noteId
        WHERE n.deckId = :deckId AND n.deletedAt IS NULL AND rs.deletedAt IS NULL
          AND rs.dueEpochDay IS NOT NULL AND rs.dueEpochDay <= :todayEpochDay
        ORDER BY rs.dueEpochDay, rs.step
        """,
    )
    suspend fun dueInDeck(deckId: String, todayEpochDay: Long): List<ReviewStateEntity>

    /** Cards never reviewed, oldest note first. */
    @Query(
        """
        SELECT rs.* FROM review_states rs
        INNER JOIN notes n ON n.id = rs.noteId
        WHERE n.deckId = :deckId AND n.deletedAt IS NULL AND rs.deletedAt IS NULL
          AND rs.dueEpochDay IS NULL
        ORDER BY n.createdAt, rs.direction
        LIMIT :limit
        """,
    )
    suspend fun newInDeck(deckId: String, limit: Int): List<ReviewStateEntity>

    @Query(
        """
        SELECT rs.* FROM review_states rs
        INNER JOIN notes n ON n.id = rs.noteId
        WHERE n.deckId = :deckId AND n.deletedAt IS NULL AND rs.deletedAt IS NULL
        """,
    )
    fun observeInDeck(deckId: String): Flow<List<ReviewStateEntity>>
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entry: ReviewLogEntity)

    @Query("SELECT * FROM review_log WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY reviewedAt")
    suspend fun forNote(noteId: String): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE reviewedAt >= :from AND reviewedAt < :until AND deletedAt IS NULL ORDER BY reviewedAt")
    suspend fun between(from: Long, until: Long): List<ReviewLogEntity>

    @Query("SELECT * FROM review_log WHERE deletedAt IS NULL ORDER BY reviewedAt")
    fun observeAll(): Flow<List<ReviewLogEntity>>
}

@Dao
interface AlarmDao {
    @Upsert
    suspend fun upsert(alarm: AlarmEntity)

    @Query("SELECT * FROM alarms WHERE deletedAt IS NULL ORDER BY minuteOfDay")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): AlarmEntity?

    @Query("UPDATE alarms SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface WakeSessionDao {
    @Upsert
    suspend fun upsert(session: WakeSessionEntity)

    @Query("SELECT * FROM wake_sessions WHERE deletedAt IS NULL ORDER BY rangAt DESC")
    fun observeAll(): Flow<List<WakeSessionEntity>>
}
