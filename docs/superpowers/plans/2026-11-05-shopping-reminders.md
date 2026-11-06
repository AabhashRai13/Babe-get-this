# Shopping Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One on-device nudge notification when a shopping list is left half done: some items ticked on this phone, others not, and three quiet hours on that list.

**Architecture:** A new `core/reminder` package mirrors `core/sync`. The repository calls a `ReminderKicker` seam after local writes; a singleton `ReminderScheduler` owns one `Mutex`, a durable `PendingReminder` record in SharedPreferences, and one unique WorkManager job carrying a generation number. The `ReminderWorker` re-validates against Room at fire time and hands the post back to the scheduler so the final ownership check, the post and the clear are atomic.

**Tech Stack:** Kotlin, Hilt (ksp), WorkManager 2.10 with `hilt-work`, Room, SharedPreferences, Jetpack Compose + Material 3, Robolectric, `work-testing`, MockK, Kover.

**Spec:** `docs/superpowers/specs/2026-11-01-shopping-reminders-design.md`

## Global Constraints

- Delay from last local activity to fire: **3 hours**. Age ceiling at fire time: **0 ≤ age ≤ 24 hours**. Prompt snooze: **14 days**.
- Unique work name `shopping_reminder`, policy `REPLACE`. Input data keys `list_id`, `generation`.
- Feature flag key `shopping_reminders`, read at arm time in the UI process only, never in the worker.
- Notification channel id `reminders`, notification id and PendingIntent request code `1001`, PendingIntent flags `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, activity flags `NEW_TASK | SINGLE_TOP | CLEAR_TOP`. Intent extras `reminder_list_id`, `reminder_list_name`.
- Only `ShoppingItemRepository` and `ShoppingListRepository.addItemsToList` call the kicker. `SyncEngine` is not touched. List-level edits call nothing.
- All user-visible copy in `app/src/main/res/values/strings.xml`. No item names in a notification when the list row is locked.
- Prefs record writes use `commit()` on `Dispatchers.IO` inside the scheduler's lock, never `apply()`. WorkManager calls are awaited inside the lock.
- Every new class under `com.babegetthis.android.core.reminder` is Kover-gated at 100% except the listed Android passthroughs and the `ui` sub-package.
- Run `./gradlew spotlessApply` before every commit; CI runs `spotlessCheck`, `lintProdDebug`, `testProdDebugUnitTest`, `koverVerifyDevDebug`.
- Assumes `targetSdk >= 33` (it is 36) and `minSdk 24`.

## Review Focus

Spec-implied inputs no task's tests exercised at first draft; each now has its test in the owning task.

1. **A list name containing `/` or `?`** reaches `Routes.shoppingItems(id, name)` from a notification tap. Today's in-app path has the same exposure, but a tap is a new entry point. Expected: the route must not break. Test in Task 13 (`ReminderIntentParserTest`: name is URL-encoded by the parser; nav test decodes).
2. **An item name over the notification width** (hundreds of characters). Expected: the body truncates by the system, we cap each name at 40 characters with an ellipsis so two names and the count fit. Test in Task 6 (`ReminderNotifierTest`).
3. **Tick on a list whose row is tombstoned** but whose items are still live (sync race). Expected: not eligible, nothing armed. Test in Task 3 (`ReminderContentReaderTest` null for tombstoned) and Task 5 (`arm` with eligibility false).
4. **Permission granted while the sheet is up and the list was finished meanwhile.** Expected: nothing armed. Test in Task 5 (`onPermissionGranted` with eligibility false).
5. **Worker input missing or malformed** (`generation = -1`, null list id), e.g. a job enqueued by an older build. Expected: return success, post nothing, clear nothing. Test in Task 9.

---

## File Structure

New package `com.babegetthis.android.core.reminder` under `app/src/main/java/com/babegetthis/android/core/reminder/`:

| File | Responsibility |
|---|---|
| `ReminderKicker.kt` | The repository-facing seam: `onLocalPickUp`, `onLocalEdit`. |
| `ReminderScheduler.kt` | The lock, the gate, arm/reset/cancel, `owns`/`finish`, prompt decisions. |
| `ReminderPermission.kt` | `PermissionState` enum and the pure derivation function. |
| `WorkEnqueuer.kt` | Suspend interface over the two WorkManager calls; `WorkManagerEnqueuer` impl. |
| `Platform.kt` | `NotificationGate`, `ForegroundState`, `ReminderClock` interfaces and their Android impls. |
| `ReminderWorker.kt` | `@HiltWorker` with the seven fire-time checks. |
| `ReminderNotifier.kt` | Channel, copy, content intent, post. |
| `ReminderIntentParser.kt` | `ReminderTarget` and intent extras parsing. |
| `data/local/ReminderPrefs.kt` | `PendingReminder`, `ReminderPrefs` interface, `SharedPrefsReminderPrefs`. |
| `data/local/ReminderContentReader.kt` | `ReminderContent` snapshot in one Room transaction; `ReminderEligibility`. |
| `di/ReminderModule.kt` | Bindings. `ReminderPlatformModule` for the Android impls (replaced in e2e). |
| `ui/ReminderPermissionSheet.kt` | The in-context bottom sheet. |

Modified: `ShoppingItemRepository`, `ShoppingListRepository`, `ShoppingItemsViewModel`, `ShoppingItemsScreen`, `SettingsViewModel`, `SettingsScreen`, `BgtNavGraph`, `MainActivity`, `BabeGetThisApp`, `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `strings.xml`, `AnalyticsEvent.kt`, `AnalyticsEventMapper.kt`, `Buckets.kt`, `SyncTrigger.kt` (comment), `README.md`, `docs/publishing/privacy-policy.md`.

Test fakes in `app/src/test/java/com/babegetthis/android/testing/ReminderFakes.kt`: `FakeReminderPrefs`, `FakeWorkEnqueuer`, `FakeReminderKicker`, `FakeEligibility`, `FakeNotificationGate`, `FakeForegroundState`, `FakeClock`.

---

### Task 1: Build wiring

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (dependencies block ~line 340–420; `kover.reports.filters` ~line 207–260)
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `libs.androidx.work.runtime`, `libs.androidx.work.testing`, `libs.androidx.hilt.work`, `libs.androidx.hilt.compiler`, `libs.androidx.lifecycle.process` available to the app module; `POST_NOTIFICATIONS` declared; WorkManager auto-init removed.

- [ ] **Step 1: Version catalog**

In `[versions]`: rename `hiltNavigationCompose = "1.2.0"` to `androidxHilt = "1.2.0"`, add `work = "2.10.1"`. In `[libraries]`: point `androidx-hilt-navigation-compose` at `version.ref = "androidxHilt"`; add

