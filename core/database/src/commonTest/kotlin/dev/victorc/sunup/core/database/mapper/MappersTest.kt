package dev.victorc.sunup.core.database.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.MasteryLevel
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteType
import dev.victorc.sunup.core.domain.learning.ReviewContext
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class MappersTest {

    @Test
    fun deckRoundTrips() {
        val deck = Deck(
            id = "d",
            name = "Biology",
            kind = DeckKind.STUDY,
            newCardsPerDay = 5,
            allowedGames = setOf(Game.FLIP_AND_RATE, Game.PICK_MEANING),
            examDate = LocalDate(2027, 1, 15),
        )

        assertThat(deck.toEntity(createdAt = 1, updatedAt = 2).toDomain()).isEqualTo(deck)
    }

    @Test
    fun unknownGameNamesAreDroppedNotCrashed() {
        val entity = Deck(id = "d", name = "x", kind = DeckKind.TRIVIA)
            .toEntity(1, 1)
            .copy(allowedGames = "PICK_MEANING,SOME_FUTURE_GAME")

        assertThat(entity.toDomain().allowedGames).isEqualTo(setOf(Game.PICK_MEANING))
    }

    @Test
    fun noteWithListsRoundTrips() {
        val note = Note(
            id = "n",
            deckId = "d",
            type = NoteType.WORD,
            front = "despertarse",
            back = "to wake up",
            acceptedAnswers = listOf("wake up", "get up"),
            example = "Me despierto a las siete.",
            exampleTranslation = "I wake up at seven.",
            hint = "reflexive",
            wrongAnswers = listOf("to sleep, \"badly\""),
        )

        assertThat(note.toEntity(1, 2).toDomain()).isEqualTo(note)
    }

    @Test
    fun reviewStateRoundTripsIncludingNew() {
        val learned = ReviewState(
            noteId = "n",
            direction = Direction.REVERSE,
            step = 2,
            level = MasteryLevel.RECALL,
            due = LocalDate(2026, 11, 11),
            reviews = 4,
            lapses = 1,
        )
        val new = ReviewState(noteId = "n", direction = Direction.FORWARD)

        assertThat(learned.toEntity(1).toDomain()).isEqualTo(learned)
        assertThat(new.toEntity(1).toDomain()).isEqualTo(new)
    }

    @Test
    fun reviewRecordRoundTrips() {
        val record = ReviewRecord(
            id = "r",
            noteId = "n",
            direction = Direction.FORWARD,
            game = Game.TYPE_IT,
            grade = Grade.HARD,
            context = ReviewContext.SILENCING,
            reviewedAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            duration = 4.seconds,
        )

        assertThat(record.toEntity().toDomain()).isEqualTo(record)
    }

    @Test
    fun alarmDaysRoundTripThroughTheBitmask() {
        val alarm = WakeAlarm(
            id = "a",
            time = LocalTime(6, 45),
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            deckId = "d",
            mode = WakeMode.HEAVY_SLEEPER,
            preAlarm = true,
        )

        val entity = alarm.toEntity(1, 1)

        assertThat(entity.repeatDays).isEqualTo(0b1000101)
        assertThat(entity.toDomain()).isEqualTo(alarm)
    }
}
