package dev.victorc.sunup.feature.decks.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteType
import kotlin.test.Test

class CardDraftTest {

    private fun save(draft: CardDraft): Note =
        (CardDrafts.toNote(draft, id = "c1", deckId = "d", deckKind = DeckKind.LANGUAGE) as Result.Success).data

    @Test
    fun pickNeedsAQuestionAndAnAnswer() {
        assertThat(CardDrafts.check(CardDraft(Game.PICK_MEANING))).isEqualTo(CardProblem.NO_QUESTION)
        assertThat(CardDrafts.check(CardDraft(Game.PICK_MEANING, question = "el sol"))).isEqualTo(CardProblem.NO_ANSWER)
        assertThat(CardDrafts.check(CardDraft(Game.PICK_MEANING, question = "el sol", answer = "the sun"))).isNull()
    }

    @Test
    fun pickKeepsOnlyTheWrongAnswersThatWereTyped() {
        val note = save(CardDraft(Game.PICK_MEANING, question = " el sol ", answer = "the sun", wrong = listOf("the moon", " ", "")))

        assertThat(note.front).isEqualTo("el sol")
        assertThat(note.wrongAnswers).containsExactly("the moon")
        assertThat(note.game).isEqualTo(Game.PICK_MEANING)
        assertThat(note.type).isEqualTo(NoteType.WORD)
    }

    @Test
    fun trueOrFalseKeepsTheStatementTruthAndWhy() {
        val note = save(CardDraft(Game.TRUE_FALSE, question = "The sun is a star.", isTrue = false, why = ""))

        assertThat(note.front).isEqualTo("The sun is a star.")
        assertThat(note.isTrue).isEqualTo(false)
        assertThat(note.explanation).isNull()
    }

    @Test
    fun typeItSplitsTheOtherRightAnswers() {
        val note = save(CardDraft(Game.TYPE_IT, question = "to wake up", answer = "despertarse", alsoRight = "despertar; levantarse,  "))

        assertThat(note.acceptedAnswers).containsExactly("despertar", "levantarse")
    }

    @Test
    fun fillTheGapNeedsTheWordInBrackets() {
        assertThat(CardDrafts.check(CardDraft(Game.FILL_THE_GAP, question = "Me despierto con el amanecer.")))
            .isEqualTo(CardProblem.NO_BRACKETS)

        val note = save(CardDraft(Game.FILL_THE_GAP, question = "Me despierto con el [amanecer].", wrongList = "atardecer, reloj"))
        assertThat(note.wrongAnswers).containsExactly("atardecer", "reloj")
    }

    @Test
    fun matchPairsNeedsTwoCompleteDifferentPairs() {
        val one = CardDraft(Game.MATCH_PAIRS, pairs = listOf("sol" to "sun", "luna" to "", "" to "", "" to ""))
        val twice = CardDraft(Game.MATCH_PAIRS, pairs = listOf("sol" to "sun", "Sol" to "star", "" to "", "" to ""))
        val ok = CardDraft(Game.MATCH_PAIRS, pairs = listOf("sol" to "sun", "luna" to "moon", "" to "", "" to ""))

        assertThat(CardDrafts.check(one)).isEqualTo(CardProblem.TOO_FEW_PAIRS)
        assertThat(CardDrafts.check(twice)).isEqualTo(CardProblem.SAME_WORD_TWICE)
        assertThat(save(ok).pairs).containsExactly("sol" to "sun", "luna" to "moon")
    }

    @Test
    fun aSavedCardOpensAgainInTheSameForm() {
        val draft = CardDraft(Game.PICK_MEANING, question = "el sol", answer = "the sun", wrong = listOf("the moon", "", ""))

        assertThat(CardDraft.from(save(draft))).isEqualTo(draft)
    }

    @Test
    fun anotherGamesFieldsAreNotSaved() {
        val note = save(CardDraft(Game.TRUE_FALSE, question = "The sun is a star.", answer = "left over from Pick"))

        assertThat(note.back).isEqualTo("")
        assertThat(note.wrongAnswers.isNotEmpty()).isFalse()
    }
}
