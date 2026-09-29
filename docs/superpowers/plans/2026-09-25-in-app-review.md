# In-App Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ask for a Play Store rating when a user completes a list, add a "Rate the app" row to Settings, and remove the inaccurate privacy note from Settings.

**Architecture:** Two top-level functions in a new `core/review/InAppReview.kt`: `requestInAppReview(activity)` wraps the Play In-App Review API, and `openPlayListing(context)` opens the store listing. `ShoppingItemsScreen` calls the first from its existing `ListJustCompleted` event branch; `SettingsScreen` calls the second from a new "About" row. No class, no DI, no stored state: Play's quota decides whether the sheet shows.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Play In-App Review (`com.google.android.play:review-ktx:2.0.2`), coroutines.

**Spec:** `docs/superpowers/specs/2026-09-25-in-app-review-design.md`

## Global Constraints

- Work in the worktree `.claude/worktrees/in-app-review` on branch `feat/in-app-review`, branched from `development`. The main checkout holds ~147 uncommitted files from the build-hardening change (Spotless, lint gate, R8, reformatting), including every file this plan touches. Never commit from the main checkout and never copy those changes into the worktree.
- Code snippets below match `development` HEAD, not the main checkout's working tree.
- Review gate (user rule): after each task, stop, summarise what to look at, and wait. Commit only after the user approves that task.
- Commits carry no Claude co-author trailer (user rule).
- All user-visible copy goes in `app/src/main/res/values/strings.xml`. No em dashes in UI text (user rule).
- Store listing package id is the prod one, hardcoded: `com.babegetthis.android`. Dev and staging add `.dev` / `.staging` suffixes that have no listing.
- No telemetry event, no local counters or throttling, no "Enjoying the app?" pre-prompt (Play policy forbids it).
- `core.review` is outside the Kover include list and the two screens are composables, so no coverage gate applies. No new automated tests (spec section 5).
- Commands: `./gradlew assembleDevDebug`, `./gradlew installDevDebug`, `./gradlew testDevDebugUnitTest`, `./gradlew lintProdDebug`, `./gradlew assembleProdRelease`, `./gradlew connectedDevDebugAndroidTest` (needs an emulator).

## Setup (no review gate)

- [ ] **Create the worktree**

```bash
cd /Users/aabashrai/Documents/android_projects/Babe-get-this
git worktree add .claude/worktrees/in-app-review -b feat/in-app-review development
cp local.properties .claude/worktrees/in-app-review/local.properties
cd .claude/worktrees/in-app-review
```

`local.properties` is gitignored and holds `sdk.dir`, Supabase keys and release signing, so the worktree cannot build without it.

- [ ] **Baseline: confirm the branch builds and tests pass before any change**

Run: `./gradlew assembleDevDebug testDevDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. If it fails, stop and report; do not start Task 1 on a red baseline.

---

### Task 1: Remove the inaccurate privacy note from Settings

Not part of the spec: a separate user request made during planning. The user flagged the note under the Privacy switches ("Your lists, items and recordings never leave your device...") as inaccurate and asked for it to be removed, not reworded.

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsScreen.kt` (the `Text` using `R.string.settings_privacy_note`, ~line 167)
- Modify: `app/src/main/res/values/strings.xml:104`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing. Task 3 inserts its section at the spot this `Text` used to occupy (directly after the crash-reports `SettingsRow`, before the `// Debug-only:` comment).

- [ ] **Step 1: Delete the note from `SettingsScreen.kt`**

Replace:

```kotlin
                trailing = {
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { viewModel.setCrashReportingEnabled(it) },
                    )
                },
            )

            Text(
                text = stringResource(R.string.settings_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            // Debug-only: the one reliable way to confirm crash reporting still
```

with:

```kotlin
                trailing = {
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { viewModel.setCrashReportingEnabled(it) },
                    )
                },
            )

            // Debug-only: the one reliable way to confirm crash reporting still
```

- [ ] **Step 2: Delete the now-unused string**

Remove this line from `app/src/main/res/values/strings.xml`:

