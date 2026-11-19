# Project map

Where everything lives, and which files to open for a given change. The **rules** of the app (modes, games, grading) are in [HOW_IT_WORKS.md](HOW_IT_WORKS.md); the plan is in [ROADMAP.md](../ROADMAP.md). Keep this file in sync: when a module, file or flow is added, moved or renamed, update it in the same commit.

All Kotlin lives under the package `dev.victorc.sunup`, so `core/domain/.../learning/Deck.kt` means `core/domain/src/commonMain/kotlin/dev/victorc/sunup/core/domain/learning/Deck.kt`. Shared code is in `commonMain`, platform code in `androidMain` / `iosMain`, tests in `commonTest`.

## Top level

| Path | What it is |
|---|---|
| `CLAUDE.md` | Instructions for Claude: rules, stack, conventions, how to work here |
| `ROADMAP.md` | Milestones M0–M6, what's done, device test results, decisions since the PRD, open questions |
| `docs/PRD.md` | The original product requirements |
| `docs/HOW_IT_WORKS.md` | How the app behaves today: morning flow, modes, wake-up check, games, grading |
| `docs/PROJECT_MAP.md` | This file |
| `androidApp/` | Android entry point: Application, activities, manifest, test bench |
| `shared/` | Root composable `App()` and the "Shared" framework the iOS app links |
| `core/` | Shared building blocks (domain, alarm engine, database, data, design system, map) |
| `feature/` | Features, each split into `domain` / `presentation` (and `data` when needed) |
| `iosApp/` | Xcode project (built on the Mac) |
| `tools/geo/` | Python script that builds the country map file |
| `build-logic/` | Gradle convention plugins |
| `gradle/libs.versions.toml` | Every dependency version |
| `settings.gradle.kts` | The list of modules |

## Modules and who depends on whom

```
androidApp ──► shared, feature:alarms:presentation, feature:decks:presentation, feature:onboarding:presentation,
               feature:wakeup:presentation, core:alarm, core:data, core:database, core:design-system, core:geo
shared ──────► feature:alarms:presentation, feature:decks:presentation, feature:onboarding:presentation, core:domain,
               core:design-system

feature:alarms:presentation ─────► core:alarm, core:design-system
feature:decks:presentation ──────► feature:decks:domain, core:design-system, core:geo, core:data
feature:decks:domain ────────────► core:domain
feature:onboarding:presentation ─► core:alarm, core:design-system, core:geo, core:data
feature:progress:presentation ───► core:domain, core:design-system
feature:wakeup:presentation ─────► feature:wakeup:domain, core:alarm, core:design-system, core:geo
feature:wakeup:domain ────────► core:domain

core:alarm ──────────► core:domain
core:data ───────────► core:domain, core:database
core:database ───────► core:domain
core:geo ────────────► core:domain, core:design-system
core:design-system ──► (nothing)
core:domain ─────────► (nothing; only kotlinx libraries)
```

Features never depend on each other; anything two features need moves to `core:*`.

## How modules are wired

Most build errors here ("Unresolved reference", "Cannot access class … check your module classpath") come from one of these rules.

