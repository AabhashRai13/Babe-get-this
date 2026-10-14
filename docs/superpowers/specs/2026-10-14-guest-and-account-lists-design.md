# Guest and account lists — design

**Date:** 2026-10-14 · **Status:** awaiting review · **Branch:** `feat/guest-and-account-lists`
**Supersedes in part:** [technical decision 004](../../technical-decisions/004-logout-semantics-for-shared-lists.md) (the "local-only lists" column)

## Summary

The app gets two clear modes, chosen on a new welcome screen:

- **Guest** — lists live only on this phone, exactly as local lists do today.
- **Signed in** — every list belongs to the account. It is stored on the server,
  with a full copy on the phone so it can be read and edited with no signal
  (the in-store case), and it appears on any phone the user signs in on.

Mechanically, every list a signed-in user owns works the way a shared list
already does: a Room replica that syncs through `SyncEngine`, evicted on
explicit sign-out and rediscovered on sign-in. "Shared" stops meaning "synced"
and starts meaning "has a share code".

## Why

This came out of the **Got This** feature (a per-person record of what you
have already bought, with "you already bought this" warnings and "running
low" reminders). Got This is personal history, and today's model breaks it:

1. Lists that aren't shared belong to the *phone*. Anyone using it — signed
   out, or signed in as any account — sees and ticks the same lists, so
   purchases would land in the wrong person's history, and a guest would see
   the account owner's history in a warning.
2. Got This must be per person, not per shared list: sharing a list does not
   mean sharing a home (a flatmate, a friend, a coworker for one trip).

Got This itself is **out of scope** here. This change only fixes who owns a
list, which Got This needs first.

## Decisions

Recorded so they can't be lost again. Each one says what was rejected.

| # | Decision | Rejected alternative, and why |
|---|---|---|
| D1 | **Signed-in lists belong to the account and sync; guest lists belong to the device and never leave it.** | Keep today's "local lists belong to the phone" model — breaks Got This (see Why). |
| D2 | **Local-first with sync, not online-first.** Screens read and write Room; `SyncEngine` pushes and pulls in the background. | Online-first (screens wait on the server, Room as a read cache) — adding or ticking items with no signal in a store would fail, which is the case the user said must work. |
| D3 | **When a guest signs in, their guest lists move into the account** (uploaded). | Keep them on the phone, visible only when signed out — two separate sets of lists that swap on sign-in is confusing, and lists would seem to vanish. |
| D4 | **Existing signed-in users' local lists move to their account on update**, with a one-time notice. The privacy policy and Play Data safety form are updated **before** release. | Ask each user first — anyone answering "no" would have lists hidden whenever signed in, because signed-in users only see account lists. |
| D5 | **Got This is per person, not shared between list members.** (Recorded here for the Got This spec; not built in this change.) | Household-level Got This — list members are not necessarily one household. |
| D6 | **A lost session keeps the account's lists visible and editable**; only an explicit sign-out removes them. New `AuthState.SessionExpired(userId)`. | Treat session loss like sign-out — lists would vanish from view on a technicality (token expiry), which decision 004 already ruled out. |
| D7 | **A server-side list no longer needs a share code.** `lists.share_code` becomes nullable and write-once. | Generate a share code for every list at creation — every private list would become joinable by anyone who guesses a 6-character code. |
| D8 | **Background catch-up becomes account-wide** (a few requests per sync, not two per list). | Keep per-list catch-up for every list — fine for a handful of shared lists, but once every list syncs a user with 50 lists would send 100 sequential requests on every app open. |

## What users see

### First launch: welcome screen

A new **Welcome** screen is the start destination when the user is signed out
and hasn't chosen yet. It has two buttons:

- **Sign in** → the existing Login screen (Register, Google sign-in and
  forgot-password are reachable from there, as today). Back returns to Welcome.
- **Continue as guest** → the lists screen. Copy under the button makes the
  trade clear: guest lists stay on this phone only; signing in saves them to an
  account, enables sharing and voice.

Rules:

- Shown when `AuthState` is `Unauthenticated` and the `welcome_done` flag is false.
- `welcome_done` is set by either button (on sign-in, set on success), and
  cleared by explicit sign-out and by account deletion.
- **Existing installs never see it on update.** If `welcome_done` has never
  been written, it defaults to `true` when the app was installed before this
  version (`firstInstallTime != lastUpdateTime`) and `false` on a fresh install.
- Guests can still sign in later from the profile sheet, as today.

### Guest

Unchanged from today's signed-out experience: instant local lists, no network
needed. Sharing, joining and voice still prompt for sign-in
(`AuthPromptDialog`), as today. A guest sees **only guest lists**.

### Signed in

- Sees **only lists that belong to the signed-in account**: lists they created,
  lists moved in from guest mode, and lists they joined by code.
- Every create, edit, tick and delete is written to Room first and pushed in
  the background, so all of it works with no signal.
- Signing in on another phone brings all the account's lists to it.

### Signing in (guest → account)

1. All guest lists on the phone move into the account and are queued for upload.
2. Lists belonging to any **other** account still on the phone (only possible
   after a lost session, see below) are removed from the phone.
3. Sync runs; the account's existing lists arrive.
4. If at least one guest list moved, a one-time dialog says so:
   *"Your N lists are now saved to your account, so they're backed up and show
   up on any phone you sign in on."* with a link to the privacy policy.

### Updating the app (existing users)

- **Signed in:** treated exactly like a guest signing in — their local lists
  move into the account, and they get the same one-time dialog. Their shared
  lists are already on the server and just stay.
- **Signed out:** they are guests; nothing moves. No welcome screen.

### Session lost (token expired or revoked)

- The account's lists stay on screen and stay editable; changes queue up.
- A non-blocking banner on the lists screen: *"You've been signed out. Sign in
  again to keep your lists in sync."* with a **Sign in** button.
- Signing back in with the **same** account pushes the queued changes.
- Signing in with a **different** account removes the old account's lists from
  the phone (any changes that never synced are lost — see trade-offs).
- Sharing, joining and voice prompt for sign-in, as when signed out.

### Explicit sign-out

1. Try to push any unsynced changes.
2. If changes are still unsynced (offline, or the push failed), warn first:
   *"Some changes haven't been saved to your account yet. If you sign out now,
   they'll be lost."* — **Cancel** / **Sign out anyway**.
3. Remove all account lists from the phone, clear sync points.
4. Sign out, clear `welcome_done`, go to the Welcome screen with the back
   stack cleared.

Today an offline sign-out silently loses unsynced edits to shared lists, which
004 accepted as rare. Once every list is an account list it isn't rare, hence
the warning.

### Account deletion

Same as sign-out, plus the server deletes every list where the user was the
**only member** (and its items). Lists other people joined stay with them, as
today. The confirmation dialog copy already warns that deletion is permanent;
it gains one line: lists you haven't shared are deleted from our server.

## Data model

### Room (version 4 → 5, additive)

`shopping_lists` gains two columns:

| Column | Type | Meaning |
|---|---|---|
| `accountId` | `TEXT NULL` | Account this list belongs to. `NULL` = guest list on this device. |
| `onServer` | `INTEGER NOT NULL DEFAULT 0` | The list row exists on the server. Decides INSERT vs upsert on push (see Sync). |

Migration: add both columns; `UPDATE shopping_lists SET onServer = 1 WHERE
shareCode IS NOT NULL` (existing shared replicas are already on the server).
`accountId` stays `NULL` for every row; ownership is assigned at runtime by the
claim step (below), because the migration can't read the signed-in user id.

`shareCode` keeps its meaning: non-null = shared. It **no longer** means
"synced". Every `shareCode != null` check that means "goes through sync" moves
to `accountId != null`: the item and list repositories (pending flags, soft vs
hard delete, undo), `SyncEngine.push`, and eviction. Checks that really mean
"shared" stay on `shareCode`: never auto-deleting an empty list someone else
may be filling, the realtime subscription, and the share dialog.

The list-screen query gains a filter:

- signed in or session expired as user `U`: `accountId = U`
- guest: `accountId IS NULL AND shareCode IS NULL`

The guest filter excludes `shareCode IS NOT NULL` because of orphaned shared
replicas from before this update (see Sync → Claim).

Creating a list (typed or voice) sets `accountId` to the data owner — `NULL`
for a guest — and, for account lists, `pendingSync = 1`.

### Auth state

```kotlin
sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data class SessionExpired(val userId: String) : AuthState()   // new
    data object Unauthenticated : AuthState()
}
```

- `AuthStateManager.initialize()`: token and user id → `Authenticated`;
  user id but no token → `SessionExpired`; otherwise `Unauthenticated`.
- New `AuthStateManager.expireSession()`: clears the token only and moves to
  `SessionExpired(userId)`. No-op when no user id is stored, so it can never
  resurrect an explicitly signed-out user. Called where `logout()` is called
  today for session loss: `BabeGetThisApp` (`SessionStatus.NotAuthenticated`)
  and `AuthAuthenticator` (401).
- `logout()` keeps clearing everything. Explicit sign-out therefore always ends
  `Unauthenticated` whichever order `logout()` and the Supabase
  `NotAuthenticated` event arrive in.
- Server features (sync, share, join, voice) require `Authenticated`, as today.
  List ownership uses the "data owner": the user id of `Authenticated` or
  `SessionExpired`, else none.

### Device prefs

- `welcome_done: Boolean` (plain prefs — not a secret).
- `pending_adoption_notice: Int` — set by the claim step, cleared when the
  dialog is dismissed, so the notice survives process death.
- `account_lists_migrated: Boolean` — the one-time first-run claim (Sync → Claim).
- `locked_list_ids: Set<String>` — see Locks.

## Sync

### Claim on sign-in (new: `SyncEngine.claimDeviceLists(userId)`)

Runs on every transition to `Authenticated(userId)`, including app start when
already signed in (`SyncTrigger` already observes this), before the sync kick,
in one Room transaction:

1. Delete lists whose `accountId` is non-null and not `userId` (another
   account's replicas, possible after a lost session) and their sync points.
2. Claim guest lists (`accountId IS NULL AND shareCode IS NULL`): set
   `accountId = userId`, and `pendingSync = 1` on the list and its live items.
3. **First run after this update only** (`account_lists_migrated` pref not
   set): also claim `accountId IS NULL AND shareCode IS NOT NULL` — an
   already-signed-in user's existing shared replicas — so they stay visible
   offline from the very first launch. Their `pendingSync` is left as is
   (they're already on the server). Set the flag; it is also set when the
   first launch after the update is signed out.
4. Return how many lists step 2 claimed → `pending_adoption_notice`.

Idempotent: running it twice claims nothing the second time.

**Orphaned shared replicas.** A user whose session was lost *before* this
update can have shared replicas with no known owner. They're hidden from the
guest view, never shown to an account that hasn't proved membership, claimed by
`apply()` when discovery returns them for the signed-in account (the server
answering is the membership proof), and deleted by the next explicit sign-out.

### Push

`push()` sends every `pendingSync` list with `accountId == currentUserId`
(previously: every pending list with a share code), lists before items as today.

- `onServer = 0` → `remote.insertList` (a plain INSERT is required for a
  brand-new row; the existing RLS note explains why an upsert fails). A `false`
  return means the row already exists, which counts as success. Then set
  `onServer = 1`.
- `onServer = 1` → `remote.upsertLists`, as today.

Items: only pending items whose list belongs to the current account. Today
every pending item is sent in one batch, so a single row the server rejects
(e.g. from an orphaned replica) would fail the whole batch, every time.

### Account-wide catch-up (replaces `catchUpAllShared`)

On every sync kick:

1. **Discovery:** `remote.fetchAllLists()` as today (every list row the account
   can see; one row per list, no items) → apply. Lists not in Room before this
   step are **new to this phone** (joined elsewhere, or a new phone).
2. **New lists:** full item pull for each (`fetchItems(listId, null)`).
3. **Known lists:** items changed since one account-wide sync point, filtered
   to the account's list ids in chunks of 50
   (`items?list_id=in.(…)&updated_at=gte.…`), served by the existing
   `items (list_id, updated_at)` index. Advance the account sync point only
   after a fully successful pass.

Discovery has to fetch all list rows rather than "changed since": joining a
list adds a membership row but doesn't touch the list's `updated_at`, so a
since-filter would never discover it.

`apply()` sets `accountId = currentUserId` and `onServer = 1` on rows it
inserts, and on existing rows whose `accountId` is `NULL` — even when the local
row is dirty and the incoming data is otherwise skipped (that's the orphan
claim).

Per-list `catchUp(listId)` stays for an open list: all account lists catch up
once when opened (another phone may have changed them), and shared lists keep
the realtime subscription. Solo lists don't subscribe, to save a socket per
open list.

### Sharing an account list

`ShareRepository.share(listId)`:

1. Code already set → return it (unchanged).
2. `onServer = 0` → push first. If it fails: "Couldn't share — check your
   connection."
3. Upsert the row with a freshly generated code, then read the row back and
   adopt whatever code the server holds (another phone may have shared it
   first; the write-once trigger keeps the first code).

Today's separate item upload in `share()` goes away: the push in step 2
uploads the items along with the list.

Join is unchanged apart from `apply()` assigning `accountId`.

### Eviction

`evictSharedReplicas()` becomes `evictAccountLists()`: best-effort push, then
delete every list with `accountId IS NOT NULL OR shareCode IS NOT NULL` (the
second half removes orphaned replicas), clear all sync points. Called
from explicit sign-out and account deletion only, as today. The sign-out
warning (above) needs a `hasUnsyncedChanges(): Boolean` check after the push.

### Locks

`isLocked` stays device-only and never syncs. Today a local list keeps its
lock forever. After this change, sign-out evicts the list and sign-in brings it
back **unlocked** — a privacy regression for anyone who locked a list. Fix:
`setLocked` also records the id in `locked_list_ids`, and `apply()` sets
`isLocked = true` when it inserts a list whose id is in the set. `unlockAll`
clears the set.

## Server (Supabase migration `003_account_lists.sql`)

1. `alter table lists alter column share_code drop not null;` (the unique
   constraint already allows multiple nulls).
2. `protect_list_immutables()`: `new.share_code := coalesce(old.share_code,
   new.share_code)` — a code can be set once, never changed or removed.
   `created_by` handling unchanged.
3. `delete_user()`: before deleting the user, delete lists where the caller
   is the only member (items cascade). **`delete_user` was created in the
   Supabase dashboard and isn't in the repo** — its current SQL is needed
   so the migration preserves everything else it does, and so it finally
   lives in version control.
4. Negative tests appended, in the style of 001: a non-member can't read a
   solo list; a share code can't be changed once set; `delete_user` leaves
   lists that other members still have.

Compatibility: old app versions keep working against the new schema (they
always send a code). The new app against the old schema fails to push unshared
lists (they stay queued), so **the migration ships before the app**.

## Privacy and release

Before the app release:

1. Apply migration 003 on staging, run the two-device test, then apply on prod.
2. Rewrite the storage sections of `docs/publishing/privacy-policy.md` (and
   the copy on babegetthis.com): signed-in users' lists are stored on our
   server so they sync across devices; guest lists never leave the phone;
   deleting an account deletes the lists nobody else has. Today's text
   promises "if you never share, nothing about your lists leaves your phone"
   — that becomes untrue for signed-in users.
3. Update the Play Console Data safety form: shopping list content is
   collected (for app functionality, linked to the account, deletable).
4. Write `docs/technical-decisions/008-account-owned-lists.md` (D1–D8 above,
   in the 004 house style) and mark 004 as superseded in part.

Telemetry: one new event, `welcome_choice` (`guest` | `sign_in`), so the
guest-vs-account split is measurable, following the rules in decision 007.

## Error handling and edge cases

- **Push fails:** rows stay `pendingSync`; next kick retries (unchanged model).
- **Claim interrupted:** it runs in one transaction, so it either applies fully or not at all.
- **INSERT returns "already exists":** treated as success (a previous push
  reached the server but the app died before recording `onServer`).
- **Share while offline:** `onServer = 0` → push fails → clear error, no code shown.
- **Old app version on a second phone:** discovers the account's solo lists
  and treats them as local; edits made in the old version don't sync back.
  Accepted (see trade-offs).

## Testing

Following the existing coverage gate and test style (in-memory Room,
`SyncFakes`, `MainDispatcherRule`):

- **Migration 4 → 5** (`MigrationTest`, androidTest): columns added; shared
  rows get `onServer = 1`; data intact.
- **SyncEngine:** claim (guest lists adopted, other accounts' lists evicted,
  first-run claim of shared replicas only once, notice count, idempotent);
  orphan claimed by `apply()` only when discovery returns it, including when
  the local row is dirty; items push skips other accounts' items; push INSERT vs
  upsert and the "already exists" path; account-wide catch-up, including a list
  joined on another phone (`updated_at` older than the sync point) and chunking;
  `apply()` assigning `accountId`; lock re-application; eviction by account.
- **Repositories:** routing by `accountId` (account soft-delete vs guest hard
  delete, undo, empty-list cleanup with shared lists never auto-deleted);
  query filters for guest / account / session-expired.
- **AuthStateManager:** `SessionExpired` from `initialize()` and
  `expireSession()`; `expireSession()` no-op after `logout()`; both orderings
  of explicit sign-out end `Unauthenticated`.
- **ShareRepository:** sharing an `onServer = 1` list sets the code; adopts
  the server's code on a race; offline share fails cleanly.
- **UI / ViewModels:** Welcome shown on fresh install, not on update, again
  after sign-out; back from Login returns to Welcome; sign-out warning only
  with unsynced changes; session-expired banner; adoption dialog shown once.
- **Manual, two devices on staging** (as in `REALTIME_TEST_FINDINGS.md`):
  guest → sign in → lists appear on the second phone; offline edits in airplane
  mode sync on reconnect; sign-out and sign back in; share a solo list; delete
  account and check the server rows are gone.

## Out of scope

- Got This (needs this change, plus "who picked this up" on shared lists —
  [tradeoff 001](../../tradeoff/001-no-attribution-on-shared-list-edits.md)).
- Leaving a shared list, rotating share codes.
- Background sync with WorkManager (sync still runs on foreground and connectivity).

## Accepted trade-offs

- Signing out with unsynced changes loses them — the user is warned and chooses.
- Session lost + a **different** account signs in → the old account's unsynced
  changes are lost, without a warning.
- Guest lists move into whichever account signs in on that phone (a couple
  sharing one phone: the first to sign in gets them).
- Guest lists are device-only: uninstalling or clearing data loses them, as today.
- Mixed app versions across one account's phones: edits from an old version
  don't sync back until it updates.
