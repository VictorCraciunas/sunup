package dev.victorc.sunup.feature.decks.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import kotlin.test.Test

class DeckImportTest {

    private fun ok(text: String): ImportedDeck = (DeckImport.parse(text) as Result.Success).data

    private val full = """
        {"deck": "World history", "type": "study", "cards": [
          {"game": "pick", "question": "Who painted the Mona Lisa?", "answer": "Leonardo da Vinci",
           "wrong": ["Michelangelo", "Raphael", "Van Gogh"]},
          {"game": "truefalse", "statement": "The Eiffel Tower is in Paris.", "answer": true, "why": "Built in 1889."},
          {"game": "type", "question": "In which year did World War II end?", "answer": "1945", "also": ["nineteen forty-five"]},
          {"game": "gap", "sentence": "The [Romans] built Hadrian's Wall.", "wrong": ["Vikings", "Normans", "Celts"]},
          {"game": "match", "pairs": [["France", "Paris"], ["Italy", "Rome"], ["Japan", "Tokyo"], ["Egypt", "Cairo"]]}
        ]}
    """.trimIndent()

    @Test
    fun readsEveryGame() {
        val deck = ok(full)

        assertThat(deck.name).isEqualTo("World history")
        assertThat(deck.kind).isEqualTo(DeckKind.STUDY)
        assertThat(deck.cards.map { it.game }).containsExactly(
            Game.PICK_MEANING, Game.TRUE_FALSE, Game.TYPE_IT, Game.FILL_THE_GAP, Game.MATCH_PAIRS,
        )
        assertThat(deck.cards[0].wrong).containsExactly("Michelangelo", "Raphael", "Van Gogh")
        assertThat(deck.cards[1].isTrue).isEqualTo(true)
        assertThat(deck.cards[2].alsoRight).isEqualTo("nineteen forty-five")
        assertThat(deck.cards[4].pairs.first()).isEqualTo("France" to "Paris")
        assertThat(deck.skipped).hasSize(0)
    }

    @Test
    fun ignoresTheChatAroundTheJson() {
        val reply = "Sure! Here's your deck:\n```json\n$full\n```\nLet me know if you want more."

        assertThat(ok(reply).cards).hasSize(5)
    }

    @Test
    fun understandsOtherNamesAndGuessesTheGame() {
        val deck = ok(
            """
            {"title": "Capitals", "category": "trivia", "flashcards": [
              {"type": "Multiple choice", "front": "Capital of Spain?", "back": "Madrid", "distractors": "Rome, Lisbon, Paris"},
              {"statement": "Water boils at 100°C at sea level.", "answer": "yes"},
              {"pairs": {"Germany": "Berlin", "Greece": "Athens"}},
              {"game": "Fill in the blank", "sentence": "The ___ is the largest ocean.", "answer": "Pacific"}
            ]}
            """.trimIndent(),
        )

        assertThat(deck.name).isEqualTo("Capitals")
        assertThat(deck.kind).isEqualTo(DeckKind.TRIVIA)
        assertThat(deck.cards.map { it.game }).containsExactly(Game.PICK_MEANING, Game.TRUE_FALSE, Game.MATCH_PAIRS, Game.FILL_THE_GAP)
        assertThat(deck.cards[0].wrong).containsExactly("Rome", "Lisbon", "Paris")
        assertThat(deck.cards[3].question).isEqualTo("The [Pacific] is the largest ocean.")
    }

    @Test
    fun skipsCardsItCantMakeAndSaysWhich() {
        val deck = ok(
            """
            {"deck": "Mixed", "cards": [
              {"game": "pick", "question": "2 + 2?", "answer": "4"},
              {"game": "pick", "question": "No answer here"},
              {"game": "dance", "question": "?"},
              {"game": "gap", "sentence": "No brackets at all."}
            ]}
            """.trimIndent(),
        )

        assertThat(deck.cards).hasSize(1)
        assertThat(deck.skipped).containsExactly(
            SkippedCard(2, SkipReason.INCOMPLETE),
            SkippedCard(3, SkipReason.UNKNOWN_GAME),
            SkippedCard(4, SkipReason.INCOMPLETE),
        )
    }

    @Test
    fun aBareListOfCardsWorksToo() {
        val deck = ok("""[{"game": "type", "question": "Capital of France?", "answer": "Paris"}]""")

        assertThat(deck.name).isEqualTo("Imported deck")
        assertThat(deck.cards).hasSize(1)
    }

    @Test
    fun curlyQuotesFromChatAppsAreFixed() {
        assertThat(ok("{“cards”: [{“game”: “type”, “question”: “Capital of Italy?”, “answer”: “Rome”}]}").cards).hasSize(1)
    }

    @Test
    fun curlyQuotesInsideTheCardsAreKept() {
        val deck = ok("""{"cards": [{"game": "truefalse", "statement": "“Hola” means hello.", "answer": true}]}""")

        assertThat(deck.cards[0].question).isEqualTo("“Hola” means hello.")
    }

    @Test
    fun thePromptsOwnExampleImports() {
        val example = DeckImport.PROMPT.substringAfter("Format:")

        assertThat(ok(example).cards).hasSize(5)
    }

    @Test
    fun saysWhenThereIsNothingToImport() {
        assertThat(DeckImport.parse("I can't help with that.")).isEqualTo(Result.Error(ImportError.NOT_JSON))
        assertThat(DeckImport.parse("""{"deck": "Empty", "cards": []}""")).isEqualTo(Result.Error(ImportError.NO_CARDS))
        assertThat(DeckImport.parse("""{"deck": "Bad", "cards": [{"game": "pick"}]}""")).isEqualTo(Result.Error(ImportError.NO_CARDS))
    }

    @Test
    fun theRightAnswerIsNeverAlsoAWrongOne() {
        val deck = ok("""{"cards": [{"game": "pick", "question": "Q", "answer": "A", "wrong": ["A", "B"]}]}""")

        assertThat(deck.cards[0].wrong.contains("A")).isFalse()
    }
}