```xml
    <string name="settings_privacy_note">Your lists, items and recordings never leave your device for either of these. We collect counts and outcomes only.</string>
```

- [ ] **Step 3: Confirm nothing else references it**

Run: `grep -rn "settings_privacy_note" app/src`
Expected: no output.

- [ ] **Step 4: Build and lint**

Run: `./gradlew assembleDevDebug lintProdDebug`
Expected: `BUILD SUCCESSFUL`, no new lint errors.

- [ ] **Step 5: Look at it (emulator)**

Run: `./gradlew installDevDebug`, open the app, tap the Settings icon on the lists screen.
Expected: the Privacy section ends with the "Crash reports" row; no note text under it.

- [ ] **Step 6: Stop for review.** Summarise the diff (one `Text` block and one string removed) and wait for approval.

- [ ] **Step 7: Commit (after approval)**

```bash
git add app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "fix: remove inaccurate privacy note from Settings"
```

---

### Task 2: Request a Play review when a list is completed

**Files:**
- Modify: `gradle/libs.versions.toml:45` (versions) and `:108-109` (libraries)
- Modify: `app/build.gradle.kts:348-349`
- Create: `app/src/main/java/com/babegetthis/android/core/review/InAppReview.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/ui/ShoppingItemsScreen.kt` (imports, ~line 133, ~lines 160-167)

**Interfaces:**
- Consumes: `ShoppingItemsViewModel.UiEvent.ListJustCompleted` (existing; emitted only on the "not all done" to "all done" transition), and `val activity = LocalContext.current as? android.app.Activity` (existing, `ShoppingItemsScreen.kt:105`).
- Produces: `suspend fun requestInAppReview(activity: Activity)` in package `com.babegetthis.android.core.review`. Task 3 adds a second function to the same file.

- [ ] **Step 1: Add the dependency to the version catalog**

In `gradle/libs.versions.toml`, replace:

```toml
playUpdate = "2.1.0"
```

with:

```toml
playUpdate = "2.1.0"
playReview = "2.0.2"
```

and replace:

```toml
# Play Core: in-app update (flexible/immediate flow), no other Play Core modules needed.
play-app-update-ktx = { group = "com.google.android.play", name = "app-update-ktx", version.ref = "playUpdate" }
```

with:

```toml
# Play Core: in-app update (flexible/immediate flow) and in-app review.
play-app-update-ktx = { group = "com.google.android.play", name = "app-update-ktx", version.ref = "playUpdate" }
play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "playReview" }
```

- [ ] **Step 2: Add it to the app module**

In `app/build.gradle.kts`, replace:

```kotlin
    // Play Core in-app update (flexible/immediate flow).
    implementation(libs.play.app.update.ktx)
```

with:

```kotlin
    // Play Core in-app update (flexible/immediate flow).
    implementation(libs.play.app.update.ktx)
    // Play Core in-app review, requested when a list is completed.
    implementation(libs.play.review.ktx)
```

- [ ] **Step 3: Confirm it resolves**

Run: `./gradlew assembleDevDebug`
Expected: `BUILD SUCCESSFUL` (nothing uses it yet; this proves the coordinate and version resolve).

- [ ] **Step 4: Create `core/review/InAppReview.kt`**

```kotlin
package com.babegetthis.android.core.review

import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

// Asks Play to show its rating sheet. Play decides whether it actually appears
// (a per-user quota it does not disclose) and reports nothing back, so this is
// safe to call on every completed list without counting anything ourselves.
// Installs that did not come from Play (dev, staging, tests) get a silent
// no-op. See docs/superpowers/specs/2026-09-25-in-app-review-design.md.
suspend fun requestInAppReview(activity: Activity) {
    try {
        val manager = ReviewManagerFactory.create(activity)
        manager.launchReview(activity, manager.requestReview())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A failed request has no user-visible effect and nothing to retry.
    }
}
```

- [ ] **Step 5: Wire it into `ShoppingItemsScreen.kt` imports**

