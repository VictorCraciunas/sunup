package dev.victorc.sunup.feature.wakeup.domain

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import dev.victorc.sunup.core.domain.Result
import dev.victorc.sunup.core.domain.learning.CardBuilder
import dev.victorc.sunup.core.domain.learning.CardContent
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.NoteType
import dev.victorc.sunup.core.domain.learning.ReviewRecord
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

private class FakeDecks(private val decks: List<Deck>, private val fail: Boolean = false) : DeckRepository {
    override fun observeDecks(): Flow<List<Deck>> {
        if (fail) error("database locked")
        return flowOf(decks)
    }
    override suspend fun getDeck(id: String): Deck? = decks.firstOrNull { it.id == id }
    override suspend fun saveDeck(deck: Deck): EmptyResult<DataError.Local> = Result.Success(Unit)
}

private class FakeNotes(private val notes: List<Note>) : NoteRepository {
    override suspend fun notesInDeck(deckId: String) = notes.filter { it.deckId == deckId }
    override suspend fun saveNote(note: Note, bothDirections: Boolean): EmptyResult<DataError.Local> = Result.Success(Unit)
}

private class FakeReviews(private val states: List<ReviewState>) : ReviewRepository {
    override suspend fun statesInDeck(deckId: String) = states
    override suspend fun dueCards(deckId: String, today: LocalDate) = states.filter { (it.due ?: today) <= today }
    override suspend fun saveStates(states: List<ReviewState>): EmptyResult<DataError.Local> = Result.Success(Unit)
    override suspend fun recordReview(record: ReviewRecord, newState: ReviewState): EmptyResult<DataError.Local> =
        Result.Success(Unit)
}

private object FixedClock : Clock {
    // Tuesday 3 Nov 2026, 07:00 UTC
    override fun now(): Instant = Instant.parse("2026-11-03T07:00:00Z")
}

class WakeSessionPlannerTest {

    private val today = LocalDate(2026, 11, 3)
    private val spanish = Deck(id = "es", name = "Spanish", kind = DeckKind.LANGUAGE, targetLanguage = "es")
    private val words = listOf("sol" to "sun", "luna" to "moon", "nube" to "cloud", "cielo" to "sky", "mar" to "sea", "río" to "river")
        .mapIndexed { i, (f, b) -> Note(id = "n$i", deckId = "es", type = NoteType.WORD, front = f, back = b) }

    private fun known(i: Int) = ReviewState("n$i", Direction.FORWARD, step = 2, due = today)
    private fun new(i: Int) = ReviewState("n$i", Direction.FORWARD)

    private fun planner(
        decks: List<Deck> = listOf(spanish),
        notes: List<Note> = words,
        states: List<ReviewState>,
        decksFail: Boolean = false,
    ) = WakeSessionPlanner(
        decks = FakeDecks(decks, decksFail),
        notes = FakeNotes(notes),
        reviews = FakeReviews(states),
        cards = CardBuilder(Random(7)),
        clock = FixedClock,
        timeZone = { TimeZone.UTC },
    )

    private fun alarm(mode: WakeMode) = WakeAlarm(id = "a", time = LocalTime(7, 0), mode = mode)

    @Test
    fun knownCardStopsTheAlarmAndBonusHasPracticeAndANewWord() = runTest {
        val plan = planner(states = listOf(known(0), known(1), new(2), new(3), new(4), new(5)))
            .plan(alarm(WakeMode.NORMAL))

        assertThat(plan.silencing).hasSize(1)
        val silencing = plan.silencing.single()
        assertThat(silencing.state?.isKnown).isNotNull().isTrue()
        assertThat(silencing.content).isInstanceOf(CardContent.PickMeaning::class)

        assertThat(plan.bonus).hasSize(2)
        assertThat(plan.bonus.map { it.label }).isEqualTo(listOf("Practice", "New word"))
        assertThat(plan.bonus.none { it.state == silencing.state }).isTrue()
    }

    @Test
    fun heavySleeperGetsThreeSilencingCardsAndNoBonus() = runTest {
        val plan = planner(states = listOf(known(0), known(1), known(2), new(3)))
            .plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing).hasSize(3)
        assertThat(plan.silencing.map { it.state }.distinct()).hasSize(3)
        assertThat(plan.bonus).hasSize(0)
    }

    @Test
    fun gentleModeHasNoBonusRound() = runTest {
        val plan = planner(states = listOf(known(0), new(1))).plan(alarm(WakeMode.GENTLE))

        assertThat(plan.silencing).hasSize(1)
        assertThat(plan.bonus).hasSize(0)
    }

    @Test
    fun nothingLearnedYetFallsBackToQuickMath() = runTest {
        val plan = planner(states = words.indices.map { new(it) }).plan(alarm(WakeMode.NORMAL))

        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
        assertThat(plan.silencing.single().note).isNull()
    }

    @Test
    fun deckTooSmallForChoicesFallsBackToQuickMath() = runTest {
        val plan = planner(notes = words.take(3), states = listOf(known(0), known(1), known(2)))
            .plan(alarm(WakeMode.GENTLE))

        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
    }

    @Test
    fun noDeckFallsBackToQuickMath() = runTest {
        val plan = planner(decks = emptyList(), states = emptyList()).plan(alarm = null)

        assertThat(plan.mode).isEqualTo(WakeMode.NORMAL)
        assertThat(plan.deck).isNull()
        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
    }

    @Test
    fun brokenDatabaseStillGivesAPlayableCard() = runTest {
        val plan = planner(states = emptyList(), decksFail = true).plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing).hasSize(3)
        assertThat(plan.silencing.all { it.content is CardContent.QuickMath }).isTrue()
    }

    @Test
    fun gradeIsGoodOnlyWhenRightFirstTime() {
        assertThat(gradeFor(0)).isEqualTo(Grade.GOOD)
        assertThat(gradeFor(1)).isEqualTo(Grade.AGAIN)
    }
}
