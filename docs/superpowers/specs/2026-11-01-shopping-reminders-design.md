# Shopping reminders — design

**Date:** 2026-11-01 · **Status:** awaiting review · **Branch:** `feat/guest-and-account-lists` (spec only; implementation gets its own branch)

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
| D4 | **One pending reminder at a time, for the most recently ticked list.** | One per list — a user shopping two lists in one trip would get two notifications about the same outing. |
| D5 | **Once per shopping session.** After a nudge the list is left alone until the next tick on it, which starts a fresh session. | Daily until done — a half-shopped list that sits for a week is often intentional, and repeat nudges drive uninstalls. |
| D6 | **Permission is asked in context**: after the first tick that would arm a reminder, via an in-app sheet before the system dialog. | At onboarding — the user does not yet know why they would want it, and Android stops showing the dialog after two refusals. Settings-only — near-zero adoption. |
| D7 | **WorkManager, not AlarmManager.** | Exact alarms need a special permission that Android 13 denies by default to non-alarm apps; inexact alarms die on reboot without a boot receiver and have no replace-pending semantic. |
| D8 | **Kill switch through the existing feature flag cache**, read at arm time in the UI process. | Reading the flag inside the worker — the cache is empty on a background cold start, so the worker would always see "off". |
| D9 | **Doubt means silence.** Every check at fire time that cannot be satisfied suppresses the nudge; the worker never retries. | Retry on failure — a reminder that arrives twice or late is worse than one that is dropped. |
| D10 | **Every enqueue carries a generation number**, and a worker acts only if its generation is the one currently recorded. A stale worker neither posts nor clears state. | Trusting `REPLACE` alone — it cancels a running worker only cooperatively, leaving a window where an old worker could post for a list that was retargeted, or wipe the record of a newer job. |
| D11 | **Prefs are written before the job is enqueued**, under one lock, and the worker's generation check is what reconciles the two stores. | Two-phase bookkeeping or a start-up reconciliation pass — more code for a failure that is already harmless (see Consistency). |
| D12 | **The preference is user intent; the permission is OS state. Both are required to arm and both are re-checked in the worker.** | Treating the toggle as the only switch — a user who revokes notifications in system settings would still see the switch on and get nothing. |

## What users see

### The first partly-shopped tick (Android 13 and up, permission not granted)

The tick lands as today. Then a Material 3 modal bottom sheet appears:

> **Forget something?**
> Get one nudge if you leave items unticked after a shop.
> [Remind me] [Not now]

- **Remind me** launches the system notification permission dialog. On grant,
  the list the user just ticked is armed, so the first-ever session gets its
  reminder too. On deny, nothing else happens.
- **Not now**, or dismissing the sheet by swipe, snoozes the offer for 14 days
  on this device.

The sheet appears only when the list the user just ticked is **genuinely
partly shopped**: after the tick, at least one live item is picked up and at
least one is not. Ticking the last item never prompts, and nor does ticking
the first item on a one-item list.

The sheet never appears when: the device is below Android 13 (no runtime
permission exists; reminders simply work), permission is already granted, the
Settings toggle is off, the offer is snoozed, or the system has permanently
refused (two denials).

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
(`isLocked` on the row and a PIN exists) item names are withheld: "3 items are
still unticked". The list name still shows, matching what the home screen card
already reveals for locked lists.

