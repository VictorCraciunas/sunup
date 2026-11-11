# Sunup — Product Requirements

Oct 22, 2026 · @Victor

## Overview

Sunup is an alarm app that wakes you up with one quick card from something you're learning, so you get out of bed instead of snoozing. Working name: Sunup. Mascot: Pip, a sun.

- **Problem:** most people snooze 5–10 minutes again and again. Puzzle alarms exist, but they feel like punishment and teach nothing.
- **Principle:** you do this for yourself, not as a punishment. The alarm is the delivery moment for learning, not a lock.
- **Feel:** playful and bouncy, Duolingo-style: chunky 3D buttons, spring animations, a mascot that reacts.
- **Mockups:** [main flows](https://claude.ai/artifact/Cs6LrpXMvhderkFmJeZcLr) · [cards & play](https://claude.ai/artifact/Jtm3mMbUWN91Fo2UVpmCXm)

## Users and positioning

Core users are students and self-learners who snooze and want a tiny daily learning habit: exam prep, a new language, fun facts.

The main competitor is Alarmy (50M+ downloads on Google Play). Its known weak spots shape our promises:

| Alarmy complaint | Sunup answer |
| --- | --- |
| Ads during the wake-up, even on snooze | No ads during the wake-up, ever |
| Premium around $5–9/month | Fair price, yearly + lifetime + student discount |
| Alarms sometimes don't fire on some phones | Reliability is requirement #1 (see Non-functional) |
| People uninstall it to escape | A reason to keep it: streaks, learning, friends |
| Missions teach nothing | Every card is something you learn or review |

Small "learning alarm" apps exist (e.g. Language Alarm on iOS), but none combines quiz-style cards, your own decks and Android support.

## Morning wake-up flow

Proposed (2026-10-22, to confirm): **one easy card stops the alarm**; the rest of the learning happens in quiet. Three cards with a ringing alarm is too stressful and fights the "for yourself" principle.

&#91;embedded content: morning wake-up flow · Normal mode\]

The noise lasts seconds; a 60-second wake-up check stops you falling back asleep.

1. **Silencing card:** one card, always easy (a card you already know, in True/False or Pick the meaning), about 5 seconds.
2. **Wake-up check:** after the card, if the phone is idle for 60 s the alarm rings again, softly.
3. **Bonus round:** Pip offers 2 more cards in silence (practice + something new). Optional; it counts extra for the streak.
4. **Volume:** the alarm starts gentle and only gets louder while nothing happens.
5. **No ads, upsells or paywalls** anywhere in this flow.
6. **Optional pre-alarm:** a soft chime 5 min early with the card open; finish it and the loud alarm never rings.

### Wake-up modes (picked in onboarding)

| Mode | To stop the alarm | After |
| --- | --- | --- |
| Gentle | 1 card | nothing |
| Normal (default) | 1 card + 60 s wake-up check | 2-card quiet bonus round |
| Heavy sleeper | 3 cards, re-rings when idle | — |

### Android vs iOS

- **Android:** our own full-screen alarm (foreground service + full-screen intent). It keeps ringing until the card is answered.
- **iOS:** Apple's AlarmKit screen with our "Wake up with Pip" button. Apple's Stop can't be hidden, so after Stop we schedule a soft follow-up alarm (\~3 min) that the card cancels. Same 1-card logic, so both platforms feel alike.

## Card types

Nine card formats, each a 5–20 second mini-game; six are free.

| Card type | How it plays | Best for | Tier |
| --- | --- | --- | --- |
| Pick the meaning | Choose the right answer out of 4 | Languages, exams, facts | Free |
| Flip & rate | Think, flip, rate yourself: Not yet (tomorrow) / Almost (3 days) / Knew it (8 days) | Own notes, exams | Free |
| Type it | Type the answer; small typos and missing accents count as right, with a note | Languages, definitions | Free |
| Match pairs | Tap 4 word–meaning pairs; one card, four words reviewed | Vocabulary | Free |
| True or false | One statement, one tap, then a one-line "why" | Fun facts, warm-up | Free |
| Quick math | Solve in your head, adaptive difficulty | Brain warm-up | Free |
| Build the sentence | Tap word tiles into order, with decoy words | Languages | Plus |
| Listen & pick | Hear a native voice, pick what you heard; slow replay | Languages | Plus |
| Say it | Say the word; speech recognition checks it | Pronunciation | Plus |

- **Level-up path:** a new word starts as Pick the meaning, then Match pairs, then Type it as you learn it.
- **Spaced repetition** decides which card comes when; a missed card comes back within about 2 days.
- **Silencing card** (morning flow) is always an easy, known card in a fast format.
- **Feedback:** wrong answers shake gently with a kind message; right answers bounce. Never shame.

## Decks and card creation

Users learn from built-in packs or make their own deck in under a minute; one deck is active per alarm.

- **New deck:** choose Language, Study notes or Fun & trivia.
  - Language: pick the language being learned.
  - Study: optional exam date; Pip spaces the cards so each is seen twice before it.
  - Choose which card formats Pip may use for this deck.
- **Add a word (language):** type a word, tap "Fill in" to get meaning, example sentence and audio. Shows the level-up path it will follow.
- **Card editor (study):** question + answer; formats Flip / Pick / Type / True-False. "Pip suggests" writes wrong options (Plus). Preview shows the card exactly as it plays at 7 AM.
- **AI deck generator (Plus):** upload a PDF, notebook photo or pasted text, choose 10/20/40 cards, review and remove before saving. Monthly limit (e.g. 5 decks).
- **Import (Plus):** Anki, Quizlet, CSV.
- **Deck detail:** Known / Learning / New counts, what's due tomorrow, each card's current level, "Practice now" outside the alarm.
- **Built-in packs:** German, French, Italian, English, Spanish basics, fun facts (space, animals, history), mental math.

## Free vs Plus

The alarm and the wake-up are never paywalled; Plus makes the learning better.

| Feature | Free | Plus |
| --- | --- | --- |
| Alarms, wake-up flow, no ads | Yes | Yes |
| Built-in starter packs | All | All |
| Own decks | 1 deck, up to 100 cards | Unlimited |
| Card types | 6 | 9 (+ sentence, listen, say it) |
| Bonus cards per morning | 2 | 4 or 6 |
| AI deck generator, Pip suggests | — | Yes, monthly limit |
| Anki / Quizlet / CSV import | — | Yes |
| Native-voice audio | Phone voice | Native voice |
| Stats, streak freezes | Basic, 1 per month | Detailed, more freezes |
| Squads | Up to 5 members | Larger squads, AI group decks |
| 1v1 cards and duels | Yes | Yes |

**Pricing (to test):** €2.99/month, €19.99/year, €39.99 lifetime, 40% student discount, 7-day free trial. RevenueCat handles purchases on both stores.

**Upgrade prompts** appear only in calm moments (after a 7-day streak, adding a second deck, uploading a PDF), never in the morning.

## Social

Friends wake each other up with cards; squads learn from a shared pool. Social needs an account; the alarm never does.

- **Friends:** see who's up this morning, "Cheer" awake friends, "Send a sun" to a sleeping one.
- **Squads (study groups):**
  - Everyone contributes cards to a shared pool, with author shown and upvotes.
  - Every member gets the same cards each morning, each on their own alarm time ("same morning", not forced same time; optional same-time mode).
  - Soft squad streak: holds while most members wake up (e.g. 4 of 5), so one oversleeper doesn't punish everyone.
  - Owner can approve or remove cards; members can flag wrong ones.
- **1v1, "Wake a friend up":** three modes.
  - Quiz them: send cards to learn.
  - Surprise: one fun card (a joke, an inside question).
  - Duel: same cards for both; result screen shows score, time and monthly record, with Rematch / Send GG / Share.
- **Safety rules:**
  - Cards only from accepted friends.
  - The receiver accepts the night before (questions visible, answers hidden), with "always accept from" toggle.
  - Declining is silent; block and report on every incoming card.
- **Build order:** 1v1 "send cards" first (simplest, tests demand), squads after.

## Tech stack and architecture

Decided: Kotlin Multiplatform + Compose Multiplatform, Android and iOS, with native alarm code on each platform.

| Layer | Choice | Notes |
| --- | --- | --- |
| Shared logic | Kotlin (KMP) | Decks, spaced repetition, streaks, data, sync, AI import |
| UI | Compose Multiplatform | SwiftUI per screen on iOS where it needs to feel native |
| Android alarm | AlarmManager exact alarms, foreground service, full-screen intent, boot receiver | Test on Xiaomi + Samsung battery killers |
| iOS alarm | AlarmKit (iOS 26+) + App Intents, in Swift | Called from shared code through an interface |
| Swift interop | SKIE | Nicer Kotlin types, async and Flows in Swift |
| Local DB | Room (KMP), DataStore for settings | Source of truth on the device |
| Backend | Supabase (Postgres, auth, edge functions) or Firebase | Open question; push via FCM either way |
| Payments | RevenueCat | KMP SDK, both stores |
| Crash + analytics | Firebase Crashlytics + Analytics |  |

- **Local-first:** alarms, decks, review state, streaks and settings live on the device and work offline.
- **Needs the backend:** AI generation (keeps the API key off the device), account + backup, multi-device sync, friends, squads, 1v1.
- **From day one:** every record has a UUID and an `updatedAt` timestamp, so sync can be added without a migration.
- **Alarm interface:** shared Kotlin defines `AlarmScheduler` (schedule / cancel); Android and iOS each implement it natively.

## Non-functional requirements

A missed alarm is the one unforgivable bug; everything else comes second.

- **Reliability:** alarms fire on time after reboot, with the app killed, in Doze, on silent and in Focus. Fallback: if the card screen fails to show, the alarm still rings and can be stopped.
- **Offline:** tomorrow's cards (and any friend or squad cards) download the night before; the morning needs no network.
- **Permissions onboarding:** a friendly screen for notifications, exact alarms, battery-optimization exemption, autostart on Xiaomi/Samsung, and AlarmKit on iOS.
- **No ads** anywhere in the wake-up flow; no upsell before the day has started.
- **Speed:** card screen visible within 1 s of the alarm; silencing card answerable in about 5 s.
- **Accessibility:** touch targets ≥ 44 px, text contrast ≥ 4.5:1, screen-reader labels, colour never the only signal.
- **Motion:** bouncy animations everywhere, all turned off when the system's reduce-motion setting is on.
- **Privacy:** imported notes and photos are used only to make cards; account deletion removes server data.

## Roadmap and open questions

Ship a reliable local-only MVP first; add accounts, AI and social once people use it.

1. **MVP:** alarm engine on both platforms, onboarding + permissions, morning flow (3 modes), 6 free card types, built-in packs, 1 own deck, streaks, RevenueCat. No backend.
2. **v1.1:** sign-in + backup/restore, Plus card types, Anki/CSV import.
3. **v1.2:** AI deck generator via a server function, monthly limit.
4. **v1.3:** friends + 1v1 (send cards, surprise, duel).
5. **v1.4:** squads with shared pools.

Validate early: post the mockup flow on TikTok ("this alarm teaches me Spanish before I get out of bed") and watch for "where can I download this?" comments.

### Open questions

- [ ] Confirm the morning flow: 1 card + 60 s check + bonus round as the default?
- [ ] Backend: Supabase or Firebase?
- [ ] Final name (Sunup is a working name) and mascot style
- [ ] Free own-deck limit: 100 cards right, or lower?
- [ ] Prices: test €2.99 / €19.99 / €39.99 against alternatives
- [ ] AI generator monthly limit and per-deck cost
- [ ] Pre-alarm: in the MVP or later?
