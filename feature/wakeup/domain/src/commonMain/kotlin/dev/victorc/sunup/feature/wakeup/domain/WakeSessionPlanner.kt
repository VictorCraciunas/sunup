package dev.victorc.sunup.feature.wakeup.domain

import dev.victorc.sunup.core.domain.learning.CardBuilder
import dev.victorc.sunup.core.domain.learning.CardContent
import dev.victorc.sunup.core.domain.learning.Deck
import dev.victorc.sunup.core.domain.learning.DeckKind
import dev.victorc.sunup.core.domain.learning.DeckRepository
import dev.victorc.sunup.core.domain.learning.Direction
import dev.victorc.sunup.core.domain.learning.Game
import dev.victorc.sunup.core.domain.learning.GameEligibility
import dev.victorc.sunup.core.domain.learning.GeoRegion
import dev.victorc.sunup.core.domain.learning.Grade
import dev.victorc.sunup.core.domain.learning.Note
import dev.victorc.sunup.core.domain.learning.NoteRepository
import dev.victorc.sunup.core.domain.learning.ReviewRepository
import dev.victorc.sunup.core.domain.learning.ReviewState
import dev.victorc.sunup.core.domain.wakeup.WakeAlarm
import dev.victorc.sunup.core.domain.wakeup.WakeMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A note being reviewed, in one direction. */
data class CardItem(val note: Note, val state: ReviewState)

/**
 * One card in the morning flow. Quick math cards have no note. [extraItems] is for a card
 * that reviews several notes at once (none do today: Match pairs cards hold their own pairs).
 */
data class MorningCard(
    val content: CardContent,
    val label: String,
    val note: Note? = null,
    val state: ReviewState? = null,
    val extraItems: List<CardItem> = emptyList(),
) {
    /** Every note this card reviews, the card's own first. */
    val items: List<CardItem>
        get() = listOfNotNull(if (note != null && state != null) CardItem(note, state) else null) + extraItems
}

data class WakePlan(
    val mode: WakeMode,
    val deck: Deck?,
    /** Answering all of these stops the alarm: 1 card, or 3 for heavy sleepers. */
    val silencing: List<MorningCard>,
    /** The optional quiet round after waking up (Normal mode only). */
    val bonus: List<MorningCard>,
)

/**
 * Picks this morning's cards: random cards from the alarm's deck, each in its own game.
 * Never fails: anything missing or broken (no deck, no playable card, database locked
 * after a reboot) falls back to Quick math, so the alarm can always be stopped.
 */
