# Shopping reminders — design

**Date:** 2026-11-01 · **Status:** implemented · **Branch:** `feat/guest-and-account-lists` (spec only; implementation gets its own branch)

## Summary

One nudge notification when the user leaves a shop half done: some items on a
list were ticked on this phone, others were not, and nothing has happened on
that list for three hours. The notification names the list and up to two of
the unticked items, and tapping it opens the list.

Everything runs on the device with WorkManager. There is no server involvement
and no push messaging. The app has many lists, so only the list most recently
ticked on this phone can have a pending reminder, and each shopping session
gets at most one nudge.

## Why

Users add items over the week, then shop. A trip gets interrupted (a call, a
queue, a child) and the last few items stay unticked without anyone noticing
until the next trip. The DoorDash-style "you left things in your cart" nudge
is the closest pattern, with one difference: a cart is one thing you intend to
buy now, while a shopping list legitimately sits unbought for days. So the
trigger is not "items exist" but "a shop was started and not finished".

## Decisions

Recorded with the alternative that was rejected, so they are not re-argued.

| # | Decision | Rejected alternative, and why |
|---|---|---|
| D1 | **On-device scheduling with WorkManager.** No backend, no FCM. | Server-side scan (pg_cron + edge function + FCM) — guest lists never leave the phone, so guests would get no reminders at all. Also needs a device-token table, the FCM module, and Play Data safety updates. Revisit only for partner-driven nudges while the app is dead. |
| D2 | **Trigger is "partly shopped then quiet"**: at least one item ticked on this phone, at least one unticked, and no local activity on that list for 3 hours. | Fixed delay after any edit, or a time of day — both fire on lists that are intentionally waiting for shopping day. |
| D3 | **Only ticks made on this phone arm a reminder, and only local edits reset it.** Synced-in partner changes neither arm nor reset. At fire time the list's Room state decides, whoever wrote it: the worker does not check that the locally ticked item is still ticked, only that the list is partly shopped. | Counting partner ticks for arming — if the partner is shopping, nudging this phone is wrong; the partner's phone gets its own nudge. Tracking the specific ticked item — a user who ticks milk, unticks it, and ticks eggs is still mid-shop; per-item tracking adds state for no better decision. |
| D4 | **One pending reminder at a time, belonging to the most recently *eligible* locally ticked list.** A tick that does not leave its list partly shopped (the last item, or a one-item list) does not start a session and does not disturb the pending reminder of another list. | One per list — a user shopping two lists in one trip would get two notifications about the same outing. "Most recently ticked" literally — finishing list B would silently kill A's reminder even though B never started a session. |
| D5 | **Once per shopping session.** After a nudge the list is left alone until the next tick on it, which starts a fresh session. | Daily until done — a half-shopped list that sits for a week is often intentional, and repeat nudges drive uninstalls. |
| D6 | **Permission is asked in context**: after the first tick that would arm a reminder, via an in-app sheet before the system dialog. | At onboarding — the user does not yet know why they would want it, and Android stops showing the dialog after two refusals. Settings-only — near-zero adoption. |
| D7 | **WorkManager, not AlarmManager.** | Exact alarms need a special permission that Android 13 denies by default to non-alarm apps; inexact alarms die on reboot without a boot receiver and have no replace-pending semantic. |
| D8 | **Kill switch through the existing feature flag cache**, read at arm time in the UI process. | Reading the flag inside the worker — the cache is empty on a background cold start, so the worker would always see "off". |
| D9 | **Doubt means silence.** Every check at fire time that cannot be satisfied suppresses the nudge; the worker never retries. | Retry on failure — a reminder that arrives twice or late is worse than one that is dropped. |
| D10 | **Every enqueue carries a generation number, and the worker's final ownership check, the post, and the conditional clear run under the same singleton lock the scheduler uses.** A stale worker neither posts nor clears state. Ownership means the *active* pending record matches on list id and generation; a cleared record owns nothing even though the counter keeps its value. | Trusting `REPLACE` alone — it cancels a running worker only cooperatively. Two unlocked generation reads — they shrink the window but a retarget can still land between the last read and `notify()`. Comparing the counter alone — a worker from a cleared session would still match the retained counter. |
| D11 | **The pending record is committed to disk synchronously (on IO, under the lock) before the job is enqueued, and the enqueue is awaited before the lock is released.** A failed commit means no enqueue. The worker's ownership check is what reconciles the two stores. | `apply()` plus fire-and-forget enqueue — `apply()` persists asynchronously and `enqueueUniqueWork` returns an async `Operation`, so "prefs before work" would hold in memory but not on disk, and two rapid calls could reorder at WorkManager. A start-up reconciliation pass — more code for a failure that is already harmless (see Consistency). |
| D12 | **The preference is user intent; the permission is OS state. Both are required to arm and both are re-checked in the worker.** | Treating the toggle as the only switch — a user who revokes notifications in system settings would still see the switch on and get nothing. |

