package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class GameEligibilityTest {

    @Test
    fun cardsCanBeAnyOfTheFiveGames() {
        assertThat(GameEligibility.CARD_GAMES).isEqualTo(
            listOf(Game.PICK_MEANING, Game.TRUE_FALSE, Game.TYPE_IT, Game.FILL_THE_GAP, Game.MATCH_PAIRS),
        )
    }

    @Test
    fun onlyPickAndTypeCanBeAskedBothWays() {
        assertThat(GameEligibility.canReverse(Game.PICK_MEANING)).isTrue()
        assertThat(GameEligibility.canReverse(Game.TYPE_IT)).isTrue()
        assertThat(GameEligibility.canReverse(Game.TRUE_FALSE)).isFalse()
        assertThat(GameEligibility.canReverse(Game.MATCH_PAIRS)).isFalse()
    }

    @Test
    fun geographyNamesNewCountriesAndFindsKnownOnes() {
        val mapGames = setOf(Game.NAME_ON_MAP, Game.FIND_ON_MAP)
        val state = ReviewState(noteId = "pt", direction = Direction.FORWARD)

        assertThat(GameEligibility.mapGame(state, mapGames)).isEqualTo(Game.NAME_ON_MAP)
        assertThat(GameEligibility.mapGame(state.copy(level = MasteryLevel.MATCH), mapGames)).isEqualTo(Game.FIND_ON_MAP)
        assertThat(GameEligibility.mapGame(state.copy(level = MasteryLevel.RECALL), mapGames)).isEqualTo(Game.FIND_ON_MAP)
    }

    @Test
    fun geographyFollowsTheDecksAllowedGames() {
        val state = ReviewState(noteId = "pt", direction = Direction.FORWARD)

        assertThat(GameEligibility.mapGame(state, setOf(Game.FIND_ON_MAP))).isEqualTo(Game.FIND_ON_MAP)
    }
}
