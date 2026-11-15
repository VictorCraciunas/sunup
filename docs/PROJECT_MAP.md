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
androidApp ──► shared, feature:alarms:presentation, feature:wakeup:presentation, core:alarm, core:data, core:database,
               core:design-system, core:geo
shared ──────► feature:alarms:presentation, core:domain, core:design-system

feature:alarms:presentation ──► feature:alarms:domain, core:design-system
feature:alarms:domain ────────► core:domain, core:alarm
feature:wakeup:presentation ──► feature:wakeup:domain, core:alarm, core:design-system, core:geo
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
- Use **`api`** when a type from the other module appears in your **public signatures**: constructor parameters, public function parameters or return types, public properties, base classes. Whoever uses your class needs that type too. Examples: `core:alarm` exposes `core:domain` (its `Result`, `Error`); `feature:alarms:domain` exposes `core:alarm`, because `AlarmService(scheduler: AlarmScheduler, engine: AlarmStore, …)` takes engine types and the presentation module's Koin module builds it.
- Use **`implementation`** when the other module is only used inside your code: `core:data` uses `core:database` privately; the presentation modules use `core:design-system` privately.

**Who builds a class needs its constructor's types.** Koin modules call constructors, so the module holding the Koin `module { }` needs every type in those constructors, directly or through `api`.

**`androidApp` must depend directly on every module whose Koin module it registers.** `SunupApplication.kt` imports `alarmModule`, `databaseModule`, `dataModule`, `alarmsModule`, `wakeUpModule`, so `androidApp/build.gradle.kts` lists `core:alarm`, `core:database`, `core:data`, `feature:alarms:presentation`, `feature:wakeup:presentation` itself. Getting them through `shared` doesn't count: `shared` uses `implementation`.

**Screens are reached through `shared`.** `App.kt` (in `shared`) holds the navigation, so `shared` depends on each feature's presentation module whose `Root` composable it shows. The morning flow is the exception: `AlarmRingingActivity` in `androidApp` shows `WakeUpRoot` directly.

**Features never depend on each other.** Code two features need moves to `core:*`.

**Compose resources** (fonts, the map file) live in the module that reads them, under `src/commonMain/composeResources/`, with `compose.resources { packageOfResClass = … }` in its build file (see `core:geo`).

**Every dependency in each build file today:**

| Module | `api` | `implementation` |
|---|---|---|
| `core:domain` | kotlinx-datetime, kotlinx-coroutines | — |
| `core:alarm` | `core:domain`, kotlinx-datetime | coroutines, serialization, Kermit, Koin (+ androidx-core on Android) |
| `core:database` | `core:domain` | coroutines, serialization, Koin, Room (via `sunup.room`) |
| `core:data` | `core:domain` | `core:database`, coroutines, Koin |
| `core:design-system` | — | Compose (via `sunup.kmp.compose`) |
| `core:geo` | `core:domain` | `core:design-system`, coroutines, serialization |
| `feature:alarms:domain` | `core:domain`, `core:alarm` | coroutines |
| `feature:alarms:presentation` | — | `feature:alarms:domain`, `core:design-system`, lifecycle, Koin |
| `feature:wakeup:domain` | `core:domain` | coroutines |
| `feature:wakeup:presentation` | — | `feature:wakeup:domain`, `core:alarm`, `core:design-system`, `core:geo`, lifecycle, Koin |
| `shared` | — | `core:domain`, `core:design-system`, `feature:alarms:presentation`, lifecycle, navigation, serialization |
| `androidApp` | — | `shared`, `core:alarm`, `core:data`, `core:database`, `core:design-system`, `core:geo`, `feature:alarms:presentation`, `feature:wakeup:presentation`, activity, Compose, Koin |

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
| `learning/Note.kt` | `Note` (one fact the user writes), `NoteType`, `Direction` |
| `learning/ReviewState.kt` | How well a note is known: step, `MasteryLevel`, due date; `Grade` |
| `learning/ReviewRecord.kt` | One answered card, kept for stats; `ReviewContext` |
| `learning/SpacedRepetition.kt` | When a card comes back (the 1 → 3 → 8 → 21 → 45 → 90 day ladder) |
| `learning/Game.kt` | Every game, with Free/Plus and "can stop the alarm" |
| `learning/GameEligibility.kt` | Which games a note can be played as, and which one fits its level |
| `learning/CardBuilder.kt` | `CardContent` (what each card shows) and `CardBuilder` (note → card, wrong options) |
| `learning/AnswerMatcher.kt` | Forgiving check for typed answers (accents, typos, articles) |
| `learning/SentenceGap.kt` | Finds the word in its example sentence, for Fill the gap |
| `learning/GeoRegion.kt` | Map regions (Europe … World): continents and map bounds |
| `learning/Country.kt` | A country's facts (code, name, continent, size, nearby countries) |
| `learning/LearningRepositories.kt` | `DeckRepository`, `NoteRepository`, `ReviewRepository` interfaces |
| `wakeup/WakeAlarm.kt` | An alarm as the user sets it up (deck, `WakeMode`, label) |
| `wakeup/WakeAlarmRepository.kt` | Its repository interface |

