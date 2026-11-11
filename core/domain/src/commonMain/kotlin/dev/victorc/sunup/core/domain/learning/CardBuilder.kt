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
}