## What users see

### The first partly-shopped tick (Android 13 and up, permission not granted)

The tick lands as today. Then a Material 3 modal bottom sheet appears:

> **Forget something?**
> Get one nudge if you leave items unticked after a shop.
> [Remind me] [Not now]

- **Remind me** launches the system notification permission dialog and records
  `permissionRequested = true`. On grant, the list the user just ticked is
  armed (after re-checking it is still partly shopped), so the first-ever
  session gets its reminder too. On deny, the offer is snoozed for 14 days,
  the same as Not now: a user who said yes to us and no to the system should
  not see the sheet again on the next tick.
- **Not now**, or dismissing the sheet by swipe, snoozes the offer for 14 days
  on this device.

This flow assumes `targetSdk >= 33`, which the app already satisfies (36). An
app targeting 32 or lower gets a system-driven prompt on Android 13 where a
single denial can end prompting, which would break the in-context design.

The sheet appears only when the list the user just ticked is **genuinely
partly shopped**: after the tick, at least one live item is picked up and at
least one is not. Ticking the last item never prompts, and nor does ticking
the first item on a one-item list.

The sheet never appears when: the device is below Android 13 (no runtime
permission exists, so there is nothing to ask; OS notification state still
matters, see Settings), permission is already granted, the Settings toggle is
off, the offer is snoozed, or the system has permanently refused.

**Permanently refused** is derived, not inferred from one API:
`permissionRequested && !granted && !shouldShowRequestPermissionRationale`.
The rationale flag alone is false both before the first request and after the
user has refused for good, so without the `permissionRequested` record the two
states are indistinguishable.

On grant, eligibility is checked again before arming: the list must still be
partly shopped at that moment. The user may have ticked the remaining items
while the system dialog was up, in which case nothing is armed.

### The reminder

A notification on the `reminders` channel ("Shopping reminders", default
importance: sound and shade, no heads-up):

> **Forget something on Weekend shop?**
> Milk, eggs and 2 more are still unticked

The second line names up to two unticked items and counts the rest, with a
plural resource so one item reads "Milk is still unticked". For a locked list
item names are withheld: "3 items are still unticked". The list name still
shows, matching what the home screen card already reveals for locked lists.

"Locked" here is the conservative reading: `isLocked` on the list row at the
moment the snapshot is taken, whether or not a PIN currently exists (the app
treats a locked row with no PIN as unlocked for display, but a notification is
not the place to make that call). The snapshot is taken by the worker just
before its locked final section, and if it cannot be taken at all the worker
posts nothing. Names and lock state come from the same Room transaction, so a
list that was locked between two reads can never leak names from the earlier
one.

