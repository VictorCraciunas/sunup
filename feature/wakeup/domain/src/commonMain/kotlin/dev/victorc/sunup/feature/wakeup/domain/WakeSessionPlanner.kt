package dev.victorc.sunup.feature.wakeup.domain

import dev.victorc.sunup.core.domain.learning.CardBuilder
import dev.victorc.sunup.core.domain.learning.CardContent
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GameEligibility
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One card in the morning flow. Quick math cards have no note. */
data class MorningCard(
    val content: CardContent,
    val label: String,
    val note: Note? = null,
    val state: ReviewState? = null,
)

data class WakePlan(
    val mode: WakeMode,
    val deck: Deck?,
    /** Answering all of these stops the alarm: 1 card, or 3 for heavy sleepers. */
    val silencing: List<MorningCard>,
    /** The optional quiet round after waking up (Normal mode only). */
    val bonus: List<MorningCard>,
)

/**
 * Picks this morning's cards. Never fails: anything missing or broken (no deck, nothing
 * learned yet, database locked after a reboot) falls back to Quick math, so the alarm
 * can always be stopped.
 */
class WakeSessionPlanner(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
    private val reviews: ReviewRepository,
    private val cards: CardBuilder,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    suspend fun plan(alarm: WakeAlarm?): WakePlan {
        val mode = alarm?.mode ?: WakeMode.NORMAL
        val fromDeck = try {
            planFromDeck(alarm, mode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return fromDeck ?: WakePlan(
            mode = mode,
            deck = null,
            silencing = List(silencingCount(mode)) { quickMath() },
            bonus = emptyList(),
        )
    }

    private suspend fun planFromDeck(alarm: WakeAlarm?, mode: WakeMode): WakePlan? {
        val deck = alarm?.deckId?.let { decks.getDeck(it) }
            ?: decks.observeDecks().first().firstOrNull()
            ?: return null
        val deckNotes = notes.notesInDeck(deck.id)
        if (deckNotes.isEmpty()) return null
        val notesById = deckNotes.associateBy { it.id }
        val today = clock.now().toLocalDateTime(timeZone()).date
        val states = reviews.statesInDeck(deck.id).filter { it.noteId in notesById }
        val used = mutableSetOf<ReviewState>()

        // Silencing: easy, known cards in a fast game. Most overdue first.
        val known = states
            .filter { it.isKnown }
            .sortedWith(compareBy(nullsLast<LocalDate>()) { it.due })
        val silencing = mutableListOf<MorningCard>()
        for (state in known) {
            if (silencing.size == silencingCount(mode)) break
            val note = notesById.getValue(state.noteId)
            val content = fastCard(note, state, deck, deckNotes) ?: continue
            silencing += MorningCard(content, label = "${deck.name} · you know this one", note = note, state = state)
            used += state
        }
        while (silencing.size < silencingCount(mode)) silencing += quickMath()

        // Bonus: one card to practise, one new word.
        val bonus = mutableListOf<MorningCard>()
        if (mode == WakeMode.NORMAL) {
            val due = states.filter { it !in used && !it.isNew && (it.due ?: today) <= today }
            val new = states.filter { it !in used && it.isNew }
            val candidates = listOfNotNull(
                due.firstOrNull()?.let { it to "Practice" },
                new.firstOrNull()?.let { it to "New word" },
            ) + (due.drop(1) + new.drop(1)).map { it to "Practice" }

            for ((state, label) in candidates) {
                if (bonus.size == BONUS_COUNT) break
                val note = notesById.getValue(state.noteId)
                val content = fastCard(note, state, deck, deckNotes) ?: continue
                bonus += MorningCard(content, label = label, note = note, state = state)
            }
        }

        return WakePlan(mode = mode, deck = deck, silencing = silencing, bonus = bonus)
    }

    /** The games built so far (M3): Pick the meaning, then True or false. */
    private fun fastCard(note: Note, state: ReviewState, deck: Deck, deckNotes: List<Note>): CardContent? {
        val eligible = GameEligibility.eligibleGames(note, state.direction, deck, deckNotes.size, hasPlus = false)
        return when {
            Game.PICK_MEANING in eligible -> cards.pickMeaning(note, state.direction, deckNotes)
            Game.TRUE_FALSE in eligible -> cards.trueFalse(note, state.direction, deckNotes)
            else -> null
        }
    }

    private fun quickMath() = MorningCard(cards.quickMath(), label = "Quick math · wake up your brain")

    private fun silencingCount(mode: WakeMode) = if (mode == WakeMode.HEAVY_SLEEPER) 3 else 1

    private companion object {
        const val BONUS_COUNT = 2
    }
}

/** First try right is GOOD; needing another go counts as AGAIN, so the card comes back soon. */
fun gradeFor(wrongAttempts: Int): Grade = if (wrongAttempts == 0) Grade.GOOD else Grade.AGAIN
