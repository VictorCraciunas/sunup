package dev.victorc.sunup.feature.wakeup.domain

import assertk.assertThat
import assertk.assertions.containsExactly
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
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.MasteryLevel
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
    override suspend fun deleteDeck(id: String): EmptyResult<DataError.Local> = Result.Success(Unit)
}

private class FakeNotes(private val notes: List<Note>) : NoteRepository {
    override suspend fun notesInDeck(deckId: String) = notes.filter { it.deckId == deckId }
    override suspend fun saveNote(note: Note, bothDirections: Boolean): EmptyResult<DataError.Local> = Result.Success(Unit)
    override fun observeNotesInDeck(deckId: String): Flow<List<Note>> = flowOf(notes.filter { it.deckId == deckId })
    override suspend fun getNote(id: String): Note? = notes.firstOrNull { it.id == id }
    override suspend fun deleteNote(id: String): EmptyResult<DataError.Local> = Result.Success(Unit)
    override fun observeCardCounts(): Flow<Map<String, Int>> = flowOf(notes.groupingBy { it.deckId }.eachCount())
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
    private val spanish = Deck(id = "es", name = "Spanish", kind = DeckKind.LANGUAGE)
    private val words = listOf("sol" to "sun", "luna" to "moon", "nube" to "cloud", "cielo" to "sky", "mar" to "sea", "río" to "river")
        .mapIndexed { i, (f, b) ->
            Note(id = "n$i", deckId = "es", type = NoteType.WORD, front = f, back = b, game = Game.PICK_MEANING)
        }

    private fun card(id: String, game: Game, front: String = "", back: String = "", isTrue: Boolean? = null, pairs: List<Pair<String, String>> = emptyList()) =
        Note(id = id, deckId = "es", type = NoteType.QUESTION, front = front, back = back, game = game, isTrue = isTrue, pairs = pairs)

    private val statement = card("tf", Game.TRUE_FALSE, front = "The sun is a star.", isTrue = true)

    private fun planner(
        decks: List<Deck> = listOf(spanish),
        notes: List<Note> = words,
        states: List<ReviewState> = emptyList(),
        decksFail: Boolean = false,
        seed: Int = 3,
    ) = WakeSessionPlanner(
        decks = FakeDecks(decks, decksFail),
        notes = FakeNotes(notes),
        reviews = FakeReviews(states),
        cards = CardBuilder(Random(seed)),
        clock = FixedClock,
        timeZone = { TimeZone.UTC },
        random = Random(seed),
    )

    private fun alarm(mode: WakeMode) = WakeAlarm(id = "a", time = LocalTime(7, 0), mode = mode)

    private fun MorningCard.noteId() = note?.id