```toml
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "androidxHilt" }
androidx-work-runtime = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

Add a comment above `work` in the style of the file: pinned to 2.10.x because 2.11+ requires compileSdk/Kotlin the project has not moved to. If the build fails with "Module was compiled with an incompatible version of Kotlin", drop to `2.9.1`.

- [ ] **Step 2: App dependencies**

Next to the Hilt lines: `implementation(libs.androidx.work.runtime)`, `implementation(libs.androidx.hilt.work)`, `ksp(libs.androidx.hilt.compiler)`, `implementation(libs.androidx.lifecycle.process)`, `testImplementation(libs.androidx.work.testing)`, `androidTestImplementation(libs.androidx.work.testing)`. Keep the existing comment style: one line on why each exists (reminders use WorkManager; sync deliberately does not).

- [ ] **Step 3: Kover**

In `includes.classes(...)` add `"com.babegetthis.android.core.reminder.*"`. In `excludes.classes(...)` add, with a one-line reason each: `"com.babegetthis.android.core.reminder.ui.*"` (composables), `"com.babegetthis.android.core.reminder.WorkManagerEnqueuer"`, `"com.babegetthis.android.core.reminder.AndroidNotificationGate"`, `"com.babegetthis.android.core.reminder.ProcessForegroundState"`, `"com.babegetthis.android.core.reminder.SystemReminderClock"` (platform passthroughs, no branches), `"com.babegetthis.android.core.reminder.di.*"`.

- [ ] **Step 4: Manifest**

Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` after `RECORD_AUDIO`. Inside `<application>`, add the provider block from the spec's "HiltWorkerFactory setup" step 4 verbatim (`tools:node="merge"` on the provider, `tools:node="remove"` on the `androidx.work.WorkManagerInitializer` meta-data), with a comment that only WorkManager's initializer is removed and `ProcessLifecycleInitializer` must stay.

- [ ] **Step 5: Verify the build**

Run: `./gradlew :app:compileDevDebugKotlin :app:compileDevDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "build(reminders): add WorkManager, hilt-work and the notification permission"
```

---

### Task 2: ReminderPrefs

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/data/local/ReminderPrefs.kt`
- Create: `app/src/test/java/com/babegetthis/android/testing/ReminderFakes.kt` (start it with `FakeReminderPrefs`)
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/data/local/SharedPrefsReminderPrefsTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class PendingReminder(val listId: String, val generation: Long, val armedAt: Long)

interface ReminderPrefs {
    var enabled: Boolean                 // default true; apply() is fine here
    val generationCounter: Long          // default 0; written only by commitPending
    fun readPending(): PendingReminder?
    fun commitPending(record: PendingReminder): Boolean   // one commit(): counter = record.generation + record fields
    fun commitClearPending(): Boolean                      // one commit(): removes the three record keys, keeps the counter
    var promptSnoozedUntil: Long         // default 0
    var permissionRequested: Boolean     // default false
}

class SharedPrefsReminderPrefs(context: Context) : ReminderPrefs   // file name "reminders"
```

- `FakeReminderPrefs : ReminderPrefs` with `var failCommits = false` (both commit functions return false and change nothing when set) and `val commitLog = mutableListOf<String>()` recording `"pending:<listId>:<gen>"` and `"clear"`.

- [ ] **Step 1: Write the failing tests** (Robolectric, `ApplicationProvider.getApplicationContext()`)

```kotlin
`defaults are enabled, counter 0, no pending, no snooze, not requested`
`commitPending stores the record and advances the counter`         // readPending() == record; generationCounter == 7
`commitClearPending removes the record but keeps the counter`       // after commit(gen 7) then clear: readPending()==null, counter==7
`a record with a missing field reads as null`                       // write only list id directly through SharedPreferences → readPending()==null
`enabled, snooze and permissionRequested round-trip`
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "*SharedPrefsReminderPrefsTest*"`
Expected: compilation failure (class not found).

- [ ] **Step 3: Implement `ReminderPrefs.kt`** with the interface above and `SharedPrefsReminderPrefs`. Keys: `enabled`, `generation_counter`, `pending_list_id`, `pending_generation`, `pending_armed_at`, `prompt_snoozed_until`, `permission_requested`. `readPending` returns null unless all three record keys are present.

- [ ] **Step 4: Write `FakeReminderPrefs`** in `ReminderFakes.kt`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "*SharedPrefsReminderPrefsTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/data/local/ReminderPrefs.kt app/src/test/java/com/babegetthis/android/testing/ReminderFakes.kt app/src/test/java/com/babegetthis/android/core/reminder/data/local/SharedPrefsReminderPrefsTest.kt
git commit -m "feat(reminders): durable pending-reminder record in prefs"
```

---

### Task 3: ReminderContentReader and ReminderEligibility

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/data/local/ReminderContentReader.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/data/local/ReminderContentReaderTest.kt`

**Interfaces:**
- Consumes: `AppDatabase.withTransaction` (room-ktx), `ShoppingListDao.getListRaw(listId)`, `ShoppingItemDao.getItemsByListIdOnce(listId)` (live items only, as it already is).
- Produces:

```kotlin
data class ReminderContent(
    val listId: String,
    val listName: String,
    val isLocked: Boolean,
    val pickedCount: Int,
    val uncheckedNames: List<String>,   // list order as the DAO returns it
) {
    val uncheckedCount: Int get() = uncheckedNames.size
    val isPartlyShopped: Boolean get() = pickedCount >= 1 && uncheckedCount >= 1
}

class ReminderContentReader(private val db: AppDatabase) {
    suspend fun snapshot(listId: String): ReminderContent?   // null when the list row is missing or deletedAt != null
}

class ReminderEligibility(private val reader: ReminderContentReader) {
    suspend fun isPartlyShopped(listId: String): Boolean = reader.snapshot(listId)?.isPartlyShopped == true
}
```

- [ ] **Step 1: Write the failing tests** (`InMemoryDatabaseRule`, `TestData.listEntity` / `TestData.itemEntity`)

```kotlin
`snapshot is null for a missing list`
`snapshot is null for a tombstoned list even with live items`        // deletedAt = 5L on the row
`snapshot excludes tombstoned items from counts and names`
`snapshot names come in dao order and count the picked ones`         // 2 picked, names of the 3 unpicked
`isLocked mirrors the row at snapshot time`                          // setLocked(true) between two snapshots → false then true
`eligibility is true only with at least one picked and one unpicked` // 4 cases: 0/0, 1/0, 0/1, 1/1
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "*ReminderContentReaderTest*"`
Expected: compilation failure.

- [ ] **Step 3: Implement `ReminderContentReader.kt`.** `snapshot` wraps both DAO reads in `db.withTransaction { }`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "*ReminderContentReaderTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/data/local/ReminderContentReader.kt app/src/test/java/com/babegetthis/android/core/reminder/data/local/ReminderContentReaderTest.kt
git commit -m "feat(reminders): one-transaction content snapshot and eligibility"
```

---

### Task 4: PermissionState

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderPermission.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/ReminderPermissionTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class PermissionState { NotRequired, Granted, Requestable, PermanentlyRefused }

fun permissionState(sdkInt: Int, granted: Boolean, requested: Boolean, showRationale: Boolean): PermissionState
```

