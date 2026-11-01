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
| D3 | **Only ticks made on this phone arm a reminder.** Synced-in partner changes neither arm nor reset. | Counting partner ticks — if the partner is shopping, nudging this phone is wrong. The partner's phone gets its own nudge. |
| D4 | **One pending reminder at a time, for the most recently ticked list.** | One per list — a user shopping two lists in one trip would get two notifications about the same outing. |
| D5 | **Once per shopping session.** After a nudge the list is left alone until the next tick on it, which starts a fresh session. | Daily until done — a half-shopped list that sits for a week is often intentional, and repeat nudges drive uninstalls. |
| D6 | **Permission is asked in context**: after the first tick that would arm a reminder, via an in-app sheet before the system dialog. | At onboarding — the user does not yet know why they would want it, and Android stops showing the dialog after two refusals. Settings-only — near-zero adoption. |
| D7 | **WorkManager, not AlarmManager.** | Exact alarms need a special permission that Android 13 denies by default to non-alarm apps; inexact alarms die on reboot without a boot receiver and have no replace-pending semantic. |
| D8 | **Kill switch through the existing feature flag cache**, read at arm time in the UI process. | Reading the flag inside the worker — the cache is empty on a background cold start, so the worker would always see "off". |
| D9 | **Doubt means silence.** Every check at fire time that cannot be satisfied suppresses the nudge; the worker never retries. | Retry on failure — a reminder that arrives twice or late is worse than one that is dropped. |

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

The sheet never appears when: the device is below Android 13 (no runtime
permission exists; reminders simply work), permission is already granted, the
Settings toggle is off, the offer is snoozed, or the system has permanently
refused (two denials).

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
another list replaces rather than stacks.

### Tapping the reminder

Lands on the items screen for that list, with Back returning to the list
catalog. A locked list shows its PIN gate first, exactly as it does today when
opened from the catalog.

### Settings

A new **Reminders** section with one row and a switch:

> **Shopping reminders**
> A nudge when you leave items unticked after a shop

- Off cancels the pending job and stops arming.
- On, with permission missing on Android 13 and up, launches the system dialog
  directly (no sheet).
- If the system has permanently refused, the switch stays off, the subtitle
  becomes "Allow notifications in system settings to turn this on", and
  tapping the row opens the app's system notification settings page.

The toggle defaults to on.

All copy lives in `strings.xml` from the start.

## Components

All new code lives in `core/reminder`, wired by a `ReminderModule` in
`core/reminder/di`, mirroring `core/sync`.

| Unit | Does | Depends on |
|---|---|---|
| `ReminderKicker` | `fun interface` with `onLocalPickUp(listId)` and `onLocalEdit(listId)`. Called by `ShoppingItemRepository` after local writes. Sits next to `SyncKicker`; the repository never learns WorkManager exists. | nothing |
| `ReminderScheduler` | Implements the kicker. Checks the gate (toggle, feature flag, notification permission) and enqueues a unique one-time request named `shopping_reminder` with a 3-hour initial delay and `REPLACE` policy, carrying the list id. `onLocalEdit` re-enqueues only if a job for that list is pending. Also answers `shouldOfferPermission()` for the ViewModel and cancels on toggle-off, flag-off and sign-out. | `WorkEnqueuer`, `ReminderPrefs`, `FeatureFlagCache`, `NotificationManagerCompat`, `AuthStateManager` |
| `WorkEnqueuer` | Interface over the two WorkManager calls used (enqueue unique, cancel unique) so the scheduler is unit-testable without WorkManager. | `WorkManager` (production impl) |
| `ReminderWorker` | `@HiltWorker`. At fire time re-validates against Room and the process state, posts through the notifier or stays silent, clears the pending record, always returns `success`. | `ShoppingListDao`, `ShoppingItemDao`, `ReminderNotifier`, `ReminderPrefs`, `CrashReporter`, injected foreground-state and clock providers |
| `ReminderNotifier` | Creates the channel idempotently, formats the copy, builds the content intent, posts. Holds no decisions. | `NotificationManagerCompat`, string resources |
| `ReminderPrefs` | SharedPreferences, same pattern as `PrefsSyncPointStore`. Fields: `enabled` (default true), `pendingListId`, `pendingArmedAt`, `promptSnoozedUntil`. | `Context` |
| `ReminderIntentParser` | Pure Kotlin. Reads the list id and name extras from an `Intent`, returns null when absent or when `startAtWelcome` is true. | nothing |

### Repository hook

`ShoppingItemRepository.togglePickedUp` calls `onLocalPickUp(listId)` when the
new state is picked up and `onLocalEdit(listId)` when it is unticked. `addItem`,
`updateItem`, `deleteItem` and `restoreItem` call `onLocalEdit(listId)`.
`SyncEngine.apply` writes DAOs directly and is untouched, which is what makes
D3 hold.

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
a successful tick it asks `scheduler.shouldOfferPermission()`. The screen shows
the sheet and owns the permission launcher; grant calls
`scheduler.onPermissionGranted(listId)`, which arms; deny or dismiss calls
`scheduler.snoozePrompt()`.

### Dependencies

Added to the app: `work-runtime-ktx`, `hilt-work` and its compiler,
`work-testing` (test only), and `lifecycle-process` (already in the catalog,
not yet applied). The `SyncTrigger` comment about "no WorkManager in v1" is
updated to say reminders use it and sync does not.

