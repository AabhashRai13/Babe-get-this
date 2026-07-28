# 003 — In-app update (Play Core) + Supabase feature flags

**Status:** Accepted (planning) · **Date:** 2026-07-28
**Area:** new `core/update/` capability, new `core/featureflags/` capability

## Context

Two related but separate friction problems:

1. Users on Play Store auto-update settle on old versions if auto-update is off, or lag a day or two even with it on. We want new releases to reach devices with minimal user action, without going outside Play Store review (that's fine — Play review is fast).
2. We want to ship risky/large features (e.g. a "Reels"-style feature) in small merged increments without exposing unfinished work, and turn things on/off without needing a new release for every toggle. Real-time propagation is not required — checking once per app launch is enough.

These are independent systems. In-app update is entirely local (Play Core SDK talking to Play Store). Feature flags are a Supabase table read once at startup.

## Decision

### 1. In-app update — Play Core, flexible flow by default

- Add `com.google.android.play:app-update-ktx` (Play Core).
- New `core/update/InAppUpdateManager.kt` (Hilt singleton) wraps `AppUpdateManager`.
- Default to **FLEXIBLE** update type. **IMMEDIATE** is reserved for updates you mark high priority at release time (see priority table below) — e.g. a security fix or a breaking API change that makes old clients unusable.
- Completion behavior depends on app state when the download finishes:
  - **App backgrounded** → call `completeUpdate()` silently. No UI. User just opens the app already updated next time.
  - **App foregrounded** → show a Snackbar ("Update ready — Restart") because `completeUpdate()` forces an immediate process restart; that needs user consent when they're actively using the app.
- Foreground/background state comes from `ProcessLifecycleOwner`, not from each Activity individually (single check point for the whole app).
- Check for updates in `MainActivity.onResume()` (Play's own recommendation — catches an update that was already in progress when the app was last backgrounded).

### 2. Feature flags — plain Supabase table, non-realtime

- New table `feature_flags (key text primary key, enabled boolean not null default false, updated_at timestamptz default now())`.
- RLS: anon role gets `SELECT` only. No client writes.
- New `core/featureflags/data/FeatureFlagRepository.kt` — Postgrest read, same pattern as `CategoryRepository.kt`.
- New `core/featureflags/FeatureFlagCache.kt` — Hilt `@Singleton`, in-memory `Map<String, Boolean>`, populated once via a `fetchFlags()` call from `BabeGetThisApp` at process start. No polling, no Room table, no realtime subscription — a fresh fetch only happens on next app process start, which is the accepted non-realtime tradeoff.
- Usage: inject `FeatureFlagCache`, guard new/incomplete feature UI with `cache.isEnabled("reels")`.

## Flow diagram — release → Play Console → device

```
 DEV MACHINE                      PLAY CONSOLE                    USER DEVICE
┌───────────────┐            ┌─────────────────────┐        ┌───────────────────────┐
│ Code change    │           │                      │        │                       │
│ (small,        │           │                      │        │                       │
│  milestone-    │           │                      │        │                       │
│  sized)        │           │                      │        │                       │
│       │        │           │                      │        │                       │
│       ▼        │           │                      │        │                       │
│ Feature wrapped │          │                      │        │                       │
│ in flag check:  │          │                      │        │                       │
│ if (flags       │          │                      │        │                       │
│  .isEnabled(    │          │                      │        │                       │
│  "reels")) {…}  │          │                      │        │                       │
│       │        │           │                      │        │                       │
│       ▼        │           │                      │        │                       │
│ Bump versionCode│          │                      │        │                       │
│ Build AAB       │          │                      │        │                       │
│       │        │           │                      │        │                       │
│       ▼        │           │                      │        │                       │
│ Upload to       │──────────▶ Review (hours,       │        │                       │
│ Play Console    │          │ not days)             │        │                       │
│                 │          │       │               │        │                       │
│                 │          │       ▼               │        │                       │
│                 │          │ Set update priority   │        │                       │
│                 │          │ 0–5 (inAppUpdate       │        │                       │
│                 │          │ Priority) for this     │        │                       │
│                 │          │ release:               │        │                       │
│                 │          │  0–3 → routine          │        │                       │
│                 │          │        (flexible)       │        │                       │
│                 │          │  4–5 → urgent            │        │                       │
│                 │          │        (immediate)      │        │                       │
│                 │          │       │               │        │                       │
│                 │          │       ▼               │        │                       │
│                 │          │ Roll out (staged %     │        │                       │
│                 │          │ or 100%)               │        │                       │
│                 │          │       │               │        │                       │
│                 │          │       ▼               │        │                       │
│                 │          │ Published ─────────────┼───────▶ App resumes            │
│                 │          │                        │        │ (onResume)             │
│                 │          │                        │        │       │               │
│                 │          │                        │        │       ▼               │
│                 │          │                        │        │ InAppUpdateManager     │
│                 │          │                        │        │ .checkForUpdate()      │
│                 │          │                        │        │       │               │
│                 │          │                        │        │       ▼               │
│                 │          │                        │        │ updateAvailability     │
│                 │          │                        │        │ == UPDATE_AVAILABLE?   │
│                 │          │                        │        │   │           │        │
│                 │          │                        │        │  no          yes       │
│                 │          │                        │        │   │           │        │
│                 │          │                        │        │  done   read priority   │
│                 │          │                        │        │          set on release  │
│                 │          │                        │        │           │      │      │
│                 │          │                        │        │      routine   urgent    │
│                 │          │                        │        │           │      │      │
│                 │          │                        │        │           ▼      ▼      │
│                 │          │                        │        │      FLEXIBLE  IMMEDIATE │
│                 │          │                        │        │      flow      flow      │
│                 │          │                        │        │           │      │      │
│                 │          │                        │        │           ▼      ▼      │
│                 │          │                        │        │  Silent background   Full-screen│
│                 │          │                        │        │  download            blocking   │
│                 │          │                        │        │           │          update,    │
│                 │          │                        │        │           ▼          user must  │
│                 │          │                        │        │  InstallStateUpdated  wait       │
│                 │          │                        │        │  Listener fires            │      │
│                 │          │                        │        │  DOWNLOADED                │      │
│                 │          │                        │        │      │         │           │      │
│                 │          │                        │        │  app in     app in          │      │
│                 │          │                        │        │  background foreground      │      │
│                 │          │                        │        │      │         │            ▼      │
│                 │          │                        │        │      ▼         ▼        Play Store │
│                 │          │                        │        │ completeUpdate() Snackbar  installs│
│                 │          │                        │        │  silently      "Restart"   + relaunches│
│                 │          │                        │        │      │         │  │              │
│                 │          │                        │        │      ▼         ▼  (tap)           │
│                 │          │                        │        │  App reopens  completeUpdate()     │
│                 │          │                        │        │  updated,     → process restart,   │
│                 │          │                        │        │  no dialog    app reopens updated   │
└───────────────┘            └─────────────────────┘        └───────────────────────┘
```

## Priority guide (set per-release in Play Console → in-app update priority, 0–5)

| Priority | When to use | Update type Android code requests |
|---|---|---|
| 0–2 | Routine feature work, minor fixes, flag-gated dark features | FLEXIBLE |
| 3 | Notable fix, not urgent | FLEXIBLE |
| 4 | Important fix (data bug, broken flow for a subset of users) | IMMEDIATE |
| 5 | Critical (security, app-breaking, backend contract change) | IMMEDIATE |

Priority is set in Play Console per release; `AppUpdateInfo.updatePriority()` is read on-device to decide which flow to request. Not automatic — the app's code branches on this number.

## Feature flag flow

```
Supabase table: feature_flags
┌────────────────────┬─────────┐
│ key                 │ enabled │
├────────────────────┼─────────┤
│ reels               │ false   │  ← shipped dark, code is live in APK, flag off
│ voice_add_to_list   │ true    │  ← rolled out
└────────────────────┴─────────┘
        │
        │ SELECT * (anon, read-only via RLS)
        ▼
FeatureFlagRepository.fetchFlags()
        │  (once, at BabeGetThisApp process start)
        ▼
FeatureFlagCache (in-memory singleton, lives for process lifetime)
        │
        ▼
composables / viewmodels: cache.isEnabled("reels") → show or hide
```

Flipping a flag in the Supabase dashboard takes effect for a user the **next time they cold-start the app** (no realtime, no push). Acceptable per non-realtime requirement — avoids adding a Realtime subscription or polling loop for something that doesn't need to be instant.

## What is required (implementation checklist)

**In-app update:**
1. Add `app-update-ktx` dependency.
2. `core/update/InAppUpdateManager.kt` — Hilt singleton, `checkForUpdate(activity)`, `InstallStateUpdatedListener`.
3. Hook into `ProcessLifecycleOwner` to know foreground/background at `DOWNLOADED` time.
4. Call `checkForUpdate()` from `MainActivity.onResume()`.
5. Snackbar UI only for the foreground case.
6. Set `inAppUpdatePriority` per release in Play Console (manual step, not code).

**Feature flags:**
1. Create `feature_flags` table + RLS policy in Supabase.
2. `core/featureflags/data/FeatureFlagRepository.kt` (Postgrest read).
3. `core/featureflags/FeatureFlagCache.kt` (in-memory singleton).
4. `core/featureflags/di/FeatureFlagModule.kt` (Hilt).
5. Fetch once in `BabeGetThisApp` at startup.

## Non-goals (v1)

- Staged rollout percentages / per-user targeting for feature flags — flat on/off only.
- Realtime flag updates — next cold start is enough.
- Forced immediate-update logic beyond reading Play Console's priority value — no custom staleness-day threshold logic yet (`clientVersionStalenessDays`), add if we see users stuck on ancient versions despite priority signaling.
- Admin UI for flags — edit the Supabase table directly via dashboard for now.

## When to revisit

- If we need staged rollout or per-user targeting for flags → evaluate Firebase Remote Config or a `rollout_percent` column with a deterministic user-hash bucketing function, at that point — not now.
- If in-app update flexible flow.completion rate is poor → consider `clientVersionStalenessDays` to auto-escalate a routine update to immediate after N days.
