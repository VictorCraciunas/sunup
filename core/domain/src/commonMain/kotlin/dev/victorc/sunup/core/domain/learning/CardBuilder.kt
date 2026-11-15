package dev.victorc.sunup.core.domain.learning

import kotlin.random.Random

/** What a card shows. Built fresh each time from a note, so options are shuffled every morning. */
sealed interface CardContent {
    val game: Game

    data class PickMeaning(
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
    ) : CardContent {
        override val game: Game get() = Game.PICK_MEANING
    }

    /** "Does [prompt] mean [shownAnswer]?" */
    data class TrueFalse(
        val prompt: String,
        val shownAnswer: String,
        val isTrue: Boolean,
        val correctAnswer: String,
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

    /** The example sentence with the word missing: pick it from four. */
    data class FillTheGap(
        /** Sentence with [SentenceGap.BLANK] where the word was. */
        val sentence: String,
        val translation: String?,
        val options: List<String>,
        val correctIndex: Int,
        /** The note's word and meaning, for the learned list. */
        val word: String,
        val meaning: String,
    ) : CardContent {
        override val game: Game get() = Game.FILL_THE_GAP
    }

    /** Tap [countryName] on the map of [region]. */
    data class FindOnMap(
        /** Country code, e.g. "PT" (Kosovo: "XK"). */
        val countryId: String,
        val countryName: String,
        val region: GeoRegion,
    ) : CardContent {
        override val game: Game get() = Game.FIND_ON_MAP
    }

    /** Type the answer; checked forgivingly by [AnswerMatcher]. */
    data class TypeIt(
        val prompt: String,
        /** Every answer that counts; the first is the one shown when revealed. */
        val answers: List<String>,
        /** Language of the answer, for articles and accent keys, e.g. "es". */
        val language: String?,
    ) : CardContent {
        override val game: Game get() = Game.TYPE_IT
    }

    /** Four prompts and their answers. answers[i] belongs to prompts[i]; [answerOrder] is how the right column is shuffled. */
    data class MatchPairs(
        val prompts: List<String>,
        val answers: List<String>,
        val answerOrder: List<Int>,
    ) : CardContent {
        override val game: Game get() = Game.MATCH_PAIRS
    }
}

/**
 * Turns notes into playable cards. Without AI, wrong answers come from the user's own
 * "wrong answers" first, then from other notes in the same deck.
 */
class CardBuilder(private val random: Random = Random.Default) {

    fun pickMeaning(note: Note, direction: Direction, deckNotes: List<Note>): CardContent.PickMeaning? {
        val correct = note.answers(direction).first()
        val wrong = distractors(note, direction, deckNotes, count = 3) ?: return null
        val options = (wrong + correct).shuffled(random)
        return CardContent.PickMeaning(
            prompt = note.prompt(direction),
            options = options,
            correctIndex = options.indexOf(correct),
        )
    }

    fun trueFalse(note: Note, direction: Direction, deckNotes: List<Note>): CardContent.TrueFalse? {
        val correct = note.answers(direction).first()
        val wrong = distractors(note, direction, deckNotes, count = 1)?.first() ?: return null
        val isTrue = random.nextBoolean()
        return CardContent.TrueFalse(
            prompt = note.prompt(direction),
            shownAnswer = if (isTrue) correct else wrong,
            isTrue = isTrue,
            correctAnswer = correct,
            explanation = note.explanation,
        )
    }

    /**
     * Wrong options are other words from the deck, in the same form as the gap: with their
     * article if the gap kept it ("la luna"), without if it didn't ("luna").
     */
    fun fillTheGap(note: Note, deckNotes: List<Note>): CardContent.FillTheGap? {
        val gap = SentenceGap.find(note) ?: return null
        val wrong = deckNotes
            .filter { it.id != note.id && it.type == NoteType.WORD }
            .map { other ->
                val word = other.front.trim()
                if (gap.withoutArticle) SentenceGap.withoutArticle(word) ?: word else word
            }
            .filter { it.isNotEmpty() && !it.equals(gap.answer, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .shuffled(random)
            .take(3)
        if (wrong.size < 3) return null

        val options = (wrong + gap.answer).shuffled(random)
        return CardContent.FillTheGap(
            sentence = gap.sentence,
            translation = note.exampleTranslation,
            options = options,
            correctIndex = options.indexOf(gap.answer),
            word = note.front,
            meaning = note.back,
        )
    }

    fun typeIt(note: Note, direction: Direction, answerLanguage: String?): CardContent.TypeIt =
        CardContent.TypeIt(
            prompt = note.prompt(direction),
            answers = note.answers(direction),
            language = answerLanguage,
        )

    /**
     * One card, four words: [notes] in order, the first being the card's own note.
     * Null unless there are exactly [MATCH_SIZE] notes with distinct prompts and answers.
     */
    fun matchPairs(notes: List<Note>, direction: Direction): CardContent.MatchPairs? {
        if (notes.size != MATCH_SIZE) return null
        val prompts = notes.map { it.prompt(direction) }
        val answers = notes.map { it.answers(direction).first() }
        val distinct = prompts.map { it.lowercase() }.toSet().size == MATCH_SIZE &&
            answers.map { it.lowercase() }.toSet().size == MATCH_SIZE
        if (!distinct) return null
        return CardContent.MatchPairs(
            prompts = prompts,
            answers = answers,
            answerOrder = answers.indices.shuffled(random),
        )
    }

    /** Needs no notes, so it works before a deck exists or before the first unlock after a reboot. */
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
     * [count] distinct wrong answers for [note], or null if the deck can't supply enough.
     * Never returns anything that would also count as right.
     */
    fun distractors(note: Note, direction: Direction, deckNotes: List<Note>, count: Int): List<String>? {
        val rightOnes = note.answers(direction).map { it.trim().lowercase() }.toSet()
        val own = if (direction == Direction.FORWARD) note.wrongAnswers.shuffled(random) else emptyList()
        val fromDeck = deckNotes
            .filter { it.id != note.id && it.type == note.type }
            .map { it.answers(direction).first() }
            .shuffled(random)

        val picked = (own + fromDeck)
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in rightOnes }
            .distinctBy { it.lowercase() }
            .take(count)
        return picked.takeIf { it.size == count }
    }

    companion object {
        const val MATCH_SIZE = 4
    }
}
