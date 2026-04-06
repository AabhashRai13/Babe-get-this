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

## Coding Conventions
- Use meaningful variable names — no abbreviations while I'm learning
- Add comments explaining *why*, not just what
- Prefer simple solutions over optimized ones until I understand the basics

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