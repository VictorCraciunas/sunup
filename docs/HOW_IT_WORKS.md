# How Sunup works

The current rules of the app, in plain words. Keep this in sync with the code: when a rule changes, change it here in the same commit. What's built vs. planned lives in [ROADMAP.md](../ROADMAP.md); why things are this way is under "Decisions since the PRD" there.

## First run

The first time the app opens (nothing saved yet says it's done), it shows six screens before anything else. Back goes one screen back.

1. **Welcome**: Pip, "Good morning!", **Get started**.
2. **What should we wake up with?** (pick one to continue):
   - **World geography**: pick a region to start with (Europe by default). Finishing adds that map pack, and the first alarm uses it.
   - **My own cards**: after the last screen, the Decks tab opens to make or import a deck. The alarm uses the first deck (Quick math until there is one).
   - **Quick mental math**: no deck; the alarm uses Quick math (or the first deck, once one exists).
3. **When should I wake you?**: time (hours by 1, minutes by 5, tap to type) and repeat days (Monday to Friday by default; none = once).
4. **This is for you**: no ads while you wake up; one card stops the alarm; a gentle check makes sure you stay up. **I'm doing this for me**, then **Let's go**.
5. **Let's make sure I can wake you** (mockup C1): one row per thing this phone has to allow, each with **Allow** (or **Show me**) until it's **Done**, and "N of M ready". The rows are checked again whenever the user comes back to the app.
   - **Notifications**: the system prompt the first time; after that, the app's notification settings.
   - **Exact alarms** (Android 12+): the "Alarms & reminders" setting. Android 13+ allows it for alarm clocks by itself.
   - **Show over lock screen** (Android 14+): the full-screen setting, so the card opens when it rings.
   - **Battery: no limits**: a one-tap system prompt (or the battery list if the phone has none).
   - **Autostart** (Xiaomi, Redmi, Poco, Samsung, Oppo, Realme, OnePlus, Vivo, Huawei, Honor only): opens the maker's screen, or Sunup's app info if it moved, with a hint of what to switch on. Android can't check this one, so it counts as done once opened.
   - **Try a test alarm**: rings softly in 10 seconds (it needs Exact alarms). It plays as Gentle: one card stops it, no wake-up check, nothing saved except that card's review.
   - **Continue** always works, even with rows not done.
6. **How hard is it to get you up?** (mockup C2): Gentle, Normal (Recommended) or Heavy sleeper, with "Your morning" step by step. **Set my 07:00 alarm** saves the first alarm (the chosen time, days and mode, no label), remembers that onboarding is done, and opens Home (or the Decks tab, for My own cards). If exact alarms aren't allowed it says so and stays.

Onboarding shows again only if the app's data is cleared. If remembering "done" fails, it shows again next time (the alarm is already set).

## Home

After the first run the app opens on **Home**, the Alarm tab (the original Home mockup):

- **Top:** "sunup", the streak (Pip and "12": sunrises in a row) and the number of cards answered in every morning, bonus cards included.
- **Alarm card** (big, indigo): the alarm that rings next, with "Today", "Tomorrow" or the weekday, its time, days, deck and mode, and an on/off switch. Switching it off on Home keeps it on the card as "Off · enjoy the sleep-in" (so it doesn't jump to another alarm under your finger) until you leave Home; when you come back, the card shows the next alarm to ring again. With every alarm off, it shows the earliest one, off. (The alarm list is sorted by time of day; Home picks by the next ring, so days matter.) Tapping the card opens the **alarm list**. No alarms at all: "No alarm set" and **Add alarm**.
- **Deck card:** the deck that alarm uses (Any deck = the first deck), with "24 of 60 cards learned" (countries for a map). Learned = answered right and not forgotten since. Tapping it opens the deck. No decks: "No deck yet", **Go to Decks**.
- **This week:** Monday to Sunday: a sun for a sunrise, a dash for a missed morning, a glowing ring for today, a dashed ring for no alarm or days still to come; "3 sunrises".
- **Preview tomorrow's wake-up:** plays that alarm's morning (its deck and mode) on the ringing screen, without sound, without the wake-up check, saving nothing.

## Streaks

Every time the morning screen opens for a real alarm, Sunup keeps a record of that morning: when it rang, how it ended, how many cards. The test alarm, previews, test bench "Preview all games" and the wake-up check's re-rings don't make one.

| The day | Streak |
|---|---|
| An alarm stopped by answering (a **sunrise**) | +1 |
| An alarm rang and wasn't answered: **Emergency stop**, or left ringing | back to 0 |
| No alarm rang (a day off, e.g. a weekend) | unchanged, never breaks it |
| Today, before its sunrise | unchanged until the alarm is answered |

One sunrise is enough for a day, even if another alarm that day was missed. If the ringing screen never opened (e.g. full-screen alarms not allowed and the user never tapped the notification), that morning isn't recorded, so it counts as no alarm. **"You're up!"** shows "Up at 07:03 · 13 sunrises in a row" ("day 1 of your streak" for the first). No streak freezes yet.

## Setting alarms

The **alarm list** (from Home's alarm card): each alarm's time, days, deck and mode, with a switch, and a banner saying when the next one rings ("Rings in 8 h 19 min"). Tap an alarm to edit it, or **+** to add one. Back returns to Home.

**The editor:** time (hours step by 1, minutes by 5; tap the time to type an exact one), repeat days (none = rings once, then switches itself off), wake-up mode, and the deck the cards come from (**Any deck** = the first deck). It shows when the alarm will ring. **Save alarm** always switches the alarm on. Existing alarms can be deleted (asks first).

**Two copies, engine first:** the alarm engine keeps its own small copy of each alarm (time, days, on/off) that works before the phone is unlocked; the app's database keeps the rest. Saving tells the engine first, so a failed database write never costs an alarm ("The alarm will ring, but its settings didn't save"). Opening the list brings the two in line: a one-off alarm that rang shows as off, and an alarm only the engine knows about is added to the list.

If Android doesn't allow exact alarms, saving fails with a message pointing to "Alarms & reminders" in the phone's settings.

## The morning, step by step

1. **The alarm rings.** Starts soft and gets louder over about 50 seconds, on the alarm volume (rings in silent mode). Vibrates. Shows the ringing screen over the lock screen (mockup 6): the day and time, Pip rising from behind a hill with rings pulsing around it and stars twinkling, "Good morning! 1 quick card and the day is yours." (3 for Heavy sleeper), and **I'm up, let's go**, which opens the first card. No snooze. Previews and the test alarm show it too (previews say "no sound, nothing saved"); the wake-up check's re-ring skips it.
2. **Silencing card(s).** Answer right to stop the alarm. A wrong answer keeps it ringing; try again.
3. **Wake-up check** starts (Normal and Heavy sleeper only), on its own screen (improvement A2, "first light"): "✓ Alarm off · 07:01", Pip peeking over the horizon, "Stay with me for a minute", and a ring counting down the 60 seconds. See below.
4. **Bonus round** (Normal only): **Play 2 bonus cards** on the wake-up check screen, or **I'm up, skip** (Heavy sleeper: just **I'm up**). Previews have no check, so they ask "Bonus round?" with Let's go / Not today instead.
5. **"You're up!"**: Pip, and the words answered this morning. **Start my day** closes it.

There is **no snooze**. The only other way to stop the alarm is **Emergency stop** (small button under the first card): stops it at once, goes to "You're up!", no wake-up check. Whether to add a real snooze is an open question.

## Wake-up modes

| Mode | To stop the alarm | Wake-up check | Bonus round |
|---|---|---|---|
| Gentle | 1 card | no | no |
| Normal (default) | 1 card | yes | 2 cards, optional |
| Heavy sleeper | 3 cards | yes | no |

## The 60-second wake-up check

After the alarm stops, a soft re-ring (45% volume at most) is always scheduled 1 minute ahead. The wake-up check screen shows it as a countdown ring; after that, a chip on the other screens shows "Wake-up check · 0:42". Only touches and typing count as moving; phone movement (the motion sensor) doesn't yet.

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

Every card has **one game**, picked by the user when making it. Any card can stop the alarm.

| Game | How it plays | What the card holds |
|---|---|---|
| Pick from 4 | A question and 4 answers; tap the right one | Question, right answer, up to 3 wrong answers (missing ones are borrowed from other cards' answers) |
| True or false | The user's statement; tap True or False, then the "why" shows | Statement, true or false, optional "why" |
| Type it | Type the answer. Forgives capitals, punctuation, a leading article (el, la, le, der, the…), missing accents and small typos (with a note). Accent keys are the special letters used in that deck's cards. **Show answer** after one wrong try | Question, answer, other answers that also count |
| Fill the gap | A sentence with one word missing; pick it from 4 | Sentence with the word in [brackets], optional translation, up to 3 wrong answers (missing ones borrowed) |
| Match pairs | Up to 4 words and their shuffled meanings; tap a word, then its meaning | 2 to 4 pairs |
| Name the country | Geography decks only. One country is shown in indigo and the map starts zoomed in on it; pick its name from 4. The wrong options are its **neighbours** (3 of the 4 closest in the deck; for islands, the nearest coasts). If the region has too few, countries of a similar size fill in. A wrong pick shows that country on the map in orange with its name; the right one turns green | Built in |
| Find on the map | Geography decks only. "Find: Portugal" above a map of the region; pinch to zoom, drag to move, tap the country. Wrong tap turns that country orange with its name ("That's Spain"); right turns it green. Tiny countries (Luxembourg and smaller) get a circle you can tap. After 3 wrong countries the right one glows | Built in |
| Quick math | "7 × 8", 4 options. Needs no deck | Generated (fallback only) |
| Build the sentence, Listen & pick, Say it | Not built yet (Plus) | |

Feedback in every game: wrong shakes and turns orange ("Not quite. Have another go."), right bounces and turns green, then a panel slides up with **Continue**. Never shaming.

## Which cards appear when

**Every morning:** random cards from the alarm's deck (**Any deck** = the first deck), never the same card twice in one morning: 1 to stop the alarm and 2 in the bonus round (Normal), 3 to stop it (Heavy sleeper), 1 and no bonus (Gentle).

**A hidden nudge:** cards that are due again come up 3 times as often as the rest. A card you got wrong is due the next day, so it tends to come back soon. Spaced repetition runs underneath only for this; the user never sees levels or due dates.

**As written:** cards are always asked the way the user wrote them. To be asked the other way round (meaning → word), make a second card.

**Skipped cards:** a card that can't be played yet is skipped, e.g. a Pick from 4 with no wrong answers of its own in a deck with too few other answers to borrow.

**Quick math instead** when: no deck, no playable card, or the database can't be read (e.g. right after a reboot, before the first unlock). If a deck has fewer playable cards than needed to stop the alarm, Quick math fills in the rest.

**Geography decks** (a region: Europe, Africa, Asia, North and Central America, South America, Oceania, or the World) play on the map, with the same counts. Countries are picked **at random** from the whole region, never twice in one morning. A country that is new, or was just forgotten, is **Name the country** (pick from 4); once it is known it becomes **Find on the map** (no options). Tapping the same wrong country twice counts as one mistake. If the map can't be read, map cards that would stop the alarm become Quick math and bonus map cards are skipped.

## How answers are graded

| Result | Grade |
|---|---|
| Right first try (Match pairs: no wrong pair) | GOOD |
| Type it: close (accent, typo) | HARD |
| Needed another try, or Show answer | AGAIN |
| Find on the map: one wrong country first | HARD |
| Find on the map: two or more wrong | AGAIN |

The grade feeds the hidden spaced repetition (1 → 3 → 8 → 21 → 45 → 90 days; AGAIN = tomorrow), which only decides which cards are a bit more likely to come up. For geography it also decides Name the country vs Find on the map.

## Decks and cards

Users don't write notes for Sunup to turn into games any more; they make **cards**, each with its own game.
- **In the app:** the **Decks** tab (bottom bar) lists your decks with their card count and how many alarms use them. **New deck** asks a name and a type (Language, Study, Trivia; no language). Tap a deck to see all its cards (filter by game), **Add a card**, tap a card to edit or delete it, or **Settings** to rename or delete the deck (its cards go too; alarms that used it fall back to the first deck).
- A card that can't be played yet is marked in the deck ("add wrong answers, or more cards to borrow them from").
- **Browse packs** lists the map regions: **+** loads one as a deck, **✓** removes it (asks first).
- **Import** (Decks tab, next to New deck) makes a whole deck with the user's own AI: **Copy prompt** (it explains the five games and shows a full example), paste it into ChatGPT, Claude or any AI with a topic or notes, then **Paste** the answer (or **Upload .json** on Android) and **Check**. The preview shows the card count per game, the first cards, an editable deck name and any skipped cards with why; **Create deck** saves it and opens it. Free, unlimited, nothing leaves the phone.
- **The importer is forgiving:** it ignores chat text and ``` fences around the JSON, fixes curly quotes, accepts other field names (front/back, distractors, explanation…) and game names (multiple choice, fill in the blank…), guesses the game from the fields when it's missing, accepts pairs as lists or as {"France": "Paris"}, and turns "The ___ is…" plus an answer into a [bracketed] gap. Cards it still can't make are skipped and listed. At most 500 cards, files up to 2 MB.
- **Making a card:** pick the game, then fill in its form (see the table above). **Save & add another** keeps the same game with an empty form; **Done** goes back to the deck. A form that can't be saved says why (e.g. "Put the missing word in [brackets]").
- Wrong answers are the card's own first, then borrowed from other cards in the same deck.
- Cards made before cards had games (the old word and question notes) became **Pick from 4** cards.
- **Geography decks** are built in: one card per country (name, its two-letter code, and its closest neighbours in the region as wrong answers). Loading a deck again updates the cards and keeps what was learned. The map comes with the app (Natural Earth, public domain), so it works offline.
- **Free limit:** a few decks (number to decide), unlimited with Plus. Built-in decks (sample, maps) don't count. Not enforced yet.

## Reliability rules

- A missed alarm is the one unforgivable bug.
- The sound starts first; nothing on the ring path waits for the database, the network or the UI.
- Alarms and the ring log live in storage readable before the first unlock after a reboot.
- Alarms are re-registered after reboot, time or time zone change, and app update. A one-off alarm missed while the phone was off is turned off, not moved to tomorrow.
- A ringing alarm stops on its own after 15 minutes (logged as TIMED_OUT).
- No ads, upsells or paywalls anywhere in the morning.

## Testing without waiting for an alarm

Test bench (the **Test bench** link at the bottom of the alarm list): **Load sample Spanish deck** (12 cards, every game), or pick a **Map** region and **Load geography deck**. Choose the **Deck for test alarms** (loading a deck selects it) and a **Mode**, then **Ring in 1 minute**, or **Preview all games** (one card of each game in the first deck, plus Name the country and Find on the map when a geography deck exists; no alarm, saves nothing).

The map file (outlines, plus each country's nearby countries) is built from Natural Earth's countries file with `python tools/geo/build_countries.py <ne_50m_admin_0_countries.geojson>`; it writes `core/geo/src/commonMain/composeResources/files/geo/countries.json`. `--update-nearby` recomputes just the nearby countries in that file, without the download. The **ring log** shows scheduled vs. actual ring times, skips and cancels.
