package dev.victorc.sunup.core.domain.learning

import dev.victorc.sunup.core.domain.DataError
import dev.victorc.sunup.core.domain.EmptyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface DeckRepository {
    fun observeDecks(): Flow<List<Deck>>
    suspend fun getDeck(id: String): Deck?
    suspend fun saveDeck(deck: Deck): EmptyResult<DataError.Local>

    /** Removes the deck and all its cards. */
    suspend fun deleteDeck(id: String): EmptyResult<DataError.Local>
}

interface NoteRepository {
    suspend fun notesInDeck(deckId: String): List<Note>

    /** The deck's cards, oldest first, updated as they change. */
    fun observeNotesInDeck(deckId: String): Flow<List<Note>>

    suspend fun getNote(id: String): Note?

    suspend fun deleteNote(id: String): EmptyResult<DataError.Local>

    /** Number of cards per deck id. Decks without cards are missing. */
    fun observeCardCounts(): Flow<Map<String, Int>>

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
