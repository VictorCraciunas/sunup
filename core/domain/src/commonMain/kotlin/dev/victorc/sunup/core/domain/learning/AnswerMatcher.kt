package dev.victorc.sunup.core.domain.learning

/** The outcome of checking a typed (or spoken) answer. */
sealed interface AnswerCheck {
    data object Correct : AnswerCheck

    /** Counts as right, with a gentle note showing the exact spelling. */
    data class Close(val expected: String, val reason: CloseReason) : AnswerCheck

    data object Wrong : AnswerCheck
}

enum class CloseReason { ACCENTS, TYPO }

/**
 * Forgiving answer checking for Type it and Say it (PRD: small typos and missing accents
 * count as right, with a note). Ignores capitals, punctuation, extra spaces and a leading
 * article ("el amanecer" = "amanecer", "to wake up" = "wake up").
 */
object AnswerMatcher {

    fun check(input: String, accepted: List<String>, language: String? = null): AnswerCheck {
        val typed = normalize(input, language)
        if (typed.isEmpty()) return AnswerCheck.Wrong

        var best: AnswerCheck = AnswerCheck.Wrong
        for (answer in accepted) {
            val expected = normalize(answer, language)
            if (expected.isEmpty()) continue
            when {
                typed == expected -> return AnswerCheck.Correct
                stripAccents(typed) == stripAccents(expected) ->
                    best = AnswerCheck.Close(answer, CloseReason.ACCENTS)
                best == AnswerCheck.Wrong && isTypo(stripAccents(typed), stripAccents(expected)) ->
                    best = AnswerCheck.Close(answer, CloseReason.TYPO)
            }
        }
        return best
    }

    internal fun normalize(text: String, language: String?): String {
        val words = text
            .lowercase()
            .replace('’', '\'')
            .let { stripElision(it, language) }
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        val articles = ARTICLES[language].orEmpty()
        val withoutArticle = if (words.size > 1 && words.first() in articles) words.drop(1) else words
        return withoutArticle.joinToString(" ")
    }

    /** French and Italian glue articles on with an apostrophe: "l'amour" → "amour". */
    private fun stripElision(text: String, language: String?): String {
        if (language != "fr" && language != "it") return text
        val trimmed = text.trimStart()
        return ELISIONS.firstOrNull { trimmed.startsWith(it) }?.let { trimmed.removePrefix(it) } ?: text
    }

    internal fun stripAccents(text: String): String = buildString {
        for (char in text) append(ACCENTS[char] ?: char.toString())
    }

    private fun isTypo(typed: String, expected: String): Boolean {
        val allowed = when {
            expected.length < 4 -> 0
            expected.length < 8 -> 1
            else -> 2
        }
        return allowed > 0 && levenshtein(typed, expected) <= allowed
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private val ARTICLES = mapOf(
        "en" to setOf("the", "a", "an", "to"),
        "es" to setOf("el", "la", "los", "las", "un", "una", "unos", "unas"),
        "fr" to setOf("le", "la", "les", "un", "une", "des"),
        "it" to setOf("il", "lo", "la", "i", "gli", "le", "un", "uno", "una"),
        "de" to setOf("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen"),
    )

    private val ELISIONS = listOf("l'", "un'")

    private val ACCENTS: Map<Char, String> = buildMap {
        "àáâãäåā".forEach { put(it, "a") }
        "çćč".forEach { put(it, "c") }
        "èéêëēė".forEach { put(it, "e") }
        "ìíîïī".forEach { put(it, "i") }
        "ñń".forEach { put(it, "n") }
        "òóôõöōø".forEach { put(it, "o") }
        "ùúûüū".forEach { put(it, "u") }
        "ýÿ".forEach { put(it, "y") }
        "șş".forEach { put(it, "s") }
        "țţ".forEach { put(it, "t") }
        "ă".forEach { put(it, "a") }
        put('ß', "ss")
        put('œ', "oe")
        put('æ', "ae")
    }
}
