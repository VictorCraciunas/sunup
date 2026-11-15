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
 * One card in the morning flow. Quick math cards have no note.
 * Match pairs reviews four notes at once: [note]/[state] plus [extraItems], in the card's order.
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
     * Every game once, from the first deck, for trying them out without an alarm:
     * True or false to silence, then Pick the meaning, Fill the gap (if a sentence allows),
     * Type it and Match pairs in the bonus round. Nothing is saved.
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
        // The choice games need wrong answers from the deck; a smaller deck isn't used yet.
        if (deckNotes.size < GameEligibility.MIN_NOTES_FOR_CHOICES) return null
        val notesById = deckNotes.associateBy { it.id }
        val today = clock.now().toLocalDateTime(timeZone()).date
        val states = reviews.statesInDeck(deck.id).filter { it.noteId in notesById }
        if (deck.kind == DeckKind.GEOGRAPHY) return geographyPlan(deck, mode, deckNotes, states, today)
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

        // Bonus: one card to practise, one new word; each in the game that fits its level.
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
                if (state in used) continue
                val note = notesById.getValue(state.noteId)
                val card = levelCard(note, state, deck, deckNotes, states, notesById, used, label) ?: continue
                bonus += card
                used += card.items.map { it.state }
            }
        }

        return WakePlan(mode = mode, deck = deck, silencing = silencing, bonus = bonus)
    }

    private suspend fun previewFromDeck(): WakePlan? {
        val allDecks = decks.observeDecks().first()
        val mapCard = allDecks.firstOrNull { it.kind == DeckKind.GEOGRAPHY }?.let { previewMapCard(it) }
        val deck = allDecks.firstOrNull { it.kind != DeckKind.GEOGRAPHY }
            ?: return mapCard?.let { WakePlan(WakeMode.NORMAL, deck = null, silencing = listOf(it), bonus = emptyList()) }
        val deckNotes = notes.notesInDeck(deck.id)
        if (deckNotes.size < CardBuilder.MATCH_SIZE) return null
        val direction = Direction.FORWARD
        val items = deckNotes.take(CardBuilder.MATCH_SIZE).map { CardItem(it, ReviewState(it.id, direction)) }
        val (first, second) = items
        fun item(note: Note) = CardItem(note, ReviewState(note.id, direction))

        val silencing = listOf(
            MorningCard(
                content = cards.trueFalse(first.note, direction, deckNotes) ?: return null,
                label = "Preview · True or false",
                note = first.note,
                state = first.state,
            ),
        )
        val pick = cards.pickMeaning(second.note, direction, deckNotes) ?: return null
        // Fill the gap only works for words whose example sentence contains them.
        val gap = deckNotes.firstNotNullOfOrNull { note -> cards.fillTheGap(note, deckNotes)?.let { note to it } }
        val match = cards.matchPairs(items.map { it.note }, direction) ?: return null
        val typed = items.last()

        val bonus = listOfNotNull(
            MorningCard(pick, "Preview · Pick the meaning", second.note, second.state),
            gap?.let { (note, content) -> MorningCard(content, "Preview · Fill the gap", note, item(note).state) },
            MorningCard(
                cards.typeIt(typed.note, direction, answerLanguage(deck, direction)),
                "Preview · Type it",
                typed.note,
                typed.state,
            ),
            MorningCard(match, "Preview · Match pairs", first.note, first.state, extraItems = items.drop(1)),
            mapCard,
        )
        return WakePlan(mode = WakeMode.NORMAL, deck = deck, silencing = silencing, bonus = bonus)
    }

    private suspend fun previewMapCard(deck: Deck): MorningCard? {
        val region = GeoRegion.fromPackId(deck.builtInPackId) ?: GeoRegion.WORLD
        val note = notes.notesInDeck(deck.id).randomOrNull(random) ?: return null
        return MorningCard(mapContent(note, region), "Preview · Find on the map", note, ReviewState(note.id, Direction.FORWARD))
    }

    /**
     * Geography decks: every card is Find on the map. Same counts as the other decks
     * (1 to stop the alarm and 2 bonus, or 3 to stop it for heavy sleepers), but any country
     * can stop the alarm: countries due today first, then new ones biggest first (easy before
     * hard), then the rest of the known ones.
     */
    private fun geographyPlan(
        deck: Deck,
        mode: WakeMode,
        deckNotes: List<Note>,
        states: List<ReviewState>,
        today: LocalDate,
    ): WakePlan {
        val region = GeoRegion.fromPackId(deck.builtInPackId) ?: GeoRegion.WORLD
        val stateOf = states.filter { it.direction == Direction.FORWARD }.associateBy { it.noteId }
        fun state(note: Note) = stateOf[note.id] ?: ReviewState(note.id, Direction.FORWARD)

        val (newNotes, seen) = deckNotes.partition { state(it).isNew }
        val (due, later) = seen
            .sortedWith(compareBy(nullsLast<LocalDate>()) { state(it).due })
            .partition { (state(it).due ?: today) <= today }
        // Note ids carry the size rank (see GeoDeckSeeder), so this is biggest first.
        val order = due + newNotes.sortedBy { it.id } + later

        val wanted = silencingCount(mode) + if (mode == WakeMode.NORMAL) BONUS_COUNT else 0
        val picked = order.take(wanted).map { note ->
            MorningCard(mapContent(note, region), "${region.title} · find it on the map", note, state(note))
        }
        val silencing = picked.take(silencingCount(mode))
        if (silencing.size < silencingCount(mode)) return quickMathPlan(mode)
        return WakePlan(mode = mode, deck = deck, silencing = silencing, bonus = picked.drop(silencing.size))
    }

    /**
     * For when the map can't be shown (file missing or broken, country not on it): map cards
     * that would stop the alarm become Quick math, bonus map cards are dropped.
     */
    fun withoutUnplayableMaps(plan: WakePlan, isPlayable: (CardContent.FindOnMap) -> Boolean): WakePlan {
        fun MorningCard.unplayable() = (content as? CardContent.FindOnMap)?.let { !isPlayable(it) } ?: false
        return plan.copy(
            silencing = plan.silencing.map { if (it.unplayable()) quickMath() else it },
            bonus = plan.bonus.filterNot { it.unplayable() },
        )
    }

    /** Geography notes: front = country name, back = country code. */
    private fun mapContent(note: Note, region: GeoRegion) =
        CardContent.FindOnMap(countryId = note.back, countryName = note.front, region = region)

    /** Silencing games: Pick the meaning or True or false, mixed for variety. */
    private fun fastCard(note: Note, state: ReviewState, deck: Deck, deckNotes: List<Note>): CardContent? {
        val eligible = GameEligibility.eligibleGames(note, state.direction, deck, deckNotes.size, hasPlus = false)
        val canPick = Game.PICK_MEANING in eligible
        val canTrueFalse = Game.TRUE_FALSE in eligible
        return when {
            canPick && canTrueFalse && random.nextBoolean() -> cards.trueFalse(note, state.direction, deckNotes)
            canPick -> cards.pickMeaning(note, state.direction, deckNotes)
            canTrueFalse -> cards.trueFalse(note, state.direction, deckNotes)
            else -> null
        }
    }

    /** The level-up path: Pick the meaning → Match pairs / Fill the gap → Type it. */
    private fun levelCard(
        note: Note,
        state: ReviewState,
        deck: Deck,
        deckNotes: List<Note>,
        states: List<ReviewState>,
        notesById: Map<String, Note>,
        used: Set<ReviewState>,
        label: String,
    ): MorningCard? {
        val eligible = GameEligibility.eligibleGames(note, state.direction, deck, deckNotes.size, hasPlus = false)
            .intersect(BUILT_GAMES)
        val game = GameEligibility.preferredGame(state, eligible) ?: return null
        val direction = state.direction

        fun single(content: CardContent?) = content?.let { MorningCard(it, label, note, state) }

        return when (game) {
            Game.MATCH_PAIRS -> matchCard(note, state, states, notesById, used, label)
                ?: single(cards.pickMeaning(note, direction, deckNotes))
            Game.TYPE_IT -> single(cards.typeIt(note, direction, answerLanguage(deck, direction)))
            Game.FILL_THE_GAP -> single(cards.fillTheGap(note, deckNotes))
                ?: single(cards.pickMeaning(note, direction, deckNotes))
            Game.PICK_MEANING -> single(cards.pickMeaning(note, direction, deckNotes))
            Game.TRUE_FALSE -> single(cards.trueFalse(note, direction, deckNotes))
            else -> null
        }
    }

    /** Pairs [note] with three other cards in the same direction, learned ones first. */
    private fun matchCard(
        note: Note,
        state: ReviewState,
        states: List<ReviewState>,
        notesById: Map<String, Note>,
        used: Set<ReviewState>,
        label: String,
    ): MorningCard? {
        val others = states
            .filter { it != state && it !in used && it.direction == state.direction }
            .sortedBy { if (it.isNew) 1 else 0 }
            .take(CardBuilder.MATCH_SIZE - 1)
            .map { CardItem(notesById.getValue(it.noteId), it) }
        val content = cards.matchPairs(listOf(note) + others.map { it.note }, state.direction) ?: return null
        return MorningCard(content, label, note, state, extraItems = others)
    }

    /** Typed answers: the meaning is in the user's language, the word in the language being learned. */
    private fun answerLanguage(deck: Deck, direction: Direction): String? {
        if (deck.kind != DeckKind.LANGUAGE) return deck.nativeLanguage
        return if (direction == Direction.FORWARD) deck.nativeLanguage ?: "en" else deck.targetLanguage
    }

    private fun quickMathPlan(mode: WakeMode) = WakePlan(
        mode = mode,
        deck = null,
        silencing = List(silencingCount(mode)) { quickMath() },
        bonus = emptyList(),
    )

    private fun quickMath() = MorningCard(cards.quickMath(), label = "Quick math · wake up your brain")

    private fun silencingCount(mode: WakeMode) = if (mode == WakeMode.HEAVY_SLEEPER) 3 else 1

    private companion object {
        const val BONUS_COUNT = 2

        /** Games the morning flow can show so far. The Plus games come later. */
        val BUILT_GAMES = setOf(
            Game.PICK_MEANING,
            Game.TRUE_FALSE,
            Game.FILL_THE_GAP,
            Game.TYPE_IT,
            Game.MATCH_PAIRS,
        )
    }
}

/** First try right is GOOD; needing another go counts as AGAIN, so the card comes back soon. */
fun gradeFor(wrongAttempts: Int): Grade = if (wrongAttempts == 0) Grade.GOOD else Grade.AGAIN
