package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class AnswerMatcherTest {

    @Test
    fun exactAnswerIsCorrect() {
        assertThat(AnswerMatcher.check("mañana", listOf("mañana"), "es")).isEqualTo(AnswerCheck.Correct)
    }

    @Test
    fun capitalsSpacesAndPunctuationAreIgnored() {
        assertThat(AnswerMatcher.check("  Mañana! ", listOf("mañana"), "es")).isEqualTo(AnswerCheck.Correct)
    }

    @Test
    fun missingAccentIsCloseWithANote() {
        assertThat(AnswerMatcher.check("manana", listOf("mañana"), "es"))
            .isEqualTo(AnswerCheck.Close("mañana", CloseReason.ACCENTS))
    }

    @Test
    fun leadingArticleIsOptional() {
        assertThat(AnswerMatcher.check("amanecer", listOf("el amanecer"), "es")).isEqualTo(AnswerCheck.Correct)
        assertThat(AnswerMatcher.check("el amanecer", listOf("amanecer"), "es")).isEqualTo(AnswerCheck.Correct)
        assertThat(AnswerMatcher.check("wake up", listOf("to wake up"), "en")).isEqualTo(AnswerCheck.Correct)
    }

    @Test
    fun frenchElisionIsOptional() {
        assertThat(AnswerMatcher.check("amour", listOf("l'amour"), "fr")).isEqualTo(AnswerCheck.Correct)
        assertThat(AnswerMatcher.check("l’amour", listOf("l'amour"), "fr")).isEqualTo(AnswerCheck.Correct)
    }

    @Test
    fun aSingleArticleOnItsOwnIsNotStripped() {
        // "the" alone must not become an empty answer that matches anything.
        assertThat(AnswerMatcher.check("the", listOf("the"), "en")).isEqualTo(AnswerCheck.Correct)
        assertThat(AnswerMatcher.check("a", listOf("the"), "en")).isEqualTo(AnswerCheck.Wrong)
    }

    @Test
    fun oneTypoInAMediumWordIsClose() {
        assertThat(AnswerMatcher.check("desayno", listOf("desayuno"), "es"))
            .isEqualTo(AnswerCheck.Close("desayuno", CloseReason.TYPO))
    }

    @Test
    fun shortWordsMustBeExact() {
        assertThat(AnswerMatcher.check("sol", listOf("sal"), "es")).isEqualTo(AnswerCheck.Wrong)
    }

    @Test
    fun aDifferentWordIsWrong() {
        assertThat(AnswerMatcher.check("noche", listOf("mañana"), "es")).isEqualTo(AnswerCheck.Wrong)
    }

    @Test
    fun anyAcceptedAnswerCounts() {
        assertThat(AnswerMatcher.check("wake up", listOf("to awaken", "to wake up"), "en"))
            .isEqualTo(AnswerCheck.Correct)
    }

    @Test
    fun emptyInputIsWrong() {
        assertThat(AnswerMatcher.check("   ", listOf("sol"), "es")).isEqualTo(AnswerCheck.Wrong)
    }
}
