# Sunup

An alarm app that wakes you with one quick learning card instead of a snooze button. Mascot: Pip, a sun. "Sunup" is a working name.

- Product: [docs/PRD.md](docs/PRD.md). Later decisions that override it are in [ROADMAP.md](ROADMAP.md) under "Decisions since the PRD".
- Mockups (claude.ai Design canvases):
  - [Main flows, plus an "Improvements" section](https://claude.ai/artifact/Cs6LrpXMvhderkFmJeZcLr)
  - [Cards & play](https://claude.ai/artifact/Jtm3mMbUWN91Fo2UVpmCXm)
- Plan and progress: [ROADMAP.md](ROADMAP.md)
- How the app behaves today (morning flow, modes, wake-up check, games, grading): [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md). Read it before changing behaviour, and update it in the same commit when a rule changes.
- Where everything lives (every module and file, key flows, which files to open for a change, the checklist for adding a game): [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md). Start there to find code, and update it in the same commit when files or modules are added, moved or renamed.

## Rule #1: a missed alarm is the one unforgivable bug
Anything on the path from "alarm scheduled" to "alarm rings" must not depend on the network, the database being unlocked, or the UI rendering. The sound starts first; the card screen comes second.

## Stack
Kotlin Multiplatform + Compose Multiplatform, Android first; iOS builds on the Mac.
Koin for DI, Room KMP with `BundledSQLiteDriver`, DataStore, Kermit, kotlinx (coroutines, serialization, datetime). Tests use `kotlin.test` + AssertK + Turbine (not JUnit5, which doesn't run in `commonTest`).
Native alarm code per platform behind a shared `AlarmScheduler` interface: AlarmManager on Android, AlarmKit (Swift) on iOS.
All versions live in `gradle/libs.versions.toml`; never hardcode a version in a build file.

## Modules
```
:androidApp                    Android entry point (Application, activities, manifest, test bench)
:shared                        Root composable App(); the "Shared" framework the iOS app links
:core:domain                   Result, DataError, models, learning rules, repository interfaces
:core:alarm                    The alarm engine: scheduling, ringing, ring log (Rule #1); AlarmService (engine first, then database)
:core:database                 Room database, entities, DAOs, mappers
:core:data                     Repository implementations, settings (DataStore), sample and geography deck seeders
:core:design-system            SunupTheme tokens and shared components (SunupButton, SunupRaised, SunupStepper, Pip)
:core:geo                      Country map file loader and the interactive map (geography games)
:feature:alarms:presentation   Home (the Alarm tab: streak, next alarm, deck progress, week), the alarm list and the editor
:feature:decks:domain          Card forms: what each game's form holds, checking it, turning it into a card
:feature:decks:presentation    The Decks tab: My decks / Browse packs, New deck, Deck detail, game picker, card editor
:feature:onboarding:presentation  First-run screens and the permission checklist; saves the first alarm
:feature:wakeup:domain         Morning flow planning (which cards, per mode)
:feature:wakeup:presentation   Morning flow screens and ViewModel
tools/geo/                     Script that builds the map file from Natural Earth
build-logic/                   Convention plugins
iosApp/                        Xcode project
```
File-by-file details: [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md).
Feature modules are `:feature:<name>:{domain,data,presentation}`.
Wiring rules (details and a checklist in [docs/PROJECT_MAP.md](docs/PROJECT_MAP.md), "How modules are wired"): use `api` for a module whose types appear in your public signatures (constructors, return types), `implementation` otherwise; `androidApp` depends directly on every module whose Koin module `SunupApplication` registers; screens are reached through `shared`'s `App.kt`. Features never depend on each other; shared code moves to `:core:*`. Create a module only when code needs it.

Convention plugins (apply these, don't repeat config):
- `sunup.kmp.library`: KMP for Android + iOS, namespace taken from the module path, test dependencies included
- `sunup.kmp.compose`: the above plus Compose Multiplatform
- `sunup.android.application`: the Android app module
- `sunup.room`: Room KMP with the bundled SQLite driver (apply after `sunup.kmp.library`)

## Conventions
- Errors: return `Result<T, E>` from `:core:domain`, never throw for expected failures. The layer that owns an exception catches it.
- Presentation: MVI (State / Action / Event, ViewModel, Root + Screen composables).
- UI uses `SunupTheme.colors / typography / spacing / shapes / sizes`, never raw hex or dp values in feature code.
- Every synced record gets a UUID, `updatedAt` and `deletedAt` so sync can be added later without a migration.
- No ads, upsells or paywalls anywhere in the morning flow.
- Animations respect the system's reduce-motion setting: use `reduceMotion()` and the helpers in `core:design-system/motion` (`riseIn`, `wobbleWhenOn`). Raised cards and chips use `SunupRaised`.

## Commands
```bash
./gradlew :androidApp:assembleDebug     # build the Android app
./gradlew testAndroidHostTest           # run common + Android unit tests
```
iOS targets are skipped on Windows (`kotlin.native.ignoreDisabledTargets`). Build and run iOS from the Mac.

## Working here
- Don't run Gradle builds or tests, and don't start the emulator. The user builds and tests; finish each change by telling them what to run and what to check.
- Work on a branch per milestone or feature, with small commits that each build.