Rules, in order: `sdkInt < 33` → `NotRequired`; `granted` → `Granted`; `requested && !showRationale` → `PermanentlyRefused`; else `Requestable`.

- [ ] **Step 1: Write the failing tests**

```kotlin
`below api 33 is NotRequired regardless of the other inputs`
`granted wins on api 33`
`the four requested-rationale combinations`   // (f,f)→Requestable, (f,t)→Requestable, (t,f)→PermanentlyRefused, (t,t)→Requestable
```

- [ ] **Step 2: Run to verify they fail.** `./gradlew testDevDebugUnitTest --tests "*ReminderPermissionTest*"` → compilation failure.

- [ ] **Step 3: Implement.** A `when` in the order above.

- [ ] **Step 4: Run the tests.** Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ReminderPermission.kt app/src/test/java/com/babegetthis/android/core/reminder/ReminderPermissionTest.kt
git commit -m "feat(reminders): derive notification permission state"
```

---

### Task 5: ReminderScheduler

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderKicker.kt`
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/WorkEnqueuer.kt` (interface only in this task)
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/Platform.kt` (interfaces only in this task)
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderScheduler.kt`
- Modify: `app/src/test/java/com/babegetthis/android/testing/ReminderFakes.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/ReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `ReminderPrefs`, `PendingReminder` (Task 2), `ReminderEligibility` (Task 3), `PermissionState` (Task 4), `FeatureFlagCache.isEnabled(key)`, `CrashReporter.recordNonFatal(throwable, AppError)`.
- Produces:

```kotlin
interface ReminderKicker {
    fun onLocalPickUp(listId: String)
    fun onLocalEdit(listId: String)
}

interface WorkEnqueuer {
    suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long)
    suspend fun cancelUnique()
}

fun interface NotificationGate { fun notificationsEnabled(): Boolean }
fun interface ForegroundState { fun isForeground(): Boolean }
fun interface ReminderClock { fun now(): Long }

class ReminderScheduler(
    private val enqueuer: WorkEnqueuer,
    private val prefs: ReminderPrefs,
    private val eligibility: ReminderEligibility,
    private val flags: FeatureFlagCache,
    private val gate: NotificationGate,
    private val crashReporter: CrashReporter,
    private val clock: ReminderClock,
    private val scope: CoroutineScope,           // Dispatchers.IO + SupervisorJob in production
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReminderKicker {
    override fun onLocalPickUp(listId: String)   // scope.launch { arm(listId) }
    override fun onLocalEdit(listId: String)     // scope.launch { reset(listId) }

    suspend fun arm(listId: String): Boolean     // gate + eligibility; new generation; commit; enqueue. False when anything blocks.
    suspend fun reset(listId: String)            // only when readPending()?.listId == listId: new generation, commit, enqueue
    suspend fun owns(listId: String, generation: Long): Boolean          // record != null && both match; no lock
    suspend fun finish(listId: String, generation: Long, post: (suspend () -> Unit)?): Boolean
        // under lock: if !owns → false; else run post (if non-null) in try/finally that commitClearPending(); true

    suspend fun shouldOfferPermission(listId: String, permission: PermissionState): Boolean
        // permission == Requestable && prefs.enabled && flags on && clock.now() >= prefs.promptSnoozedUntil && eligibility
    fun onPermissionRequested()                  // prefs.permissionRequested = true
    fun permissionRequested(): Boolean           // prefs.permissionRequested
    suspend fun onPermissionGranted(listId: String): Boolean   // arm(listId)
    fun onPermissionDenied()                     // snoozePrompt()
    fun snoozePrompt()                           // promptSnoozedUntil = now + 14 days

    suspend fun setEnabled(enabled: Boolean)     // prefs.enabled = enabled; if false → cancelAndClear()
    suspend fun onFlagsLoaded()                  // if flag off → cancelAndClear()
    fun observeSignOuts(signOuts: Flow<Unit>)    // scope.launch { signOuts.collect { cancelAndClear() } }

    companion object {
        const val FLAG_KEY = "shopping_reminders"
        const val REMINDER_DELAY_MILLIS = 3L * 60 * 60 * 1000
        const val PROMPT_SNOOZE_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}
```

Lock discipline: `arm`, `reset`, `finish`, `setEnabled`, `onFlagsLoaded`, `cancelAndClear` each take the single `Mutex`. Inside: prefs commits run `withContext(ioDispatcher)`; a false commit reports `AppError.DatabaseError("reminder prefs commit failed")` as a non-fatal and returns without enqueueing; the enqueue/cancel is awaited before the lock is released. The gate for `arm` is `prefs.enabled && flags.isEnabled(FLAG_KEY) && gate.notificationsEnabled() && eligibility.isPartlyShopped(listId)`. `reset` applies the same gate minus eligibility.

Fakes to add: `FakeWorkEnqueuer` (records `enqueued: List<Triple<String, Long, Long>>`, `cancels: Int`, an optional `CompletableDeferred<Unit>` gate the test can hold each enqueue on, and an `onEnqueue: (() -> Unit)?` hook so a test can assert prefs at enqueue time), `FakeEligibility(var partlyShopped: (String) -> Boolean)`, `FakeNotificationGate(var enabled = true)`, `FakeForegroundState(var foreground = false)`, `FakeClock(var now = 0L)`, `FakeReminderKicker` (lists of `pickUps` and `edits`).

- [ ] **Step 1: Write the failing tests** (`runTest`, `StandardTestDispatcher` as both `scope` and `ioDispatcher`, `FeatureFlagCache().apply { update(mapOf(FLAG_KEY to true)) }`, `CrashReporter` relaxed mock)