Add, keeping alphabetical order within each group:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
```

directly after `import androidx.compose.runtime.remember`;

```kotlin
import com.babegetthis.android.core.review.requestInAppReview
```

directly after `import com.babegetthis.android.core.pin.ui.PinSetupDialog`; and

```kotlin
import kotlinx.coroutines.launch
```

as the last import.

- [ ] **Step 6: Add a coroutine scope**

Replace:

```kotlin
    val haptic = rememberHaptic()
    val context = LocalContext.current
```

with:

```kotlin
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
```

- [ ] **Step 7: Request the review on `ListJustCompleted`**

Replace:

```kotlin
    // Fire a Success haptic the moment the list goes from
    // "some unchecked" → "all checked off". The ViewModel filters out the
    // initial load of an already-complete list, so this only buzzes on
    // the actual transition.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingItemsViewModel.UiEvent.ListJustCompleted -> haptic(Haptic.Success)
```

with:

```kotlin
    // Fire a Success haptic the moment the list goes from
    // "some unchecked" → "all checked off", then ask Play for a review. The
    // ViewModel filters out the initial load of an already-complete list, so
    // this only fires on the actual transition.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ShoppingItemsViewModel.UiEvent.ListJustCompleted -> {
                    haptic(Haptic.Success)
                    // Launched, not awaited: the Play round trip must not hold
                    // up this collector, which also delivers ShareList.
                    activity?.let { scope.launch { requestInAppReview(it) } }
                }
```

- [ ] **Step 8: Build, test, lint**

Run: `./gradlew assembleDevDebug testDevDebugUnitTest lintProdDebug`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; no new lint errors.

- [ ] **Step 9: Smoke test on the emulator**

The dev build is not installed from Play, so no sheet appears. This step proves the call is harmless off-Play.

```bash
./gradlew installDevDebug
adb logcat -c
```

In the app: create a list with one item, open it, check the item off.
Expected: the success haptic fires as before; the app does not crash.

Run: `adb logcat -d | grep -E "FATAL EXCEPTION|com.babegetthis"`
Expected: no `FATAL EXCEPTION`.

- [ ] **Step 10: Stop for review.** Summarise: new dependency, new `InAppReview.kt`, one branch changed in `ShoppingItemsScreen`. Note that the sheet itself can only be seen on a Play-installed build (Task 4). Wait for approval.

- [ ] **Step 11: Commit (after approval)**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/babegetthis/android/core/review/InAppReview.kt app/src/main/java/com/babegetthis/android/feature/shoppingitems/ui/ShoppingItemsScreen.kt
git commit -m "feat: request Play in-app review when a list is completed" -m "Calls the Play In-App Review API on every ListJustCompleted event. Play's own quota decides whether the sheet shows, so nothing is counted or stored locally."
```

---

### Task 3: "Rate the app" row in Settings

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/review/InAppReview.kt` (created in Task 2)
- Modify: `app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsScreen.kt` (imports; new section after the crash-reports row)
- Modify: `app/src/main/res/values/strings.xml` (after `settings_crash_subtitle`)

**Interfaces:**
- Consumes: `SettingsRow(title, onClick, modifier, icon, subtitle, tint, trailing)` from `com.babegetthis.android.core.ui.components` (existing); `val context = LocalContext.current` (existing in `SettingsScreen`).
- Produces: `fun openPlayListing(context: Context)` in package `com.babegetthis.android.core.review`.

- [ ] **Step 1: Add `openPlayListing` to `InAppReview.kt`**

Replace the import block:

```kotlin
import android.app.Activity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException
```

with:

```kotlin
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.CancellationException

// The prod package, not BuildConfig.APPLICATION_ID: dev and staging add
// .dev / .staging suffixes, which have no store listing.
private const val PLAY_PACKAGE = "com.babegetthis.android"
```

and append at the end of the file:

```kotlin

