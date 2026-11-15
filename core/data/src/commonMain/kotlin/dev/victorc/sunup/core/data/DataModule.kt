package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.wakeup.WakeAlarmRepository
import kotlin.time.Clock
import org.koin.dsl.module

/** Needs the DAOs from the platform's database module. */
val dataModule = module {
    single<Clock> { Clock.System }
    single<DeckRepository> { RoomDeckRepository(dao = get(), notes = get(), clock = get()) }
    single<NoteRepository> { RoomNoteRepository(notes = get(), states = get(), clock = get()) }
    single<ReviewRepository> { RoomReviewRepository(states = get(), log = get(), clock = get()) }
    single<WakeAlarmRepository> { RoomWakeAlarmRepository(dao = get(), clock = get()) }
    single { SampleDeckSeeder(decks = get(), notes = get()) }
    single { GeoDeckSeeder(decks = get(), notes = get()) }
}
