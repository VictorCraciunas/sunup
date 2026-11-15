# Sunup

An alarm app that wakes you with one quick learning card instead of a snooze button. Mascot: Pip, a sun. "Sunup" is a working name.

- Product: [docs/PRD.md](docs/PRD.md). Later decisions that override it are in [ROADMAP.md](ROADMAP.md) under "Decisions since the PRD".
- Mockups (claude.ai Design canvases):
  - [Main flows, plus an "Improvements" section](https://claude.ai/artifact/Cs6LrpXMvhderkFmJeZcLr)
  - [Cards & play](https://claude.ai/artifact/Jtm3mMbUWN91Fo2UVpmCXm)
- Plan and progress: [ROADMAP.md](ROADMAP.md)
- How the app behaves today (morning flow, modes, wake-up check, games, grading): [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md). Read it before changing behaviour, and update it in the same commit when a rule changes.

## Rule #1: a missed alarm is the one unforgivable bug
Anything on the path from "alarm scheduled" to "alarm rings" must not depend on the network, the database being unlocked, or the UI rendering. The sound starts first; the card screen comes second.

## Stack
Kotlin Multiplatform + Compose Multiplatform, Android first; iOS builds on the Mac.
Koin for DI, Room KMP with `BundledSQLiteDriver`, DataStore, Kermit, kotlinx (coroutines, serialization, datetime). Tests use `kotlin.test` + AssertK + Turbine (not JUnit5, which doesn't run in `commonTest`).
Native alarm code per platform behind a shared `AlarmScheduler` interface: AlarmManager on Android, AlarmKit (Swift) on iOS.
All versions live in `gradle/libs.versions.toml`; never hardcode a version in a build file.

## Modules
```
:androidApp            Android entry point (Activity, manifest)
:shared                Root composable App(); the "Shared" framework the iOS app links
:core:domain           Result, DataError, shared models and repository interfaces
:core:design-system    SunupTheme tokens and shared components (SunupButton)
:core:geo              Country map file loader and the interactive map (geography game)
tools/geo/             Script that builds the map file from Natural Earth
build-logic/           Convention plugins
iosApp/                Xcode project
```
Feature modules come later as `:feature:<name>:{domain,data,presentation}`. Features never depend on each other; shared code moves to `:core:*`. Create a module only when code needs it.

Convention plugins (apply these, don't repeat config):
- `sunup.kmp.library`: KMP for Android + iOS, namespace taken from the module path, test dependencies included
- `sunup.kmp.compose`: the above plus Compose Multiplatform
- `sunup.android.application`: the Android app module

## Conventions
- Errors: return `Result<T, E>` from `:core:domain`, never throw for expected failures. The layer that owns an exception catches it.
- Presentation: MVI (State / Action / Event, ViewModel, Root + Screen composables).
- UI uses `SunupTheme.colors / typography / spacing / shapes / sizes`, never raw hex or dp values in feature code.
- Every synced record gets a UUID, `updatedAt` and `deletedAt` so sync can be added later without a migration.
- No ads, upsells or paywalls anywhere in the morning flow.
- Animations respect the system's reduce-motion setting.

## Commands
```bash
./gradlew :androidApp:assembleDebug     # build the Android app
./gradlew testAndroidHostTest           # run common + Android unit tests
```
iOS targets are skipped on Windows (`kotlin.native.ignoreDisabledTargets`). Build and run iOS from the Mac.

## Working here
- Don't run Gradle builds or tests, and don't start the emulator. The user builds and tests; finish each change by telling them what to run and what to check.
- Work on a branch per milestone or feature, with small commits that each build.
