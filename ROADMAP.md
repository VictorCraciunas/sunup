# Sunup roadmap

One codebase, released step by step. The MVP is the first public release, not a throwaway.

## M0 · Foundation
- [x] Project from the JetBrains KMP wizard (Android + iOS, shared Compose UI)
- [x] `build-logic` convention plugins and a cleaned-up version catalog
- [x] minSdk 26, Java 17, iOS bundle ID `dev.victorc.sunup`
- [x] `:core:domain` with `Result` / `DataError` and tests
- [x] `:core:design-system` with tokens from the mockups and `SunupButton`
- [x] `CLAUDE.md`, this roadmap, PRD copied into `docs/`
- [ ] Check the iOS build on the Mac
- [ ] Nunito font files in composeResources (the mockups use it; the theme falls back to the system font for now)
- [ ] CI on GitHub Actions, with a macOS runner for the iOS build

## M1 · Android alarm engine
Code is written on `feature/m1-alarm-engine`; nothing is ticked until it's tested on a real phone.
- [ ] `:core:alarm` with the shared `AlarmScheduler` interface and `NextRingTime` (weekdays, daylight saving)
- [ ] `setAlarmClock()` scheduling, `USE_EXACT_ALARM` (Android 13+)
- [ ] Foreground service (`systemExempted`) that starts the sound first, ramping from soft to loud, with a beep fallback
- [ ] Full-screen ringing screen over the lock screen; without that permission it rings as a notification
- [ ] Reschedule after reboot, time or time zone change, and app update; a one-off alarm missed while the phone was off is turned off
- [ ] Direct boot: alarms and the ring log live in device-protected storage (one card for the ringing screen comes in M3)
- [ ] Ring log of scheduled vs. actual ring time, shown in the temporary Alarm test bench
- [ ] Real-device test matrix (Xiaomi, Samsung): reboot, app killed, Doze, silent, Do Not Disturb
- [ ] Later: ring right away after a reboot if an alarm was missed in the last few minutes

### Device test results
| Date | Phone | Test | Result |
|---|---|---|---|
| 2026-11-11 | Samsung | Locked, app swiped away, 2 runs | ✅ Rang 0.1 s and 1.3 s late, ringing screen shown (battery not unrestricted) |
| 2026-11-11 | Samsung | Locked, app swiped away, with the M2 build | ✅ Rang and showed the ringing screen |
| | Samsung | Silent mode / Do Not Disturb | not yet |
| | Samsung | Reboot, left locked | not yet |
| | Samsung | Overnight | not yet |
| | Xiaomi | All of the above | not yet |

## M2 · Data and logic
- [x] Domain models in `:core:domain`: Deck, Note (Word / Question), ReviewState, ReviewRecord, Game, WakeAlarm
- [x] `:core:database` (Room KMP) with entities, DAOs and mappers; `sunup.room` convention plugin; schema exported to `core/database/schemas`
- [x] Spaced repetition: ladder 1 → 3 → 8 → 21 → 45 → 90 days, exam dates, level-up path, with tests
- [x] Forgiving answer checking (case, punctuation, articles, accents, small typos), with tests
- [x] Which games each note can play, and which can stop the alarm, with tests
- [ ] Repositories: added with the first feature that uses them (M3)
- [ ] Streaks: moved to M4, once wake sessions exist
- [ ] Built-in pack format and loading: waiting on the built-in pack decision

