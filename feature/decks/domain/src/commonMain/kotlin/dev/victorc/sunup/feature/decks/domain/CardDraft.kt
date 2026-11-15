package dev.victorc.sunup.feature.decks.domain

import dev.victorc.sunup.core.domain.Error
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteType
import dev.victorc.sunup.core.domain.learning.SentenceGap

/**
 * What a card form holds while the user types. One shape for every game; each game's form
 * shows only its fields (see mockups E1–E5):
 *
 * | Game          | Fields                                                    |
 * |---------------|-----------------------------------------------------------|
 * | Pick from 4   | [question], [answer], [wrong] (3, optional)               |
 * | True or false | [question] (the statement), [isTrue], [why]               |
 * | Type it       | [question], [answer], [alsoRight] (comma-separated)       |
 * | Fill the gap  | [question] (sentence with [word]), [translation], [wrongList] |
 * | Match pairs   | [pairs] (4 rows, 2 needed)                                |
 */
data class CardDraft(
    val game: Game,
    val question: String = "",
    val answer: String = "",
    val wrong: List<String> = List(WRONG_ANSWERS) { "" },
    /** Fill the gap: wrong answers in one comma-separated field. */
    val wrongList: String = "",
    val alsoRight: String = "",
    val isTrue: Boolean = true,
    val why: String = "",
    val translation: String = "",
    val pairs: List<Pair<String, String>> = List(PAIRS) { "" to "" },
) {
    companion object {
        const val WRONG_ANSWERS = 3
        const val PAIRS = 4
        const val MIN_PAIRS = 2

        /** The form filled in from a saved card, for editing it. */
        fun from(note: Note): CardDraft {
            val base = CardDraft(game = note.game)
            return when (note.game) {
                Game.TRUE_FALSE -> base.copy(question = note.front, isTrue = note.isTrue ?: true, why = note.explanation.orEmpty())
                Game.TYPE_IT -> base.copy(question = note.front, answer = note.back, alsoRight = note.acceptedAnswers.joinToString(", "))
                Game.FILL_THE_GAP -> base.copy(
                    question = note.front,
                    translation = note.exampleTranslation.orEmpty(),
                    wrongList = note.wrongAnswers.joinToString(", "),
                )
                Game.MATCH_PAIRS -> base.copy(pairs = (note.pairs + List(PAIRS) { "" to "" }).take(PAIRS))
                else -> base.copy(
                    question = note.front,
                    answer = note.back,
                    wrong = (note.wrongAnswers + List(WRONG_ANSWERS) { "" }).take(WRONG_ANSWERS),
                )
            }
        }
    }
}

/** Why a card can't be saved yet. Each is shown next to the form. */
enum class CardProblem : Error {
    NO_QUESTION,
    NO_ANSWER,
    NO_STATEMENT,
    NO_SENTENCE,

    /** Fill the gap: the missing word must be written in [brackets]. */
    NO_BRACKETS,

    /** Match pairs: at least two complete pairs. */
    TOO_FEW_PAIRS,

    /** Match pairs: the same word twice on one side can't be matched. */
    SAME_WORD_TWICE,
}

/** Turns a form into a card, or says what's missing. */
object CardDrafts {

    fun check(draft: CardDraft): CardProblem? = when (draft.game) {
        Game.TRUE_FALSE -> if (draft.question.isBlank()) CardProblem.NO_STATEMENT else null
        Game.FILL_THE_GAP -> when {
            draft.question.isBlank() -> CardProblem.NO_SENTENCE
            SentenceGap.fromBrackets(draft.question) == null -> CardProblem.NO_BRACKETS
            else -> null
        }
        Game.MATCH_PAIRS -> {
            val complete = completePairs(draft)
            when {
                complete.size < CardDraft.MIN_PAIRS -> CardProblem.TOO_FEW_PAIRS
                complete.map { it.first.lowercase() }.toSet().size < complete.size ||
                    complete.map { it.second.lowercase() }.toSet().size < complete.size -> CardProblem.SAME_WORD_TWICE
                else -> null
            }
        }
        else -> when {
            draft.question.isBlank() -> CardProblem.NO_QUESTION
            draft.answer.isBlank() -> CardProblem.NO_ANSWER
            else -> null
        }
    }

    /** The card to save, keeping [id] when editing. Only the chosen game's fields are kept. */
    fun toNote(draft: CardDraft, id: String, deckId: String, deckKind: DeckKind): Result<Note, CardProblem> {
        check(draft)?.let { return Result.Error(it) }
        val base = Note(
            id = id,
            deckId = deckId,
            type = if (deckKind == DeckKind.LANGUAGE) NoteType.WORD else NoteType.QUESTION,
            front = "",
            back = "",
            game = draft.game,
        )
        val note = when (draft.game) {
            Game.TRUE_FALSE -> base.copy(
                front = draft.question.trim(),
                isTrue = draft.isTrue,
                explanation = draft.why.trim().ifEmpty { null },
            )
            Game.TYPE_IT -> base.copy(
                front = draft.question.trim(),
                back = draft.answer.trim(),
                acceptedAnswers = splitList(draft.alsoRight),
            )
            Game.FILL_THE_GAP -> base.copy(
                front = draft.question.trim(),
                exampleTranslation = draft.translation.trim().ifEmpty { null },
                wrongAnswers = splitList(draft.wrongList),
            )
            Game.MATCH_PAIRS -> base.copy(pairs = completePairs(draft))
            else -> base.copy(
                front = draft.question.trim(),
                back = draft.answer.trim(),
                wrongAnswers = draft.wrong.map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
        return Result.Success(note)
    }

    /** "a, b; c" → [a, b, c]. Commas or semicolons, blanks dropped. */
    fun splitList(text: String): List<String> =
        text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    private fun completePairs(draft: CardDraft): List<Pair<String, String>> = draft.pairs
        .map { (left, right) -> left.trim() to right.trim() }
        .filter { (left, right) -> left.isNotEmpty() && right.isNotEmpty() }
}
