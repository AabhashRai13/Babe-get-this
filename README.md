# Babe, Get This

A modern, offline-first shopping list app for couples — built with Jetpack Compose and Material 3. Create shared lists, add items as you think of them, and sync with your partner when you're online.

## Features

- Create and manage multiple shopping lists
- Voice capture — dictate your groceries and get a new, auto-named list (requires internet)
- Add items with quantities, categories, notes, and a per-item shop
- Mark items as picked up with a single tap, with undo on accidental deletes
- Share any list as plain text through any messaging app
- Time-aware greeting and progress tracking on the home screen
- Fully usable offline — Room is the single source of truth
- Light and dark theme that follow the system setting
- Authenticated accounts, with real-time sync between partners over a shared list code

## Tech Stack

- **Language** — Kotlin
- **UI** — Jetpack Compose with Material 3 (Material You)
- **Architecture** — MVVM + Repository
- **Async** — Kotlin Coroutines + Flow
- **Dependency Injection** — Hilt
- **Local Storage** — Room (offline-first)
- **Auth** — Supabase (email/password sessions with automatic token refresh)
- **Networking** — Retrofit + Kotlin Serialization (voice transcription API)
- **Min SDK** 24, **Target SDK** 36

## Project Structure

```
app/src/main/java/com/babegetthis/android/
├── BabeGetThisApp.kt          # Application class — Hilt entry point
├── MainActivity.kt            # Single-activity host
├── navigation/                # Compose NavHost graph
├── ui/theme/                  # Color palette, typography, theme wiring
├── core/                      # Cross-feature building blocks
│   ├── auth/                  # Login, registration, tokens, session state
│   ├── data/                  # Network clients and shared data sources
│   ├── error/                 # App-wide error types and Result wrappers
│   ├── model/                 # Shared domain models
│   ├── network/               # Connectivity monitoring
│   ├── ui/                    # Reusable Compose components
│   ├── util/                  # Formatters and shared helpers
│   └── voice/                 # Voice recording, transcription client, capture UI
└── feature/                   # Feature modules — each owns its data, model, ui
    ├── profile/               # Profile bottom sheet
    ├── shoppinglist/          # List catalog (home screen)
    └── shoppingitems/         # Items inside a list
```

Each feature directory follows a consistent shape:

```
feature/<name>/
├── data/         # Repositories, DAOs, mappers
├── model/        # Domain models and UI state
└── ui/           # Screens, ViewModels, components
```

## Build & Run

The project uses Gradle's Kotlin DSL.

```bash
./gradlew assembleDebug              # Build the debug APK
./gradlew installDebug               # Install on a connected device or emulator
./gradlew test                       # Run unit tests
./gradlew connectedAndroidTest       # Run instrumented tests (device required)
./gradlew lint                       # Run the linter
```

### Build Variants

The app ships with three variants that can be installed side by side:

| Variant   | Application ID                     | Notes                                |
| --------- | ---------------------------------- | ------------------------------------ |
| `dev`     | `com.babegetthis.android.dev`      | Local development; uses fake auth    |
| `staging` | `com.babegetthis.android.staging`  | Hits the staging backend             |
| `prod`    | `com.babegetthis.android`          | Release build                        |

## Architecture at a Glance

- **UI** stays dumb. Composables observe state from a `ViewModel` and emit user intents back.
- **ViewModel** owns business logic and screen state. It talks to repositories only — never directly to the network or database.
- **Repository** is the single boundary for a feature's data. It mediates between Room (local source of truth) and Retrofit (remote sync), and returns plain Kotlin data classes.
- **Room** is the local store. The app is fully usable offline; sync runs in the background when the user is authenticated and online.

We deliberately stay close to MVVM + Repository. Use cases and a full Clean Architecture split are not introduced unless a screen genuinely needs them.

## Testing

```bash
./gradlew testDevDebugUnitTest        # unit + Compose, on the JVM, seconds
./gradlew koverVerifyDevDebug         # coverage gate
./gradlew connectedDevDebugAndroidTest  # end-to-end, needs an emulator
```

Three layers, each answering a different question:

| Layer | Where | Question it answers |
|---|---|---|
| Unit | `src/test/` | Is this logic correct? |
| Compose | `src/test/` (Robolectric) | Does the screen render and dispatch correctly? |
| End-to-end | `src/androidTest/` | Are the pieces actually wired together? |

Compose tests run on the JVM under Robolectric, so only the five end-to-end
journeys need a device. Nothing in either suite touches the network — auth,
Supabase and voice transcription are replaced with local fakes, so no
credentials are needed anywhere.

**The coverage gate is a floor, not a grade.** It enforces 100% line coverage on
the logic layer — ViewModels, repositories, mappers, error handling, PIN, auth —
and fails the build below that, so new logic cannot land untested by accident.
Composables sit outside it and are covered by Compose tests instead; a handful of
classes that cannot run on the JVM at all (anything behind `EncryptedSharedPreferences`,
which needs the AndroidKeyStore) are excluded with the reason stated in
`app/build.gradle.kts` and covered by instrumented tests.

It says nothing about whether the tests are any good — a line can execute without
being asserted on. Reviewers still have to read them.

See [docs/running-tests.md](docs/running-tests.md) for running from Android
Studio and for the failure modes worth recognising.

## Contributing

Contributions are welcome. The project is in an early stage and there is plenty of room to shape what comes next.

A few things that help PRs land quickly:

1. **Open an issue first** for anything that changes scope or architecture so we can align before you build.
2. **Keep PRs focused** — one logical change per PR, with a short note on *why*.
3. **Match the existing style** — feature-based packages, MVVM + Repository, Compose for UI, Material 3 color roles for theming.
4. **Run `./gradlew lint test`** before pushing.

If you are not sure where to start, look for issues labeled `good first issue` or open one with your idea — happy to help scope it.

## Roadmap

- [x] Offline list and item management
- [x] Authentication and account creation
- [x] Light and dark theme with Material 3 color roles
- [x] Voice-to-list — dictate a whole shopping list
- [x] Share a list as plain text
- [ ] Auto-categorization of common items
- [x] Real-time sync between partners
- [x] Shared list invitations
- [ ] Camera and gallery capture with image-driven item autofill (v2)
- [ ] "Store room" pantry — completed grocery items carry over to the next list (v2)