Arming and resetting:
```kotlin
`a tick on a partly shopped list arms with the list, 3h delay and generation 1`
`a second tick on the same list re-enqueues with generation 2`
`a tick on another partly shopped list retargets with generation 3`
`a tick that leaves its list not partly shopped does not arm and keeps the other list's record`   // D4
`an edit with nothing pending does not arm`
`an edit on the pending list re-enqueues with a new generation`
`an edit on a different list changes nothing`
```
Gate:
```kotlin
`enabled false blocks arming and cancels and clears`
`flag off blocks arming and cancels and clears`
`os notifications disabled blocks arming and cancels and clears`
`onFlagsLoaded with the flag off cancels and clears; with it on does nothing`
`observeSignOuts cancels and clears on each emission`
```
Durability and ordering:
```kotlin
`the record is committed before the enqueuer is called`           // onEnqueue hook asserts prefs.readPending()?.generation == 1
`a failed commit means no enqueue, a non-fatal and no record`      // failCommits = true; verify recordNonFatal called once
`a second tick waits for the first enqueue to complete`           // hold the deferred; launch tick 2; assert enqueued.size == 1; release; advanceUntilIdle; size == 2, record.generation == 2
`fifty interleaved ticks and edits leave one job whose generation matches the record`  // launch 50 coroutines; after idle: enqueued.last().second == prefs.generationCounter == readPending()!!.generation
```
Ownership and finish:
```kotlin
`owns is true only for the active record`                          // matching; wrong list; wrong generation; cleared record with counter unchanged → false
`finish with ownership runs the post and clears`
`finish without ownership neither posts nor clears`
`finish with a null post clears without posting`
`finish clears even when the post throws, and rethrows`
`a retarget launched while the post is suspended waits, then writes B`   // post suspends on a CompletableDeferred; launch arm(B); assert enqueued still 1 and prefs record is A; complete; idle; post ran once; record is B gen 2; enqueued 2
`a retarget that completes first makes finish for A a no-op`
```
Prompt:
```kotlin
`shouldOfferPermission is true only when Requestable, enabled, flag on, not snoozed and partly shopped`  // flip each input in turn
`the snooze boundary is inclusive at exactly 14 days`               // now == snoozedUntil → true; now == snoozedUntil - 1 → false
`onPermissionDenied snoozes for 14 days`
`onPermissionGranted arms only while still partly shopped`
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "*ReminderSchedulerTest*"`
Expected: compilation failure.

- [ ] **Step 3: Implement `ReminderKicker.kt`, the two interface files, and `ReminderScheduler.kt`** per the Interfaces block. Add the fakes.

- [ ] **Step 4: Run the tests**

Run: `./gradlew testDevDebugUnitTest --tests "*ReminderSchedulerTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ app/src/test/java/com/babegetthis/android/testing/ReminderFakes.kt app/src/test/java/com/babegetthis/android/core/reminder/ReminderSchedulerTest.kt
git commit -m "feat(reminders): locked scheduler with generation-owned pending record"
```

---

### Task 6: ReminderNotifier and copy

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderNotifier.kt`
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderIntentParser.kt` (constants and `ReminderTarget` only; parsing logic lands in Task 13)
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/ReminderNotifierTest.kt`

**Interfaces:**
- Consumes: `ReminderContent` (Task 3).
- Produces:

```kotlin
data class ReminderTarget(val listId: String, val listName: String)

object ReminderIntentParser {
    const val EXTRA_LIST_ID = "reminder_list_id"
    const val EXTRA_LIST_NAME = "reminder_list_name"
}

data class ReminderText(val title: String, val body: String)

class ReminderNotifier(private val context: Context, private val notifications: NotificationManagerCompat) {
    fun text(content: ReminderContent): ReminderText
    fun post(content: ReminderContent)      // creates channel idempotently, builds, notifies with NOTIFICATION_ID
    companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_CODE = 1001
        const val MAX_NAME_CHARS = 40
    }
}
```

Strings to add:

```xml
<!-- Shopping reminders -->
<string name="reminder_channel_name">Shopping reminders</string>
<string name="reminder_title">Forget something on %1$s?</string>
<string name="reminder_body_one">%1$s is still unticked</string>
<string name="reminder_body_two">%1$s and %2$s are still unticked</string>
<string name="reminder_body_more">%1$s, %2$s and %3$d more are still unticked</string>
<plurals name="reminder_body_locked">
    <item quantity="one">%d item is still unticked</item>
    <item quantity="other">%d items are still unticked</item>
</plurals>
<string name="reminder_prompt_title">Forget something?</string>
<string name="reminder_prompt_body">Get one nudge if you leave items unticked after a shop.</string>
<string name="reminder_prompt_accept">Remind me</string>
<string name="reminder_prompt_decline">Not now</string>
<string name="settings_reminders_section">Reminders</string>
<string name="settings_reminders_title">Shopping reminders</string>
<string name="settings_reminders_subtitle">A nudge when you leave items unticked after a shop</string>
<string name="settings_reminders_blocked_subtitle">Allow notifications in system settings to turn this on</string>
```

Copy rules in `text`: names longer than `MAX_NAME_CHARS` are cut to 39 characters plus `…`; locked → `reminder_body_locked` with `uncheckedCount`; else by `uncheckedCount` 1 / 2 / 3+. Content intent: `Intent(context, MainActivity::class.java)` with the two extras and `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`; `PendingIntent.getActivity(context, REQUEST_CODE, intent, FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)`. `setAutoCancel(true)`, `setSmallIcon(R.drawable.ic_notification)` — if no monochrome notification drawable exists, add a 24dp vector `ic_notification.xml` using the launcher's monochrome asset.

- [ ] **Step 1: Write the failing tests** (Robolectric; `shadowOf(context.getSystemService(NotificationManager::class.java))`)

```kotlin
`one unticked item names it`                       // "Milk is still unticked"
`two unticked items name both`                     // "Milk and Eggs are still unticked"
`three or more name two and count the rest`        // "Milk, Eggs and 2 more are still unticked"
`a locked list withholds names`                    // "3 items are still unticked"; "1 item is still unticked"
`a long item name is cut to 40 characters with an ellipsis`
`the title carries the list name`
`posting twice leaves one notification carrying the second list`   // activeNotifications.size == 1; extras title contains "B"; content intent extras list id == "B"
`the content intent has the required flags and extras`   // shadowOf(pendingIntent).flags has UPDATE_CURRENT and IMMUTABLE; savedIntent flags include the three; component == MainActivity
`the channel is created once across two posts`
```

- [ ] **Step 2: Run to verify they fail.** `./gradlew testDevDebugUnitTest --tests "*ReminderNotifierTest*"` → compilation failure.

- [ ] **Step 3: Add the strings, implement `ReminderNotifier.kt` and the constants file.**

- [ ] **Step 4: Run the tests.** Expected: PASS. Also run `./gradlew lintDevDebug` to confirm no unused-string or plurals findings.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/babegetthis/android/core/reminder/ReminderNotifier.kt app/src/main/java/com/babegetthis/android/core/reminder/ReminderIntentParser.kt app/src/main/res/drawable/ic_notification.xml app/src/test/java/com/babegetthis/android/core/reminder/ReminderNotifierTest.kt
git commit -m "feat(reminders): notification channel, copy and content intent"
```

---

### Task 7: Telemetry events

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/telemetry/model/AnalyticsEvent.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/telemetry/model/Buckets.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/telemetry/data/AnalyticsEventMapper.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/telemetry/model/BucketsTest.kt`, `app/src/test/java/com/babegetthis/android/core/telemetry/data/AnalyticsEventMapperTest.kt`

**Interfaces:**
- Produces:

