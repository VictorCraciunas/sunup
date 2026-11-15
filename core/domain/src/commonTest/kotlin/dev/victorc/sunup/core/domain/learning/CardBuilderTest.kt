package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
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

    // Geography notes as GeoDeckSeeder writes them: ids carry the size rank.
    private val countries = listOf("RU" to "Russia", "UA" to "Ukraine", "FR" to "France", "ES" to "Spain",
        "SE" to "Sweden", "NO" to "Norway", "DE" to "Germany", "FI" to "Finland", "PL" to "Poland",
        "IT" to "Italy", "GB" to "United Kingdom", "RO" to "Romania", "LU" to "Luxembourg", "MT" to "Malta")
        .mapIndexed { i, (code, name) ->
            Note(id = "geo-${i.toString().padStart(3, '0')}-$code", deckId = "g", type = NoteType.QUESTION, front = name, back = code)
        }

    @Test
    fun nameTheCountryOffersFourCountriesOfASimilarSize() {
        val luxembourg = countries.first { it.back == "LU" }

        val card = builder.nameOnMap(luxembourg, countries, GeoRegion.EUROPE)!!

        assertThat(card.countryId).isEqualTo("LU")
        assertThat(card.options).hasSize(4)
        assertThat(card.options.distinct()).hasSize(4)
        assertThat(card.options[card.correctIndex]).isEqualTo("Luxembourg")
        assertThat(card.optionIds[card.correctIndex]).isEqualTo("LU")
        // Never Russia next to Luxembourg: the options come from the 8 closest in size.
        assertThat(card.options).doesNotContain("Russia")
    }

    @Test
    fun nameTheCountryOffersNeighboursFirst() {
        val germany = countries.first { it.back == "DE" }
            .copy(wrongAnswers = listOf("France", "Poland", "Luxembourg", "Italy", "Spain"))

        val card = builder.nameOnMap(germany, countries, GeoRegion.EUROPE)!!

        val wrong = card.options.filterIndexed { index, _ -> index != card.correctIndex }
        // Three of the four closest neighbours, never Spain (fifth) or a far-off country.
        assertThat(setOf("France", "Poland", "Luxembourg", "Italy").containsAll(wrong)).isTrue()
        assertThat(card.optionIds[card.options.indexOf(wrong.first())]).isEqualTo(
            countries.first { it.front == wrong.first() }.back,
        )
    }

    @Test
    fun nameTheCountryFillsInWhenNeighboursAreMissing() {
        // Only one neighbour is in this deck; the other two come from similar sizes.
        val malta = countries.first { it.back == "MT" }.copy(wrongAnswers = listOf("Italy", "Libya", "Tunisia"))

        val card = builder.nameOnMap(malta, countries, GeoRegion.EUROPE)!!

        assertThat(card.options).hasSize(4)
        assertThat(card.options).contains("Italy")
    }

    @Test
    fun nameTheCountryNeedsFourCountries() {
        assertThat(builder.nameOnMap(countries[0], countries.take(3), GeoRegion.EUROPE)).isNull()
    }

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
    fun fillTheGapBlanksTheWordWithItsArticle() {
        val withExample = sol.copy(example = "El sol sale a las siete.", exampleTranslation = "The sun rises at seven.")

        val card = builder.fillTheGap(withExample, deck)!!

        assertThat(card.sentence).isEqualTo("${SentenceGap.BLANK} sale a las siete.")
        assertThat(card.options[card.correctIndex]).isEqualTo("El sol")
        assertThat(card.options).hasSize(4)
        // Wrong options keep their article too, so the article doesn't give the answer away.
        assertThat(card.options.filterIndexed { i, _ -> i != card.correctIndex }.all { it.contains(" ") }).isTrue()
        assertThat(card.translation).isEqualTo("The sun rises at seven.")
    }

    @Test
    fun fillTheGapDropsTheArticleWhenTheSentenceUsesAnother() {
        val cafe = word("8", "el café", "coffee").copy(example = "Necesito un café.")

        val card = builder.fillTheGap(cafe, deck)!!

        assertThat(card.sentence).isEqualTo("Necesito un ${SentenceGap.BLANK}.")
        assertThat(card.options[card.correctIndex]).isEqualTo("café")
        assertThat(card.options.none { it.startsWith("el ") || it.startsWith("la ") }).isTrue()
    }

    @Test
    fun fillTheGapNeedsTheWholeWordInTheSentence() {
        val conjugated = word("8", "despertarse", "to wake up").copy(example = "Me despierto temprano.")
        val partOfAnotherWord = word("9", "sol", "sun").copy(example = "Hace un día soleado.")

        assertThat(builder.fillTheGap(conjugated, deck)).isNull()
        assertThat(builder.fillTheGap(partOfAnotherWord, deck)).isNull()
    }

    @Test
    fun typeItAcceptsEveryAnswerAndKnowsTheAnswerLanguage() {
        val note = sol.copy(acceptedAnswers = listOf("sun"))

        val card = builder.typeIt(note, Direction.FORWARD, answerLanguage = "en")

        assertThat(card.answers).isEqualTo(listOf("the sun", "sun"))
        assertThat(card.language).isEqualTo("en")
    }

    @Test
    fun matchPairsKeepsEachAnswerWithItsPrompt() {
        val card = builder.matchPairs(deck.take(4), Direction.FORWARD)!!

        assertThat(card.prompts).isEqualTo(listOf("el sol", "la luna", "la nube", "la lluvia"))
        assertThat(card.answers).isEqualTo(listOf("the sun", "the moon", "a cloud", "rain"))
        assertThat(card.answerOrder.sorted()).isEqualTo(listOf(0, 1, 2, 3))
    }

    @Test
    fun matchPairsNeedsFourDistinctWords() {
        assertThat(builder.matchPairs(deck.take(3), Direction.FORWARD)).isNull()
        val duplicate = deck.take(3) + word("9", "el astro", "The Sun")
        assertThat(builder.matchPairs(duplicate, Direction.FORWARD)).isNull()
    }

    @Test
    fun trueOrFalseNeedsAtLeastOneOtherNote() {
        assertThat(builder.trueFalse(sol, Direction.FORWARD, listOf(sol))).isNull()
        assertThat(builder.trueFalse(sol, Direction.FORWARD, deck.take(2))).isNotNull()
    }
}
