# Babe, Get This

A modern, offline-first shopping list app for couples — built with Jetpack Compose and Material 3. Create shared lists, add items as you think of them, and sync with your partner when you're online.

## Features

- Create and manage multiple shopping lists
- Add items with quantities, categories, notes, and a per-item shop
- Mark items as picked up with a single tap, with undo on accidental deletes
- Time-aware greeting and progress tracking on the home screen
- Fully usable offline — Room is the single source of truth
- Light and dark theme that follow the system setting
- Authenticated accounts (real-time sync between partners is in active development)

## Tech Stack

- **Language** — Kotlin
- **UI** — Jetpack Compose with Material 3 (Material You)
- **Architecture** — MVVM + Repository
- **Async** — Kotlin Coroutines + Flow
- **Dependency Injection** — Hilt
- **Local Storage** — Room (offline-first)
- **Networking** — Retrofit + Kotlin Serialization
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
│   ├── ui/                    # Reusable Compose components
│   └── util/                  # Formatters and shared helpers
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
- [ ] Real-time sync between partners
- [ ] Shared list invitations
- [ ] Voice-to-list (v2)
- [ ] Camera and gallery capture with image-driven item autofill (v2)
