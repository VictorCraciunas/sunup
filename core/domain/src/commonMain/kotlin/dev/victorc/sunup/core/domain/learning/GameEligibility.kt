package dev.victorc.sunup.core.domain.learning

/** Which games a note can be played as. The morning flow picks one of these per card. */
object GameEligibility {

    /**
     * The choice games need wrong answers, which come from other notes in the deck, so a
     * deck is only used in the morning once it has this many notes. Until then: Quick math.
     */
    const val MIN_NOTES_FOR_CHOICES = 4

    /** Longer answers are too much to type at 7 AM; those stay with the choice games. */
    const val MAX_WORDS_TO_TYPE = 3

    fun eligibleGames(
        note: Note,
        direction: Direction,
        deck: Deck,
        notesInDeck: Int,
        hasPlus: Boolean,
    ): Set<Game> {
        // Geography decks play on the map: name the highlighted country, or find the named one.
        if (deck.kind == DeckKind.GEOGRAPHY) {
            return setOf(Game.NAME_ON_MAP, Game.FIND_ON_MAP).filter { it in deck.allowedGames }.toSet()
        }

        val hasChoices = notesInDeck >= MIN_NOTES_FOR_CHOICES
        val isWord = note.type == NoteType.WORD
        val answer = note.answers(direction).first()
        val shortAnswer = answer.trim().split(Regex("\\s+")).size <= MAX_WORDS_TO_TYPE

        val games = buildSet {
            if (shortAnswer) add(Game.TYPE_IT)
            if (hasChoices) {
                add(Game.PICK_MEANING)
                add(Game.TRUE_FALSE)
                add(Game.MATCH_PAIRS)
                // One card per word, not per direction: the sentence is always in the language being learned.
                if (direction == Direction.FORWARD && SentenceGap.find(note) != null) add(Game.FILL_THE_GAP)
            }
            if (isWord && !note.example.isNullOrBlank() && direction == Direction.FORWARD) add(Game.BUILD_THE_SENTENCE)
            // Listening and speaking work on the word in the language being learned.
            if (isWord && deck.kind == DeckKind.LANGUAGE && hasChoices) add(Game.LISTEN_AND_PICK)
            if (isWord && deck.kind == DeckKind.LANGUAGE && direction == Direction.REVERSE) add(Game.SAY_IT)
        }

        return games
            .filter { it in deck.allowedGames }
            .filter { hasPlus || !it.isPlus }
            .toSet()
    }

    /** The game that fits where the user is on the level-up path, if it's allowed. */
    fun preferredGame(state: ReviewState, eligible: Set<Game>): Game? {
        val order = when (state.level) {
            // Geography: recognise a country from four names first, then find it with no help.
            MasteryLevel.RECOGNIZE -> listOf(Game.PICK_MEANING, Game.TRUE_FALSE, Game.NAME_ON_MAP)
            MasteryLevel.MATCH -> listOf(Game.MATCH_PAIRS, Game.FILL_THE_GAP, Game.PICK_MEANING, Game.FIND_ON_MAP)
            MasteryLevel.RECALL -> listOf(Game.TYPE_IT, Game.FILL_THE_GAP, Game.MATCH_PAIRS, Game.FIND_ON_MAP)
        }
        return order.firstOrNull { it in eligible } ?: eligible.firstOrNull()
    }

    /** The alarm stops on an easy, known card in a fast game only. */
    fun canSilenceAlarm(game: Game, state: ReviewState): Boolean =
        game.canSilenceAlarm && game.usesNotes && state.isKnown
}
