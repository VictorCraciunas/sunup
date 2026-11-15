package dev.victorc.sunup.core.domain.learning

/** A note's example sentence with its word taken out, for Fill the gap. */
data class SentenceGap(
    /** The sentence with the word replaced by [BLANK]. */
    val sentence: String,
    /** The word exactly as it appears in the sentence. */
    val answer: String,
    /** True when the word was found without its leading article ("café" for "el café"). */
    val withoutArticle: Boolean,
) {
    companion object {
        const val BLANK = "_____"

        private val BRACKETED = Regex("\\[([^\\[\\]]+)]")

        /**
         * Fill the gap cards: the missing word is written in brackets, e.g.
         * "Me despierto con el [amanecer]." Null when there's no bracketed word.
         */
        fun fromBrackets(text: String): SentenceGap? {
            val match = BRACKETED.find(text) ?: return null
            val word = match.groupValues[1].trim()
            if (word.isEmpty()) return null
            val sentence = text.replaceRange(match.range, BLANK).replace("[", "").replace("]", "")
            return SentenceGap(sentence = sentence, answer = word, withoutArticle = false)
        }

        /**
         * Finds the note's word in its example sentence, first as written ("el amanecer"),
         * then without a leading article ("café" in "Necesito un café."). Null if the word
         * isn't in the sentence, e.g. a verb that appears conjugated.
         */
        fun find(note: Note): SentenceGap? {
            if (note.type != NoteType.WORD) return null
            val example = note.example?.takeIf { it.isNotBlank() } ?: return null
            val word = note.front.trim()

            locate(example, word)?.let { range ->
                return SentenceGap(example.replaceRange(range, BLANK), example.substring(range), withoutArticle = false)
            }
            val bare = withoutArticle(word) ?: return null
            locate(example, bare)?.let { range ->
                return SentenceGap(example.replaceRange(range, BLANK), example.substring(range), withoutArticle = true)
            }
            return null
        }

        /** "el café" → "café". Null for a single word, which has no article to drop. */
        fun withoutArticle(word: String): String? {
            val parts = word.trim().split(Regex("\\s+"), limit = 2)
            return parts.getOrNull(1)
        }

        /** Case-insensitive, whole words only, so "sol" doesn't match inside "soleado". */
        private fun locate(text: String, word: String): IntRange? {
            if (word.isEmpty()) return null
            var start = text.indexOf(word, ignoreCase = true)
            while (start >= 0) {
                val end = start + word.length
                val before = text.getOrNull(start - 1)
                val after = text.getOrNull(end)
                val wholeWord = (before == null || !before.isLetter()) && (after == null || !after.isLetter())
                if (wholeWord) return start until end
                start = text.indexOf(word, startIndex = start + 1, ignoreCase = true)
            }
            return null
        }
    }
}