// Opens the store listing directly. Backs the Settings row, because the review
// API cannot be driven from a button: Play may silently show nothing.
fun openPlayListing(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$PLAY_PACKAGE".toUri()))
    } catch (e: ActivityNotFoundException) {
        // No Play Store on this device; the browser can still show the listing.
        context.startActivity(
            Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE".toUri()),
        )
    }
}
```

- [ ] **Step 2: Add the strings**

In `app/src/main/res/values/strings.xml`, directly after:

```xml
    <string name="settings_crash_subtitle">Sends diagnostics when the app fails, so it can be fixed.</string>
```

add:

```xml
    <string name="settings_about_section">About</string>
    <string name="settings_rate_title">Rate the app</string>
    <string name="settings_rate_subtitle">Leave a review on Google Play</string>
```

- [ ] **Step 3: Add imports to `SettingsScreen.kt`**

Add:

```kotlin
import androidx.compose.material.icons.outlined.StarRate
```

directly after `import androidx.compose.material.icons.outlined.Refresh`, and:

```kotlin
import com.babegetthis.android.core.review.openPlayListing
```

directly after `import com.babegetthis.android.core.pin.ui.RemovePinDialog`.

- [ ] **Step 4: Add the About section**

After Task 1, the crash-reports row is followed directly by the debug comment. Replace:

```kotlin
                trailing = {
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { viewModel.setCrashReportingEnabled(it) },
                    )
                },
            )

            // Debug-only: the one reliable way to confirm crash reporting still
```

with:

```kotlin
                trailing = {
                    Switch(
                        checked = crashReportingEnabled,
                        onCheckedChange = { viewModel.setCrashReportingEnabled(it) },
                    )
                },
            )

            Text(
                text = stringResource(R.string.settings_about_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )

            SettingsRow(
                icon = Icons.Outlined.StarRate,
                title = stringResource(R.string.settings_rate_title),
                subtitle = stringResource(R.string.settings_rate_subtitle),
                onClick = { openPlayListing(context) },
            )

            // Debug-only: the one reliable way to confirm crash reporting still
```

The header `Text` copies the styling of the existing "List lock" and "Privacy" headers in the same file.

- [ ] **Step 5: Build, test, lint**

Run: `./gradlew assembleDevDebug testDevDebugUnitTest lintProdDebug`
Expected: `BUILD SUCCESSFUL`; all unit tests pass; no new lint errors.

- [ ] **Step 6: Check it on the emulator**

```bash
./gradlew installDevDebug
adb logcat -c
```

In the app: Settings, scroll to "About", tap "Rate the app".
Expected: the Play Store opens on the Babe, Get This listing (Google Play system image), or the browser opens `play.google.com/store/apps/details?id=com.babegetthis.android` (image without Play).

Run: `adb logcat -d | grep -E "START u0.*(market://|play.google.com)"`
Expected: one `START` line with `act=android.intent.action.VIEW`.

- [ ] **Step 7: Stop for review.** Summarise: new `openPlayListing`, three strings, one section in Settings. Wait for approval.

- [ ] **Step 8: Commit (after approval)**

```bash
git add app/src/main/java/com/babegetthis/android/core/review/InAppReview.kt app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add Rate the app row to Settings" -m "Opens the Play listing directly, falling back to the web listing when no Play Store is installed. Lives in Settings rather than the Profile sheet so guests can reach it."
```

---

### Task 4: Final verification

No code. Proves the branch as a whole before it is merged.

- [ ] **Step 1: Full local check**

Run: `./gradlew testDevDebugUnitTest lintProdDebug assembleProdRelease`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Instrumented suite (emulator required)**

Run: `./gradlew connectedDevDebugAndroidTest`
Expected: all pass. No journey completes a whole list, so the review call is not exercised here; this confirms nothing else moved.

- [ ] **Step 3: Play-installed check (user)**

The review sheet only appears for an app installed from Play. The user uploads a prod build to internal app sharing or the internal test track, installs it from the Play link, completes a list, and confirms the rating sheet appears. If it does not appear, that can be Play's quota or an account that has already reviewed the app rather than a bug; retry with a different test account before debugging.

- [ ] **Step 4: Stop.** Report results of steps 1 to 3 and hand off to superpowers:finishing-a-development-branch.
