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
- [ ] Part 2: 60 s wake-up check that rings again softly if the phone sits still
- [ ] Part 2: Heavy sleeper rings again if idle
- [ ] Later parts: Flip & rate, Type it, Match pairs → Build the sentence → Listen & pick → Say it
- [ ] More motion: Pip rising between screens, new-card pop, haptics

## M4 · The rest of the app
- [ ] Onboarding and permissions
- [ ] Alarm list and editor
- [ ] Home
- [ ] Decks: create a deck, quick add, paste a list, note editor, deck detail
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
- v1.2 AI deck generator, "Fill in" for words
- v1.3 Friends and 1v1
- v1.4 Squads

## Decisions since the PRD
- Morning flow: one easy card stops the alarm, then a 60 s check and an optional quiet bonus round (Normal mode).
- All 9 card games ship in the MVP. Build the sentence, Listen & pick and Say it stay Plus-only, as a test of whether they're worth paying for.
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
- Free limit of 100: notes or cards?
- Snooze vs. emergency stop; pre-alarm in the MVP?
- Pricing, free deck limit, final name.
