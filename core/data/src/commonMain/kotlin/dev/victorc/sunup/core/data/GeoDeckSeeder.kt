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
 * back = country code), all new. Loading it again adds nothing twice.
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

        countries
            .sortedByDescending { it.areaKm2 }
            .forEachIndexed { rank, country ->
                val note = Note(
                    // The rank in the id keeps "biggest first" when new countries are picked.
                    id = "${deck.id}-${rank.toString().padStart(3, '0')}-${country.id}",
                    deckId = deck.id,
                    type = NoteType.QUESTION,
                    front = country.name,
                    back = country.id,
                )
                notes.saveNote(note, bothDirections = false)
            }
        return deck
    }
}
