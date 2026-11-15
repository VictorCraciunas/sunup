# How Sunup works

The current rules of the app, in plain words. Keep this in sync with the code: when a rule changes, change it here in the same commit. What's built vs. planned lives in [ROADMAP.md](../ROADMAP.md); why things are this way is under "Decisions since the PRD" there.

## The morning, step by step

1. **The alarm rings.** Starts soft and gets louder over about 50 seconds, on the alarm volume (rings in silent mode). Vibrates. Shows the ringing screen over the lock screen.
2. **Silencing card(s).** Answer right to stop the alarm. A wrong answer keeps it ringing; try again.
3. **Wake-up check** starts (Normal and Heavy sleeper only). See below.
4. **Bonus round** offer (Normal only): 2 quiet cards, or "Not today".
5. **"You're up!"**: Pip, and the words answered this morning. **Start my day** closes it.

There is **no snooze**. The only other way to stop the alarm is **Emergency stop** (small button under the first card): stops it at once, goes to "You're up!", no wake-up check. Whether to add a real snooze is an open question.

## Wake-up modes

| Mode | To stop the alarm | Wake-up check | Bonus round |
|---|---|---|---|
| Gentle | 1 card | no | no |
| Normal (default) | 1 card | yes | 2 cards, optional |
| Heavy sleeper | 3 cards | yes | no |

## The 60-second wake-up check

After the alarm stops, a soft re-ring (45% volume at most) is always scheduled 1 minute ahead. A chip shows "Wake-up check · 0:42".

| What the user does | What happens |
|---|---|
| Touches or types in Sunup | Re-ring moves back to 1 minute from now |
| Leaves Sunup on screen, no touch for 1 minute | Soft ring and **"Still with me?"** |
| Locks the phone / screen turns off | Soft ring after 1 minute |
| Swipes Sunup away and puts the phone down | Soft ring once the screen is off |
| Swipes Sunup away and uses other apps | No ring; checks again every minute |
| Taps **I'm up** on "Still with me?" | Stops the soft ring and restarts the 1-minute check |

**It ends** when the user taps **Start my day**, or after using other apps (screen on, unlocked) for about 5 minutes.
**It never starts** in Gentle mode, after Emergency stop, or in Preview all games.

**No cap, on purpose:** someone who keeps tapping "I'm up" and falling asleep again gets a soft ring every minute until they tap Start my day (or use other apps for about 5 minutes).

## The games

| Game | How it plays | Free / Plus | Can stop the alarm |
|---|---|---|---|
| Pick the meaning | A word, 4 options | Free | yes |
| True or false | "*el sol* means *the moon*?" True / False; if false, shows the real meaning | Free | yes |
| Quick math | "7 × 8", 4 options. Needs no deck | Free | yes (fallback only) |
| Fill the gap | Example sentence with the word missing, 4 options | Free | no |
| Match pairs | 4 words, 4 shuffled meanings; tap a word, then its meaning. All 4 words count as reviewed | Free | no |
| Find on the map | Geography decks only. "Find: Portugal" above a map of the region; pinch to zoom, drag to move, tap the country. Wrong tap turns that country orange with its name ("That's Spain"); right turns it green. Tiny countries (Luxembourg and smaller) get a circle you can tap. After 3 wrong countries the right one glows | Free | yes |
| Type it | Type the answer. Forgives capitals, punctuation, a leading article, missing accents and small typos (with a note). Accent keys for es/fr/de/it/pt. **Show answer** after one wrong try | Free | no |
| Build the sentence | Not built yet | Plus | no |
| Listen & pick | Not built yet | Plus | no |
| Say it | Not built yet; always offers "Can't speak now" | Plus | no |

Feedback in every game: wrong shakes and turns orange ("Not quite. Have another go."), right bounces and turns green, then a panel slides up with **Continue**. Never shaming.

## Which game appears when