## Data flow

1. User ticks an item on list A. Repository writes Room, then calls
   `onLocalPickUp(A)`.
2. Scheduler passes the gate, enqueues the job for A replacing any pending job,
   records A and the arm time in prefs.
3. Further ticks or edits on A within three hours re-enqueue, resetting the
   clock. A tick on list B retargets the single job to B.
4. Partner changes arrive through `SyncEngine.apply`, which never touches the
   kicker.
5. Three hours after the last local activity the worker runs, re-validates, and
   posts or stays silent. Either way it clears the pending record.

## Fire-time checks (worker)

All must hold or the worker exits silently:

1. The list exists and `deletedAt` is null.
2. At least one live item is picked up and at least one is not.
3. The app is not in the foreground (`ProcessLifecycleOwner`, injected).
4. Notifications are still enabled (`areNotificationsEnabled()`).
5. The job was armed less than 24 hours ago. Aggressive OEM battery killers can
   hold a job for a day; a nudge the next afternoon about yesterday's shop is
   noise.

## Error handling and edge cases

| Case | Behaviour |
|---|---|
| List deleted or tombstoned before firing | Check 1 fails; silent. |
| User finishes the list | Check 2 fails (zero unticked); silent. No explicit cancel on the last tick: the worker's check is the single source of truth. |
| User unticks everything | Check 2 fails (zero ticked); silent. |
| Partner finishes the list on their phone | Sync writes Room; check 2 fails at fire time. Partner activity never arms or resets, but final Room state wins. |
| Sign-out | Account lists are evicted. Scheduler also cancels the job on the `AuthState` signed-out transition as tidy-up; check 1 covers a missed cancel. |
| Toggle off or flag off | Scheduler cancels the pending job; the gate blocks new ones. Flag flips take effect on next cold start, like every flag. |
| Permission revoked after arming | Check 4 fails; silent. The Settings toggle is not changed. |
| App in the foreground at fire time | Check 3 fails; silent, no reschedule. The next tick is a fresh session. |
| Doze delay | Fires late, sometimes by an hour or more. Copy says "still unticked", not "three hours ago", so late reads fine. Capped by check 5. |
| Exception in the worker | Caught, reported as a non-fatal through `CrashReporter`, pending record cleared, `success` returned so WorkManager never retries. |
| Two ticks in quick succession | `REPLACE` on one unique name; WorkManager serialises, last wins. |
| Process death between Room write and enqueue | Tick is saved, job is lost. The next tick re-arms. A lost nudge is never data loss. |
| Reboot | WorkManager persists the job and its remaining delay. |
| Clock or timezone change | WorkManager delays are elapsed-time based. |
| Tap with `startAtWelcome` true | Extras ignored. Cannot happen with a live notification (sign-out evicts the list and check 1 fails), stated so nobody has to reason about it. |

## Telemetry

Two events through `TelemetryContext`: `reminder_posted` and `reminder_tapped`.
No list or item names, consistent with screen views stripping the
user-authored list name. These answer whether three hours is the right delay.

## Privacy and release

- New manifest permission: `android.permission.POST_NOTIFICATIONS`.
- Nothing leaves the device. No privacy policy change is needed beyond noting
  that the app can show local notifications, which the policy's data-collection
  sections do not cover because no data is collected.
- Locked lists never put item names in a notification.

## Testing

The coverage gate is 100% on logic, so WorkManager and Android touch points
are kept thin and decisions live in plain classes.

### Unit and Robolectric (`app/src/test`)

- **`ReminderSchedulerTest`** against a fake `WorkEnqueuer`: tick arms with list
  id and 3-hour delay; second tick on the same list replaces; tick on another
  list retargets; edit with nothing pending does not arm; edit with a pending
  job re-arms the same list; toggle off, flag off, and permission missing on
  API 33 each block arming and cancel the pending job; `shouldOfferPermission`
  covers each of its five conditions and the snooze boundary at exactly 14
  days.
- **`ReminderWorkerTest`** with `TestListenableWorkerBuilder` and an in-memory
  Room database (the pattern the sync DAO tests use): posts when partly
  shopped; silent on each of the five fire-time checks; DAO failure reports a
  non-fatal and returns success. Foreground state and clock are injected.
- **`ReminderNotifierTest`**: one item, two items, three or more with the "and
  N more" tail, the locked-list variant, the content intent extras and the
  fixed notification id.
- **`ShoppingItemRepositoryTest`**, extended with a fake `ReminderKicker`: tick
  calls `onLocalPickUp`; untick, add, edit, delete and restore call
  `onLocalEdit`; the sync `apply` path calls neither.
- **`ShoppingItemsViewModelTest`**, extended: prompt state follows the
  scheduler after a tick; grant arms the current list; not-now snoozes.
- **`SettingsViewModelTest`**, extended: toggle on and off; permanently-refused
  subtitle state.
- **`ReminderIntentParserTest`**: extras present, extras missing,
  `startAtWelcome` suppressing navigation.

### End-to-end (`app/src/androidTest`)

One test: tick one of two items, drive the enqueued job with
`TestDriver.setInitialDelayMet`, assert the notification via
`NotificationManager.activeNotifications`. Then launch the activity with the
parsed intent and assert the items screen for that list is showing.

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