The notification auto-cancels on tap and uses a fixed id, so a later nudge for
another list replaces rather than stacks. Its content `PendingIntent` uses a
fixed request code with `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, so the extras
(list id and name) are refreshed each time rather than the first list's extras
being reused for every later nudge.

### Tapping the reminder

Lands on the items screen for that list, with Back returning to the list
catalog. This holds wherever the user was: on the catalog, inside a different
list, or in Settings. The back stack after a tap is always catalog → target
list, never the previously open screen. A locked list shows its PIN gate
first, exactly as it does today when opened from the catalog.

### Settings

A new **Reminders** section with one row and a switch:

> **Shopping reminders**
> A nudge when you leave items unticked after a shop

The switch shows **preference AND permission**: it reads as on only when the
`enabled` preference is true and notifications are allowed by the OS.

- Off sets `enabled = false`, cancels the pending job and stops arming. The OS
  permission is left alone.
- On, with permission missing on Android 13 and up, launches the system dialog
  directly (no sheet). Grant sets `enabled = true`; deny leaves it unchanged
  and the switch stays off.
- On, with permission already granted, sets `enabled = true`.
- If the system has permanently refused (as defined above), the switch stays
  off, the subtitle becomes "Allow notifications in system settings to turn
  this on", and tapping the row opens the app's system notification settings
  page.
- **Android 12 and below.** There is no runtime permission, but
  `areNotificationsEnabled()` can still be false if the user disabled the
  app's notifications in system settings. The switch shows preference AND OS
  state on every version; when the OS state is off, the row behaves like the
  permanently-refused case and opens system settings.
- If the user revokes notifications in system settings while `enabled` is
  true, the switch reads off next time Settings is shown; turning it on again
  follows the "permission missing" path. The preference itself is not
  rewritten by OS state.

The `enabled` preference defaults to true, so a user who grants permission from
the in-context sheet is done in one step.

All copy lives in `strings.xml` from the start.

## Components

All new code lives in `core/reminder`, wired by a `ReminderModule` in
`core/reminder/di`, mirroring `core/sync`.

| Unit | Does | Depends on |
|---|---|---|
| `ReminderKicker` | `fun interface` with `onLocalPickUp(listId)` and `onLocalEdit(listId)`. Called by `ShoppingItemRepository` after local writes. Sits next to `SyncKicker`; the repository never learns WorkManager exists. | nothing |
| `ReminderScheduler` | Singleton. Implements the kicker and owns **the** `Mutex` for all reminder state. Checks the gate (`enabled` preference, feature flag, OS notification state, list eligibility) and enqueues a unique one-time request named `shopping_reminder` with a 3-hour initial delay and `REPLACE` policy, carrying the list id and a generation number. `onLocalEdit` re-enqueues only if a job for that list is pending. Answers `shouldOfferPermission(listId)`, arms on `onPermissionGranted(listId)` after re-checking eligibility, cancels on toggle-off, sign-out and `onFlagsLoaded()` with the flag off. Exposes `finish(listId, generation, post: suspend () -> Unit)`, which the worker calls for its final section: under the lock, verify ownership, run `post`, clear the record. Every mutation commits prefs and awaits WorkManager inside the lock. | `WorkEnqueuer`, `ReminderPrefs`, `ReminderEligibility`, `FeatureFlagCache`, `NotificationManagerCompat`, `AuthStateManager`, `CrashReporter` |
| `ReminderEligibility` | One suspend function, `isPartlyShopped(listId)`: the list exists with `deletedAt` null and, among live items, at least one is picked up and at least one is not. Backed by the same DAO query `ReminderContentReader` uses, so "partly shopped" has one definition. Used by the scheduler for prompt and arm. | `ShoppingListDao`, `ShoppingItemDao` |
| `ReminderContentReader` | One suspend function, `snapshot(listId): ReminderContent?`, read in a single Room transaction: list name, `isLocked`, count of picked live items, and the unpicked live items' names in list order. Null when the list is missing or tombstoned. The worker derives both eligibility and copy from this one snapshot, so the counts and the names come from the same instant. | `ShoppingListDao`, `ShoppingItemDao` |
| `WorkEnqueuer` | Interface over the two WorkManager calls used, as **suspend** functions that await the returned `Operation`: `enqueueUnique(listId, generation, delay)` and `cancelUnique()`. The production implementation calls `WorkManager.enqueueUniqueWork(…, REPLACE, …).await()` and `cancelUniqueWork(…).await()`. | `WorkManager` (production impl) |
| `ReminderWorker` | `@HiltWorker` `CoroutineWorker`. Reads its list id and generation from input data. Does a cheap unlocked ownership pre-check, then the environment checks (preference, OS notification state, foreground, age), then takes the snapshot, then hands the post to `scheduler.finish`, which re-verifies ownership under the lock. Always returns `success`. Rethrows `CancellationException`; reports any other exception as a non-fatal. | `ReminderScheduler`, `ReminderContentReader`, `ReminderPrefs`, `ReminderNotifier`, `CrashReporter`, injected foreground-state and clock providers |
| `ReminderNotifier` | Creates the channel idempotently, formats the copy from a `ReminderContent`, builds the content intent, posts. Holds no decisions and reads no data. | `NotificationManagerCompat`, string resources |
| `ReminderPrefs` | SharedPreferences, same pattern as `PrefsSyncPointStore`, but with a record API rather than loose fields. `enabled: Boolean` (default true). `generationCounter: Long` (monotonic, default 0, never reset). `pending: PendingReminder?` where `PendingReminder(listId, generation, armedAt)` is read and written as one unit: `readPending()`, `commitPending(record)`, `commitClearPending()`. `promptSnoozedUntil: Long`. `permissionRequested: Boolean`. The two `commit…` functions use `commit()`, not `apply()`, return the result, and are only ever called on an IO dispatcher inside the scheduler's lock. | `Context` |
| `ReminderIntentParser` | Pure Kotlin. Reads the list id and name extras from an `Intent`, returns null when absent or when `startAtWelcome` is true. | nothing |

### Repository hook

Arming and resetting are decided at the repository layer, which only local UI
writes pass through. `SyncEngine.apply` writes DAOs directly and is untouched,
which is what makes D3 hold.

| Local write | Kicker call | Effect |
|---|---|---|
| `togglePickedUp(…, isPickedUp = true)` | `onLocalPickUp(listId)` | Arms (or retargets) if the list is now partly shopped; resets the clock if already pending for this list. |
| `togglePickedUp(…, isPickedUp = false)` | `onLocalEdit(listId)` | Resets the clock if pending for this list; otherwise nothing. |
| `addItem`, `updateItem`, `deleteItem`, `restoreItem` on an item of the list | `onLocalEdit(listId)` | Same as untick: reset only. |
| List-level edits: rename, lock or unlock, reorder, complete | none | Not shopping activity. They neither arm nor reset. |
| Any write arriving through `SyncEngine.apply` | none | Partner activity. Neither arms nor resets. |

"Reset" means re-enqueue with a fresh 3-hour delay and a new generation for the
same list. An edit on a list other than the pending one is ignored: it does not
cancel or retarget the pending reminder, because adding next week's items while
shopping today is normal.

### Activity and navigation

`MainActivity` reads the extras in `onCreate` and in `onNewIntent`, pushes the
parsed target into a `MutableStateFlow`, and `BgtNavGraph` gets an optional
`pendingList` parameter it navigates to after the start destination and then
clears, so a rotation does not re-navigate. The navigation call is
`navigate(Routes.shoppingItems(id, name)) { popUpTo(Routes.SHOPPING_LIST) }`,
which pops anything above the catalog (another list's items screen, Settings)
before pushing the target, so the back stack is catalog → target regardless of
where the user was. The content intent uses
`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`,
so a backgrounded activity receives `onNewIntent` rather than a second
instance.

### Items screen

`ShoppingItemsViewModel` gains `showReminderPrompt: StateFlow<Boolean>`. After
a successful tick it asks `scheduler.shouldOfferPermission(listId)`, which
returns true only when the five prompt conditions hold **and**
`ReminderEligibility.isPartlyShopped(listId)` is true. The screen shows the
sheet and owns the permission launcher; grant calls
`scheduler.onPermissionGranted(listId)`, which re-checks eligibility and arms
only if the list is still partly shopped; deny or dismiss calls
`scheduler.snoozePrompt()`.

### Feature flag kill switch

The flag `shopping_reminders` is read from `FeatureFlagCache` at arm time, in
the UI process, never in the worker (D8). On its own that only prevents
*future* arming, so a job armed before a flag flip would still fire. To make
the flag a real kill switch, `BabeGetThisApp` calls
`scheduler.onFlagsLoaded()` right after `featureFlagCache.update(...)` in its
existing start-up coroutine. With the flag off, `onFlagsLoaded` takes the lock,
clears the pending record and awaits `cancelUniqueWork`. With the flag on it
does nothing.

What this does and does not guarantee: a job armed before the flip is
cancelled on the next cold start once the flag fetch completes. If the job
fires before that fetch (the phone was off, then the app process starts for
the job itself), the worker, which does not consult the flag, may still post
once. Accepted; see Accepted trade-offs.

### Dependencies

Added to the app: `work-runtime-ktx`, `hilt-work` with its compiler on `ksp`,
`work-testing` (test only), and `lifecycle-process` (already in the catalog,
not yet applied). The `SyncTrigger` comment about "no WorkManager in v1" is
updated to say reminders use it and sync does not.

### HiltWorkerFactory setup

Hilt cannot construct workers through WorkManager's default factory, so four
pieces are needed, all standard:

1. **Dependencies.** `androidx.hilt:hilt-work` as `implementation` and
   `androidx.hilt:hilt-compiler` as `ksp`, both on the existing
   `hiltNavigationCompose` version (1.2.0): all three are `androidx.hilt`
   artifacts released together, so they share one version entry, which gets
   renamed to `androidxHilt` to reflect that.
2. **The worker.** `@HiltWorker class ReminderWorker @AssistedInject constructor(
   @Assisted context: Context, @Assisted params: WorkerParameters, …deps) :
   CoroutineWorker(context, params)`. Everything after the two assisted
   parameters is injected from the singleton component.
3. **The Application.** `BabeGetThisApp` implements `Configuration.Provider`,
   injects `HiltWorkerFactory`, and returns
   `Configuration.Builder().setWorkerFactory(factory).build()` from
   `workManagerConfiguration`. Because Hilt field injection happens in
   `super.onCreate()`, the property is a getter, not a value computed at
   construction.
4. **Manifest.** Disable WorkManager's automatic initialiser so the custom
   configuration is used:

   ```xml
   <provider
       android:name="androidx.startup.InitializationProvider"
       android:authorities="${applicationId}.androidx-startup"
       android:exported="false"
       tools:node="merge">
       <meta-data
           android:name="androidx.work.WorkManagerInitializer"
           android:value="androidx.startup"
           tools:node="remove" />
   </provider>
   ```

   The `tools` namespace is already declared in the manifest. Only the
   WorkManager initializer is removed; other `androidx.startup` initializers
   (Firebase's, for example) are untouched.

Instrumented and Robolectric tests initialise WorkManager themselves with
`WorkManagerTestInitHelper` and a test `Configuration` carrying the same
`HiltWorkerFactory` (or, for `ReminderWorkerTest`, construct the worker directly
through `TestListenableWorkerBuilder.setWorkerFactory`).

## Data flow

1. User ticks an item on list A. Repository writes Room, then calls
   `onLocalPickUp(A)`.
2. Scheduler, on its IO scope, takes the lock, passes the gate (preference,
   flag, OS notification state, `isPartlyShopped(A)`), increments
   `generationCounter` to *g*, **commits** `PendingReminder(A, g, now)` to
   disk, and only if the commit succeeded **enqueues and awaits** the job for
   A with input `(A, g)` using `REPLACE`. Then it releases the lock. The UI
   thread never waits on any of this: the kicker call returns immediately,
   exactly like `SyncKicker.pushSoon`.
3. Further ticks or edits on A within three hours repeat step 2 with a new
   generation, resetting the clock. A tick on list B that leaves B partly
   shopped retargets the single job to B, also with a new generation. A tick
   on B that does not (B's last item) changes nothing (D4).
4. Partner changes arrive through `SyncEngine.apply`, which never touches the
   kicker.
5. Three hours after the last local activity the worker for *(A, g)* runs. It
   pre-checks ownership without the lock, runs its environment checks, takes a
   content snapshot, then calls `scheduler.finish(A, g) { notifier.post(…) }`.
   Under the lock the scheduler verifies ownership again, runs the post, and
   clears the record. If ownership fails at either point, nothing is posted
   and nothing is cleared.

## Consistency between WorkManager and prefs

Two stores, one lock, one writer order, one reconciler.

- **One lock.** The scheduler's `Mutex` guards every read-modify-write of the
  pending record, every WorkManager call, and the worker's final section.
  Workers run in the app process, so the lock covers them. Rapid rescheduling
  therefore serialises: each call sees the previous call's committed record
  and its completed enqueue.
- **Writer order, durably.** Inside the lock: `commit()` the record on IO,
  check the result, then `enqueueUniqueWork(...).await()`. Because the enqueue
  is awaited, two successive calls cannot reach WorkManager out of order, so
  the last job standing always carries the generation of the committed record.
- **Commit failure.** `commit()` returning false means no enqueue, a non-fatal
  through `CrashReporter`, and the in-memory counter is still advanced (it is
  only a uniqueness source). Outcome: no reminder for this tick; the next tick
  tries again.
- **Process death after commit, before enqueue completes.** The record claims
  a job that may not exist. Harmless: no notification can fire, and the next
  tick or edit on that list overwrites the record and enqueues normally.
  `onLocalEdit` believing a job is pending and "re-enqueueing" simply creates
  it.
- **Process death after enqueue, before commit.** Cannot happen; the commit
  comes first and is synchronous.
- **Job exists, record cleared or changed.** The worker fails ownership and
  exits without posting or clearing. Ownership is
  `pending != null && pending.listId == workerListId && pending.generation ==
  workerGeneration`. The counter alone is never consulted, so a worker from a
  session whose record was cleared does not match even though the counter
  still holds its value (D10).
- **Why the final section is locked.** Without it this ordering is possible:
  worker A reads ownership (match), scheduler B retargets and commits
  generation 5, worker A posts. With it, either B's retarget completes first
  and A's locked ownership check fails, or A's post and clear complete first
  and B's retarget then writes a fresh record. Both orders are correct.
- **Clearing.** Only the locked final section of a worker whose ownership
  held clears the record; cancel paths (toggle off, sign-out, flag off at
  cold start) clear it under the same lock and await `cancelUniqueWork`. The
  counter is never reset.
- **No start-up reconciliation.** Every inconsistency above resolves to "no
  notification" or "self-heals on the next tick", so a scan on app start would
  add code without changing any outcome.

## Fire-time checks (worker)

In this order; all must hold or the worker exits silently, returning `success`:

1. **Ownership pre-check**, unlocked and cheap: the active pending record
   matches this worker's list id and generation. A mismatch means a newer job
   superseded this one or the session was cleared; exit at once (D10).
2. The `enabled` preference is true (D12; covers a toggle-off that raced the
   job).
3. Notifications are allowed by the OS (`areNotificationsEnabled()`), on every
   Android version: below 13 there is no runtime prompt, but the user can
   still disable the app's notifications in system settings.
4. The app is not in the foreground (`ProcessLifecycleOwner`, injected).
5. **Age is sane.** `age = now - pending.armedAt` must satisfy
   `0 <= age <= 24h`. Negative means the wall clock moved backwards since
   arming; over 24 hours means an aggressive OEM battery killer held the job,
   or the clock jumped forward. Either way a nudge now is noise. WorkManager's
   own delay bookkeeping may use wall-clock time, so this check is the one
   place clock behaviour is reasoned about.
6. **Snapshot and eligibility.** `ReminderContentReader.snapshot(listId)` is
   non-null, with at least one picked live item and at least one unpicked,
   regardless of who wrote those states (D3). Copy is formatted from this
   snapshot; if `isLocked` is true, item names are withheld.
7. **Ownership under the lock**, inside `scheduler.finish`. Re-verify the
   record as in check 1, then `notify()`, then clear the record, all before
   the lock is released. This is the step that makes the post atomic with
   respect to rescheduling and cancellation (D10).

Exceptions: `CancellationException` is rethrown so WorkManager's own
cancellation (a `REPLACE`, a `cancelUniqueWork`) is not mistaken for a crash.
Anything else is caught, reported as a non-fatal, and the worker returns
`success` without retry; the record is cleared only if the failure happened
inside the locked section after ownership held.

## Error handling and edge cases

| Case | Behaviour |
|---|---|
| List deleted or tombstoned before firing | Check 6 fails; silent. |
| User finishes the list | Check 6 fails (zero unticked); silent. No explicit cancel on the last tick: the worker's check is the single source of truth. |
| User unticks everything | Check 6 fails (zero ticked); silent. |
| User ticks milk, unticks milk, ticks eggs | Still partly shopped at fire time; the nudge fires. The worker does not care which item was ticked (D3). |
| Partner finishes the list on their phone | Sync writes Room; check 6 fails at fire time. Partner activity never arms or resets, but final Room state wins. |
| Partner ticks items, user ticks none | Nothing was ever armed on this phone. No nudge here; the partner's phone handles its own. |
| User ticks the last item on list B while A has a pending reminder | B is not partly shopped, so nothing is armed and A's reminder stays (D4). |
| Stale worker: user retargets from A to B while A's worker is running | Serialised by the lock. Either B's retarget commits first and A's worker fails the locked ownership check (7), posting nothing and clearing nothing; or A's post and clear finish first and B's retarget then writes a fresh record. B's job is never disturbed by A's worker. |
| Stale worker: record cleared, counter unchanged | A worker from the cleared session finds `pending == null` and fails ownership. The counter is never compared on its own (D10). |
| Stale worker: toggle off while the worker is running | The cancel path clears the record under the lock. The worker fails check 1, or if already past it, check 2 or the locked check 7. |
| Flag turned off remotely while a job is pending | On the next cold start, once the flag fetch completes, `onFlagsLoaded` cancels the job and clears the record. If the job fires before that fetch, it may post once. |
| Prefs `commit()` fails while arming | No enqueue; non-fatal reported; no reminder for this tick. The next tick tries again. |
| Sign-out | Account lists are evicted. Scheduler also cancels the job and clears the record on the `AuthState` signed-out transition; check 6 covers a missed cancel. |
| Toggle off or flag off | Scheduler cancels the pending job and clears the record; the gate blocks new ones. Flag flips take effect on next cold start, like every flag. |
| Permission revoked after arming, or notifications disabled in system settings on Android 12 and below | Check 3 fails; silent. The `enabled` preference is not changed; the Settings switch reads off because it shows preference AND OS state. |
| User taps Remind me, then denies the system dialog | `permissionRequested` is recorded, the offer is snoozed 14 days, nothing is armed. |
| App in the foreground at fire time | Check 4 fails; silent, no reschedule. The next tick is a fresh session. |
| Doze delay | Fires late, sometimes by an hour or more. Copy says "still unticked", not "three hours ago", so late reads fine. Capped by check 5. |
| List locked after arming, before firing | The snapshot in check 6 sees `isLocked = true`; names are withheld, the count still shows. |
| Exception in the worker | `CancellationException` is rethrown. Anything else is caught, reported as a non-fatal through `CrashReporter`, `success` returned so WorkManager never retries. The record is cleared only if the failure happened inside the locked section after ownership held. |
| Two ticks in quick succession | Serialised by the scheduler's lock, each with its own generation; `REPLACE` on one unique name means the last job wins and the only job left carries the recorded generation. |
| Process death between Room write and kicker call | Tick is saved, nothing was armed. The next tick arms. A lost nudge is never data loss. |
| Process death between prefs write and enqueue | Prefs claim a job that does not exist. No notification can fire; the next tick or edit on that list writes a new record and enqueues. See Consistency. |
| Reboot | WorkManager persists the job and its remaining delay. |
| Clock or timezone change | WorkManager's delay bookkeeping may use wall-clock time, so a clock jump can fire the job early or late. Check 5 suppresses a negative age (clock moved back) and an age over 24 hours (clock moved forward, or the job was held). A timezone change alone does not move the epoch clock and has no effect. |
| Tap while inside a different list, or in Settings | `popUpTo(SHOPPING_LIST)` pops that screen; the back stack becomes catalog → target. |
| Tap with `startAtWelcome` true | Extras ignored. Cannot happen with a live notification (sign-out cancels the job and evicts the list, so check 6 fails), stated so nobody has to reason about it. |

## Telemetry

Two events through `TelemetryContext`: `reminder_posted` and `reminder_tapped`.
No list or item names, consistent with screen views stripping the
user-authored list name.

`reminder_posted` carries one parameter, `delay_bucket`: the elapsed time from
`armedAt` to the post, bucketed as `3-4h`, `4-6h`, `6-12h`, `12-24h`. Posts
and taps on their own say whether people engage; the bucket says whether a
reminder that was ignored arrived when designed or hours late under Doze,
which is the difference between a product timing problem and a delivery
problem. The bucket is coarse enough to identify nobody.

## Privacy and release

- New manifest permission: `android.permission.POST_NOTIFICATIONS`.
- **No new data is collected, stored off-device, or transmitted.** The
  notification is built on the device from the list already in Room and is
  shown by the OS on that device only. No server, SDK or third party is
  involved in scheduling or delivery.
- The two telemetry events (`reminder_posted`, `reminder_tapped`) carry no
  list, item or user content, only the event name and the existing build
  context, and go through the same consented telemetry pipeline as screen
  views. No new data type is added to the Play Data safety form.
- The privacy policy gets one sentence under the existing on-device section
  stating that the app can show reminder notifications built from the lists
  on your device, that they can be turned off in Settings, and that nothing
  about them is sent anywhere.
- Locked lists never put item names in a notification. The list name appears,
  as it already does on the home screen card for a locked list.
- Notification content is visible on the lock screen according to the user's
  own system settings for the app; the channel does not override lock-screen
  visibility.

## Testing

The coverage gate is 100% on logic, so WorkManager and Android touch points
are kept thin and decisions live in plain classes.

### Unit and Robolectric (`app/src/test`)

- **`ReminderSchedulerTest`** against a fake suspend `WorkEnqueuer` whose
  completion the test controls, a fake `ReminderEligibility`, and a fake
  `ReminderPrefs` whose `commit…` calls can be made to fail:
  - Arming: tick on a partly-shopped list arms with list id, 3-hour delay and
    generation 1; a second tick on the same list re-enqueues with generation 2;
    a tick on another partly-shopped list retargets with generation 3; a tick
    on a list that is not partly shopped (last item ticked, one-item list)
    does not arm and leaves an existing pending record for another list
    untouched (D4).
  - Resetting: edit with nothing pending does not arm; edit on the pending list
    re-enqueues with a new generation; edit on a different list changes
    nothing.
  - Gate: `enabled` false, flag off, and OS notifications disabled each block
    arming and cancel the pending job, clearing the record.
    `onFlagsLoaded()` with the flag off cancels and clears; with it on, does
    nothing.
  - Durable ordering: the fake enqueuer asserts the record was committed
    before it was called. A failing `commit()` results in no enqueue, a
    non-fatal, and no record.
  - Asynchronous ordering: with the first enqueue's completion held, a second
    tick must not reach the enqueuer until the first completes; after release,
    the enqueuer saw exactly two calls in order and the record matches the
    second.
  - Concurrency: 50 interleaved ticks and edits from several coroutines leave
    exactly one enqueued job whose generation equals the committed record's.
  - `finish`: with a matching record, runs the post and clears; with a
    mismatched list id, generation, or `pending == null` (counter unchanged),
    neither posts nor clears.
  - **Stale worker at the post boundary**, deterministic: the post lambda
    suspends on a latch; while it is suspended, a retarget to list B is
    launched and must be blocked on the lock; release the latch; assert the
    post ran once for A, then B's record is written and B's job enqueued. And
    the mirror case: retarget first, then `finish` for A's generation posts
    nothing and B's record survives.
  - Prompt: `shouldOfferPermission(listId)` covers each of its conditions, the
    snooze boundary at exactly 14 days, false when the list is not partly
    shopped, and the permanently-refused derivation in all four combinations
    of `permissionRequested` and the rationale flag. `onPermissionGranted`
    arms when the list is still partly shopped and does nothing when it no
    longer is. `onPermissionDenied` records the request and snoozes.
- **`ReminderWorkerTest`** with `TestListenableWorkerBuilder`, an in-memory
  Room database (the pattern the sync DAO tests use) and the real scheduler
  over fakes:
  - Posts when partly shopped and ownership holds, then the record is cleared.
  - Silent on each of the seven fire-time checks, including: `pending == null`
    with the counter still equal to the worker's generation (check 1); a
    negative age and an age of 24h + 1s (check 5); a retarget performed from
    the content reader hook so it lands between check 6 and check 7 (nothing
    posted, B's record intact).
  - Partly shopped where the only ticked item was written through the DAO
    directly (simulating a partner tick): still posts, proving D3's fire-time
    rule.
  - Lock state: list locked before the snapshot withholds names; a snapshot
    with `isLocked = true` and no PIN still withholds names.
  - `CancellationException` from the content reader propagates out of
    `doWork` and is not reported as a non-fatal.
  - Any other DAO failure reports a non-fatal and returns success. Foreground
    state and clock are injected.
- **`ReminderContentReaderTest`** (Room in-memory): returns null for a missing
  or tombstoned list; excludes tombstoned items from both counts and names;
  names are in list order; a list row updated to locked between two snapshots
  is reported locked in the second.
- **`ReminderNotifierTest`** (Robolectric, `ShadowNotificationManager`):
  - Copy: one item, two items, three or more with the "and N more" tail, the
    locked-list variant.
  - Replacement: posting for list A then list B leaves exactly one active
    notification, carrying B's title and B's extras.
  - PendingIntent: `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, fixed request code,
    target `MainActivity`, flags `NEW_TASK | SINGLE_TOP | CLEAR_TOP`, extras
    present and correct after a replacement.
  - Channel creation is idempotent across two posts.
