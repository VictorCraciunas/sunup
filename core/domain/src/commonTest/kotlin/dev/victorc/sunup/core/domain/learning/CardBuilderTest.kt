package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.random.Random
import kotlin.test.Test

class CardBuilderTest {

    private val builder = CardBuilder(Random(42))

    private fun pick(id: String, front: String, back: String, wrong: List<String> = emptyList()) =
        Note(id = id, deckId = "d", type = NoteType.WORD, front = front, back = back, wrongAnswers = wrong, game = Game.PICK_MEANING)

    private val sol = pick("1", "el sol", "the sun")
    private val deck = listOf(
        sol,
        pick("2", "la luna", "the moon"),
        pick("3", "la nube", "a cloud"),
        pick("4", "la lluvia", "rain"),
        pick("5", "el cielo", "the sky"),
    )

    // Pick from 4

    @Test
    fun pickHasFourDifferentOptionsIncludingTheAnswer() {
        val card = builder.pickMeaning(sol, Direction.FORWARD, deck)!!

        assertThat(card.options).hasSize(4)
        assertThat(card.options.distinct()).hasSize(4)
        assertThat(card.options[card.correctIndex]).isEqualTo("the sun")
        assertThat(card.prompt).isEqualTo("el sol")
    }

    @Test
    fun pickCanBeAskedTheOtherWayRound() {
        val card = builder.pickMeaning(sol, Direction.REVERSE, deck)!!

        assertThat(card.prompt).isEqualTo("the sun")
        assertThat(card.options[card.correctIndex]).isEqualTo("el sol")
    }

    @Test
    fun pickUsesTheCardsOwnWrongAnswersFirst() {
        val own = sol.copy(wrongAnswers = listOf("the star", "the heat", "the day"))

        val card = builder.pickMeaning(own, Direction.FORWARD, deck)!!

        assertThat(card.options.toSet()).isEqualTo(setOf("the sun", "the star", "the heat", "the day"))
    }

    @Test
    fun pickBorrowsTheRestFromOtherCards() {
        val oneOwn = sol.copy(wrongAnswers = listOf("the star"))

        val card = builder.pickMeaning(oneOwn, Direction.FORWARD, deck)!!

        assertThat(card.options).contains("the star")
        assertThat(card.options).hasSize(4)
    }

    @Test
    fun pickWithoutEnoughWrongAnswersCantBePlayed() {
        assertThat(builder.pickMeaning(sol, Direction.FORWARD, deck.take(3))).isNull()
        // Its own three are enough, even alone in the deck.
        val selfContained = sol.copy(wrongAnswers = listOf("a", "b", "c"))
        assertThat(builder.pickMeaning(selfContained, Direction.FORWARD, listOf(selfContained))).isNotNull()
    }

    @Test
    fun pickNeverOffersAnotherRightAnswerOrDuplicates() {
        val tricky = deck + pick("6", "el astro rey", "The Sun") + pick("7", "la noche", "the moon")

        repeat(20) {
            val card = builder.pickMeaning(sol, Direction.FORWARD, tricky)!!
            val wrong = card.options.filterIndexed { i, _ -> i != card.correctIndex }.map { it.lowercase() }
            assertThat(wrong).doesNotContain("the sun")
            assertThat(card.options.map { it.lowercase() }.distinct()).hasSize(4)
        }
    }

    // True or false

    @Test
    fun trueOrFalsePlaysTheUsersStatement() {
        val card = Note(
            id = "tf", deckId = "d", type = NoteType.QUESTION, front = "The sun is a star.", back = "",
            game = Game.TRUE_FALSE, isTrue = true, explanation = "The closest one.",
        )

        val content = builder.trueFalse(card)!!

        assertThat(content.statement).isEqualTo("The sun is a star.")
        assertThat(content.isTrue).isTrue()
        assertThat(content.explanation).isEqualTo("The closest one.")
    }

    // Type it

    @Test
    fun typeItAcceptsEveryAnswerAndOffersTheDecksLetters() {
        val card = pick("t", "to wake up", "despertarse").copy(game = Game.TYPE_IT, acceptedAnswers = listOf("levantarse"))

        val content = builder.typeIt(card, Direction.FORWARD, letters = listOf("ñ"))!!

        assertThat(content.answers).containsExactly("despertarse", "levantarse")
        assertThat(content.letters).containsExactly("ñ")
    }

