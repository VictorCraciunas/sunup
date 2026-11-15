package dev.victorc.sunup.feature.decks.domain

import dev.victorc.sunup.core.domain.Error
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A deck read from JSON, ready to preview and create. */
data class ImportedDeck(
    val name: String,
    val kind: DeckKind,
    /** The cards that can be made, in order. */
    val cards: List<CardDraft>,
    val skipped: List<SkippedCard>,
)

/** A card that couldn't be made, and why (shown in the preview). */
data class SkippedCard(
    /** 1-based, as the user would count. */
    val number: Int,
    val reason: SkipReason,
)

enum class SkipReason {
    /** No "game", and nothing to tell which game it is. */
    UNKNOWN_GAME,

    /** The card's game is known, but a field it needs is missing. */
    INCOMPLETE,
}

enum class ImportError : Error {
    /** Couldn't find any JSON in the text. */
    NOT_JSON,

    /** JSON, but no "cards" list with anything usable in it. */
    NO_CARDS,
}

/**
 * Reads a deck an AI wrote from [PROMPT]. Forgiving on purpose: AIs wrap JSON in prose or
 * ``` fences, name fields a little differently, or forget the game. The format:
 *
 * ```
 * { "deck": "World history", "type": "study",
 *   "cards": [
 *     { "game": "pick", "question": "…", "answer": "…", "wrong": ["…", "…", "…"] },
 *     { "game": "truefalse", "statement": "…", "answer": true, "why": "…" },
 *     { "game": "type", "question": "…", "answer": "…", "also": ["…"] },
 *     { "game": "gap", "sentence": "The [Romans] built …", "extra": "…", "wrong": ["…"] },
 *     { "game": "match", "pairs": [["France", "Paris"], ["Italy", "Rome"]] } ] }
 * ```
 */
object DeckImport {

    /** Cards beyond this are ignored, so a runaway paste can't freeze the phone. */
    const val MAX_CARDS = 500

    /**
     * What the user copies into ChatGPT, Claude or any AI. Its example is real and imports
     * as-is (there's a test for that).
     */
    val PROMPT: String = """
        Make a Sunup deck about [YOUR TOPIC, or paste your notes below this message].
        Make 20 cards and mix the games. Reply with only the JSON, no other text.

        Games:
        - "pick": a question, the right answer, and 3 wrong answers
        - "truefalse": a statement, whether it's true, and a one-line "why"
        - "type": a question with a short answer (1 to 3 words)
        - "gap": a sentence with the missing word in [brackets], and 3 wrong words
        - "match": 4 pairs that belong together

        Deck "type" is one of: "language", "study", "trivia".

        Format:
        {
          "deck": "World history",
          "type": "study",
          "cards": [
            {"game": "pick", "question": "Who painted the Mona Lisa?", "answer": "Leonardo da Vinci", "wrong": ["Michelangelo", "Raphael", "Van Gogh"]},
            {"game": "truefalse", "statement": "The Eiffel Tower is in Paris.", "answer": true, "why": "It was built for the 1889 World's Fair."},
            {"game": "type", "question": "In which year did World War II end?", "answer": "1945"},
            {"game": "gap", "sentence": "The [Romans] built Hadrian's Wall.", "wrong": ["Vikings", "Normans", "Celts"]},
            {"game": "match", "pairs": [["France", "Paris"], ["Italy", "Rome"], ["Japan", "Tokyo"], ["Egypt", "Cairo"]]}
          ]
        }
    """.trimIndent()

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(text: String): Result<ImportedDeck, ImportError> {
        val root = extractJson(text) ?: return Result.Error(ImportError.NOT_JSON)
        val deckObject = root as? JsonObject
        val cardElements = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("cards", "flashcards", "items", "questions")
            else -> null
        } ?: return Result.Error(ImportError.NO_CARDS)

