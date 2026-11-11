package dev.victorc.sunup.core.domain.learning

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface DeckRepository {
    fun observeDecks(): Flow<List<Deck>>
    suspend fun getDeck(id: String): Deck?
    suspend fun saveDeck(deck: Deck): EmptyResult<DataError.Local>
}

interface NoteRepository {
    suspend fun notesInDeck(deckId: String): List<Note>

    /** Saves the note and makes sure it has a review state for each direction the deck tests. */
    suspend fun saveNote(note: Note, bothDirections: Boolean): EmptyResult<DataError.Local>
}

interface ReviewRepository {
    /** Every review state in the deck, new ones included. */
    suspend fun statesInDeck(deckId: String): List<ReviewState>

    suspend fun dueCards(deckId: String, today: LocalDate): List<ReviewState>

    suspend fun saveStates(states: List<ReviewState>): EmptyResult<DataError.Local>

    /** Logs the answer and stores the card's new schedule. */
    suspend fun recordReview(record: ReviewRecord, newState: ReviewState): EmptyResult<DataError.Local>
}
