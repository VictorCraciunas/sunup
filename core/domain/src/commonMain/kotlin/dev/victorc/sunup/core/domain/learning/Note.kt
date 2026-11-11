package dev.victorc.sunup.core.domain.learning

/**
 * One fact the user writes once, e.g. "el amanecer → sunrise". Sunup turns each note
 * into the different games; the user never builds a "Match pairs card" by hand.
 */
data class Note(
    val id: String,
    val deckId: String,
    val type: NoteType,
    /** The word, or the question. */
    val front: String,
    /** The meaning, or the answer. */
    val back: String,
    /** Other answers that also count, e.g. "wake up" for "to wake up". */
    val acceptedAnswers: List<String> = emptyList(),
    /** Word notes: an example sentence in the language being learned. Unlocks Build the sentence. */
    val example: String? = null,
    val exampleTranslation: String? = null,
    /** Word notes: a short extra, e.g. gender or plural. */
    val hint: String? = null,
    /** Question notes: the one-line "why" shown after True or false. */
    val explanation: String? = null,
    /** Optional wrong answers written by the user, used before random ones from the deck. */
    val wrongAnswers: List<String> = emptyList(),
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