- **`ShoppingItemRepositoryTest`**, extended with a fake `ReminderKicker`: tick
  calls `onLocalPickUp`; untick, add, edit, delete and restore call
  `onLocalEdit`; list-level edits in `ShoppingListRepository` call neither;
  the sync `apply` path calls neither.
- **`ShoppingItemsViewModelTest`**, extended: prompt state follows the
  scheduler after a tick; no prompt after ticking the last item; grant calls
  `onPermissionGranted` with the current list; not-now snoozes.
- **`SettingsViewModelTest`**, extended: switch reads on only with preference
  AND permission; toggle off clears the preference; toggle on with permission
  requests it and sets the preference only on grant; permanently-refused
  subtitle state; preference true with permission revoked reads off.
- **`ReminderIntentParserTest`**: extras present, extras missing,
  `startAtWelcome` suppressing navigation.
- **`BgtNavGraphTest`** (new; Robolectric Compose with a
  `TestNavHostController`, the first test of the graph itself): with a
  `pendingList` the graph ends on the items route for that id with the
  catalog directly beneath it; the same when the graph was already showing a
  different list's items screen or Settings (that entry is popped); a
  rotation after navigation does not navigate twice; a second `pendingList`
  value after the first was consumed navigates again.
- **`MainActivityTest`** (Robolectric): `onNewIntent` with reminder extras
  updates the pending-list flow; `onNewIntent` without them leaves it null.

