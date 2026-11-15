package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.MasteryLevel
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.NoteType
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.ReviewState
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A small Spanish deck for trying the morning flow before deck creation exists (M4).
 * Half the words start as "already learned" so the alarm can be stopped with a real card.
 */
class SampleDeckSeeder(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
    private val reviews: ReviewRepository,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    suspend fun seedSpanish(): Deck {
        val deck = Deck(
            id = DECK_ID,
            name = "Spanish · Morning words",
            kind = DeckKind.LANGUAGE,
            targetLanguage = "es",
            nativeLanguage = "en",
        )
        decks.saveDeck(deck)

        val words = WORDS.mapIndexed { index, (front, back, example) ->
            Note(
                id = "$DECK_ID-$index",
                deckId = DECK_ID,
                type = NoteType.WORD,
                front = front,
                back = back,
                example = example,
            )
        }
        words.forEach { notes.saveNote(it, bothDirections = false) }

        val today = clock.now().toLocalDateTime(timeZone()).date
        // Half at Match pairs level, half at Type it level, so the bonus round shows both games.
        val learned = words.take(KNOWN_COUNT).mapIndexed { index, note ->
            val recall = index >= KNOWN_COUNT / 2
            ReviewState(
                noteId = note.id,
                direction = Direction.FORWARD,
                step = if (recall) 3 else 2,
                level = if (recall) MasteryLevel.RECALL else MasteryLevel.MATCH,
                due = today,
                reviews = 3,
            )
        }
        reviews.saveStates(learned)
        return deck
    }

    companion object {
        const val DECK_ID = "sample-spanish"
        private const val KNOWN_COUNT = 6

        private val WORDS = listOf(
            Triple("el sol", "the sun", "El sol sale a las siete."),
            Triple("la luna", "the moon", "La luna está muy brillante."),
            Triple("el desayuno", "breakfast", "El desayuno está listo."),
            Triple("despertarse", "to wake up", "Me despierto temprano."),
            Triple("la mañana", "the morning", "Buenos días, es la mañana."),
            Triple("el café", "coffee", "Necesito un café."),
            Triple("el amanecer", "sunrise", "Me despierto con el amanecer."),
            Triple("la ducha", "the shower", "La ducha está fría."),
            Triple("la cama", "the bed", "No quiero salir de la cama."),
            Triple("el reloj", "the clock", "El reloj suena a las siete."),
            Triple("levantarse", "to get up", "Me levanto ahora."),
            Triple("temprano", "early", "Hoy me despierto temprano."),
        )
    }
}
