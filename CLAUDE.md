# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"Babe, Get This" is a shopping list Android app in early MVP stage. Single-module Compose-first project using Clean Architecture with feature-based package organization.

**Package:** `com.babegetthis.android`

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "com.babegetthis.android.ExampleUnitTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Clean build
./gradlew clean assembleDebug
```
# Android Learning Project

## About Me
- Experienced Flutter/Dart developer
- Complete beginner to native Android
- Goal: learn native Android quickly through building
- I understand mobile concepts (state management, navigation, lifecycle)
  but need native Android equivalents explained

## Teaching Style
- Always explain the "Flutter equivalent" when introducing native concepts
  e.g. "This is like Provider in Flutter, but here it's ViewModel + StateFlow"
- Explain *why* we do things the native Android way, not just how
- Prefer explicit, readable code over clever shortcuts while I'm learning
- When you introduce a new concept (Coroutines, Hilt, Compose, etc.),
  give a one-line explanation before using it

## Stack
- Language: Kotlin (not Java)
- UI: Jetpack Compose (I'm coming from Flutter widgets, this maps well)
- Architecture: MVVM + Repository Pattern (no full clean arch yet)
- Min SDK: 26, Target SDK: 35

## Architecture
We use MVVM + Repository pattern only. No domain layer or use cases yet.
Add use cases later only when a ViewModel gets complex enough to justify it.
- ViewModels never talk directly to data sources — always go through a Repository
- Repositories return Kotlin data classes, never raw API/DB models
- Do NOT add use cases/interactors until a ViewModel is clearly getting too fat
- Make UI dumb, as long as it is reasonable we will keep business logic in viewmodel.

## Coding Conventions
- Use meaningful variable names — no abbreviations while I'm learning
- Add comments explaining *why*, not just what
- Prefer simple solutions over optimized ones until I understand the basics
- Only write absolutely needed comments, don't explain every possible corner case

## What I Already Know (don't over-explain)
- Mobile app lifecycle concepts
- State management patterns
- Navigation patterns
- REST APIs and JSON parsing
- Async programming concepts (I know async/await, teach me Coroutines)

## App Overview
BabeGetThis — a grocery list app for couples.
Core feature: shared grocery lists with real-time sync between partners.
Everything we build should serve this core or be deferred to v2.

## Offline-First Architecture
- App must be fully functional without internet — a grocery app should never show a blank screen
- All data is stored locally in Room first (single source of truth)
- Core offline features: create lists, add/edit/delete items, mark as picked up
- Sharing between users requires internet, but local usage never does
- Auto-sync when online — no manual sync button
- Conflict resolution strategy: TBD when we add the backend

## Project Structure
[Update this as the project grows]

## Running the App
[Add your build/run commands here]


## TODO
- [ ] **(tomorrow) Add Supabase keys** to `local.properties`: `SUPABASE_URL` and
      `SUPABASE_ANON_KEY` (from Supabase dashboard → Project Settings → API).
      Until then, auth builds but won't connect. See
      `docs/technical-decisions/001-auth-via-supabase.md`. Test on the **staging**
      variant (dev uses the fake auth repo).
- [ ] **(tomorrow, discuss first) Improve `SupabaseAuthRepository` readability** —
      not happy with it as-is; explore a cleaner structure before refactoring.
- [ ] **(tomorrow, discuss first) Two Supabase projects** — one for production, one
      shared by staging + dev — wired per flavor (like `BASE_URL`), with dev still
      using the fake auth repo. Decide project split + how keys map to flavors.
- Shopping lists should show a "completed" status when all items in the list are checked/picked up
- Greeting on the home screen should be time-aware (Good morning/afternoon/evening) — already implemented, keep it this way
- Create a work manager that checks for any stale list (Yet to decide Criteria to define stale list) and move them to history list or something. Purpose is to one good ui and learning opportunity. And a way to move this stale list back to active or complete.
- Use code magic for CI-CD (codemagic.yaml  not ui). Two work flows, for staging firebase Distribution and production on play store.
- One live scenario, let's say someone deletes the item, user have to put in optional note on why they deleted the item. IDK i just want it to be easier for user to communicate.
- What happens if user can't find the item? our user find the alternative. There should be a better way than chat app. Should solve a problem that call and chatting app doesn't do. Have to make it quick, just take image and may be just approve or reject message. 
  and algorithm that provides suggestion based on history.
- auto categorization of product on basic stuff. (For eg: Eggs will always be a food)

Strategy for first release
a) Complete offline first method.

## for v2
1) open camera or gallery to add image.(Auto fill form from image)
2) Store room, when user completes a list we will add the item to store room for grocery items only. In store they can mark item as finish to keep it in the next list automatically.