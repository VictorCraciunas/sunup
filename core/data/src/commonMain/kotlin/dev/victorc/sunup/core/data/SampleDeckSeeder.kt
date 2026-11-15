package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.NoteType

/**
 * A small Spanish deck with a card of every game, for trying the morning flow from the
 * test bench. Loading it again updates the cards and keeps what was answered.
 */
class SampleDeckSeeder(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
) {

    suspend fun seedSpanish(): Deck {
        val deck = Deck(
            id = DECK_ID,
            name = "Spanish · Morning words",
            kind = DeckKind.LANGUAGE,
            builtInPackId = PACK_ID,
        )
        decks.saveDeck(deck)
        cards().forEach { notes.saveNote(it, bothDirections = false) }
        return deck
    }

    private fun cards(): List<Note> {
        var index = 0
        fun card(
            game: Game,
            front: String,
            back: String = "",
            wrong: List<String> = emptyList(),
            also: List<String> = emptyList(),
            translation: String? = null,
            isTrue: Boolean? = null,
            why: String? = null,
            pairs: List<Pair<String, String>> = emptyList(),
        ) = Note(
            id = "$DECK_ID-${index++}",
            deckId = DECK_ID,
            type = NoteType.WORD,
            front = front,
            back = back,
            wrongAnswers = wrong,
            acceptedAnswers = also,
            exampleTranslation = translation,
            isTrue = isTrue,
            explanation = why,
            pairs = pairs,
            game = game,
        )

        return listOf(
            card(Game.PICK_MEANING, "el sol", "the sun"),
            card(Game.PICK_MEANING, "la luna", "the moon"),
            card(Game.PICK_MEANING, "el desayuno", "breakfast"),
            card(Game.PICK_MEANING, "el amanecer", "sunrise", wrong = listOf("sunset", "noon", "midnight")),
            card(Game.PICK_MEANING, "la cama", "the bed"),
            card(Game.TRUE_FALSE, "“La luna” means “the sun”.", isTrue = false, why = "It means the moon."),
            card(Game.TRUE_FALSE, "“El café” means coffee.", isTrue = true),
            card(Game.TYPE_IT, "to wake up", "despertarse", also = listOf("despertar")),
            card(Game.TYPE_IT, "early", "temprano"),
            card(Game.FILL_THE_GAP, "Me despierto con el [amanecer].", translation = "I wake up with the sunrise."),
            card(Game.FILL_THE_GAP, "Necesito un [café].", translation = "I need a coffee.", wrong = listOf("té", "zumo", "agua")),
            card(
                Game.MATCH_PAIRS,
                front = "",
                pairs = listOf("el sol" to "the sun", "la luna" to "the moon", "el cielo" to "the sky", "el mar" to "the sea"),
            ),
        )
    }

    companion object {
        const val DECK_ID = "sample-spanish"

        /** Marks it as Sunup's, not the user's: it won't count toward the free deck limit. */
        const val PACK_ID = "sample"
    }
}
