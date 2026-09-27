# In-app review — design

**Date:** 2026-09-25
**Status:** Approved; implemented on `feat/in-app-review`

## Why this exists

The app has no way to ask for a Play Store rating. Ratings drive store ranking
and conversion, and the users most likely to leave a good one are the ones who
just finished a shopping trip with the app. This spec adds the Play In-App
Review API at that moment, plus a permanent "Rate the app" row for users who
go looking.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Mechanism | Play In-App Review API (`review-ktx`) | Native sheet, rating without leaving the app. Same Play Core family as the in-app update flow already shipped. |
| Trigger | Every `ListJustCompleted` event, from the first completed list | A finished trip is the app's clearest win. The event already fires only on the "not all done" to "all done" transition. |
| Frequency control | None of our own; Play's quota decides | Google advises against guessing the quota. No counters means no stored state. |
| Pre-prompt ("Enjoying the app?") | None | Play policy forbids asking questions before or while showing the review sheet, and forbids sentiment gating. |
| Manual entry point | "Rate the app" row in Settings, opening the Play listing | The API cannot be driven from a button (it may silently show nothing). Settings works signed in or not; the Profile sheet is signed-in only, so guests would never see it there. |
| Wrapper class | None; two top-level functions | Mirroring `InAppUpdateManager` would add a singleton and DI for no gain: off-Play builds (dev, staging, tests) get a silent no-op from Play, so there is nothing to swap out. |

## Non-goals

- Prompting on other moments (voice items saved, shared list joined).
- A telemetry event for the request. The API does not report whether the
  sheet was shown or a rating left, so the event would answer nothing
  (see `docs/technical-decisions/007-telemetry.md`).
- Tracking or throttling requests locally.

## 1. Dependency

- `gradle/libs.versions.toml`: add a `playReview` version (latest stable at
  implementation time) and
  `play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "playReview" }`.
  Update the comment above `play-app-update-ktx`, which currently says no other
  Play Core modules are needed.
- `app/build.gradle.kts`: `implementation(libs.play.review.ktx)` next to
  `libs.play.app.update.ktx`.
- No ProGuard changes expected, but unproven: the review libraries ship no
  keep rules of their own (`review-ktx` has only `-dontwarn module-info`),
  and minification is off on `development` today. When R8 lands with the
  build-hardening change, re-check the review sheet on a minified,
  Play-installed build.

## 2. `core/review/InAppReview.kt` (new)

Two top-level functions, no class:

- `suspend fun requestInAppReview(activity: Activity)`: creates a
  `ReviewManager` with `ReviewManagerFactory.create(activity)`, calls
  `requestReview()` then `launchReview(activity, info)`. Any exception is
  swallowed (a failed request has no user-visible consequence and nothing to
  retry), except `CancellationException`, which is rethrown.
- `fun openPlayListing(context: Context)`: starts `ACTION_VIEW` on
  `market://details?id=com.babegetthis.android`, pinned to the Play Store
  app (`com.android.vending`) because other stores also answer `market://`;
  on `ActivityNotFoundException` (no Play Store installed) falls back to
  `https://play.google.com/store/apps/details?id=com.babegetthis.android`.
  If no browser can open that either, it does nothing.
  The package id is the prod one, hardcoded: dev and staging add
  `.dev` / `.staging` suffixes that have no store listing.

## 3. Automatic request — `ShoppingItemsScreen`

The `ListJustCompleted` branch of the events collector keeps its `haptic(Haptic.Success)` and then
launches `requestInAppReview(activity)` on a `rememberCoroutineScope()`. It is
launched rather than awaited so the network round trip does not block the
collector, which also handles `ShareList`. `activity` is the screen's existing
`LocalContext.current as? Activity`; if it is null, nothing
happens.

Accepted behaviour:

- On a shared list, `ListJustCompleted` also fires when a partner checks off
  the last item while this user has the list open. That is still a finished
  trip on screen, and Play's quota prevents repeat sheets.
- Leaving the screen cancels a request still in flight. The next completed
  list tries again.
- Deleting the last unchecked item also completes the list, so the sheet can
  appear while the item's Undo snackbar is showing and cover it. The haptic
  and analytics already fire on this path; Play's quota makes the sheet rare.

## 4. Manual row — `SettingsScreen`

A new "About" section, placed after the privacy section and before the debug
section and version text, containing one `SettingsRow`:

- icon: `Icons.Outlined.StarRate`
- title: `settings_rate_title` = "Rate the app"
- subtitle: `settings_rate_subtitle` = "Leave a review on Google Play"
- onClick: `openPlayListing(context)`

Section header string `settings_about_section` = "About". All copy in
`strings.xml`, no em dashes.

## 5. Testing and verification

No new automated tests. The code is thin wiring onto a Play API that no-ops
off-Play, and no instrumented journey completes a whole list, so the review
call never runs under test.

- `./gradlew lintProdDebug` passes.
- `./gradlew assembleProdRelease` builds. (Spotless, the warnings-as-errors
  lint gate and R8 are not on `development` yet; they arrive with the
  uncommitted build-hardening change. See section 1 for what to re-check
  when R8 lands.)
- Existing unit and instrumented suites still pass.
- Emulator: the Settings row opens the Play listing (Play image) or the
  browser fallback (non-Play image).
- Review sheet: only verifiable on a Play-installed build. The user uploads a
  build to internal app sharing or the internal test track, completes a list,
  and confirms the sheet appears.