### End-to-end (`app/src/androidTest`)

One test in three acts, so the real WorkManager, channel and nav graph meet:

1. Tick one of two items, drive the enqueued job with
   `TestDriver.setInitialDelayMet`, assert one notification via
   `NotificationManager.activeNotifications` with the list's title.
2. Tick an item on a second list, drive again, assert still exactly one active
   notification and that it now names the second list.
3. Fire the notification's content intent, assert the items screen for the
   second list is showing, press Back, assert the list catalog is showing.

### Deliberately not tested

Doze timing, OEM battery behaviour, and the system permission dialog. None
reproduce in a test; the telemetry events are how these show up in the field.

## Out of scope

- Partner-driven nudges while this phone's app is dead (needs a backend, D1).
- Dedup across two phones on one account.
- Repeat reminders (D5).
- Reminders for lists with no ticks at all (D2).
- Checkable item actions inside the notification (product decision 001,
  alternative D, still deferred).

## Accepted trade-offs

- A reminder can arrive later than three hours under Doze, up to the 24-hour
  cap.
- A tick followed by process death before enqueue loses that session's nudge.
- The feature flag is a cold-start kill switch, not a live one.
- The worker does not verify that the specific item ticked locally is still
  ticked; a list that is partly shopped for any reason at fire time, after a
  local tick armed it, gets the nudge.
- The generation counter in prefs grows forever. At one increment per tick it
  cannot overflow a `Long` in any realistic lifetime.
- The feature flag cancels pending work on the next cold start after the
  fetch completes. A job that fires before that fetch, or on a device that
  never cold-starts the app again, may post once after the flag was turned
  off. A live flag or a worker-side flag read would need the cache to be
  persisted, which is out of scope.
- The lock guarantees atomicity within the app process only. WorkManager runs
  workers in the app process, so that is sufficient; a future multi-process
  configuration would need a different guard.
- Each arm does one synchronous `commit()` on the IO dispatcher. The UI never
  waits on it, but on a slow disk a burst of ticks serialises behind it. At
  one record per tick this is well under a millisecond each in practice.
