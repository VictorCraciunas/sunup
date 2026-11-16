package dev.victorc.sunup.core.domain.learning

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class DeckLimitsTest {

    private fun own(n: Int) = List(n) { Deck(id = "d$it", name = "Deck $it", kind = DeckKind.STUDY) }
    private val europe = Deck(id = "geo-europe", name = "Europe", kind = DeckKind.GEOGRAPHY, builtInPackId = "geo-europe")

    @Test
    fun mapsDontCount() {
        assertThat(DeckLimits.ownDecks(own(2) + europe)).isEqualTo(2)
    }

    @Test
    fun fiveOwnDecksIsTheLimit() {
        assertThat(DeckLimits.canAddDeck(own(4) + europe)).isTrue()
        assertThat(DeckLimits.canAddDeck(own(5))).isFalse()
    }
}
