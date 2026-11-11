package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class SpacedRepetitionTest {

    private val today = LocalDate(2026, 11, 3)
    private val new = ReviewState(noteId = "n", direction = Direction.FORWARD)

    private fun learned(step: Int, level: MasteryLevel = MasteryLevel.MATCH) =
        new.copy(step = step, level = level, due = today, reviews = 3)

    @Test
    fun newCardRightFirstTimeComesBackIn3DaysAndLevelsUp() {
        val next = SpacedRepetition.review(new, Grade.GOOD, today)

        assertThat(next.due).isEqualTo(LocalDate(2026, 11, 6))
        assertThat(next.step).isEqualTo(1)
        assertThat(next.level).isEqualTo(MasteryLevel.MATCH)
        assertThat(next.reviews).isEqualTo(1)
    }

    @Test
    fun newCardWrongComesBackTomorrowWithoutCountingALapse() {
        val next = SpacedRepetition.review(new, Grade.AGAIN, today)

        assertThat(next.due).isEqualTo(LocalDate(2026, 11, 4))
        assertThat(next.lapses).isEqualTo(0)
        assertThat(next.level).isEqualTo(MasteryLevel.RECOGNIZE)
    }

    @Test
    fun flipAndRateIntervalsMatchThePrdForALearnedCard() {
        val card = learned(step = 1)

        assertThat(SpacedRepetition.review(card, Grade.AGAIN, today).due).isEqualTo(LocalDate(2026, 11, 4))
        assertThat(SpacedRepetition.review(card, Grade.HARD, today).due).isEqualTo(LocalDate(2026, 11, 6))
        assertThat(SpacedRepetition.review(card, Grade.GOOD, today).due).isEqualTo(LocalDate(2026, 11, 11))
    }

    @Test
    fun forgettingALearnedCardCountsALapseAndDropsALevel() {
        val next = SpacedRepetition.review(learned(step = 3, level = MasteryLevel.RECALL), Grade.AGAIN, today)

        assertThat(next.step).isEqualTo(0)
        assertThat(next.lapses).isEqualTo(1)
        assertThat(next.level).isEqualTo(MasteryLevel.MATCH)
    }

    @Test
    fun topStepStaysAtTheLongestInterval() {
        val top = learned(step = SpacedRepetition.INTERVAL_DAYS.lastIndex, level = MasteryLevel.RECALL)

        val next = SpacedRepetition.review(top, Grade.GOOD, today)

        assertThat(next.step).isEqualTo(SpacedRepetition.INTERVAL_DAYS.lastIndex)
        assertThat(next.level).isEqualTo(MasteryLevel.RECALL)
        assertThat(next.due).isEqualTo(LocalDate(2027, 2, 1))
    }

    @Test
    fun examDateKeepsTheCardComingBackBeforeTheExam() {
        // Would be 21 days, but the exam is in a week: meet halfway to the day before it.
        val next = SpacedRepetition.review(learned(step = 2), Grade.GOOD, today, examDate = LocalDate(2026, 11, 10))

        assertThat(next.due).isEqualTo(LocalDate(2026, 11, 6))
    }

    @Test
    fun examTomorrowMeansOneLastLookOnExamMorning() {
        val next = SpacedRepetition.review(learned(step = 2), Grade.GOOD, today, examDate = LocalDate(2026, 11, 4))

        assertThat(next.due).isEqualTo(LocalDate(2026, 11, 4))
    }

    @Test
    fun pastExamIsIgnored() {
        val next = SpacedRepetition.review(learned(step = 1), Grade.GOOD, today, examDate = LocalDate(2026, 10, 1))

        assertThat(next.due).isEqualTo(LocalDate(2026, 11, 11))
    }
}