        val drafts = mutableListOf<CardDraft>()
        val skipped = mutableListOf<SkippedCard>()
        cardElements.take(MAX_CARDS).forEachIndexed { index, element ->
            val card = element as? JsonObject
            val draft = card?.let(::toDraft)
            when {
                card == null || draft == null -> skipped += SkippedCard(index + 1, SkipReason.UNKNOWN_GAME)
                CardDrafts.check(draft) != null -> skipped += SkippedCard(index + 1, SkipReason.INCOMPLETE)
                else -> drafts += draft
            }
        }
        if (drafts.isEmpty()) return Result.Error(ImportError.NO_CARDS)

        return Result.Success(
            ImportedDeck(
                name = deckObject?.text("deck", "name", "title")?.takeIf { it.isNotBlank() } ?: "Imported deck",
                kind = deckKind(deckObject?.text("type", "kind", "category")),
                cards = drafts,
                skipped = skipped,
            ),
        )
    }

    /**
     * The JSON inside [text]: from the first { to the last }, or the first [ to the last ],
     * whichever parses (chat text around it may contain either bracket).
     */
    private fun extractJson(text: String): JsonElement? {
        // Try as pasted first: curly quotes inside the cards' text are fine there. Only if that
        // fails, swap curly quotes (from notes apps or chat copy buttons) for straight ones.
        val curlyFixed = text.replace('“', '"').replace('”', '"')
        return listOf(text, curlyFixed).distinct().firstNotNullOfOrNull(::findJson)
    }

    private fun findJson(cleaned: String): JsonElement? =
        listOf('{' to '}', '[' to ']')
            .sortedBy { (open, _) -> cleaned.indexOf(open).let { if (it < 0) Int.MAX_VALUE else it } }
            .firstNotNullOfOrNull { (open, close) ->
                val start = cleaned.indexOf(open)
                val end = cleaned.lastIndexOf(close)
                if (start < 0 || end <= start) {
                    null
                } else {
                    runCatching { json.parseToJsonElement(cleaned.substring(start, end + 1)) }.getOrNull()
                }
            }

    private fun toDraft(card: JsonObject): CardDraft? = when (gameOf(card)) {
        Game.PICK_MEANING -> CardDraft(
            game = Game.PICK_MEANING,
            question = card.text("question", "front", "prompt").orEmpty(),
            answer = card.text("answer", "back", "correct", "right").orEmpty(),
            wrong = (card.texts("wrong", "wrongAnswers", "wrong_answers", "distractors", "options") + List(CardDraft.WRONG_ANSWERS) { "" })
                .filterNot { it.equals(card.text("answer", "back", "correct", "right"), ignoreCase = true) }
                .take(CardDraft.WRONG_ANSWERS),
        )
        Game.TRUE_FALSE -> CardDraft(
            game = Game.TRUE_FALSE,
            question = card.text("statement", "question", "front", "prompt").orEmpty(),
            isTrue = card.truth("answer", "isTrue", "true", "correct", "value") ?: true,
            why = card.text("why", "explanation", "reason").orEmpty(),
        )
        Game.TYPE_IT -> CardDraft(
            game = Game.TYPE_IT,
            question = card.text("question", "front", "prompt").orEmpty(),
            answer = card.text("answer", "back", "correct").orEmpty(),
            alsoRight = card.texts("also", "alsoRight", "accepted", "alternatives").joinToString(", "),
        )
        Game.FILL_THE_GAP -> CardDraft(
            game = Game.FILL_THE_GAP,
            question = gapSentence(card),
            translation = card.text("extra", "translation", "hint", "note").orEmpty(),
            wrongList = card.texts("wrong", "wrongAnswers", "wrong_answers", "distractors").joinToString(", "),
        )
        Game.MATCH_PAIRS -> CardDraft(
            game = Game.MATCH_PAIRS,
            pairs = (pairsOf(card) + List(CardDraft.PAIRS) { "" to "" }).take(CardDraft.PAIRS),
        )
        else -> null
    }

    /** The "game" field, in any of the ways an AI might write it; or guessed from the fields. */
    private fun gameOf(card: JsonObject): Game? {
        val name = card.text("game", "type", "kind")?.lowercase()?.filter { it.isLetterOrDigit() }
        return when (name) {
            "pick", "pickfrom4", "pickfromfour", "pickmeaning", "multiplechoice", "choice", "mcq" -> Game.PICK_MEANING
            "truefalse", "tf", "trueorfalse", "boolean" -> Game.TRUE_FALSE
            "type", "typeit", "typed", "write", "shortanswer" -> Game.TYPE_IT
            "gap", "fillthegap", "fillgap", "fill", "cloze", "fillintheblank" -> Game.FILL_THE_GAP
            "match", "matchpairs", "pairs", "matching" -> Game.MATCH_PAIRS
            null -> when {
                card.has("statement") -> Game.TRUE_FALSE
                card.has("sentence") -> Game.FILL_THE_GAP
                card.has("pairs") -> Game.MATCH_PAIRS
                card.has("question", "front") && card.has("answer", "back") -> Game.PICK_MEANING
                else -> null
            }
            else -> null
        }
    }

    /** "The [Romans] built…", or a sentence with ___ plus the answer, or sentence + answer word. */
    private fun gapSentence(card: JsonObject): String {
        val sentence = card.text("sentence", "question", "front").orEmpty()
        if ('[' in sentence) return sentence
        val answer = card.text("answer", "word", "missing").orEmpty().trim()
        if (answer.isEmpty()) return sentence
        val blank = Regex("_{2,}").find(sentence)
        return when {
            blank != null -> sentence.replaceRange(blank.range, "[$answer]")
            sentence.contains(answer, ignoreCase = true) -> {
                val at = sentence.indexOf(answer, ignoreCase = true)
                sentence.replaceRange(at, at + answer.length, "[${sentence.substring(at, at + answer.length)}]")
            }
            else -> sentence
        }
    }

    /** [["a","b"], …], [{"left":"a","right":"b"}, …] or {"a":"b", …}. */
    private fun pairsOf(card: JsonObject): List<Pair<String, String>> {
        val raw = card["pairs"] ?: card["matches"] ?: return emptyList()
        return when (raw) {
            is JsonObject -> raw.entries.mapNotNull { (left, right) -> right.asText()?.let { left to it } }
            is JsonArray -> raw.mapNotNull { pair ->
                when (pair) {
                    is JsonArray -> {
                        val left = pair.getOrNull(0)?.asText()
                        val right = pair.getOrNull(1)?.asText()
                        if (left != null && right != null) left to right else null
                    }
                    is JsonObject -> {
                        val left = pair.text("left", "a", "term", "question", "front")
                        val right = pair.text("right", "b", "definition", "answer", "back")
                        if (left != null && right != null) left to right else null
                    }
                    else -> null
                }
            }
            else -> emptyList()
        }
    }

    private fun deckKind(name: String?): DeckKind = when (name?.lowercase()?.trim()) {
        "language", "languages", "vocabulary", "words" -> DeckKind.LANGUAGE
        "trivia", "fun", "facts", "fun facts" -> DeckKind.TRIVIA
        else -> DeckKind.STUDY
    }

    // JSON helpers: first matching key wins.

    private fun JsonObject.has(vararg keys: String) = keys.any { key -> this[key].let { it != null && it != JsonNull } }

    private fun JsonObject.text(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { this[it]?.asText() }?.trim()

    private fun JsonObject.texts(vararg keys: String): List<String> {
        val value = keys.firstNotNullOfOrNull { this[it] } ?: return emptyList()
        return when (value) {
            is JsonArray -> value.mapNotNull { it.asText()?.trim() }.filter { it.isNotEmpty() }
            else -> value.asText()?.let { CardDrafts.splitList(it) }.orEmpty()
        }
    }

    private fun JsonObject.array(vararg keys: String): JsonArray? = keys.firstNotNullOfOrNull { this[it] as? JsonArray }

    private fun JsonObject.truth(vararg keys: String): Boolean? = keys.firstNotNullOfOrNull { key ->
        val value = this[key] as? JsonPrimitive ?: return@firstNotNullOfOrNull null
        value.booleanOrNull ?: when (value.contentOrNull?.lowercase()?.trim()) {
            "true", "yes", "t", "1" -> true
            "false", "no", "f", "0" -> false
            else -> null
        }
    }

    private fun JsonElement.asText(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
}
