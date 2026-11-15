package dev.victorc.sunup.core.data

import dev.victorc.sunup.core.domain.learning.Country
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.NoteType

/**
 * A built-in geography deck: one note per country in the region (front = name,
 * back = country code, wrong answers = its closest neighbours in the region), all new.
 * Loading it again updates the notes and keeps what was learned.
 */
class GeoDeckSeeder(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
) {

    /** [countries] come from the map (GeoAtlas.countries), biggest first. */
    suspend fun seed(region: GeoRegion, countries: List<Country>): Deck {
        val deck = Deck(
            id = region.packId,
            name = "Geography · ${region.title}",
            kind = DeckKind.GEOGRAPHY,
            builtInPackId = region.packId,
        )
        decks.saveDeck(deck)

        val nameOf = countries.associate { it.id to it.name }
        countries
            .sortedByDescending { it.areaKm2 }
            .forEachIndexed { rank, country ->
                val note = Note(
                    // The size rank in the id lets Name the country fill in with similar-size countries.
                    id = "${deck.id}-${rank.toString().padStart(3, '0')}-${country.id}",
                    deckId = deck.id,
                    type = NoteType.QUESTION,
                    front = country.name,
                    back = country.id,
                    // Name the country offers neighbours, so the shape and place really matter.
                    wrongAnswers = country.nearby.mapNotNull { nameOf[it] }.take(NEIGHBOURS_KEPT),
                )
                notes.saveNote(note, bothDirections = false)
            }
        return deck
    }

    private companion object {
        const val NEIGHBOURS_KEPT = 6
    }
}