```kotlin
// AnalyticsEvent, new section "-- Shopping reminders --"
data class ReminderPosted(val delayMillis: Long) : AnalyticsEvent   // elapsed from armedAt to post
data object ReminderTapped : AnalyticsEvent

// Buckets.kt
fun bucketReminderDelay(millis: Long): String   // "<3h", "3-4h", "4-6h", "6-12h", "12-24h", "24h+"
```

Mapper: `ReminderPosted` → `ev("reminder_posted", text = mapOf("delay_bucket" to bucketReminderDelay(event.delayMillis)))`; `ReminderTapped` → `ev("reminder_tapped")`.

- [ ] **Step 1: Write the failing tests.** In `BucketsTest`: `reminder delay buckets at each boundary` (2h59m → `<3h`, 3h → `3-4h`, 4h → `4-6h`, 6h → `6-12h`, 12h → `12-24h`, 24h → `24h+`, negative → `<3h`). In `AnalyticsEventMapperTest`: add both events to `everyEvent`; add `reminder_posted carries only the delay bucket` asserting name and `text == mapOf("delay_bucket" to "3-4h")` for `delayMillis = 3h + 1`.

- [ ] **Step 2: Run to verify they fail.** `./gradlew testDevDebugUnitTest --tests "*BucketsTest*" --tests "*AnalyticsEventMapperTest*"` → compilation failure (the mapper's exhaustive `when` fails first).

- [ ] **Step 3: Implement** the two events, the bucket function and the two mapper branches.

- [ ] **Step 4: Run the tests.** Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/telemetry/ app/src/test/java/com/babegetthis/android/core/telemetry/
git commit -m "feat(telemetry): reminder_posted with delay bucket, reminder_tapped"
```

---

### Task 8: ReminderWorker

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderWorker.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/ReminderWorkerTest.kt`

**Interfaces:**
- Consumes: `ReminderScheduler.owns/finish` (Task 5), `ReminderPrefs` (Task 2), `ReminderContentReader` (Task 3), `ReminderNotifier.post` (Task 6), `NotificationGate`, `ForegroundState`, `ReminderClock` (Task 5), `AnalyticsRepository.track`, `AnalyticsEvent.ReminderPosted` (Task 7), `CrashReporter`.
- Produces:

```kotlin
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
    private val prefs: ReminderPrefs,
    private val reader: ReminderContentReader,
    private val notifier: ReminderNotifier,
    private val gate: NotificationGate,
    private val foreground: ForegroundState,
    private val clock: ReminderClock,
    private val analytics: AnalyticsRepository,
    private val crashReporter: CrashReporter,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result   // always Result.success()
    companion object {
        const val KEY_LIST_ID = "list_id"
        const val KEY_GENERATION = "generation"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
```

`doWork` order (spec "Fire-time checks"): read inputs, bail if list id null or generation < 0 (no `finish` call); (1) `!scheduler.owns` → return; (2) `!prefs.enabled`, (3) `!gate.notificationsEnabled()`, (4) `foreground.isForeground()`, (5) age outside `[0, MAX_AGE_MILLIS]` where `age = clock.now() - readPending()!!.armedAt`, (6) `reader.snapshot(listId)` null or `!isPartlyShopped` → each calls `scheduler.finish(listId, gen, null)` then returns; (7) `scheduler.finish(listId, gen) { notifier.post(content); analytics.track(ReminderPosted(age)) }`. `catch (e: CancellationException) { throw e }`; other exceptions → `crashReporter.recordNonFatal(e, AppError.UnknownError("reminder worker"))`.

- [ ] **Step 1: Write the failing tests** (Robolectric; `InMemoryDatabaseRule`; real `ReminderScheduler` over fakes from Task 5; `TestListenableWorkerBuilder<ReminderWorker>(context).setInputData(workDataOf(KEY_LIST_ID to "L1", KEY_GENERATION to gen)).setWorkerFactory(factory)` where `factory` is a `WorkerFactory` whose `createWorker` builds `ReminderWorker` with the fakes; a `RecordingNotifier` subclass overriding `post` to record and optionally run a hook)

```kotlin
`posts when partly shopped and ownership holds, then the record is cleared`   // arm via scheduler first; delay bucket tracked
`missing list id or negative generation returns success and touches nothing`
`a cleared record with the counter still equal to the generation posts nothing`   // check 1
`enabled false posts nothing and clears`
`os notifications disabled posts nothing and clears`
`foreground posts nothing and clears`
`negative age posts nothing`                       // clock.now = armedAt - 1
`age of 24h plus one second posts nothing`
`list finished posts nothing and clears`           // all items picked
`a partner tick written through the dao still posts`   // D3: picked via itemDao.updatePickedUpStatus directly
`a retarget between snapshot and finish posts nothing and keeps B`  // reader hook: on snapshot, runBlocking { scheduler.arm("L2") } with L2 eligible
`a locked list withholds names`                    // recorded content.isLocked true → notifier text locked variant
`CancellationException propagates and is not reported`   // reader throws; assert exception, verify recordNonFatal not called
`other failures report a non-fatal and return success`
```

- [ ] **Step 2: Run to verify they fail.** `./gradlew testDevDebugUnitTest --tests "*ReminderWorkerTest*"` → compilation failure.

- [ ] **Step 3: Implement `ReminderWorker.kt`.**

- [ ] **Step 4: Run the tests.** Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ReminderWorker.kt app/src/test/java/com/babegetthis/android/core/reminder/ReminderWorkerTest.kt
git commit -m "feat(reminders): worker with ownership-guarded fire-time checks"
```

---

### Task 9: Platform implementations, DI, Application wiring

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/reminder/WorkEnqueuer.kt` (add `WorkManagerEnqueuer`)
- Modify: `app/src/main/java/com/babegetthis/android/core/reminder/Platform.kt` (add the three Android impls)
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/di/ReminderModule.kt`
- Modify: `app/src/main/java/com/babegetthis/android/BabeGetThisApp.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/SyncTrigger.kt:14-19` (comment)
- Create: `app/src/androidTest/java/com/babegetthis/android/testing/TestReminderModules.kt`

**Interfaces:**
- Consumes: everything from Tasks 2–8.
- Produces:

```kotlin
class WorkManagerEnqueuer(private val workManager: WorkManager) : WorkEnqueuer   // UNIQUE_NAME = "shopping_reminder"; OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delayMillis, MILLISECONDS).setInputData(workDataOf(KEY_LIST_ID to listId, KEY_GENERATION to generation)); enqueueUniqueWork(..., REPLACE, ...).await(); cancelUniqueWork(UNIQUE_NAME).await()
class AndroidNotificationGate(context: Context) : NotificationGate   // NotificationManagerCompat.from(context).areNotificationsEnabled()
class ProcessForegroundState : ForegroundState                         // ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(STARTED)
object SystemReminderClock : ReminderClock                             // System.currentTimeMillis()

@Module @InstallIn(SingletonComponent::class) object ReminderModule {
    @Provides @Singleton fun provideReminderPrefs(@ApplicationContext c: Context): ReminderPrefs = SharedPrefsReminderPrefs(c)
    @Provides @Singleton fun provideContentReader(db: AppDatabase): ReminderContentReader
    @Provides @Singleton fun provideEligibility(reader: ReminderContentReader): ReminderEligibility
    @Provides @Singleton fun provideNotifier(@ApplicationContext c: Context): ReminderNotifier = ReminderNotifier(c, NotificationManagerCompat.from(c))
    @Provides @Singleton fun provideScheduler(...): ReminderScheduler   // scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Provides @Singleton fun provideKicker(s: ReminderScheduler): ReminderKicker = s
}

@Module @InstallIn(SingletonComponent::class) object ReminderPlatformModule {
    @Provides @Singleton fun provideWorkManager(@ApplicationContext c: Context): WorkManager = WorkManager.getInstance(c)
    @Provides @Singleton fun provideEnqueuer(wm: WorkManager): WorkEnqueuer = WorkManagerEnqueuer(wm)
    @Provides @Singleton fun provideGate(@ApplicationContext c: Context): NotificationGate = AndroidNotificationGate(c)
    @Provides @Singleton fun provideForeground(): ForegroundState = ProcessForegroundState()
    @Provides @Singleton fun provideClock(): ReminderClock = SystemReminderClock
}
```

`BabeGetThisApp`: implement `Configuration.Provider`; `@Inject lateinit var workerFactory: HiltWorkerFactory`; `override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workerFactory).build()`; `@Inject lateinit var reminderScheduler: ReminderScheduler`; in `onCreate` after `syncTrigger.register(this)`: `reminderScheduler.observeSignOuts(authStateManager.signOuts)`; in the flag coroutine after `featureFlagCache.update(...)`: `reminderScheduler.onFlagsLoaded()`.

`TestReminderModules.kt` (androidTest): `@TestInstallIn(replaces = [ReminderPlatformModule::class])` providing `WorkManager` via `WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(factory).setExecutor(SynchronousExecutor()).build())` then `WorkManager.getInstance(context)`; `WorkEnqueuer` = `WorkManagerEnqueuer(wm)`; `NotificationGate` = `AndroidNotificationGate(context)`; `ForegroundState { false }`; `ReminderClock` = `SystemReminderClock`.

- [ ] **Step 1: Implement the platform classes, the two modules, the Application changes, the test module, and update the `SyncTrigger` comment** to say reminders use WorkManager and sync deliberately does not.

- [ ] **Step 2: Verify Hilt and the full unit suite**

Run: `./gradlew :app:kspDevDebugKotlin testDevDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no behaviour change yet, but the graph must compile with the worker and every binding).