    @Test
    fun lettersComeFromTheDecksOwnCards() {
        val cards = listOf(pick("a", "mañana", "morning"), pick("b", "café", "coffee"), pick("c", "niño", "child"))

        assertThat(CardBuilder.lettersIn(cards).toSet()).isEqualTo(setOf("ñ", "é"))
        assertThat(CardBuilder.lettersIn(listOf(pick("x", "sun", "sol")))).isEqualTo(emptyList())
    }

    // Fill the gap

    @Test
    fun fillTheGapHidesTheBracketedWord() {
        val gap = pick("g", "Me despierto con el [amanecer].", "").copy(
            game = Game.FILL_THE_GAP,
            exampleTranslation = "I wake up with the sunrise.",
            wrongAnswers = listOf("atardecer", "desayuno", "reloj"),
        )

        val content = builder.fillTheGap(gap, listOf(gap))!!

        assertThat(content.sentence).isEqualTo("Me despierto con el ${SentenceGap.BLANK}.")
        assertThat(content.options[content.correctIndex]).isEqualTo("amanecer")
        assertThat(content.translation).isEqualTo("I wake up with the sunrise.")
    }

    @Test
    fun fillTheGapBorrowsWordsAndNeedsABracket() {
        val gap = pick("g", "Necesito un [café].", "").copy(game = Game.FILL_THE_GAP)

        assertThat(builder.fillTheGap(gap, deck + gap)).isNotNull()
        assertThat(builder.fillTheGap(gap.copy(front = "Necesito un café."), deck)).isNull()
    }

    // Match pairs

    @Test
    fun matchPairsUsesTheCardsOwnPairs() {
        val match = pick("m", "", "").copy(
            game = Game.MATCH_PAIRS,
            pairs = listOf("el sol" to "the sun", "la luna" to "the moon", "el cielo" to "the sky", "el mar" to "the sea"),
        )

        val content = builder.matchPairs(match)!!

        assertThat(content.prompts).containsExactly("el sol", "la luna", "el cielo", "el mar")
        assertThat(content.answers).containsExactly("the sun", "the moon", "the sky", "the sea")
        assertThat(content.answerOrder.sorted()).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun matchPairsNeedsTwoDifferentPairs() {
        val one = pick("m", "", "").copy(game = Game.MATCH_PAIRS, pairs = listOf("a" to "b"))
        val same = pick("m", "", "").copy(game = Game.MATCH_PAIRS, pairs = listOf("a" to "b", "a" to "c"))

        assertThat(builder.matchPairs(one)).isNull()
        assertThat(builder.matchPairs(same)).isNull()
    }

    // Build picks by game

    @Test
    fun buildFollowsTheCardsGame() {
        val tf = pick("tf", "The sun is a star.", "").copy(game = Game.TRUE_FALSE, isTrue = true)

        assertThat(builder.build(sol, Direction.FORWARD, deck)).isNotNull().isInstanceOf(CardContent.PickMeaning::class)
        assertThat(builder.build(tf, Direction.FORWARD, deck)).isNotNull().isInstanceOf(CardContent.TrueFalse::class)
    }

    // Quick math

    @Test
    fun quickMathAlwaysHasTheAnswer() {
        repeat(50) {
            val card = builder.quickMath()
            assertThat(card.options).hasSize(4)
            assertThat(card.options.distinct()).hasSize(4)
            assertThat(card.options.all { it > 0 }).isTrue()
        }
    }

    // Name the country

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
        assertThat(card.options).doesNotContain("Russia")
    }

    @Test
    fun nameTheCountryOffersNeighboursFirst() {
        val germany = countries.first { it.back == "DE" }
            .copy(wrongAnswers = listOf("France", "Poland", "Luxembourg", "Italy", "Spain"))

        val card = builder.nameOnMap(germany, countries, GeoRegion.EUROPE)!!

        val wrong = card.options.filterIndexed { index, _ -> index != card.correctIndex }
        assertThat(setOf("France", "Poland", "Luxembourg", "Italy").containsAll(wrong)).isTrue()
    }

    @Test
    fun nameTheCountryNeedsFourCountries() {
        assertThat(builder.nameOnMap(countries[0], countries.take(3), GeoRegion.EUROPE)).isNull()
    }
}
