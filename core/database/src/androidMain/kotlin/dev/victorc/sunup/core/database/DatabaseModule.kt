package dev.victorc.sunup.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun androidDatabaseBuilder(context: Context): RoomDatabase.Builder<SunupDatabase> {
    val appContext = context.applicationContext
    val file = appContext.getDatabasePath(SunupDatabase.FILE_NAME)
    return Room.databaseBuilder<SunupDatabase>(context = appContext, name = file.absolutePath)
}

/**
 * Created lazily on first use. The alarm receivers never ask for it, which matters because
 * this file can't be opened before the phone's first unlock after a reboot.
 */
val databaseModule = module {
    single { buildSunupDatabase(androidDatabaseBuilder(androidContext())) }
    single { get<SunupDatabase>().deckDao() }
    single { get<SunupDatabase>().noteDao() }
    single { get<SunupDatabase>().reviewStateDao() }
    single { get<SunupDatabase>().reviewLogDao() }
    single { get<SunupDatabase>().alarmDao() }
}