- [ ] **Step 3: Verify the instrumented build compiles**

Run: `./gradlew :app:compileDevDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ app/src/main/java/com/babegetthis/android/BabeGetThisApp.kt app/src/main/java/com/babegetthis/android/core/sync/SyncTrigger.kt app/src/androidTest/java/com/babegetthis/android/testing/TestReminderModules.kt
git commit -m "feat(reminders): Hilt wiring, HiltWorkerFactory and platform impls"
```

---

### Task 10: Repository hooks

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/data/repository/ShoppingItemRepository.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/data/repository/ShoppingListRepository.kt:30-40, 142-152`
- Modify tests constructing these repositories: `ShoppingItemDataTest.kt:31`, `ShoppingItemRepositorySharedTest.kt:26`, `ShoppingListRepositorySharedTest.kt:33`, `DeleteListIfEmptyTest.kt:25`, `ShoppingListRepositoryTest.kt:43`
- Test: `app/src/test/java/com/babegetthis/android/feature/shoppingitems/data/repository/ShoppingItemRepositoryReminderTest.kt`

**Interfaces:**
- Consumes: `ReminderKicker`, `FakeReminderKicker` (Task 5).
- Produces: `ShoppingItemRepository(…, syncKicker, reminderKicker: ReminderKicker)`; `ShoppingListRepository(…, syncEngine, reminderKicker: ReminderKicker)`.

Calls: `togglePickedUp` → `onLocalPickUp(listId)` when `isPickedUp`, else `onLocalEdit(listId)`, only when `listId != null`; `addItem`, `updateItem`, `restoreItem` → `onLocalEdit(listId)`; `deleteItem` → `onLocalEdit(listId)` when `listId != null`; `ShoppingListRepository.addItemsToList` → `onLocalEdit(listId)` when `itemEntities.isNotEmpty()`. Each call goes after the DAO write succeeds, beside the existing `syncKicker.pushSoon()`.

- [ ] **Step 1: Write the failing tests** (`InMemoryDatabaseRule`, `FakeReminderKicker`)

```kotlin
`ticking calls onLocalPickUp with the list id`
`unticking calls onLocalEdit`
`add, update, delete and restore call onLocalEdit`
`a tick on an unknown item calls nothing`
`addItemsToList with drafts calls onLocalEdit once; with none calls nothing`   // ShoppingListRepository
```

No test for "sync apply calls neither": `SyncEngine` has no `ReminderKicker` dependency and this task does not add one, which the compiler enforces.

- [ ] **Step 2: Run to verify they fail.** Compilation failure on the new constructor parameter.

- [ ] **Step 3: Implement the hooks and pass `FakeReminderKicker()` at the five existing construction sites.**

- [ ] **Step 4: Run the full unit suite.** `./gradlew testDevDebugUnitTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/feature/ app/src/test/java/com/babegetthis/android/feature/
git commit -m "feat(reminders): repositories kick the reminder seam on local writes"
```

---

### Task 11: Items screen prompt

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ui/ReminderPermissionSheet.kt`
- Create: `app/src/main/java/com/babegetthis/android/core/reminder/ui/ReminderPermissionStateProvider.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/ui/viewModels/ShoppingItemsViewModel.kt` (constructor; `togglePickedUp` at ~line 401)
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/ui/ShoppingItemsScreen.kt`
- Test: `app/src/test/java/com/babegetthis/android/feature/shoppingitems/ui/viewModels/ShoppingItemsViewModelTest.kt`, `app/src/test/java/com/babegetthis/android/feature/shoppingitems/ui/ShoppingItemsScreenTest.kt`

**Interfaces:**
- Consumes: `ReminderScheduler.shouldOfferPermission/onPermissionRequested/onPermissionGranted/onPermissionDenied/snoozePrompt`, `PermissionState`, `permissionState(...)`.
- Produces:

```kotlin
// ViewModel additions
private val reminderScheduler: ReminderScheduler            // new constructor parameter
val showReminderPrompt: StateFlow<Boolean>
fun updatePermissionState(state: PermissionState)          // stored; used after the next tick
fun onReminderPromptAccepted()                             // scheduler.onPermissionRequested(); showReminderPrompt = false; emits UiEvent.RequestNotificationPermission
fun onReminderPromptDeclined()                             // scheduler.onPermissionDenied(); showReminderPrompt = false
fun onNotificationPermissionResult(granted: Boolean)       // granted → scheduler.onPermissionGranted(listId); else scheduler.onPermissionDenied()