class WakeSessionPlanner(
    private val decks: DeckRepository,
    private val notes: NoteRepository,
    private val reviews: ReviewRepository,
    private val cards: CardBuilder,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val random: Random = Random.Default,
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
        return fromDeck ?: quickMathPlan(mode)
    }

    /**
     * Every game once, for trying them out without an alarm: one card of each game in the
     * first deck, then the map games if there's a geography deck. Nothing is saved.
     */
    suspend fun preview(): WakePlan {
        val plan = try {
            previewFromDeck()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return plan ?: quickMathPlan(WakeMode.NORMAL)
    }

    private suspend fun planFromDeck(alarm: WakeAlarm?, mode: WakeMode): WakePlan? {
        val deck = alarm?.deckId?.let { decks.getDeck(it) }
            ?: decks.observeDecks().first().firstOrNull()
            ?: return null
        val deckNotes = notes.notesInDeck(deck.id)
        if (deckNotes.isEmpty()) return null
        val notesById = deckNotes.associateBy { it.id }
        val states = reviews.statesInDeck(deck.id).filter { it.noteId in notesById }
        if (deck.kind == DeckKind.GEOGRAPHY) return geographyPlan(deck, mode, deckNotes, states)

        val today = clock.now().toLocalDateTime(timeZone()).date
        val picked = randomCards(deck, deckNotes, states, wanted = cardCount(mode), today = today) { deck.name }
        if (picked.isEmpty()) return null
        val silencing = picked.take(silencingCount(mode)).toMutableList()
        // A deck with too few playable cards still stops the alarm, with Quick math for the rest.
        while (silencing.size < silencingCount(mode)) silencing += quickMath()
        return WakePlan(mode = mode, deck = deck, silencing = silencing, bonus = picked.drop(silencingCount(mode)))
    }

    /**
     * Up to [wanted] cards from the deck, each in its own game, picked at random and never the
     * same card twice. Cards due again (the ones you got wrong come back the next day) are a
     * bit more likely: that's the only trace of spaced repetition the user notices.
     * Cards that can't be played yet (a Pick from 4 with too few wrong answers) are skipped.
     */
    private fun randomCards(
        deck: Deck,
        deckNotes: List<Note>,
        states: List<ReviewState>,
        wanted: Int,
        today: LocalDate,
        label: (Note) -> String,
    ): List<MorningCard> {
        val notesById = deckNotes.associateBy { it.id }
        val letters = CardBuilder.lettersIn(deckNotes)
        // Cards are always asked as written.
        val stateOf = states.filter { it.direction == Direction.FORWARD }.associateBy { it.noteId }
        val candidates = deckNotes
            .filter { it.game in GameEligibility.CARD_GAMES }
            .map { note -> stateOf[note.id] ?: ReviewState(note.id, Direction.FORWARD) }
            .toMutableList()

        val picked = mutableListOf<MorningCard>()
        val usedNotes = mutableSetOf<String>()
        while (picked.size < wanted && candidates.isNotEmpty()) {
            val state = weightedPick(candidates, today)
            candidates.remove(state)
            if (state.noteId in usedNotes) continue
            val note = notesById.getValue(state.noteId)
            val content = cards.build(note, state.direction, deckNotes, letters) ?: continue
            picked += MorningCard(content, label(note), note, state)
            usedNotes += note.id
        }
        return picked
    }

    /** Due cards weigh [DUE_WEIGHT], the rest 1. */
    private fun weightedPick(candidates: List<ReviewState>, today: LocalDate): ReviewState {
        fun weight(state: ReviewState) = if (state.reviews > 0 && (state.due ?: today) <= today) DUE_WEIGHT else 1
        var roll = random.nextInt(candidates.sumOf { weight(it) })
        for (state in candidates) {
            roll -= weight(state)
            if (roll < 0) return state
        }
        return candidates.last()
    }

    /** One card of every game in the first deck (that isn't a map), then the map games. Nothing is saved. */
    private suspend fun previewFromDeck(): WakePlan? {
        val allDecks = decks.observeDecks().first()
        val mapCards = allDecks.firstOrNull { it.kind == DeckKind.GEOGRAPHY }?.let { previewMapCards(it) }.orEmpty()
        val deck = allDecks.firstOrNull { it.kind != DeckKind.GEOGRAPHY }
        val deckCards = deck?.let { previewDeckCards(it) }.orEmpty()

        val all = deckCards + mapCards
        if (all.isEmpty()) return null
        return WakePlan(mode = WakeMode.NORMAL, deck = deck, silencing = all.take(1), bonus = all.drop(1))
    }

    private suspend fun previewDeckCards(deck: Deck): List<MorningCard> {
        val deckNotes = notes.notesInDeck(deck.id)
        val letters = CardBuilder.lettersIn(deckNotes)
        return GameEligibility.CARD_GAMES.mapNotNull { game ->
            deckNotes
                .filter { it.game == game }
                .firstNotNullOfOrNull { note ->
                    cards.build(note, Direction.FORWARD, deckNotes, letters)?.let { content ->
                        MorningCard(content, "Preview · ${game.title()}", note, ReviewState(note.id, Direction.FORWARD))
                    }
                }
        }
    }

    /** Name the country, then Find on the map, each with a random country from the deck. */
    private suspend fun previewMapCards(deck: Deck): List<MorningCard> {
        val region = GeoRegion.fromPackId(deck.builtInPackId) ?: GeoRegion.WORLD
        val deckNotes = notes.notesInDeck(deck.id)
        val (named, found) = deckNotes.shuffled(random).take(2).takeIf { it.size == 2 } ?: return emptyList()
        fun state(note: Note) = ReviewState(note.id, Direction.FORWARD)
        return listOfNotNull(
            cards.nameOnMap(named, deckNotes, region)?.let { MorningCard(it, "Preview · Name the country", named, state(named)) },
            MorningCard(findContent(found, region), "Preview · Find on the map", found, state(found)),
        )
    }

    /**
     * Geography decks: every card is on the map. Same counts as the other decks (1 to stop
     * the alarm and 2 bonus, or 3 to stop it for heavy sleepers). Countries are picked at
     * random from the whole region, never twice in one morning. New countries are named from
     * four; known ones are found.
     */
    private fun geographyPlan(
        deck: Deck,
        mode: WakeMode,
        deckNotes: List<Note>,
        states: List<ReviewState>,
    ): WakePlan {
        val region = GeoRegion.fromPackId(deck.builtInPackId) ?: GeoRegion.WORLD
        val stateOf = states.filter { it.direction == Direction.FORWARD }.associateBy { it.noteId }
        fun state(note: Note) = stateOf[note.id] ?: ReviewState(note.id, Direction.FORWARD)

        // Each country appears once in this list, so nothing repeats within the morning.
        val order = deckNotes.shuffled(random)

        val picked = order.take(cardCount(mode)).map { note ->
            mapCard(note, state(note), deck, deckNotes, region)
        }
        val silencing = picked.take(silencingCount(mode))
        if (silencing.size < silencingCount(mode)) return quickMathPlan(mode)
        return WakePlan(mode = mode, deck = deck, silencing = silencing, bonus = picked.drop(silencing.size))
    }

    /**
     * For when the map can't be shown (file missing or broken, country not on it): map cards
     * that would stop the alarm become Quick math, bonus map cards are dropped.
     */
    fun withoutUnplayableMaps(plan: WakePlan, isPlayable: (CardContent.OnMap) -> Boolean): WakePlan {
        fun MorningCard.unplayable() = (content as? CardContent.OnMap)?.let { !isPlayable(it) } ?: false
        return plan.copy(
            silencing = plan.silencing.map { if (it.unplayable()) quickMath() else it },
            bonus = plan.bonus.filterNot { it.unplayable() },
        )
    }

    /** Name the country while it's new, Find on the map once it's known (see GameEligibility). */
    private fun mapCard(note: Note, state: ReviewState, deck: Deck, deckNotes: List<Note>, region: GeoRegion): MorningCard {
        val named = if (GameEligibility.mapGame(state, deck.allowedGames) == Game.NAME_ON_MAP) {
            cards.nameOnMap(note, deckNotes, region)
        } else {
            null
        }
        return if (named != null) {
            MorningCard(named, "${region.title} · name the country", note, state)
        } else {
            MorningCard(findContent(note, region), "${region.title} · find it on the map", note, state)
        }
    }

    /** Geography notes: front = country name, back = country code. */
    private fun findContent(note: Note, region: GeoRegion) =
        CardContent.FindOnMap(countryId = note.back, countryName = note.front, region = region)

    private fun quickMathPlan(mode: WakeMode) = WakePlan(
        mode = mode,
        deck = null,
        silencing = List(silencingCount(mode)) { quickMath() },
        bonus = emptyList(),
    )

    private fun quickMath() = MorningCard(cards.quickMath(), label = "Quick math · wake up your brain")

    private fun silencingCount(mode: WakeMode) = if (mode == WakeMode.HEAVY_SLEEPER) 3 else 1

    /** Cards this morning: the ones that stop the alarm, plus the bonus round in Normal mode. */
    private fun cardCount(mode: WakeMode) = silencingCount(mode) + if (mode == WakeMode.NORMAL) BONUS_COUNT else 0

    private fun Game.title(): String = when (this) {
        Game.PICK_MEANING -> "Pick from 4"
        Game.TRUE_FALSE -> "True or false"
        Game.TYPE_IT -> "Type it"
        Game.FILL_THE_GAP -> "Fill the gap"
        Game.MATCH_PAIRS -> "Match pairs"
        else -> name
    }

    private companion object {
        const val BONUS_COUNT = 2

        /** How much more likely a card that's due again is to come up. */
        const val DUE_WEIGHT = 3
    }
}

/** First try right is GOOD; needing another go counts as AGAIN, so the card comes back soon. */
fun gradeFor(wrongAttempts: Int): Grade = if (wrongAttempts == 0) Grade.GOOD else Grade.AGAIN
