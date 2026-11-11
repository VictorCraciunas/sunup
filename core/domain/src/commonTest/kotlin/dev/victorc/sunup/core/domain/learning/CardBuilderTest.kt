package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.random.Random
import kotlin.test.Test

class CardBuilderTest {

    private val builder = CardBuilder(Random(42))

    private fun word(id: String, front: String, back: String, wrong: List<String> = emptyList()) =
        Note(id = id, deckId = "d", type = NoteType.WORD, front = front, back = back, wrongAnswers = wrong)

    private val sol = word("1", "el sol", "the sun")
    private val deck = listOf(
        sol,
        word("2", "la luna", "the moon"),
        word("3", "la nube", "a cloud"),
        word("4", "la lluvia", "rain"),
        word("5", "el cielo", "the sky"),
    )

    @Test
    fun pickTheMeaningHasFourDifferentOptionsIncludingTheAnswer() {
        val card = builder.pickMeaning(sol, Direction.FORWARD, deck)!!

        assertThat(card.options).hasSize(4)
        assertThat(card.options.distinct()).hasSize(4)
        assertThat(card.options[card.correctIndex]).isEqualTo("the sun")
        assertThat(card.prompt).isEqualTo("el sol")
    }

    @Test
    fun reverseDirectionAsksForTheWord() {
        val card = builder.pickMeaning(sol, Direction.REVERSE, deck)!!

        assertThat(card.prompt).isEqualTo("the sun")
        assertThat(card.options[card.correctIndex]).isEqualTo("el sol")
    }

    @Test
    fun tooFewNotesMeansNoChoiceCard() {
        assertThat(builder.pickMeaning(sol, Direction.FORWARD, deck.take(3))).isNull()
    }

    @Test
    fun usersOwnWrongAnswersComeFirst() {
        val withOwn = sol.copy(wrongAnswers = listOf("the star", "the heat", "the day"))

        val wrong = builder.distractors(withOwn, Direction.FORWARD, deck, count = 3)

        assertThat(wrong!!.toSet()).isEqualTo(setOf("the star", "the heat", "the day"))
    }

    @Test
    fun distractorsNeverIncludeAnAcceptedAnswerOrDuplicates() {
        val tricky = deck + word("6", "el astro rey", "The Sun") + word("7", "la noche", "the moon")

        val wrong = builder.distractors(sol, Direction.FORWARD, tricky, count = 4)!!

        assertThat(wrong.map { it.lowercase() }).doesNotContain("the sun")
        assertThat(wrong.map { it.lowercase() }.distinct()).hasSize(4)
    }

    @Test
    fun trueOrFalseShowsTheRightAnswerOnlyWhenTrue() {
        repeat(20) {
            val card = builder.trueFalse(sol, Direction.FORWARD, deck)!!
            if (card.isTrue) {
                assertThat(card.shownAnswer).isEqualTo("the sun")
            } else {
                assertThat(listOf("the moon", "a cloud", "rain", "the sky")).contains(card.shownAnswer)
            }
        }
    }

    @Test
    fun quickMathOptionsContainTheRightResult() {
        repeat(20) {
            val card = builder.quickMath()
            val (a, op, b) = card.question.split(" ")
            val expected = if (op == "+") a.toInt() + b.toInt() else a.toInt() * b.toInt()

            assertThat(card.options[card.correctIndex]).isEqualTo(expected)
            assertThat(card.options.distinct()).hasSize(4)
        }
    }

    @Test
    fun trueOrFalseNeedsAtLeastOneOtherNote() {
        assertThat(builder.trueFalse(sol, Direction.FORWARD, listOf(sol))).isNull()
        assertThat(builder.trueFalse(sol, Direction.FORWARD, deck.take(2))).isNotNull()
    }
}