**`api` vs `implementation`.** A module only sees what it depends on directly, plus what those modules expose with `api`. `implementation` stays private.
- Use **`api`** when a type from the other module appears in your **public signatures**: constructor parameters, public function parameters or return types, public properties, base classes. Whoever uses your class needs that type too. Examples: `core:alarm` exposes `core:domain` (its `Result`, `Error`, and `AlarmService`'s `WakeAlarm`) and `kotlinx-datetime` (`LocalTime` in `Alarm`).
- Use **`implementation`** when the other module is only used inside your code: `core:data` uses `core:database` privately; the presentation modules use `core:design-system` privately.

**Who builds a class needs its constructor's types.** Koin modules call constructors, so the module holding the Koin `module { }` needs every type in those constructors, directly or through `api`.

**`androidApp` must depend directly on every module whose Koin module it registers.** `SunupApplication.kt` imports `alarmModule`, `databaseModule`, `settingsStoreModule`, `morningInfoModule`, `dataModule`, `alarmsModule`, `decksModule`, `onboardingModule`, `progressModule`, `wakeUpModule`, so `androidApp/build.gradle.kts` lists `core:alarm`, `core:database`, `core:data` and each of those feature presentation modules itself. Getting them through `shared` doesn't count: `shared` uses `implementation`.

**Screens are reached through `shared`.** `App.kt` (in `shared`) holds the navigation, so `shared` depends on each feature's presentation module whose `Root` composable (or nav graph, like `onboardingGraph`) it shows. The morning flow is the exception: `AlarmRingingActivity` in `androidApp` shows `WakeUpRoot` directly.

**Features never depend on each other.** Code two features need moves to `core:*`.

**Compose resources** (fonts, the map file) live in the module that reads them, under `src/commonMain/composeResources/`, with `compose.resources { packageOfResClass = … }` in its build file (see `core:geo`).

**Every dependency in each build file today:**

| Module | `api` | `implementation` |
|---|---|---|
| `core:domain` | kotlinx-datetime, kotlinx-coroutines | — |
| `core:alarm` | `core:domain`, kotlinx-datetime | coroutines, serialization, Kermit, Koin (+ androidx-core on Android) |
| `core:database` | `core:domain` | coroutines, serialization, Koin, Room (via `sunup.room`) |
| `core:data` | `core:domain` | `core:database`, coroutines, serialization (forecast), DataStore (settings), Koin (+ koin-android on Android) |
| `core:design-system` | — | Compose (via `sunup.kmp.compose`) |
| `core:geo` | `core:domain` | `core:design-system`, coroutines, serialization |
| `feature:alarms:presentation` | — | `core:alarm`, `core:design-system`, lifecycle, Koin (+ activity-compose on Android, for Home's permission prompts) |
| `feature:decks:domain` | `core:domain` | coroutines, serialization (Import) |
| `feature:decks:presentation` | — | `feature:decks:domain`, `core:design-system`, `core:geo`, `core:data`, lifecycle, Koin (+ activity-compose on Android, for the file picker) |
| `feature:progress:presentation` | — | `core:domain`, `core:design-system`, kotlinx-datetime, lifecycle, Koin |
| `feature:onboarding:presentation` | — | `core:alarm`, `core:design-system`, `core:geo`, `core:data`, lifecycle, navigation, serialization, Koin (+ activity-compose on Android, for the permission prompts) |
| `feature:wakeup:domain` | `core:domain` | coroutines |
| `feature:wakeup:presentation` | — | `feature:wakeup:domain`, `core:alarm`, `core:design-system`, `core:geo`, lifecycle, Koin |
| `shared` | — | `core:domain`, `core:design-system`, `feature:alarms:presentation`, `feature:decks:presentation`, `feature:onboarding:presentation`, `feature:progress:presentation`, lifecycle, navigation, serialization |
| `androidApp` | — | `shared`, `core:alarm`, `core:data`, `core:database`, `core:design-system`, `core:geo`, `feature:alarms:presentation`, `feature:decks:presentation`, `feature:onboarding:presentation`, `feature:progress:presentation`, `feature:wakeup:presentation`, activity, Compose, Koin |

**Checklist: adding a module**
1. `include(":…")` in `settings.gradle.kts`.
2. A `build.gradle.kts` applying a convention plugin (`sunup.kmp.library`, `sunup.kmp.compose`, plus `kotlinSerialization` or `sunup.room` if needed). Never hardcode a version.
3. Dependencies: `api` for every module whose types are in your public signatures, `implementation` for the rest.
4. If it has a Koin module: register it in `SunupApplication.kt` **and** add the module to `androidApp/build.gradle.kts`.
5. If it has screens: add its presentation module to `shared/build.gradle.kts` and a route in `App.kt`.
6. Update this file (module section, the graph and the table above) and the module list in `CLAUDE.md`.

## `core:domain`: models, rules, interfaces (pure Kotlin)

| File | What's in it |
|---|---|
| `Result.kt`, `DataError.kt` | `Result<T, E>`, `map / onSuccess / onFailure`, `DataError.Local / Network` |
| `learning/Deck.kt` | `Deck`, `DeckKind` (LANGUAGE, STUDY, TRIVIA, GEOGRAPHY) |
| `learning/DeckLimits.kt` | The free plan's limit: 5 own decks (built-in packs don't count) |
| `learning/Note.kt` | `Note`: one card, with its one `game` and the fields that game uses (table in the KDoc); `NoteType`, `Direction` |
| `learning/ReviewState.kt` | Hidden spaced-repetition state per card: step, `MasteryLevel` (geography only), due date; `Grade` |
| `learning/ReviewRecord.kt` | One answered card, kept for stats; `ReviewContext` |
| `learning/SpacedRepetition.kt` | When a card is due again (1 → 3 → 8 → 21 → 45 → 90 days); due cards are more likely to come up |
| `learning/Game.kt` | Every game, with Free/Plus |
| `learning/GameEligibility.kt` | The five card games, and Name vs Find for geography |
| `learning/CardBuilder.kt` | `CardContent` (what each card shows) and `CardBuilder` (card → what the morning screen shows, per game; borrowed wrong answers; accent letters) |
| `learning/AnswerMatcher.kt` | Forgiving check for typed answers (accents, typos, articles) |
| `learning/SentenceGap.kt` | Fill the gap: the [bracketed] word in a sentence |
| `learning/GeoRegion.kt` | Map regions (Europe … World): continents and map bounds |
| `learning/Country.kt` | A country's facts (code, name, continent, size, nearby countries) |
| `learning/LearningRepositories.kt` | `DeckRepository`, `NoteRepository`, `ReviewRepository` (incl. `observeAllReviews` for Progress) interfaces |
| `wakeup/WakeAlarm.kt` | An alarm as the user sets it up (deck, `WakeMode`, label); `TEST_ALARM_ID` (onboarding's test ring) |
| `settings/AppSettingsRepository.kt` | Small app settings: whether onboarding is done, the "You're up!" weather and calendar switches |
| `morning/MorningInfo.kt` | "You're up!" extras: `WeatherToday`, `WeatherCondition`, `CalendarEvent`, the `WeatherSource` / `CalendarSource` interfaces, `MorningInfoSettings` |
| `wakeup/WakeAlarmRepository.kt` | Its repository interface |
| `wakeup/WakeSession.kt` | One morning (`WakeSession`, `WakeOutcome`: RINGING, UP, STOPPED_WITHOUT_CARD) and `WakeSessionRepository` |
| `wakeup/Streaks.kt` | The streak rules: `current`, `week` (`DayMark` per day), `cardsAnswered` |
| `wakeup/Progress.kt` | `DayRange` (a week or month) and the Progress tab's numbers: best streak, cards per day, average wake-up, mornings, learned, remembered |

## `core:alarm`: the alarm engine (Rule #1 lives here)

Nothing on the ring path may touch the network or the database. Alarms and the ring log are a JSON file in device-protected storage, readable before the first unlock after a reboot. `AlarmService` (used by the screens, never by the ring path) is the one place that also writes the database.

| File | What's in it |
|---|---|
| `commonMain/Alarm.kt` | The engine's small view of an alarm: id, time, repeat days |
| `commonMain/AlarmScheduler.kt` | The shared API: `schedule`, `cancel`, `onAlarmRang`, `rescheduleAll`, follow-ups (wake-up check); `RingOptions`; `PlatformAlarmClock`; `WakeUpScreenState` |
| `commonMain/DefaultAlarmScheduler.kt` | The scheduler: works out next ring times, logs every step |
| `commonMain/NextRingTime.kt` | Next ring time for an alarm, with repeat days and time zones |
| `commonMain/AlarmStore.kt`, `FileAlarmStore.kt` | The engine's copy of alarms plus the ring log, as one atomic JSON file |
| `commonMain/RingEvent.kt` | One ring log line (SCHEDULED, RANG, STOPPED, SKIPPED…) |
| `commonMain/RingingState.kt` | Which alarm is ringing right now (watched by the UI) |
| `commonMain/AlarmService.kt` | The one place the screens change alarms: save (engine first, then database), on/off, delete, sync the app's copy with the engine's, next ring time; `AlarmError`. Used by the alarm screens and onboarding |
| `androidMain/AndroidPlatformAlarmClock.kt` | `setAlarmClock()` with the alarm's extras |
| `androidMain/AlarmReceiver.kt` | Fires at ring time; skips wake-up-check rings while the phone is in use |
| `androidMain/AlarmRingingService.kt` | Foreground service: sound, vibration, full-screen notification, 15-minute timeout |
| `androidMain/AlarmSound.kt`, `AlarmVibration.kt` | Alarm-stream sound that gets louder (beeps as fallback); vibration |
| `androidMain/AlarmNotifications.kt` | The "ringing" notification channel and notification |
| `androidMain/SystemEventReceiver.kt` | Re-registers alarms after reboot, time or time zone change, app update |
| `androidMain/AlarmPermissions.kt` | What the phone allows (notifications, exact alarms, full screen, battery), where to fix it, and each phone maker's Autostart screen (`autostartSettings`) |
| `androidMain/AlarmIntents.kt` | Intent extras and the "show ringing screen" action |
| `androidMain/AlarmModule.kt` | Koin `alarmModule` (also `AlarmService`); device-protected file storage |
| `androidMain/AndroidManifest.xml` | Receivers, service, alarm permissions (and the one-tap battery prompt) |

## `core:database`: Room (credential-encrypted, never on the ring path)

| File | What's in it |
|---|---|
| `commonMain/SunupDatabase.kt` | The Room database and `buildSunupDatabase()` |
| `commonMain/entity/Entities.kt` | Tables: decks, notes, review_states, review_log, alarms, wake_sessions (all with `updatedAt` / `deletedAt`) |
| `commonMain/dao/Daos.kt` | All DAOs |
| `commonMain/mapper/Mappers.kt` | Entity ↔ domain model |
| `androidMain/DatabaseModule.kt` | Koin `databaseModule` (opened lazily) |
| `iosMain/IosDatabaseBuilder.kt` | iOS database location |
| `schemas/…/1.json`, `2.json`, `3.json` | Exported schemas. Version 2 added `game`, `isTrue`, `pairs` to notes; version 3 added `wake_sessions` (both auto-migrations). Changing an entity needs a new version and a migration |

## `core:data`: repository implementations

| File | What's in it |
|---|---|
| `RoomRepositories.kt` | Room-backed `Deck`, `Note`, `Review`, `WakeAlarm` and `WakeSession` repositories |
| `SafeLocal.kt` | Wraps database writes so failures become `DataError.Local` |
| `SampleDeckSeeder.kt` | The sample Spanish deck for the test bench: a card of every game |
| `GeoDeckSeeder.kt` | Builds a geography deck for a region from the map's country list |
| `DataStoreAppSettings.kt` | `AppSettingsRepository` on a DataStore file (`settings.preferences_pb`); `createSettingsDataStore` |
| `androidMain/SettingsStoreModule.android.kt` | Koin `settingsStoreModule`: the settings file in the app's files folder |
| `MetNorwayForecast.kt` | Reads MET Norway's Locationforecast JSON into `WeatherToday` (sky now, temperature, when rain starts today) |
| `androidMain/MetNorwayWeatherSource.kt` | Approximate location (last known, else a quick network fix) → MET Norway over HTTPS |
| `androidMain/AndroidCalendarSource.kt` | The next timed event later today from the phone's calendars |
| `androidMain/MorningInfoModule.android.kt` | Koin `morningInfoModule` (weather and calendar sources) |
| `androidMain/AndroidManifest.xml` | Internet, approximate location and calendar permissions |
| `DataModule.kt` | Koin `dataModule` (repositories, settings, seeders, `Clock`) |

## `core:design-system`: Sunup's look

| File | What's in it |
|---|---|
| `theme/SunupColors.kt` | Every color, including night/dawn/day, feedback and map colors |
| `theme/SunupTypography.kt` | Text styles |
| `theme/SunupDimens.kt` | Spacing, shapes, sizes (touch targets, map circle sizes) |
| `theme/SunupTheme.kt` | `SunupTheme { }` and the `SunupTheme.colors / typography / spacing / shapes / sizes` accessors |
| `components/SunupButton.kt` | The chunky 3D button |
| `components/SunupNavBar.kt` | The bottom bar (Alarm · Decks · Progress) with drawn icons |
| `components/SunupFormParts.kt` | `SunupTopBar` (back + title), `SunupBackButton`, `SunupFieldLabel`, `SunupTextField` (labelled text box) |
| `components/SunupTimeParts.kt` | `SunupStepper` (hours or minutes with arrows and ticking digits), `SunupDayChip`, `SunupIconButton`, `SunupChevron`. Used by the alarm editor and onboarding |
| `components/SunupRaised.kt` | Any surface with the mockups' darker edge underneath (cards, chips, segments, the + button); tappable ones sink when pressed |
| `motion/SunupMotion.kt` | `reduceMotion()` (Android "Remove animations" / iOS "Reduce Motion", platform code in `androidMain` / `iosMain`), `Modifier.riseIn(index)` (slide up and fade in, staggered), `Modifier.wobbleWhenOn(on)` (squash and stretch when picked), `Modifier.bob()` (Pip floating), `Modifier.popIn(index)` (pop in from small, staggered) |
| `components/Pip.kt` | Pip the sun, with moods |

## `core:geo`: the country map

| File | What's in it |
|---|---|
| `composeResources/files/geo/countries.json` | The map file (outlines, names, continents, nearby countries). Built by `tools/geo/build_countries.py`; don't edit by hand |
| `GeoAtlas.kt` | `GeoShape` (one outline, hit test), `GeoAtlas` (all shapes, countries per region, what's under a tap), file parsing |
| `GeoAtlasSource.kt` | Loads the bundled map once and caches it |
| `MapProjection.kt` | Lon/lat ↔ screen, pinch/drag limits, zooming in on a country |
| `CountryMap.kt` | The interactive map composable: colors, tiny-country circles, labels, taps |

## `feature:alarms`: Home, the alarm list and editor

Saving goes through `AlarmService` in `core:alarm`.

| File | What's in it |
|---|---|
| `presentation/HomeViewModel.kt` | Home state (`HomeAlarmUi`, `DeckProgressUi`, `WeekDayUi`), actions, events: the alarm shown on the card, its deck's progress, streak and week from `Streaks` |
| `presentation/HomeScreen.kt` | `HomeRoot` / `HomeScreen`: streak and cards chips, the alarm card with its switch, deck card, This week, Preview button, the "On You're up!" weather and calendar switches |
| `presentation/MorningPermissions.kt` (+ `androidMain` / `iosMain`) | `rememberMorningPermissions()`: location and calendar allowed?, and the prompts |
| `presentation/AlarmListViewModel.kt` | List state (`AlarmItemUi`), actions, events; runs the sync when the list opens; ticks every minute for "Rings in…" |
| `presentation/AlarmListScreen.kt` | `AlarmListRoot` / `AlarmListScreen`: Back, title and +, next-alarm banner, alarm cards with switches, empty state, test bench link |
| `presentation/AlarmEditorViewModel.kt` | Editor state and actions: time, days, mode, deck, save, delete |
| `presentation/AlarmEditorScreen.kt` | `AlarmEditorRoot` / `AlarmEditorScreen`: time steppers and time dialog, day chips, mode segments, deck list, delete confirm |
| `presentation/AlarmText.kt` | "Mon–Fri", "Rings in 8 h 19 min", mode names and hints |
| `presentation/AlarmErrors.kt` | Messages for `AlarmError` |
| `presentation/AlarmsModule.kt` | Koin `alarmsModule` (Home, list, editor) |

## `feature:decks`: making decks and cards

| File | What's in it |
|---|---|
| `domain/DeckImport.kt` | Import: `DeckImport.PROMPT` (what the user copies into their AI), `DeckImport.parse` (the AI's JSON → `ImportedDeck` with `CardDraft`s and `SkippedCard`s), `ImportError` |
| `domain/CardDraft.kt` | `CardDraft` (what a card form holds, one shape for every game), `CardProblem` (why it can't be saved), `CardDrafts.check` / `toNote` (form → card) |
| `presentation/DeckListViewModel.kt` / `DeckListScreen.kt` | The Decks tab (mockup D1): My decks with card counts and alarms, New deck; Browse packs with the map regions (add = load the region, remove = delete its deck) |
| `presentation/NewDeckScreen.kt` | New deck (D2): name, Language / Study / Trivia. ViewModel in the same file |
| `presentation/DeckDetailViewModel.kt` / `DeckDetailScreen.kt` | Deck detail (D3): card count, alarms using it, Add a card, filter by game, card rows (with "can't play yet"), settings dialog (rename, delete). Also `GamePickerScreen` (D4) |
| `presentation/CardEditorViewModel.kt` / `CardEditorScreen.kt` | The card form for each game (E1–E5), new or editing; Save & add another, Done, Delete card |
| `presentation/ImportViewModel.kt` / `ImportScreen.kt` | Import a deck (D5): copy the prompt, paste or upload, preview with an editable name and skipped cards, create |
| `presentation/FilePicker.kt` (+ `androidMain` / `iosMain`) | `rememberTextFilePicker`: the system file picker for the .json file (Android's document picker; Apple's Files picker on iOS) |
| `presentation/DeckText.kt` | Game and deck-kind names, badges and colours, card summaries, `CardProblem` messages |
| `presentation/DecksModule.kt` | Koin `decksModule` |

## `feature:onboarding`: the first-run screens

| File | What's in it |
|---|---|
| `presentation/OnboardingNavigation.kt` | `OnboardingRoute` and `onboardingGraph()`: Welcome → Goal → Time → Promise → Permissions → Mode, one shared ViewModel on the graph's back stack entry |
| `presentation/OnboardingViewModel.kt` | `LearnGoal`, State / Actions / Events for the whole flow; the test alarm; finishing (adds the map pack, saves the first alarm with `AlarmService`, sets the onboarding flag) |
| `presentation/OnboardingScreens.kt` | The six screens (mockups 1–4, C1, C2) |
| `presentation/OnboardingParts.kt` | `OnboardingFrame` (back + progress bar, scrolling body, buttons at the bottom), Pip with a speech bubble, choice cards, ticks, pills |
| `presentation/PhonePermissions.kt` (+ `androidMain` / `iosMain`) | `rememberPhonePermissions()`: which permissions this phone needs, which are allowed, and how to ask (prompts and Settings screens on Android; nothing yet on iOS) |
| `presentation/OnboardingGate.kt` | `rememberOnboardingDone()`, which `App.kt` uses to pick the first screen |
| `presentation/OnboardingText.kt` | Day names, goal and mode texts, "Your morning" steps |
| `presentation/OnboardingModule.kt` | Koin `onboardingModule` |

## `feature:progress`: the Progress tab

| File | What's in it |
|---|---|
| `presentation/ProgressViewModel.kt` | `ProgressState` (stats, `BarUi`, `MonthCellUi`, `MilestoneUi`), `Period`, actions; reads every morning and every answer |
| `presentation/ProgressScreen.kt` | `ProgressRoot` / `ProgressScreen`: Week/Month toggle, stat boxes, cards-per-morning bars, month grid with ‹ ›, milestone medals |
| `presentation/ProgressModule.kt` | Koin `progressModule` |

## `feature:wakeup`: the morning flow

| File | What's in it |
|---|---|
| `domain/WakeSessionPlanner.kt` | Picks this morning's cards: random, weighted toward due cards, per mode; geography plans; Preview all games; Quick math fallbacks |
| `presentation/WakeUpState.kt` | MVI State, `CardUi` (Choice, Typing, Match, FindOnMap), Actions, Events; `PREVIEW_ALARM_ID`, `PREVIEW_ALARM_PREFIX` |
| `presentation/WakeUpViewModel.kt` | Answers, grading, saving reviews, stopping the alarm, the wake-up check, loading the map; plays `TEST_ALARM_ID` as Gentle; `preview:<alarm id>` as practice; writes this morning's `WakeSession` and the "Up at 07:03" streak line |
| `presentation/WakeUpScreen.kt` | `WakeUpRoot`, the phase switch, the card page, the wake-up check chip, "Still with me?", palettes |
| `presentation/WakeUpBonus.kt` | The bonus round screen (improvement A3, phase BONUS_OFFER): Pip floating, the cards waiting (`BonusItemUi`), Let's go / Not today |
| `presentation/WakeUpDone.kt` | "You're up!" (improvement A4 + mockup 8's tiles, phase DONE): confetti, Pip jumping in the sky, weather and next-event tiles, Correct / Cards / Streak tiles, This morning, Start my day |
| `presentation/WakeUpRinging.kt` | The ringing screen (mockup 6, phase RINGING): stars, Pip rising with pulsing rings, the message, "I'm up, let's go" |
| `presentation/WakeUpCards.kt` | Card pieces: prompt card, options, match grid, typing input, map areas, feedback panel |
| `presentation/WakeUpModule.kt` | Koin `wakeUpModule` (planner, ViewModel, map source) |

## `androidApp` and `shared`

| File | What's in it |
|---|---|
| `androidApp/.../SunupApplication.kt` | Starts Koin with every module (also runs before first unlock) |
| `androidApp/.../MainActivity.kt` | Shows `App()`; opens the ringing screen while an alarm rings, and for Home's preview |
| `androidApp/.../alarm/AlarmRingingActivity.kt` | Shows `WakeUpRoot` over the lock screen; also opens Preview all games and Home's preview of one alarm |
| `androidApp/.../alarm/AlarmTestBench.kt` | Temporary debug panel: permissions, sample decks, modes, test alarms, ring log |
| `androidApp/src/main/AndroidManifest.xml` | Activities |
| `shared/.../App.kt` | Root composable and navigation: onboarding on first run (otherwise Home), the Alarm (Home), Decks and Progress tabs with the bottom bar, every inner screen, the test bench |
| `shared/.../MainViewController.kt` | iOS entry into Compose: the app, with the morning flow on top when one is open (its own ViewModel store per morning) |
| `shared/src/iosMain/.../ios/IosApp.kt` | `startSunup(alarmKit)`: iOS Koin (engine, database, settings, the features), then registers every alarm with AlarmKit again |
| `shared/src/iosMain/.../ios/AlarmKitBridge.kt` | The Swift-facing interface to AlarmKit (plain types) |
| `shared/src/iosMain/.../ios/IosPlatformAlarmClock.kt` | The engine's `PlatformAlarmClock` on iOS: one-off and weekly alarms, and the soft "stop follow-up" 3 minutes after each ring |
| `shared/src/iosMain/.../ios/IosMorning.kt` | Which morning is open (from "Wake up with Pip" or Home's preview); cancels the stop follow-up once the card is answered |
| `shared/src/iosMain/.../ios/IosStorage.kt` | `IosStateFile` (the engine's JSON file, written atomically) and the app's folders |
| `core:data/iosMain/IosWeatherSource.kt` | Weather on iOS: approximate location (Core Location), then MET Norway over `NSURLSession`; parsed by `MetNorwayForecast` like Android |
| `core:data/iosMain/IosCalendarSource.kt` | Next timed event today on iOS (EventKit) |
| `core:data/iosMain/MorningInfoModule.ios.kt` | iOS `morningInfoModule` (the two above) |
| `feature:alarms:presentation/iosMain/MorningPermissions.ios.kt` | Apple's location and calendar prompts behind Home's "On You're up!" switches |
| `core:alarm/iosMain/IosAlarmAuthorization.kt` | AlarmKit allowed? / ask: filled in at start-up, read by onboarding |
| `iosApp/iosApp/iOSApp.swift` | Calls `startSunup` with the AlarmKit bridge, then shows Compose |
| `iosApp/iosApp/AlarmKitBridgeImpl.swift` | AlarmKit in Swift: authorization, fixed and weekly schedules, Apple's alarm screen with "Wake up with Pip", stable UUIDs from Sunup's ids |
| `iosApp/iosApp/OpenMorningIntent.swift` | The App Intent behind "Wake up with Pip": opens Sunup on that alarm's card |
| `iosApp/iosApp/Info.plist` | `NSAlarmKitUsageDescription`. Deployment target iOS 26 (AlarmKit) |

## Key flows

**First run**: `App.kt` → `rememberOnboardingDone()` (DataStore) → false: `onboardingGraph` → the mode screen's "Set my alarm" → `OnboardingViewModel.finish`: `GeoDeckSeeder` (geography) → `AlarmService.save` → `AppSettingsRepository.setOnboardingDone` → `App.kt` opens Home (or the Decks tab). The test alarm: `AlarmScheduler.scheduleFollowUp(TEST_ALARM_ID, now + 10 s)`.

**Streaks**: `WakeUpViewModel` saves a `WakeSession` when the morning screen opens (RINGING), marks it UP when the last silencing card is answered (or STOPPED_WITHOUT_CARD on Emergency stop), and counts bonus cards → `WakeSessionRepository` → `HomeViewModel` reads them all and asks `Streaks` for the streak and the week.

**Setting an alarm**: `AlarmEditorViewModel` → `AlarmService.save` → `AlarmScheduler.schedule` (engine store + `setAlarmClock`) → then `WakeAlarmRepository.save` (database). Opening the list runs `AlarmService.syncWithEngine`.

**Importing a deck**: Decks tab → `ImportScreen` (copy `DeckImport.PROMPT`) → user's AI → paste or file → `DeckImport.parse` → preview → `DeckRepository.saveDeck` + `CardDrafts.toNote` + `NoteRepository.saveNote` per card → the new deck opens.

**Making a card**: Decks tab → `DeckDetailScreen` → `GamePickerScreen` → `CardEditorScreen` → `CardDrafts.toNote` → `NoteRepository.saveNote` (creates its review state). The deck detail and the morning planner read it back through `NoteRepository`.

**Alarm rings**: `DefaultAlarmScheduler.schedule` → `AndroidPlatformAlarmClock` (`setAlarmClock`) → at ring time `AlarmReceiver` → `AlarmRingingService` (sound first) → full-screen notification → `AlarmRingingActivity` → `WakeUpRoot`. After a reboot: `SystemEventReceiver` → `rescheduleAll`.

**Morning flow**: `WakeUpViewModel` asks `WakeSessionPlanner.plan(alarm)` → cards built by `CardBuilder` (games chosen with `GameEligibility`) → shown as `CardUi` in `WakeUpScreen` / `WakeUpCards` → answers graded in the ViewModel → `SpacedRepetition.review` → saved through `ReviewRepository`. The last silencing card stops the alarm and starts the wake-up check (`AlarmScheduler.scheduleFollowUp`).

**Geography data**: Natural Earth GeoJSON → `tools/geo/build_countries.py` → `countries.json` → `BundledGeoAtlasSource` → `GeoAtlas`. Decks: test bench → `GeoAtlas.countries(region)` → `GeoDeckSeeder` (one note per country, neighbours as wrong answers).

## Where to look for common changes

| To change… | Open |
|---|---|
| How or when an alarm rings | `core:alarm` (ring path: Receiver → Service). Read Rule #1 in `CLAUDE.md` first |
| Which cards appear in the morning, counts per mode | `WakeSessionPlanner.kt`, then `docs/HOW_IT_WORKS.md` |
| When a card comes back | `SpacedRepetition.kt` |
| Which games a note can use, the level-up path | `GameEligibility.kt` |
| Wrong options in a game | `CardBuilder.kt` |
| A screen in the morning flow | `WakeUpScreen.kt` / `WakeUpCards.kt` (+ State and ViewModel for behaviour) |
| Colors, text sizes, spacing | `core:design-system/theme/*` (feature code never uses raw hex or dp) |
| The 3D look or an animation | `SunupRaised.kt`, `motion/SunupMotion.kt`; check `reduceMotion()` in anything that moves |
| The map's look or touch handling | `CountryMap.kt`, `MapProjection.kt` |
| Map data (names, regions, neighbours) | `tools/geo/build_countries.py`, then rebuild `countries.json` |
| Database tables | `Entities.kt`, `Daos.kt`, `Mappers.kt`, then a schema version and migration |
| Home, or how streaks count | `HomeViewModel.kt` / `HomeScreen.kt`; rules in `core:domain`'s `Streaks.kt`, then `docs/HOW_IT_WORKS.md` |
| The alarm list or editor | `feature:alarms` (saving and syncing: `core:alarm`'s `AlarmService.kt`) |
| The first-run screens or the permission checklist | `feature:onboarding` (Android checks: `PhonePermissions.android.kt`, `AlarmPermissions.kt`) |
| The deck screens or a card form | `feature:decks` (form rules: `CardDraft.kt`) |
| Screens and navigation between them | `shared/.../App.kt` |
| Test tools on the phone | `AlarmTestBench.kt` (opened from the alarm list) |
| A version or library | `gradle/libs.versions.toml` |

**Adding a new game** touches: `Game.kt` → `Note.kt` (which fields it uses; a database version if it needs new ones) → `CardContent` + a builder in `CardBuilder.kt` (and `build()`) → `GameEligibility.CARD_GAMES` → `WakeUpState.kt` (`CardUi`) → `WakeUpViewModel.kt` (`toUi`, answer handling, grade) → `WakeUpCards.kt` / `WakeUpScreen.kt` (UI) → tests in `core:domain` and `feature:wakeup:domain` → `docs/HOW_IT_WORKS.md` (games table, which game when, grading).

**Adding a new module**: see the checklist under "How modules are wired".

## Tests

Each module's tests sit in its `src/commonTest`: alarm scheduling and the store (`core:alarm`), mappers (`core:database`), spaced repetition, eligibility, card building, answer checking and streaks and progress (`core:domain`), the forecast reader (`core:data`), map parsing and projection (`core:geo`), the morning planner (`feature:wakeup:domain`), the alarm service (`core:alarm`), alarm list text (`feature:alarms:presentation`). All run with `./gradlew testAndroidHostTest`.
