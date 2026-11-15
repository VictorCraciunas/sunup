package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class GameEligibilityTest {

    private val spanish = Deck(id = "d", name = "Spanish", kind = DeckKind.LANGUAGE, targetLanguage = "es")
    private val biology = Deck(id = "b", name = "Biology", kind = DeckKind.STUDY)

    private val word = Note(
        id = "n",
        deckId = "d",
        type = NoteType.WORD,
        front = "el amanecer",
        back = "sunrise",
        example = "Me despierto con el amanecer.",
    )
    private val question = Note(
        id = "q",
        deckId = "b",
        type = NoteType.QUESTION,
        front = "What does the mitochondria do?",
        back = "It makes energy for the cell from food",
    )

    @Test
    fun smallDeckOnlyGetsTypeIt() {
        val games = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 3, hasPlus = false)

        assertThat(games).isEqualTo(setOf(Game.TYPE_IT))
    }

    @Test
    fun fourNotesUnlockTheChoiceGames() {
        val games = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 4, hasPlus = false)

        assertThat(games).contains(Game.PICK_MEANING)
        assertThat(games).contains(Game.TRUE_FALSE)
        assertThat(games).contains(Game.MATCH_PAIRS)
        assertThat(games).contains(Game.FILL_THE_GAP)
    }

    @Test
    fun geographyDecksOnlyPlayTheMapGames() {
        val europe = Deck(id = "g", name = "Europe", kind = DeckKind.GEOGRAPHY, builtInPackId = GeoRegion.EUROPE.packId)
        val portugal = Note(id = "pt", deckId = "g", type = NoteType.QUESTION, front = "Portugal", back = "PT")

        val games = GameEligibility.eligibleGames(portugal, Direction.FORWARD, europe, notesInDeck = 40, hasPlus = true)

        assertThat(games).isEqualTo(setOf(Game.NAME_ON_MAP, Game.FIND_ON_MAP))
    }

    @Test
    fun geographyNamesNewCountriesAndFindsKnownOnes() {
        val mapGames = setOf(Game.NAME_ON_MAP, Game.FIND_ON_MAP)
        val state = ReviewState(noteId = "pt", direction = Direction.FORWARD)

        assertThat(GameEligibility.preferredGame(state, mapGames)).isEqualTo(Game.NAME_ON_MAP)
        assertThat(GameEligibility.preferredGame(state.copy(level = MasteryLevel.MATCH), mapGames)).isEqualTo(Game.FIND_ON_MAP)
        assertThat(GameEligibility.preferredGame(state.copy(level = MasteryLevel.RECALL), mapGames)).isEqualTo(Game.FIND_ON_MAP)
    }

    @Test
    fun wordDecksNeverPlayTheMap() {
        val games = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 10, hasPlus = true)

        assertThat(games).doesNotContain(Game.FIND_ON_MAP)
        assertThat(games).doesNotContain(Game.NAME_ON_MAP)
    }

    @Test
    fun fillTheGapNeedsTheWordInItsExampleAndIsOnlyAskedOnce() {
        val conjugated = word.copy(front = "despertarse", example = "Me despierto temprano.")
        val noExample = word.copy(example = null)

        assertThat(GameEligibility.eligibleGames(conjugated, Direction.FORWARD, spanish, 10, false))
            .doesNotContain(Game.FILL_THE_GAP)
        assertThat(GameEligibility.eligibleGames(noExample, Direction.FORWARD, spanish, 10, false))
            .doesNotContain(Game.FILL_THE_GAP)
        assertThat(GameEligibility.eligibleGames(word, Direction.REVERSE, spanish, 10, false))
            .doesNotContain(Game.FILL_THE_GAP)
    }

    @Test
    fun plusGamesNeedPlus() {
        val free = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 10, hasPlus = false)
        val plus = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 10, hasPlus = true)

        assertThat(free).doesNotContain(Game.BUILD_THE_SENTENCE)
        assertThat(free).doesNotContain(Game.LISTEN_AND_PICK)
        assertThat(plus).contains(Game.BUILD_THE_SENTENCE)
        assertThat(plus).contains(Game.LISTEN_AND_PICK)
    }

    @Test
    fun sayItAsksForTheWordInTheLanguageBeingLearned() {
        val forward = GameEligibility.eligibleGames(word, Direction.FORWARD, spanish, notesInDeck = 10, hasPlus = true)
        val reverse = GameEligibility.eligibleGames(word, Direction.REVERSE, spanish, notesInDeck = 10, hasPlus = true)

        assertThat(forward).doesNotContain(Game.SAY_IT)
        assertThat(reverse).contains(Game.SAY_IT)
    }

    @Test
    fun longAnswersAreNotTyped() {
        val games = GameEligibility.eligibleGames(question, Direction.FORWARD, biology, notesInDeck = 10, hasPlus = true)

        assertThat(games).doesNotContain(Game.TYPE_IT)
        assertThat(games).doesNotContain(Game.BUILD_THE_SENTENCE)
        assertThat(games).doesNotContain(Game.LISTEN_AND_PICK)
        assertThat(games).contains(Game.PICK_MEANING)
    }

    @Test
    fun deckSettingsCanTurnGamesOff() {
        val noTyping = spanish.copy(allowedGames = Game.entries.toSet() - Game.TYPE_IT)

        val games = GameEligibility.eligibleGames(word, Direction.FORWARD, noTyping, notesInDeck = 10, hasPlus = false)

        assertThat(games).doesNotContain(Game.TYPE_IT)
    }

    @Test
    fun levelUpPathPicksPickThenMatchThenType() {
        val all = Game.entries.toSet()
        val state = ReviewState(noteId = "n", direction = Direction.FORWARD)

        assertThat(GameEligibility.preferredGame(state, all)).isEqualTo(Game.PICK_MEANING)
        assertThat(GameEligibility.preferredGame(state.copy(level = MasteryLevel.MATCH), all)).isEqualTo(Game.MATCH_PAIRS)
        assertThat(GameEligibility.preferredGame(state.copy(level = MasteryLevel.RECALL), all)).isEqualTo(Game.TYPE_IT)
    }

    @Test
    fun onlyKnownCardsInFastGamesStopTheAlarm() {
        val known = ReviewState(noteId = "n", direction = Direction.FORWARD, step = 2)
        val new = ReviewState(noteId = "n", direction = Direction.FORWARD)

        assertThat(GameEligibility.canSilenceAlarm(Game.PICK_MEANING, known)).isTrue()
        assertThat(GameEligibility.canSilenceAlarm(Game.PICK_MEANING, new)).isFalse()
        assertThat(GameEligibility.canSilenceAlarm(Game.TYPE_IT, known)).isFalse()
    }
}