    @Test
    fun normalModeIsOneCardThenTwoBonusAllDifferent() = runTest {
        val plan = planner().plan(alarm(WakeMode.NORMAL))

        assertThat(plan.silencing).hasSize(1)
        assertThat(plan.bonus).hasSize(2)
        val ids = (plan.silencing + plan.bonus).map { it.noteId() }
        assertThat(ids.distinct()).hasSize(3)
        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.PickMeaning::class)
    }

    @Test
    fun heavySleeperGetsThreeCardsAndNoBonus() = runTest {
        val plan = planner().plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing).hasSize(3)
        assertThat(plan.silencing.map { it.noteId() }.distinct()).hasSize(3)
        assertThat(plan.bonus).hasSize(0)
    }

    @Test
    fun gentleModeHasNoBonusRound() = runTest {
        val plan = planner().plan(alarm(WakeMode.GENTLE))

        assertThat(plan.silencing).hasSize(1)
        assertThat(plan.bonus).hasSize(0)
    }

    @Test
    fun everyCardPlaysItsOwnGameAndAnyCanStopTheAlarm() = runTest {
        val match = card("m", Game.MATCH_PAIRS, pairs = listOf("sol" to "sun", "luna" to "moon"))
        val plan = planner(notes = listOf(match)).plan(alarm(WakeMode.GENTLE))

        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.MatchPairs::class)
    }

    @Test
    fun tooFewPlayableCardsFillUpWithQuickMath() = runTest {
        val plan = planner(notes = listOf(statement)).plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing.map { it.content.game })
            .containsExactly(Game.TRUE_FALSE, Game.QUICK_MATH, Game.QUICK_MATH)
    }

    @Test
    fun unplayableCardsAreSkipped() = runTest {
        // Two Pick from 4 cards can't find three wrong answers between them.
        val plan = planner(notes = words.take(2)).plan(alarm(WakeMode.NORMAL))

        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
        assertThat(plan.deck).isNull()
    }

    @Test
    fun cardsYouGotWrongComeUpMoreOften() = runTest {
        // n0 was missed yesterday, so it's due today; the others were never answered.
        val missed = ReviewState("n0", Direction.FORWARD, step = 0, due = today, reviews = 2, lapses = 1)
        val firsts = (1..300).map { seed ->
            planner(states = listOf(missed), seed = seed).plan(alarm(WakeMode.GENTLE)).silencing.single().noteId()
        }

        // Weight 3 of 8 in total, against 1 of 8 for each of the others.
        val missedShare = firsts.count { it == "n0" }
        val averageOther = firsts.count { it != "n0" } / 5
        assertThat(missedShare > averageOther * 2).isTrue()
    }

    @Test
    fun cardsAreRandomEachMorning() = runTest {
        val firsts = (1..20).map { seed -> planner(seed = seed).plan(alarm(WakeMode.GENTLE)).silencing.single().noteId() }

        assertThat(firsts.toSet().size > 1).isTrue()
    }

    @Test
    fun cardsAreAlwaysAskedAsWritten() = runTest {
        // An old deck that still says "both ways", with a leftover reverse state.
        val bothWays = spanish.copy(bothDirections = true)
        val reverse = ReviewState("n0", Direction.REVERSE)
        val plan = planner(decks = listOf(bothWays), states = listOf(reverse)).plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing.all { it.state?.direction == Direction.FORWARD }).isTrue()
    }

    @Test
    fun noDeckFallsBackToQuickMath() = runTest {
        val plan = planner(decks = emptyList()).plan(alarm = null)

        assertThat(plan.mode).isEqualTo(WakeMode.NORMAL)
        assertThat(plan.deck).isNull()
        assertThat(plan.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
    }

    @Test
    fun brokenDatabaseStillGivesAPlayableCard() = runTest {
        val plan = planner(decksFail = true).plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing).hasSize(3)
        assertThat(plan.silencing.all { it.content is CardContent.QuickMath }).isTrue()
    }

    @Test
    fun previewShowsOneCardOfEveryGameInTheDeck() = runTest {
        val type = card("t", Game.TYPE_IT, front = "to wake up", back = "despertarse")
        val gap = card("g", Game.FILL_THE_GAP, front = "Me despierto con el [amanecer].")
        val match = card("m", Game.MATCH_PAIRS, pairs = listOf("sol" to "sun", "luna" to "moon"))
        val plan = planner(notes = words + statement + type + gap + match).preview()

        assertThat((plan.silencing + plan.bonus).map { it.content.game }).containsExactly(
            Game.PICK_MEANING, Game.TRUE_FALSE, Game.TYPE_IT, Game.FILL_THE_GAP, Game.MATCH_PAIRS,
        )
    }

    // Geography: ids carry the size rank, like GeoDeckSeeder writes them.
    private val europe = Deck(id = "geo", name = "Europe", kind = DeckKind.GEOGRAPHY, builtInPackId = GeoRegion.EUROPE.packId)
    private val countries = listOf("RU" to "Russia", "UA" to "Ukraine", "FR" to "France", "ES" to "Spain", "PT" to "Portugal")
        .mapIndexed { i, (code, name) ->
            Note(id = "geo-00$i-$code", deckId = "geo", type = NoteType.QUESTION, front = name, back = code)
        }

    private fun geoPlanner(states: List<ReviewState>) =
        planner(decks = listOf(europe), notes = countries, states = states)

    private fun countryOf(card: MorningCard) = (card.content as CardContent.OnMap).countryId

    @Test
    fun geographyStopsTheAlarmOnTheMapFromTheFirstMorning() = runTest {
        val plan = geoPlanner(states = emptyList()).plan(alarm(WakeMode.NORMAL))

        val silencing = plan.silencing.single().content
        // Nothing learned yet: every card is Name the country.
        assertThat(silencing).isInstanceOf(CardContent.NameOnMap::class)
        assertThat((silencing as CardContent.NameOnMap).region).isEqualTo(GeoRegion.EUROPE)
        assertThat(plan.bonus.map { it.content.game }).isEqualTo(listOf(Game.NAME_ON_MAP, Game.NAME_ON_MAP))
        val asked = (plan.silencing + plan.bonus).map { countryOf(it) }
        assertThat(asked.distinct()).hasSize(3)
    }

    @Test
    fun knownCountriesAreFoundOnTheMapInstead() = runTest {
        val known = countries.map { ReviewState(it.id, Direction.FORWARD, step = 2, level = MasteryLevel.MATCH, due = today) }
        val plan = geoPlanner(states = known).plan(alarm(WakeMode.NORMAL))

        assertThat((plan.silencing + plan.bonus).map { it.content.game })
            .isEqualTo(listOf(Game.FIND_ON_MAP, Game.FIND_ON_MAP, Game.FIND_ON_MAP))
    }

    @Test
    fun geographyHeavySleeperFindsThreeDifferentCountries() = runTest {
        val plan = geoPlanner(states = emptyList()).plan(alarm(WakeMode.HEAVY_SLEEPER))

        assertThat(plan.silencing.map { countryOf(it) }.distinct()).hasSize(3)
        assertThat(plan.bonus).hasSize(0)
    }

    @Test
    fun withoutAMapTheAlarmStopsWithQuickMathInstead() = runTest {
        val planner = geoPlanner(states = emptyList())
        val plan = planner.plan(alarm(WakeMode.NORMAL))

        val fallback = planner.withoutUnplayableMaps(plan) { false }

        assertThat(fallback.silencing.single().content).isInstanceOf(CardContent.QuickMath::class)
        assertThat(fallback.bonus).hasSize(0)
    }

    @Test
    fun previewAddsTheMapGamesWhenThereIsAGeographyDeck() = runTest {
        val plan = planner(decks = listOf(europe, spanish), notes = words + countries).preview()

        assertThat(plan.silencing.first().content).isNotNull().isInstanceOf(CardContent.PickMeaning::class)
        assertThat(plan.bonus.takeLast(2).map { it.content.game }).isEqualTo(listOf(Game.NAME_ON_MAP, Game.FIND_ON_MAP))
    }

    @Test
    fun gradeIsGoodOnlyWhenRightFirstTime() {
        assertThat(gradeFor(0)).isEqualTo(Grade.GOOD)
        assertThat(gradeFor(1)).isEqualTo(Grade.AGAIN)
    }
}