## `core:alarm`: the alarm engine (Rule #1 lives here)

Nothing here may touch the network or the database. Alarms and the ring log are a JSON file in device-protected storage, readable before the first unlock after a reboot.

| File | What's in it |
|---|---|
| `commonMain/Alarm.kt` | The engine's small view of an alarm: id, time, repeat days |
| `commonMain/AlarmScheduler.kt` | The shared API: `schedule`, `cancel`, `onAlarmRang`, `rescheduleAll`, follow-ups (wake-up check); `RingOptions`; `PlatformAlarmClock`; `WakeUpScreenState` |
| `commonMain/DefaultAlarmScheduler.kt` | The scheduler: works out next ring times, logs every step |
| `commonMain/NextRingTime.kt` | Next ring time for an alarm, with repeat days and time zones |
| `commonMain/AlarmStore.kt`, `FileAlarmStore.kt` | The engine's copy of alarms plus the ring log, as one atomic JSON file |
| `commonMain/RingEvent.kt` | One ring log line (SCHEDULED, RANG, STOPPED, SKIPPED…) |
| `commonMain/RingingState.kt` | Which alarm is ringing right now (watched by the UI) |
| `androidMain/AndroidPlatformAlarmClock.kt` | `setAlarmClock()` with the alarm's extras |
| `androidMain/AlarmReceiver.kt` | Fires at ring time; skips wake-up-check rings while the phone is in use |
| `androidMain/AlarmRingingService.kt` | Foreground service: sound, vibration, full-screen notification, 15-minute timeout |
| `androidMain/AlarmSound.kt`, `AlarmVibration.kt` | Alarm-stream sound that gets louder (beeps as fallback); vibration |
| `androidMain/AlarmNotifications.kt` | The "ringing" notification channel and notification |
| `androidMain/SystemEventReceiver.kt` | Re-registers alarms after reboot, time or time zone change, app update |
| `androidMain/AlarmPermissions.kt` | What the phone allows (notifications, exact alarms, full screen, battery) and where to fix it |
| `androidMain/AlarmIntents.kt` | Intent extras and the "show ringing screen" action |
| `androidMain/AlarmModule.kt` | Koin `alarmModule`; device-protected file storage |
| `androidMain/AndroidManifest.xml` | Receivers, service, alarm permissions |

## `core:database`: Room (credential-encrypted, never on the ring path)

| File | What's in it |
|---|---|
| `commonMain/SunupDatabase.kt` | The Room database and `buildSunupDatabase()` |
| `commonMain/entity/Entities.kt` | Tables: decks, notes, review_states, review_log, alarms (all with `updatedAt` / `deletedAt`) |
| `commonMain/dao/Daos.kt` | All DAOs |
| `commonMain/mapper/Mappers.kt` | Entity ↔ domain model |
| `androidMain/DatabaseModule.kt` | Koin `databaseModule` (opened lazily) |
| `iosMain/IosDatabaseBuilder.kt` | iOS database location |
| `schemas/…/1.json` | Exported schema. Changing an entity needs a new version and a migration |

## `core:data`: repository implementations

| File | What's in it |
|---|---|
| `RoomRepositories.kt` | Room-backed `Deck`, `Note`, `Review` and `WakeAlarm` repositories |
| `SafeLocal.kt` | Wraps database writes so failures become `DataError.Local` |
| `SampleDeckSeeder.kt` | The sample Spanish deck for the test bench |
| `GeoDeckSeeder.kt` | Builds a geography deck for a region from the map's country list |
| `DataModule.kt` | Koin `dataModule` (repositories, seeders, `Clock`) |

## `core:design-system`: Sunup's look

| File | What's in it |
|---|---|
| `theme/SunupColors.kt` | Every color, including night/dawn/day, feedback and map colors |
| `theme/SunupTypography.kt` | Text styles |
| `theme/SunupDimens.kt` | Spacing, shapes, sizes (touch targets, map circle sizes) |
| `theme/SunupTheme.kt` | `SunupTheme { }` and the `SunupTheme.colors / typography / spacing / shapes / sizes` accessors |
| `components/SunupButton.kt` | The chunky 3D button |
| `components/SunupRaised.kt` | Any surface with the mockups' darker edge underneath (cards, chips, segments, the + button); tappable ones sink when pressed |
| `motion/SunupMotion.kt` | `reduceMotion()` (Android "Remove animations" / iOS "Reduce Motion", platform code in `androidMain` / `iosMain`), `Modifier.riseIn(index)` (slide up and fade in, staggered), `Modifier.wobbleWhenOn(on)` (squash and stretch when picked) |
| `components/Pip.kt` | Pip the sun, with moods |

## `core:geo`: the country map