## M3 · Morning flow
- [x] Part 1: repositories (`:core:data`), card builder, wake session planner, sample Spanish deck
- [x] Part 1: silencing card → bonus offer → bonus round → "You're up!", brightening from night to day; Pip drawn in Compose
- [x] Part 1: Gentle (1 card), Normal (1 card + bonus), Heavy sleeper (3 cards); emergency stop
- [x] Part 1: games Pick the meaning, True or false, Quick math (fallback when nothing is learned yet)
- [x] Wrong-answer shake, right-answer bounce, result panel slides up
- [x] Part 2: 60 s wake-up check after the alarm stops, Normal and Heavy sleeper. Every touch pushes a soft follow-up alarm 60 s ahead. When it fires: soft ring and "Still with me?". "Start my day" ends it.
- [x] Part 2 fix: leaving Sunup no longer ends the check. When the check fires, if the user is busy in another app (screen on, unlocked) it looks again a minute later, for up to 5 minutes; screen off or locked rings. A re-ring after Sunup was swiped away shows "Still with me?", not a new card. Cancels and skips are in the ring log.
- [ ] Later: count phone movement (accelerometer) as activity, not only touches
- [ ] Later: a check that survives tapping "Start my day" and going back to bed
- [x] Part 3: Fill the gap (pick the missing word in the example sentence), Type it (forgiving check, accent keys, Show answer), Match pairs (4 words, each reviewed). Bonus cards follow the level-up path; silencing mixes Pick and True or false. A deck needs 4+ notes before alarms use it. Test bench: Preview all games.
- [x] Part 4: geography, Find on the map. `:core:geo` (map file loader, projection, pinch/drag/tap map), built-in region decks (Europe, Africa, Asia, North and Central America, South America, Oceania, World), map file from Natural Earth via `tools/geo/build_countries.py`. Test bench: region picker, Load geography deck, deck for test alarms.
- [x] Part 5: Name the country. One country highlighted and zoomed in on, pick its name from 4, the wrong ones being its neighbours (from the map file's nearby list). New countries are named, known ones are found.
- [ ] Later: Build the sentence → Listen & pick → Say it (Plus)
- [ ] More motion: Pip rising between screens, new-card pop, haptics

## M4 · The rest of the app
- [x] Onboarding and permissions: `:feature:onboarding`. Welcome → what to learn (World geography with a region, My own cards, Quick math) → rise time and days → promise → permission checklist (mockup C1: notifications, exact alarms, lock screen, battery, Autostart on the phones that have it, test alarm in 10 s) → wake-up mode (C2) → saves the first alarm. Shown once (DataStore flag). `AlarmService` moved to `core:alarm` so onboarding can save alarms without depending on the alarms feature; the time stepper and day chips moved to the design system.
- [x] Alarm list and editor: `:feature:alarms`. List with switches and "Rings in…", editor with time, days, mode, deck, delete. Saves to the engine first, then the database; the list syncs one-off alarms that rang. The app now opens on the list; the test bench is behind a link. Mockup look: raised cards, chips and + button (`SunupRaised`), cards and sections rise in, ticking time digits, chips wobble when picked; all off with "Remove animations".
- [ ] Later in the editor: pre-alarm, sound, label (not built yet, so not shown)
- [ ] iOS: Koin isn't started on iOS yet, so the app screens need that before they run there (M5)
- [ ] Home (original mockup): start screen with the next alarm, deck progress and the week; its alarm card opens the Alarms list
- [x] Decks part 1: cards with one game each (database version 2: `game`, `isTrue`, `pairs`; old notes became Pick from 4), random morning cards weighted toward due ones, any card stops the alarm, True or false plays the user's statement, Match pairs holds its own pairs, Fill the gap uses [brackets], accent keys from the deck's letters, articles dropped for any language. Sample deck has every game.
- [x] Decks part 2: `:feature:decks`. Bottom bar (Alarm · Decks), My decks / Browse packs (map regions added and removed here), New deck, Deck detail with filter and settings (rename, delete), game picker, card forms for the five games (new and edit, Save & add another, delete). Not yet: exam date, free deck limit, Import (part 3).
- [x] Decks part 3: Import a deck with your own AI (mockup D5). Import button on the Decks tab, the prompt to copy (five games + a full English example), paste or upload a .json file (Android), forgiving importer (chat text, fences, curly quotes, other field and game names, guessed games), preview with editable name and skipped cards, create. Not on iOS yet: file upload.
- [ ] Streaks

## M5 · iOS
- [ ] AlarmKit in Swift behind `AlarmScheduler`
- [ ] Follow-up alarm scheduled up front and cancelled by the card
- [ ] Test "starts soft, gets louder" (AlarmKit may only use the ringer volume)
- [ ] Testing on a real iPhone (iOS 26+)

## M6 · Release prep
- [ ] Crashlytics and analytics
- [ ] RevenueCat for Plus
- [ ] Privacy policy and store listings
- [ ] Closed testing

## After the MVP
- v1.1 Accounts, backup and sync (Supabase), Anki/CSV import
- v1.2 (dropped: AI generator and "Fill in" are replaced by Import with your own AI, see decisions)
- v1.3 Friends and 1v1
- v1.4 Squads

## Decisions since the PRD
- **Cards, not notes (M4, replaces the level-up path):** each card has **one game**, picked by the user when making it (Pick from 4, True or false, Type it, Fill the gap, Match pairs, Map). No mastery levels. Every morning Sunup picks **random cards** from the deck, never twice in one morning; a hidden rule makes cards you got wrong a bit more likely to come up. Any card can stop the alarm, whatever its game. Pick from 4: the user may write 3 wrong answers; empty ones are borrowed from other cards in the deck. This changes the M2/M3 learning code (levels, `GameEligibility`, the planner) when the deck screens are built.
- **No "test both ways":** cards are always asked as written; to practise the other direction, make a second card. (The old `bothDirections` column stays, unused.)
- **Decks don't ask for a language.** Accent keys come from the special letters in the deck's own cards; a leading article (el, la, le, der, the…) is ignored for any known language. Language only matters later for the Plus speaking/listening games.
- **Import with your own AI, instead of a Sunup AI generator:** "Import deck" shows a ready prompt to copy into ChatGPT or Claude; the AI answers with the deck as JSON; the user pastes it (or uploads the .json file), sees a preview, and creates the deck. Free, no server, no AI cost. The same importer can read CSV and Anki later. Mockup 10 (AI generator) and "Fill in" (v1.2) are dropped.
- **Onboarding matches today's app (M4):** no "I already have an account" (no accounts yet); the goal choices are World geography, My own cards and Quick math (no language or fun-facts packs exist; import is with your own AI, not Anki/Quizlet/CSV); no "cards each morning" (the wake-up mode decides); the promise lists only what's built (no ads, one card stops the alarm, the wake-up check). The permission screen adds "Show over lock screen" (needed on Android 14+). The test bench stays, debug only, until before release.
- **Free limit = number of decks:** free users can have a few decks (number to decide), however they're made; Plus means unlimited decks.
- Home vs. Alarms (two mockups): the original **Home** (next alarm, deck progress, this week's sunrises) becomes the start screen once streaks and deck progress exist; tapping its alarm card opens the **Alarms** list (Improvements A2), where alarms are added and edited. Until then the app opens on the Alarms list.
- Morning flow: one easy card stops the alarm, then a 60 s check and an optional quiet bonus round (Normal mode).
- All 9 card games ship in the MVP. Build the sentence, Listen & pick and Say it stay Plus-only, as a test of whether they're worth paying for.
- Geography (inspired by map quizzes like Seterra, in Sunup's own look): built-in region decks played on the map: Name the country (pick from 4) while a country is new, Find on the map once it is known. Same counts as other decks (1 + 2 bonus, Heavy 3). Any country can stop the alarm. Countries are picked at random from the whole region, never twice in one morning; spaced repetition only decides which map game a country gets. Map data: Natural Earth, public domain. The region lives in the deck's built-in pack id, so no database change.
- Flip & rate is dropped: rating yourself is too easy to fake at 7 AM. Fill the gap replaces it (pick the missing word in the note's example sentence), so every game has a real right answer. Free games: Pick the meaning, True or false, Match pairs, Fill the gap, Type it, Quick math.
- The wake-up check has no cap on re-rings: it keeps ringing softly every idle minute until Start my day, or until the user has been busy in other apps for about 5 minutes.
- A deck needs at least 4 notes before alarms use it (the choice games take wrong answers from the deck). Below that the alarm uses Quick math, and the deck screen should say how many more to add (M4).
- Users write notes (Word or Question); the app turns each note into the games. Without AI, wrong answers come from other notes in the same deck, so Pick / True or false / Match unlock at 4 notes.
- Only Pick the meaning and True or false can stop the alarm. Say it always has a "Can't speak now" skip.
- No backend in the MVP. At v1.1 add one service, most likely Supabase. Firebase stays for crash reports, analytics and push.
- "Fill in" for new words needs a server, so it moves to v1.2.
- SKIE and per-screen SwiftUI are dropped for the MVP; the Swift surface is just `AlarmScheduler`.
- Android ships first; iOS is built and tested on the Mac.

## Open questions
- Built-in packs: who writes them, and how big are they?
- Note types: are Word and Question enough, or add a True/false statement type?
- Paste a list: free or Plus?
- Free limit: decided as a number of decks (see decisions); how many is still open.
- Snooze vs. emergency stop; pre-alarm in the MVP?
- Pricing, how many free decks (e.g. 3?), final name.