// ui/ReminderPermissionStateProvider.kt
@Composable fun rememberPermissionState(requested: Boolean): PermissionState
    // Build.VERSION.SDK_INT, ContextCompat.checkSelfPermission(POST_NOTIFICATIONS), ActivityCompat.shouldShowRequestPermissionRationale(activity, …); recomputed on ON_RESUME via LifecycleEventObserver

// ui/ReminderPermissionSheet.kt
@Composable fun ReminderPermissionSheet(onAccept: () -> Unit, onDecline: () -> Unit)
    // ModalBottomSheet like VoiceCaptureSheet; title/body/two buttons from strings; onDismissRequest = onDecline
```

ViewModel: in `togglePickedUp` success branch, when `isPickedUp`: `if (reminderScheduler.shouldOfferPermission(listId, permissionState)) _showReminderPrompt.value = true`, where `permissionState` defaults to `PermissionState.Granted` until the screen reports (so no prompt can appear before the screen has computed it). Add `fun permissionRequested(): Boolean = reminderScheduler.permissionRequested()` and a `RequestNotificationPermission` variant to the existing `UiEvent` sealed type.

Screen: `val permission = rememberPermissionState(requested = viewModel.permissionRequested())` and `LaunchedEffect(permission) { viewModel.updatePermissionState(permission) }`; `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onNotificationPermissionResult(it) }`; collect `UiEvent.RequestNotificationPermission` to call `launcher.launch(Manifest.permission.POST_NOTIFICATIONS)`; render `ReminderPermissionSheet` when `showReminderPrompt`.

- [ ] **Step 1: Write the failing ViewModel tests** (scheduler as `mockk(relaxed = true)` added to `buildViewModel()`)

```kotlin
`a tick asks the scheduler with the reported permission state and shows the prompt on true`
`a tick after the last item is not offered when the scheduler says no`
`unticking never asks`
`accepting records the request, hides the sheet and emits the launch event`
`declining denies and hides the sheet`
`a granted result arms the current list; a denied result snoozes`
`no prompt before the screen reports a permission state`   // default Granted → scheduler called with Granted → relaxed false
```

- [ ] **Step 2: Write the failing screen test** in `ShoppingItemsScreenTest`: `the reminder sheet shows its copy and routes both buttons` with the scheduler mock returning true; assert "Forget something?" visible, click "Not now" → `onPermissionDenied` verified.

- [ ] **Step 3: Run to verify they fail.** Compilation failure.

- [ ] **Step 4: Implement** the sheet, the provider, the ViewModel additions and the screen wiring.

- [ ] **Step 5: Run the tests.** `./gradlew testDevDebugUnitTest --tests "*ShoppingItems*"` → PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ui/ app/src/main/java/com/babegetthis/android/feature/shoppingitems/ app/src/test/java/com/babegetthis/android/feature/shoppingitems/
git commit -m "feat(reminders): in-context permission prompt after a partly-shopped tick"
```

---

### Task 12: Settings toggle

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/settings/ui/SettingsScreen.kt` (new section before "Privacy")
- Test: `app/src/test/java/com/babegetthis/android/feature/settings/ui/SettingsTest.kt`

**Interfaces:**
- Consumes: `ReminderScheduler.setEnabled/onPermissionRequested/onPermissionGranted`, `ReminderPrefs.enabled`, `NotificationGate`, `PermissionState`, `rememberPermissionState` (Task 11).
- Produces:

```kotlin
// SettingsViewModel additions (constructor: + reminderScheduler: ReminderScheduler, reminderPrefs: ReminderPrefs, notificationGate: NotificationGate)
enum class ReminderRowState { On, Off, Blocked }     // Blocked = PermanentlyRefused, or OS notifications disabled
sealed interface SettingsEvent { data object RequestNotificationPermission : SettingsEvent; data object OpenNotificationSettings : SettingsEvent }
val reminderRow: StateFlow<ReminderRowState>
val events: SharedFlow<SettingsEvent>
fun updatePermissionState(state: PermissionState)    // recomputes reminderRow
fun onReminderSwitch(checked: Boolean)
fun onNotificationPermissionResult(granted: Boolean) // granted → setEnabled(true)
```

`reminderRow` = `Blocked` when `permission == PermanentlyRefused || !notificationGate.notificationsEnabled()`; else `On` when `prefs.enabled && (permission == Granted || permission == NotRequired)`; else `Off`. `onReminderSwitch(false)` → `setEnabled(false)`. `onReminderSwitch(true)`: `Blocked` → emit `OpenNotificationSettings`; `Requestable` → `onPermissionRequested()` + emit `RequestNotificationPermission`; otherwise `setEnabled(true)`. Screen: section header `settings_reminders_section`; one `SettingsRow` with `Icons.Outlined.NotificationsActive`, `trailing` Switch `checked = row == On`, subtitle `settings_reminders_blocked_subtitle` when `Blocked` else `settings_reminders_subtitle`; `onClick` toggles like the analytics row; `OpenNotificationSettings` starts `Settings.ACTION_APP_NOTIFICATION_SETTINGS` with `EXTRA_APP_PACKAGE`.

- [ ] **Step 1: Write the failing tests**

```kotlin
`the reminder row is On only with the preference and permission`        // Granted+enabled → On; Granted+disabled → Off; Requestable+enabled → Off
`permanently refused or os-disabled reads Blocked`
`switching off disables the scheduler`
`switching on when Requestable records the request and asks for permission`
`switching on when Blocked opens system settings`
`switching on when Granted enables`
`a granted permission result enables`
`the reminders row shows the blocked subtitle when blocked`   // Compose, SettingsScreen with a stubbed ViewModel state
```

- [ ] **Step 2: Run to verify they fail.** Compilation failure.

- [ ] **Step 3: Implement** the ViewModel additions and the screen section.

- [ ] **Step 4: Run the tests.** `./gradlew testDevDebugUnitTest --tests "*Settings*"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/feature/settings/ app/src/test/java/com/babegetthis/android/feature/settings/
git commit -m "feat(reminders): Settings switch showing preference and OS state"
```

---

### Task 13: Tap handling and navigation

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/reminder/ReminderIntentParser.kt`
- Modify: `app/src/main/java/com/babegetthis/android/navigation/BgtNavGraph.kt`
- Modify: `app/src/main/java/com/babegetthis/android/MainActivity.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/reminder/ReminderIntentParserTest.kt`, `app/src/test/java/com/babegetthis/android/navigation/BgtNavGraphTest.kt`