**Silencing card:** a word the user already knows (reviewed at least once and not just forgotten), in **Pick the meaning** or **True or false**, picked at random. Heavy sleeper gets 3 different known words.

**Geography decks** (a region: Europe, Africa, Asia, North and Central America, South America, Oceania, or the World) always play **Find on the map**, with the same counts as other decks: 1 country stops the alarm and 2 more in the bonus round (Normal), 3 to stop it (Heavy sleeper), 1 and no bonus (Gentle). Any country can stop the alarm, not only known ones: first those due today, then new ones **biggest first** (Russia before Andorra), then the rest. Tapping the same wrong country twice counts as one mistake. If the map can't be read, map cards that would stop the alarm become Quick math and bonus map cards are skipped.

**Quick math instead** when: no deck, the deck has fewer than 4 notes, nothing learned yet, or the database can't be read (e.g. right after a reboot, before the first unlock).

**Bonus round:** one word to practise (due today) and one new word. Each plays the game that fits its level:

1. New / recognizing → **Pick the meaning** (or True or false)
2. Getting there → **Match pairs**, else **Fill the gap**
3. Well learned → **Type it**, else Fill the gap

A game is only offered if the note allows it:
- Pick, True or false, Match pairs, Fill the gap: the deck has **4+ notes** (wrong answers come from other notes).
- Fill the gap: the word appears whole in its example sentence (with its article, or without if the sentence uses another). Asked in one direction only.
- Type it: the answer is 3 words or fewer.
- Plus games: only with Plus. Deck settings can switch games off.

## How answers change what comes back

| Result | Grade | Next time (for a word learned before) | Level |
|---|---|---|---|
| Right first try | GOOD | Next rung of 1 → 3 → 8 → 21 → 45 → 90 days | Up one |
| Type it: close (accent, typo) | HARD | Same rung again | Same |
| Needed another try, or Show answer | AGAIN | Tomorrow | Down one |
| Find on the map: one wrong country first | HARD | Same rung again | Same |
| Find on the map: two or more wrong | AGAIN | Tomorrow | Down one |

A brand-new word answered right comes back in 3 days. With an exam date, cards are always seen again before the exam.

## Notes, decks and games

Users don't build games; they write **notes** and Sunup turns each note into the games.
- **Word note** (language decks): word, meaning, optional example sentence, hint, accepted answers, own wrong answers.
- **Question note** (study / trivia): question, answer, optional one-line "why", own wrong answers.
- **Geography decks** are built in, not written: one note per country (name, and its two-letter code), all new when loaded. The map comes with the app (Natural Earth, public domain), so it works offline.
- Wrong options come from the user's own wrong answers first, then other notes in the same deck.
- A deck is used by alarms only once it has **at least 4 notes**.

## Reliability rules

- A missed alarm is the one unforgivable bug.
- The sound starts first; nothing on the ring path waits for the database, the network or the UI.
- Alarms and the ring log live in storage readable before the first unlock after a reboot.
- Alarms are re-registered after reboot, time or time zone change, and app update. A one-off alarm missed while the phone was off is turned off, not moved to tomorrow.
- A ringing alarm stops on its own after 15 minutes (logged as TIMED_OUT).
- No ads, upsells or paywalls anywhere in the morning.

## Testing without waiting for an alarm

Test bench (below the home placeholder): **Load sample Spanish deck** (12 words; 3 at Match level, 3 at Type it level, 6 new), or pick a **Map** region and **Load geography deck**. Choose the **Deck for test alarms** (loading a deck selects it) and a **Mode**, then **Ring in 1 minute**, or **Preview all games** (plays each game once, no alarm, saves nothing; Find on the map is added when a geography deck exists).

The map file is built from Natural Earth's countries file with `python tools/geo/build_countries.py <ne_50m_admin_0_countries.geojson>`; it writes `core/geo/src/commonMain/composeResources/files/geo/countries.json`. The **ring log** shows scheduled vs. actual ring times, skips and cancels.
