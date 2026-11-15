package dev.victorc.sunup.core.database

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.victorc.sunup.core.database.dao.AlarmDao
import dev.victorc.sunup.core.database.dao.DeckDao
import dev.victorc.sunup.core.database.dao.NoteDao
import dev.victorc.sunup.core.database.dao.ReviewLogDao
import dev.victorc.sunup.core.database.dao.ReviewStateDao
import dev.victorc.sunup.core.database.dao.WakeSessionDao
import dev.victorc.sunup.core.database.entity.AlarmEntity
import dev.victorc.sunup.core.database.entity.DeckEntity
import dev.victorc.sunup.core.database.entity.NoteEntity
import dev.victorc.sunup.core.database.entity.ReviewLogEntity
import dev.victorc.sunup.core.database.entity.ReviewStateEntity
import dev.victorc.sunup.core.database.entity.WakeSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        DeckEntity::class,
        NoteEntity::class,
        ReviewStateEntity::class,
        ReviewLogEntity::class,
        AlarmEntity::class,
        WakeSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // 2: cards have one game each (game, isTrue, pairs on notes).
        AutoMigration(from = 1, to = 2),
        // 3: one row per morning (wake_sessions), for streaks and Home.
        AutoMigration(from = 2, to = 3),
    ],
)
@ConstructedBy(SunupDatabaseConstructor::class)
abstract class SunupDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun noteDao(): NoteDao
    abstract fun reviewStateDao(): ReviewStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun alarmDao(): AlarmDao
    abstract fun wakeSessionDao(): WakeSessionDao

    companion object {
        const val FILE_NAME = "sunup.db"
    }
}

// Room's compiler generates the actual implementations for each platform.
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object SunupDatabaseConstructor : RoomDatabaseConstructor<SunupDatabase> {
    override fun initialize(): SunupDatabase
}

/** Same SQLite on every platform, queries off the main thread. */
fun buildSunupDatabase(builder: RoomDatabase.Builder<SunupDatabase>): SunupDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
