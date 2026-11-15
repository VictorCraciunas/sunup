package dev.victorc.sunup.core.domain.learning

/**
 * One card in a deck. The user picks its [game] when making it, and fills in the fields
 * that game needs:
 *
 * | Game          | Fields used                                                        |
 * |---------------|--------------------------------------------------------------------|
 * | Pick from 4   | [front] question, [back] right answer, [wrongAnswers] (optional)   |
 * | True or false | [front] statement, [isTrue], [explanation] (optional)              |
 * | Type it       | [front] question, [back] answer, [acceptedAnswers] (optional)      |
 * | Fill the gap  | [front] sentence with the word in [brackets], [exampleTranslation], [wrongAnswers] |
 * | Match pairs   | [pairs]                                                            |
 *
 * Geography decks are built in: [front] is the country's name, [back] its code, and the
 * map games are chosen by the deck, not by [game].
 *
 * Still called Note in code (and in the database) from before cards had one game each.
 */
data class Note(
    val id: String,
    val deckId: String,
    val type: NoteType,
    /** The question, statement, or sentence. */
    val front: String,
    /** The right answer. */
    val back: String,
    /** Other answers that also count, e.g. "wake up" for "to wake up". */
    val acceptedAnswers: List<String> = emptyList(),
    /** Old word notes: an example sentence. New cards keep their sentence in [front]. */
    val example: String? = null,
    /** Fill the gap: the sentence's translation. */
    val exampleTranslation: String? = null,
    /** A short extra, e.g. gender or plural. */
    val hint: String? = null,
    /** True or false: the one-line "why", shown after answering. */
    val explanation: String? = null,
    /** Pick from 4 and Fill the gap: the user's own wrong answers. Empty ones are borrowed from other cards. */
    val wrongAnswers: List<String> = emptyList(),
    /** The one game this card plays. */
    val game: Game = Game.PICK_MEANING,
    /** True or false: whether [front] is true. */
    val isTrue: Boolean? = null,
    /** Match pairs: left ↔ right. */
    val pairs: List<Pair<String, String>> = emptyList(),
) {
    fun prompt(direction: Direction): String = if (direction == Direction.FORWARD) front else back

    /** Every answer that counts in [direction]. The main one comes first. */
    fun answers(direction: Direction): List<String> =
        if (direction == Direction.FORWARD) listOf(back) + acceptedAnswers else listOf(front)
}

enum class NoteType {
    /** Language decks: word → meaning. */
    WORD,

    /** Study and trivia decks: question → answer. */
    QUESTION,
}

/** FORWARD asks the front (word → meaning). REVERSE asks the back (meaning → word). */
enum class Direction { FORWARD, REVERSE }
