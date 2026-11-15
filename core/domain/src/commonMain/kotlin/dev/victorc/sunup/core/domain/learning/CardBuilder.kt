package dev.victorc.sunup.core.domain.learning

import kotlin.random.Random

/** What a card shows. Built fresh each time from a card, so options are shuffled every morning. */
sealed interface CardContent {
    val game: Game

    data class PickMeaning(
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
    ) : CardContent {
        override val game: Game get() = Game.PICK_MEANING
    }

    /** A statement the user wrote: is it true? */
    data class TrueFalse(
        val statement: String,
        val isTrue: Boolean,
        /** The one-line "why", shown after answering. */
        val explanation: String?,
    ) : CardContent {
        override val game: Game get() = Game.TRUE_FALSE
    }

    data class QuickMath(
        val question: String,
        val options: List<Int>,
        val correctIndex: Int,
    ) : CardContent {
        override val game: Game get() = Game.QUICK_MATH
    }

    /** A sentence with one word missing: pick it from four. */
    data class FillTheGap(
        /** Sentence with [SentenceGap.BLANK] where the word was. */
        val sentence: String,
        val translation: String?,
        val options: List<String>,
        val correctIndex: Int,
        /** The missing word, for the "You're up!" list. */
        val word: String,
    ) : CardContent {
        override val game: Game get() = Game.FILL_THE_GAP
    }

    /** A geography card: needs the map, and is about one country. */
    sealed interface OnMap : CardContent {
        /** Country code, e.g. "PT" (Kosovo: "XK"). */
        val countryId: String
        val countryName: String
        val region: GeoRegion
    }

    /** Tap [countryName] on the map of [region]. */
    data class FindOnMap(
        override val countryId: String,
        override val countryName: String,
        override val region: GeoRegion,
    ) : OnMap {
        override val game: Game get() = Game.FIND_ON_MAP
    }

    /** [countryId] is highlighted on the map of [region]: pick its name from [options]. */
    data class NameOnMap(
        override val countryId: String,
        override val countryName: String,
        override val region: GeoRegion,
        val options: List<String>,
        /** Country code of each option, so a wrong pick can be shown on the map. */
        val optionIds: List<String>,
        val correctIndex: Int,
    ) : OnMap {
        override val game: Game get() = Game.NAME_ON_MAP
    }

    /** Type the answer; checked forgivingly by [AnswerMatcher]. */
    data class TypeIt(
        val prompt: String,
        /** Every answer that counts; the first is the one shown when revealed. */
        val answers: List<String>,
        /** Special letters to offer as keys, taken from the deck's own cards (e.g. á, ñ). */
        val letters: List<String>,
    ) : CardContent {
        override val game: Game get() = Game.TYPE_IT
    }

    /** Up to four pairs. answers[i] belongs to prompts[i]; [answerOrder] is how the right column is shuffled. */
    data class MatchPairs(
        val prompts: List<String>,
        val answers: List<String>,
        val answerOrder: List<Int>,
    ) : CardContent {
        override val game: Game get() = Game.MATCH_PAIRS
    }
}

/**
 * Turns cards into what the morning screen shows. Wrong answers come from the card's own
 * list first, then are borrowed from other cards in the same deck.
 */
class CardBuilder(private val random: Random = Random.Default) {

    /** The card in its game, or null if it can't be played yet (e.g. not enough wrong answers). */
    fun build(note: Note, direction: Direction, deckNotes: List<Note>, letters: List<String> = emptyList()): CardContent? =
        when (note.game) {
            Game.PICK_MEANING -> pickMeaning(note, direction, deckNotes)
            Game.TRUE_FALSE -> trueFalse(note)
            Game.TYPE_IT -> typeIt(note, direction, letters)
            Game.FILL_THE_GAP -> fillTheGap(note, deckNotes)
            Game.MATCH_PAIRS -> matchPairs(note)
            else -> null
        }

    fun pickMeaning(note: Note, direction: Direction, deckNotes: List<Note>): CardContent.PickMeaning? {
        if (note.front.isBlank() || note.back.isBlank()) return null
        val correct = note.answers(direction).first().trim()
        val own = if (direction == Direction.FORWARD) note.wrongAnswers else emptyList()
        val wrong = wrongAnswers(own, borrowable(note, direction, deckNotes), rightOnes = note.answers(direction)) ?: return null
        val options = (wrong + correct).shuffled(random)
        return CardContent.PickMeaning(
            prompt = note.prompt(direction).trim(),
            options = options,
            correctIndex = options.indexOf(correct),
        )
    }

    fun trueFalse(note: Note): CardContent.TrueFalse? {
        if (note.front.isBlank()) return null
        return CardContent.TrueFalse(
            statement = note.front.trim(),
            isTrue = note.isTrue ?: true,
            explanation = note.explanation?.takeIf { it.isNotBlank() },
        )
    }

    fun typeIt(note: Note, direction: Direction, letters: List<String> = emptyList()): CardContent.TypeIt? {
        if (note.front.isBlank() || note.back.isBlank()) return null
        return CardContent.TypeIt(
            prompt = note.prompt(direction).trim(),
            answers = note.answers(direction).map { it.trim() }.filter { it.isNotEmpty() },
            letters = letters,
        )
    }

