package dev.victorc.sunup.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Every table carries updatedAt and deletedAt (epoch millis) so sync can be added in v1.1
// without a migration. Rows are never hard-deleted; deletedAt marks them gone.

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val targetLanguage: String?,
    val nativeLanguage: String?,
    val bothDirections: Boolean,
    val newCardsPerDay: Int,
    /** Comma-separated Game names. */
    val allowedGames: String,
    val examEpochDay: Long?,
    val builtInPackId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "notes", indices = [Index("deckId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val type: String,
    val front: String,
    val back: String,
    /** JSON array of strings. */
    val acceptedAnswers: String,
    val example: String?,
    val exampleTranslation: String?,
    val hint: String?,
    val explanation: String?,
    /** JSON array of strings. */
    val wrongAnswers: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "review_states",
    primaryKeys = ["noteId", "direction"],
    indices = [Index("dueEpochDay")],
)
data class ReviewStateEntity(
    val noteId: String,
    val direction: String,
    val step: Int,
    val level: String,
    /** Null until the first review. */
    val dueEpochDay: Long?,
    val reviews: Int,
    val lapses: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "review_log", indices = [Index("noteId"), Index("reviewedAt")])
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val direction: String,
    val game: String,
    val grade: String,
    val context: String,
    val reviewedAt: Long,
    val durationMillis: Long?,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val minuteOfDay: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 = ring once. */
    val repeatDays: Int,
    val enabled: Boolean,
    val label: String,
    val deckId: String?,
    val mode: String,
    val preAlarm: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