**Interfaces:**
- Consumes: `ReminderTarget`, `EXTRA_*` (Task 6), `AnalyticsEvent.ReminderTapped` (Task 7).
- Produces:

```kotlin
object ReminderIntentParser {
    fun parse(intent: Intent?, startAtWelcome: Boolean): ReminderTarget?   // null when either extra is missing/blank or startAtWelcome
}

// BgtNavGraph: two new parameters
pendingReminder: ReminderTarget? = null,
onPendingReminderConsumed: () -> Unit = {},
// LaunchedEffect(pendingReminder): navigate(Routes.shoppingItems(id, Uri.encode(name))) { popUpTo(Routes.SHOPPING_LIST) }; onPendingReminderConsumed()

// MainActivity
private val pendingReminder = MutableStateFlow<ReminderTarget?>(null)
override fun onNewIntent(intent: Intent)   // super; pendingReminder.value = parse(intent, startAtWelcome); track ReminderTapped when non-null
// onCreate: pendingReminder.value = parse(intent, startAtWelcome); track when non-null; pass collectAsStateWithLifecycle() value and { pendingReminder.value = null } to BgtNavGraph
```

`Routes.shoppingItems` already interpolates the raw name; the notification path encodes with `Uri.encode(name)` and the `ShoppingItemsViewModel` reads `listName` from `SavedStateHandle`, which Navigation decodes. Existing in-app navigation is unchanged.

- [ ] **Step 1: Write the failing tests**

`ReminderIntentParserTest`:
```kotlin
`both extras present yields a target`
`a missing or blank extra yields null`
`startAtWelcome yields null even with extras`
`a null intent yields null`
```
`BgtNavGraphTest`: the real screens need `hiltViewModel()`, so the navigation decision is extracted into `fun NavHostController.openReminder(target: ReminderTarget)` in `BgtNavGraph.kt` (the `navigate { popUpTo(Routes.SHOPPING_LIST) }` call with `Uri.encode(name)`), and the test drives it against a `TestNavHostController` whose graph is a stand-in with the same route strings and arguments as `BgtNavGraph` but empty composables. `BgtNavGraph` itself calls `openReminder` from its `LaunchedEffect`.
```kotlin
`a pending reminder lands on its items route with the catalog directly beneath`
`from another list's items screen the old entry is popped`
`from settings the settings entry is popped`
`a name with slashes and question marks round-trips through the route`     // Review Focus 1: backStackEntry.arguments["listName"] == original
```
`MainActivityReminderTest` is not an Activity test (`MainActivity` needs Hilt). Extract `fun handleReminderIntent(intent: Intent?, startAtWelcome: Boolean, track: (AnalyticsEvent) -> Unit): ReminderTarget?` into `ReminderIntentParser.kt`, called from both `onCreate` and `onNewIntent`, and test it in `ReminderIntentParserTest`: `tracks ReminderTapped once when a target parses, never otherwise`. Drop the separate `MainActivityReminderTest` file.

- [ ] **Step 2: Run to verify they fail.** Compilation failure.

- [ ] **Step 3: Implement** the parser, `NavHostController.openReminder`, the graph parameters, and the activity changes (inject `AnalyticsRepository`).

- [ ] **Step 4: Run the tests.** `./gradlew testDevDebugUnitTest --tests "*Reminder*" --tests "*BgtNavGraph*"` → PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/babegetthis/android/core/reminder/ReminderIntentParser.kt app/src/main/java/com/babegetthis/android/navigation/ app/src/main/java/com/babegetthis/android/MainActivity.kt app/src/test/java/com/babegetthis/android/core/reminder/ReminderIntentParserTest.kt app/src/test/java/com/babegetthis/android/navigation/
git commit -m "feat(reminders): notification tap lands on the list above the catalog"
```

---

### Task 14: End-to-end journey, docs, gates

**Files:**
- Create: `app/src/androidTest/java/com/babegetthis/android/journey/ReminderJourneyTest.kt`
- Modify: `README.md:22-30` (features list), `docs/publishing/privacy-policy.md` (one bullet under "What We Do NOT Collect" or a new bullet in section 1), `TODO.md` (none unless something was deferred)

**Interfaces:**
- Consumes: `TestReminderModules` (Task 9), `WorkManagerTestInitHelper.getTestDriver(context)`, `ReminderIntentParser.EXTRA_*`.

- [ ] **Step 1: Write the journey test** (pattern of `PinGateAndUndoTest`; add `GrantPermissionRule.grant(POST_NOTIFICATIONS)` when `SDK_INT >= 33`; inject `WorkManager`, `NotificationManager`)

```kotlin
`a half-shopped list nudges once, a second list replaces it, and the tap opens it`
  // 1. create "Weekend shop" with Milk and Eggs; tick Milk
  //    id = workManager.getWorkInfosForUniqueWork("shopping_reminder").get().single().id
  //    testDriver.setInitialDelayMet(id); wait until activeNotifications.size == 1; title contains "Weekend shop"
  // 2. back to catalog; create "Hardware" with Nails and Screws; tick Nails; drive again
  //    activeNotifications.size == 1 and title contains "Hardware"
  // 3. val intent = activeNotifications.single().notification.contentIntent → cannot be sent in-test; instead
  //    compose.activity.onNewIntent(Intent().putExtra(EXTRA_LIST_ID, hardwareId).putExtra(EXTRA_LIST_NAME, "Hardware"))
  //    awaitText("Nails"); Espresso.pressBack(); awaitText("Hardware") on the catalog
```

- [ ] **Step 2: Run on an emulator.** `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babegetthis.android.journey.ReminderJourneyTest` → PASS. If no emulator is available, say so in the commit body and leave the test in place for CI.

- [ ] **Step 3: Docs.** README feature bullet: "One reminder if you leave items unticked after a shop, scheduled on the device". Privacy policy, section 1 or 2: one sentence that the app can show reminder notifications built from the lists on your device, they can be turned off in Settings, and nothing about them is sent anywhere. Update the spec's header status to "implemented".

- [ ] **Step 4: Run every CI gate locally**

```bash
./gradlew spotlessApply spotlessCheck lintDevDebug testDevDebugUnitTest koverVerifyDevDebug
```
Expected: all BUILD SUCCESSFUL. If `koverVerifyDevDebug` fails, the report names the uncovered lines in `core.reminder`; add the missing test rather than widening the exclude list.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/babegetthis/android/journey/ReminderJourneyTest.kt README.md docs/publishing/privacy-policy.md docs/superpowers/specs/2026-11-01-shopping-reminders-design.md
git commit -m "test(reminders): end-to-end journey; docs for shopping reminders"
```