    /** The sentence's [bracketed] word is the gap; wrong options are the card's own, then other cards' answers. */
    fun fillTheGap(note: Note, deckNotes: List<Note>): CardContent.FillTheGap? {
        val gap = SentenceGap.fromBrackets(note.front) ?: return null
        val wrong = wrongAnswers(note.wrongAnswers, borrowable(note, Direction.FORWARD, deckNotes), rightOnes = listOf(gap.answer))
            ?: return null
        val options = (wrong + gap.answer).shuffled(random)
        return CardContent.FillTheGap(
            sentence = gap.sentence,
            translation = note.exampleTranslation?.takeIf { it.isNotBlank() },
            options = options,
            correctIndex = options.indexOf(gap.answer),
            word = gap.answer,
        )
    }

    /** One card holds its own pairs: 2 to [MATCH_SIZE], each side different. */
    fun matchPairs(note: Note): CardContent.MatchPairs? {
        val pairs = note.pairs
            .map { (left, right) -> left.trim() to right.trim() }
            .filter { (left, right) -> left.isNotEmpty() && right.isNotEmpty() }
            .take(MATCH_SIZE)
        if (pairs.size < 2) return null
        val prompts = pairs.map { it.first }
        val answers = pairs.map { it.second }
        val distinct = prompts.map { it.lowercase() }.toSet().size == pairs.size &&
            answers.map { it.lowercase() }.toSet().size == pairs.size
        if (!distinct) return null
        return CardContent.MatchPairs(
            prompts = prompts,
            answers = answers,
            answerOrder = answers.indices.shuffled(random),
        )
    }

    /**
     * Geography notes (front = name, back = country code, wrong answers = neighbours, closest
     * first). Wrong options are neighbours, to really test the shape and place: three of the
     * closest four. Short of neighbours in the deck, countries of a similar size fill in.
     * Null if the deck has fewer than four countries.
     */
    fun nameOnMap(note: Note, deckNotes: List<Note>, region: GeoRegion): CardContent.NameOnMap? {
        fun isOther(other: Note) = other.id != note.id && !other.front.equals(note.front, ignoreCase = true)

        val byName = deckNotes.associateBy { it.front.lowercase() }
        val neighbours = note.wrongAnswers
            .mapNotNull { byName[it.trim().lowercase()] }
            .filter(::isOther)
            .distinctBy { it.id }
            .take(CLOSEST_NEIGHBOURS)
            .shuffled(random)
            .take(3)

        val ordered = deckNotes.sortedBy { it.id }
        val position = ordered.indexOfFirst { it.id == note.id }.coerceAtLeast(0)
        val similarSize = ordered
            .withIndex()
            .filter { (_, other) -> isOther(other) && other !in neighbours }
            .sortedBy { (index, _) -> kotlin.math.abs(index - position) }
            .take(SIMILAR_COUNTRIES)
            .map { it.value }
            .shuffled(random)

        val wrong = (neighbours + similarSize).take(3)
        if (wrong.size < 3) return null

        val options = (wrong + note).shuffled(random)
        return CardContent.NameOnMap(
            countryId = note.back,
            countryName = note.front,
            region = region,
            options = options.map { it.front },
            optionIds = options.map { it.back },
            correctIndex = options.indexOf(note),
        )
    }

    /** Needs no cards, so it works before a deck exists or before the first unlock after a reboot. */
    fun quickMath(): CardContent.QuickMath {
        val (question, answer) = if (random.nextBoolean()) {
            val a = random.nextInt(4, 30)
            val b = random.nextInt(3, 20)
            "$a + $b" to a + b
        } else {
            val a = random.nextInt(2, 10)
            val b = random.nextInt(2, 10)
            "$a × $b" to a * b
        }
        val wrong = mutableSetOf<Int>()
        while (wrong.size < 3) {
            val candidate = answer + random.nextInt(-5, 6)
            if (candidate != answer && candidate > 0) wrong += candidate
        }
        val options = (wrong + answer).shuffled(random)
        return CardContent.QuickMath(question = question, options = options, correctIndex = options.indexOf(answer))
    }

    /**
     * Answers from other cards that could pass as a wrong option: the answers of Pick from 4
     * and Type it cards (in [direction]) and the missing words of Fill the gap cards.
     */
    private fun borrowable(note: Note, direction: Direction, deckNotes: List<Note>): List<String> = deckNotes
        .filter { it.id != note.id }
        .mapNotNull { other ->
            when (other.game) {
                Game.PICK_MEANING, Game.TYPE_IT -> other.answers(direction).firstOrNull()
                Game.FILL_THE_GAP -> SentenceGap.fromBrackets(other.front)?.answer
                else -> null
            }
        }
        .shuffled(random)

    /** Three distinct wrong answers, the card's own first, never one that also counts as right. Null if short. */
    private fun wrongAnswers(own: List<String>, borrowed: List<String>, rightOnes: List<String>): List<String>? {
        val right = rightOnes.map { it.trim().lowercase() }.toSet()
        val picked = (own.shuffled(random) + borrowed)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in right }
            .distinctBy { it.lowercase() }
            .take(3)
        return picked.takeIf { it.size == 3 }
    }

    companion object {
        /** Match pairs cards hold up to this many pairs. */
        const val MATCH_SIZE = 4

        /** Name the country: wrong options are three of this many closest neighbours. */
        private const val CLOSEST_NEIGHBOURS = 4

        /** Name the country, short of neighbours: fill in from this many countries closest in size. */
        private const val SIMILAR_COUNTRIES = 8

        /** Accent keys offered in Type it: the special letters used in a deck's cards, most used first. */
        fun lettersIn(notes: List<Note>, max: Int = 12): List<String> = notes
            .flatMap { note -> listOf(note.front, note.back) + note.acceptedAnswers + note.pairs.flatMap { listOf(it.first, it.second) } }
            .joinToString("")
            .lowercase()
            .filter { it.isLetter() && it.code > 127 }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key.toString() }
    }
}