| File | What's in it |
|---|---|
| `composeResources/files/geo/countries.json` | The map file (outlines, names, continents, nearby countries). Built by `tools/geo/build_countries.py`; don't edit by hand |
| `GeoAtlas.kt` | `GeoShape` (one outline, hit test), `GeoAtlas` (all shapes, countries per region, what's under a tap), file parsing |
| `GeoAtlasSource.kt` | Loads the bundled map once and caches it |
| `MapProjection.kt` | Lon/lat ↔ screen, pinch/drag limits, zooming in on a country |
| `CountryMap.kt` | The interactive map composable: colors, tiny-country circles, labels, taps |

## `feature:alarms`: the alarm list and editor

| File | What's in it |
|---|---|
| `domain/AlarmService.kt` | The one place alarms change: save (engine first, then database), on/off, delete, sync the app's copy with the engine's, next ring time; `AlarmError` |
| `presentation/AlarmListViewModel.kt` | List state (`AlarmItemUi`), actions, events; runs the sync when the list opens; ticks every minute for "Rings in…" |
| `presentation/AlarmListScreen.kt` | `AlarmListRoot` / `AlarmListScreen`: header with +, next-alarm banner, alarm cards with switches, empty state, test bench link |
| `presentation/AlarmEditorViewModel.kt` | Editor state and actions: time, days, mode, deck, save, delete |
| `presentation/AlarmEditorScreen.kt` | `AlarmEditorRoot` / `AlarmEditorScreen`: time steppers and time dialog, day chips, mode segments, deck list, delete confirm |
| `presentation/AlarmText.kt` | "Mon–Fri", "Rings in 8 h 19 min", mode names and hints |
| `presentation/AlarmErrors.kt` | Messages for `AlarmError` |
| `presentation/AlarmsModule.kt` | Koin `alarmsModule` |

## `feature:wakeup`: the morning flow

| File | What's in it |
|---|---|
| `domain/WakeSessionPlanner.kt` | Picks this morning's cards: silencing + bonus per mode, geography plans, Preview all games, Quick math fallbacks |
| `presentation/WakeUpState.kt` | MVI State, `CardUi` (Choice, Typing, Match, FindOnMap), Actions, Events |
| `presentation/WakeUpViewModel.kt` | Answers, grading, saving reviews, stopping the alarm, the wake-up check, loading the map |
| `presentation/WakeUpScreen.kt` | `WakeUpRoot` and the screens: card page, bonus offer, "You're up!", "Still with me?", palettes |
| `presentation/WakeUpCards.kt` | Card pieces: prompt card, options, match grid, typing input, map areas, feedback panel |
| `presentation/WakeUpModule.kt` | Koin `wakeUpModule` (planner, ViewModel, map source) |

## `androidApp` and `shared`

| File | What's in it |
|---|---|
| `androidApp/.../SunupApplication.kt` | Starts Koin with every module (also runs before first unlock) |
| `androidApp/.../MainActivity.kt` | Home screen; opens the ringing screen while an alarm rings |
| `androidApp/.../alarm/AlarmRingingActivity.kt` | Shows `WakeUpRoot` over the lock screen; also opens Preview all games |
| `androidApp/.../alarm/AlarmTestBench.kt` | Temporary debug panel: permissions, sample decks, modes, test alarms, ring log |
| `androidApp/src/main/AndroidManifest.xml` | Activities |
| `shared/.../App.kt` | Root composable and navigation: alarm list (start), alarm editor, test bench |
| `shared/.../MainViewController.kt` | iOS entry into Compose |

## Key flows

**Setting an alarm**: `AlarmEditorViewModel` → `AlarmService.save` → `AlarmScheduler.schedule` (engine store + `setAlarmClock`) → then `WakeAlarmRepository.save` (database). Opening the list runs `AlarmService.syncWithEngine`.

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
| The alarm list or editor | `feature:alarms` (saving and syncing: `AlarmService.kt`) |
| Screens and navigation between them | `shared/.../App.kt` |
| Test tools on the phone | `AlarmTestBench.kt` (opened from the alarm list) |
| A version or library | `gradle/libs.versions.toml` |

**Adding a new game** touches: `Game.kt` → `CardContent` + a builder in `CardBuilder.kt` → `GameEligibility.kt` (when it's allowed, where it sits on the level path) → `WakeSessionPlanner.kt` (when it's picked, Preview) → `WakeUpState.kt` (`CardUi`) → `WakeUpViewModel.kt` (`toUi`, answer handling, grade) → `WakeUpCards.kt` / `WakeUpScreen.kt` (UI) → tests in `core:domain` and `feature:wakeup:domain` → `docs/HOW_IT_WORKS.md` (games table, which game when, grading).

**Adding a new module**: see the checklist under "How modules are wired".

## Tests

Each module's tests sit in its `src/commonTest`: alarm scheduling and the store (`core:alarm`), mappers (`core:database`), spaced repetition, eligibility, card building and answer checking (`core:domain`), map parsing and projection (`core:geo`), the morning planner (`feature:wakeup:domain`), the alarm service (`feature:alarms:domain`), alarm list text (`feature:alarms:presentation`). All run with `./gradlew testAndroidHostTest`.