The notification auto-cancels on tap and uses a fixed id, so a later nudge for
another list replaces rather than stacks. Its content `PendingIntent` uses a
fixed request code with `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, so the extras
(list id and name) are refreshed each time rather than the first list's extras
being reused for every later nudge.

### Tapping the reminder

Lands on the items screen for that list, with Back returning to the list
catalog. A locked list shows its PIN gate first, exactly as it does today when
opened from the catalog.

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
- If the system has permanently refused, the switch stays off, the subtitle
  becomes "Allow notifications in system settings to turn this on", and
  tapping the row opens the app's system notification settings page.
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
| `ReminderScheduler` | Implements the kicker. Checks the gate (`enabled` preference, feature flag, notification permission, list eligibility) and enqueues a unique one-time request named `shopping_reminder` with a 3-hour initial delay and `REPLACE` policy, carrying the list id and a generation number. `onLocalEdit` re-enqueues only if a job for that list is pending. Answers `shouldOfferPermission(listId)` for the ViewModel, arms on `onPermissionGranted(listId)` after re-checking eligibility, and cancels on toggle-off, flag-off and sign-out. Every mutation runs under one `Mutex`. | `WorkEnqueuer`, `ReminderPrefs`, `ReminderEligibility`, `FeatureFlagCache`, `NotificationManagerCompat`, `AuthStateManager` |
| `ReminderEligibility` | One suspend function, `isPartlyShopped(listId)`: the list exists with `deletedAt` null and, among live items, at least one is picked up and at least one is not. Backed by a single DAO count query. Shared by the scheduler (arm and prompt) and the worker (fire), so "partly shopped" has one definition. | `ShoppingListDao`, `ShoppingItemDao` |
| `WorkEnqueuer` | Interface over the two WorkManager calls used (enqueue unique, cancel unique) so the scheduler is unit-testable without WorkManager. | `WorkManager` (production impl) |
| `ReminderWorker` | `@HiltWorker` `CoroutineWorker`. Reads its list id and generation from input data. Acts only if its generation matches `pendingGeneration` in prefs; a stale worker exits without posting and without touching prefs. Otherwise re-validates (preference, permission, eligibility, foreground, age), posts through the notifier or stays silent, clears the pending record, and always returns `success`. | `ReminderEligibility`, `ReminderPrefs`, `ReminderNotifier`, `CrashReporter`, injected foreground-state and clock providers |
| `ReminderNotifier` | Creates the channel idempotently, formats the copy, builds the content intent, posts. Holds no decisions. | `NotificationManagerCompat`, string resources |
| `ReminderPrefs` | SharedPreferences, same pattern as `PrefsSyncPointStore`. Fields: `enabled` (default true), `pendingListId`, `pendingGeneration` (monotonic `Long`, default 0), `pendingArmedAt`, `promptSnoozedUntil`. | `Context` |
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
clears, so a rotation does not re-navigate. The content intent uses
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
2. Scheduler, under its lock: passes the gate (preference, flag, permission,
   `isPartlyShopped(A)`), increments `pendingGeneration` to *g*, writes
   `pendingListId = A`, `pendingArmedAt = now` and *g* to prefs, **then**
   enqueues the job for A with input `(A, g)` using `REPLACE`.
3. Further ticks or edits on A within three hours repeat step 2 with a new
   generation, resetting the clock. A tick on list B retargets the single job
   to B, also with a new generation.
4. Partner changes arrive through `SyncEngine.apply`, which never touches the
   kicker.
5. Three hours after the last local activity the worker for *(A, g)* runs. If
   *g* is not the recorded `pendingGeneration` it exits at once, touching
   nothing. Otherwise it re-validates, posts or stays silent, and clears the
   pending record.

## Consistency between WorkManager and prefs

Two stores, one writer order, one reconciler.

- **Writer order.** Prefs first, then enqueue, both under the scheduler's
  `Mutex`. Rapid rescheduling therefore serialises: each call sees the previous
  call's prefs and enqueues after it, so the generation in the last enqueued
  job always equals the generation in prefs.
- **Process death after prefs, before enqueue.** Prefs claim a pending job that
  does not exist. Harmless: no notification can fire, and the next tick or
  edit on that list overwrites the record and enqueues normally. `onLocalEdit`
  believing a job is pending and "re-enqueueing" simply creates it.
- **Process death after enqueue, before prefs.** Cannot happen; prefs are
  written first.
- **Job exists, prefs cleared or changed.** The worker's generation does not
  match, so it exits without posting or clearing. This is also what protects
  against `REPLACE`'s cooperative cancellation window: a worker already past
  its Room reads when a newer job is enqueued will fail the generation check
  before posting, because the check is the last thing before `notify()`.
- **Clearing.** Only a worker whose generation matched clears
  `pendingListId`, `pendingArmedAt` and the generation's "pending" status (the
  counter itself is never reset, so it stays monotonic across sessions).
  Cancel paths (toggle off, flag off, sign-out) clear the same fields under the
  lock and cancel the unique work.
- **No start-up reconciliation.** Every inconsistency above resolves to "no
  notification" or "self-heals on the next tick", so a scan on app start would
  add code without changing any outcome.

## Fire-time checks (worker)

In this order; all must hold or the worker exits silently:

1. **Generation matches** `pendingGeneration` in prefs. A mismatch means a newer
   job superseded this one; exit without posting and without clearing (D10).
2. The `enabled` preference is true (D12; covers a toggle-off that raced the
   job).
3. Notifications are allowed by the OS (`areNotificationsEnabled()`).
4. `ReminderEligibility.isPartlyShopped(listId)`: the list exists with
   `deletedAt` null, and among live items at least one is picked up and at
   least one is not, regardless of who wrote those states (D3).
5. The app is not in the foreground (`ProcessLifecycleOwner`, injected).
6. The job was armed less than 24 hours ago. Aggressive OEM battery killers can
   hold a job for a day; a nudge the next afternoon about yesterday's shop is
   noise.
7. **Generation still matches**, re-read immediately before `notify()`. This
   closes the window between the Room reads and the post.

## Error handling and edge cases

| Case | Behaviour |
|---|---|
| List deleted or tombstoned before firing | Check 4 fails; silent. |
| User finishes the list | Check 4 fails (zero unticked); silent. No explicit cancel on the last tick: the worker's check is the single source of truth. |
| User unticks everything | Check 4 fails (zero ticked); silent. |
| User ticks milk, unticks milk, ticks eggs | Still partly shopped at fire time; the nudge fires. The worker does not care which item was ticked (D3). |
| Partner finishes the list on their phone | Sync writes Room; check 4 fails at fire time. Partner activity never arms or resets, but final Room state wins. |
| Partner ticks items, user ticks none | Nothing was ever armed on this phone. No nudge here; the partner's phone handles its own. |
| Stale worker: user retargets from A to B while A's worker is running | A's worker fails check 1 or 7 and exits without posting or clearing. B's record and job are untouched. |
| Stale worker: toggle off while the worker is running | Cancel path cleared the record under the lock; check 1 fails. If the worker was already past check 1, check 2 or 7 catches it. |
| Sign-out | Account lists are evicted. Scheduler also cancels the job and clears the record on the `AuthState` signed-out transition; check 4 covers a missed cancel. |
| Toggle off or flag off | Scheduler cancels the pending job and clears the record; the gate blocks new ones. Flag flips take effect on next cold start, like every flag. |
| Permission revoked after arming | Check 3 fails; silent. The `enabled` preference is not changed; the Settings switch reads off because it shows preference AND permission. |
| App in the foreground at fire time | Check 5 fails; silent, no reschedule. The next tick is a fresh session. |
| Doze delay | Fires late, sometimes by an hour or more. Copy says "still unticked", not "three hours ago", so late reads fine. Capped by check 6. |
| Exception in the worker | Caught, reported as a non-fatal through `CrashReporter`, pending record cleared only if the generation matched, `success` returned so WorkManager never retries. |
| Two ticks in quick succession | Serialised by the scheduler's lock, each with its own generation; `REPLACE` on one unique name means the last job wins and the only job left carries the recorded generation. |
| Process death between Room write and kicker call | Tick is saved, nothing was armed. The next tick arms. A lost nudge is never data loss. |
| Process death between prefs write and enqueue | Prefs claim a job that does not exist. No notification can fire; the next tick or edit on that list writes a new record and enqueues. See Consistency. |
| Reboot | WorkManager persists the job and its remaining delay. |
| Clock or timezone change | WorkManager delays are elapsed-time based. |
| Tap with `startAtWelcome` true | Extras ignored. Cannot happen with a live notification (sign-out cancels the job and evicts the list, so check 4 fails), stated so nobody has to reason about it. |

## Telemetry

Two events through `TelemetryContext`: `reminder_posted` and `reminder_tapped`.
No list or item names, consistent with screen views stripping the
user-authored list name. These answer whether three hours is the right delay.

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

- **`ReminderSchedulerTest`** against a fake `WorkEnqueuer`, a fake
  `ReminderEligibility` and in-memory prefs:
  - Arming: tick on a partly-shopped list arms with list id, 3-hour delay and
    generation 1; a second tick on the same list re-enqueues with generation 2;
    a tick on another list retargets with generation 3; a tick on a list that
    is not partly shopped (last item ticked, one-item list) does not arm.
  - Resetting: edit with nothing pending does not arm; edit on the pending list
    re-enqueues with a new generation; edit on a different list changes
    nothing.
  - Gate: `enabled` false, flag off, and permission missing on API 33 each
    block arming and cancel the pending job, clearing the record.
  - Ordering: prefs are written before the fake enqueuer is called (the fake
    asserts the prefs state at enqueue time).
  - Concurrency: 50 interleaved ticks and edits from several coroutines leave
    exactly one enqueued job whose generation equals the recorded one.
  - Prompt: `shouldOfferPermission(listId)` covers each of its five conditions,
    the snooze boundary at exactly 14 days, and false when the list is not
    partly shopped. `onPermissionGranted(listId)` arms when the list is still
    partly shopped and does nothing when it no longer is.
- **`ReminderWorkerTest`** with `TestListenableWorkerBuilder` and an in-memory
  Room database (the pattern the sync DAO tests use):
  - Posts when partly shopped and the generation matches, then clears the
    record.
  - Silent on each of the seven fire-time checks, including a stale generation
    at check 1 (record untouched afterwards) and a generation that changes
    between check 1 and check 7 (simulated by a prefs write from the injected
    notifier hook; record untouched, nothing posted).
  - Partly shopped where the only ticked item was written through the DAO
    directly (simulating a partner tick): still posts, proving D3's fire-time
    rule.
  - `enabled` false at fire time: silent.
  - DAO failure reports a non-fatal and returns success. Foreground state and
    clock are injected.
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
  `pendingList` the graph ends on the items
  route for that id with the catalog beneath it on the back stack; a
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
