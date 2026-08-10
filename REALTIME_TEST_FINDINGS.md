# Realtime list sharing — device test findings (2026-08-08)

Devices: emulator-5554 (user `aabhash`) + Focus_5G 320346148123 (user `contact`), staging flavor.

## BUG 1 (critical, data loss) — inbound list-row sync CASCADE-deletes all local items

**Root cause:** `SyncEngine.apply()` (core/sync/data/repository/SyncEngine.kt:80) calls
`listDao.insertList(row.toEntity(local))`, and `insertList` is
`@Insert(onConflict = OnConflictStrategy.REPLACE)` (ShoppingListDao.kt:37).
SQLite `INSERT OR REPLACE` = DELETE + INSERT, which fires
`shopping_items.listId ... ON DELETE CASCADE` and hard-deletes every item of that list.

**Trigger:** any inbound change to a shared list ROW (rename is the easy one) applied on a
receiving device — via realtime (list screen open) or catch-up (on list open / foreground).

**Effect:** the device loses ALL items of that shared list. Hard delete, not tombstone, so the
rows are not recoverable by catch-up (the sync point high-water mark is already past their
`updated_at`) and not by re-join (BUG 2). The Postgres rows survive, but no client path
re-fetches them.

**This hits every member, including the author of the rename.** The authoring device is
protected only while its row is dirty (`shouldSkip` returns true on `localDirty`); once its push
completes and the server echoes the row back, it applies its own change and wipes itself too.
Observed: `CascadeRenamed` (DVTV65) went to 0 items on BOTH devices. Net effect — renaming a
shared list destroys all of its items for everyone, permanently.

**Repro A (observed live):** phone renamed shared list `BQ52HQ` -> emulator (list screen open)
went from 10 items to 0. Restart + catch-up did not restore them; phone still had 11 rows.

**Repro B (clean, deterministic, reverse direction):**
1. emulator: create `CascadeTest`, add Apples/Butter/Coffee, Share live -> code `DVTV65`
2. phone: join -> both sides have the 3 items
3. emulator: long-press list -> rename to `CascadeRenamed`
4. phone: open the list (triggers catch-up)
5. -> phone list name updates to `CascadeRenamed`, phone items = [] (all 3 hard-deleted)

**Fix (one line, root cause):** in `apply()`, never REPLACE an existing list row.
`local` is already fetched there:

```kotlin
val entity = row.toEntity(local)
if (local == null) listDao.insertList(entity) else listDao.updateList(entity)
```

`@Update` issues an UPDATE, so no delete, no cascade. Applies to both realtime and catch-up
since they share the one apply path.

Note: `insertListWithItems` has the same REPLACE, but re-inserts the items inside the same
transaction, so that path is safe. Re-joining does NOT recover the lost items — see BUG 2.

## Observations (minor, not bugs)

- **O1** Share dialog "Copy" gives no visible confirmation (no snackbar/toast). Unclear whether the tap registered.
- **O2** Join dialog keeps a stale "That code didn't match any list." error visible while the user types a new code; it only clears on the next Join attempt.
- **O3** Items screen title is captured at navigation time — after a synced rename, the app bar still shows the old list name until the screen is reopened.
- **O4** Lists overview shows stale item counts for shared lists (it does not subscribe; by design per design.md open question, v1 default).
- **O5** When another member deletes a shared list while you are inside it, the screen falls back to the celebratory "List created! / Add your first item" empty state instead of navigating out or saying the list is gone. Backing out then shows it correctly removed.

## BUG 2 (medium) — re-joining an already-joined list does not refresh the replica

> **FIXED 2026-08-11** — `join()` now calls `SyncEngine.fullCatchUp(listId)`, which drops
> the stored sync point before catching up, so a re-join always does a full pull. Unit
> tests: `fullCatchUp drops the high-water mark…` (SyncEngineTest) and `re-join refreshes
> the replica…` (ShareRepositoryTest). Re-entering the code is now the working recovery
> path the report asked for.

Spec (`specs/list-sharing/spec.md`, "Joining a list twice"): *"the join is idempotent — no
duplicate membership, **the existing replica is refreshed**."*

`ShareRepository.join()` (ShareRepository.kt:62) pulls via `syncEngine.catchUp(listId)`, which
starts from the **stored sync point**. On a re-join that high-water mark is already current, so
the fetch returns nothing and the replica is left exactly as-is. The comment at line 57 assumes
"no stored sync point", which only holds for a first join.

**Observed:** phone (0 items after BUG 1) re-joined `DVTV65` -> no duplicate list row (idempotent
part ✅), but items stayed `[]`. Re-join is the natural user recovery from BUG 1 and it silently
no-ops.

**Fix:** make join force a full pull — drop the sync point first, e.g. `syncPoints.set(listId, null)`
/ a `catchUp(listId, since = null)` overload — so re-join re-fetches every row.

## Not tested (needs you)

- **Signed-out paths (tasks 8.1 / 8.2).** Deliberately skipped: signing out either device is not
  reversible for me (no credentials), and the previous session was blocked for exactly that reason.
  Task **8.4 is confirmed by code read** though — see below.
- Voice capture (per your instruction; everything was typed).

## 8.4 confirmed (static)

`core/auth/ui/AuthPromptDialog.kt` hardcodes "Sign in to use voice" / "Create an account to capture
shopping lists by voice", but it is the prompt for **share** (ShoppingItemsScreen.kt:472) and **join**
(ShoppingListScreen.kt:403) as well as voice. Meanwhile `share_auth_title` ("Sign in to share") and
`share_auth_body` exist in strings.xml and are referenced nowhere. So a signed-out user tapping
"Share live" or "Join a shared list" is told to sign in "to use voice".

## Scenarios that PASSED

| # | Scenario | Result |
|---|---|---|
| 1 | Share list (emulator) -> code shown | ✅ `BQ52HQ` |
| 2 | Share code is static across dialog reopens | ✅ same code |
| 3 | Share from the phone side too | ✅ `885HHX`, `DUTGCQ` |
| 4 | Invalid code -> inline error, nothing stored | ✅ "That code didn't match any list." |
| 5 | Lowercase code normalised to uppercase | ✅ |
| 6 | Join by code -> list + items replicate | ✅ both directions |
| 7 | Live add, phone -> emulator (both screens open) | ✅ < 5s |
| 8 | Live add, emulator -> phone | ✅ 4.7s |
| 9 | Tick / untick propagation both ways | ✅ ~2.2s / 2.5s |
| 10 | Edit name + note + shop propagation | ✅ 2.1s |
| 11 | Item delete -> tombstone, disappears remotely | ✅ 2.4s, row kept with `deletedAt` |
| 12 | Undo delete after tombstone already reached peer | ✅ tombstone cleared on both |
| 13 | Offline edits queue (`pendingSync=1`), UI instant | ✅ |
| 14 | Offline device isolated (peer sees nothing) | ✅ |
| 15 | Reconnect -> queue flushes, both converge | ✅ ~14s, flags cleared |
| 16 | Conflict: same item edited on both, one offline | ✅ LWW, later-to-server won |
| 17 | Full replica diff after reconnect | ✅ byte-identical (incl. tombstones, server `updatedAt`) |
| 18 | Catch-up on reopening a closed list screen | ✅ |
| 19 | Catch-up on app foreground (backgrounded) | ✅ |
| 20 | Catch-up after process death (force-stop) | ✅ add + tick + delete all recovered |
| 21 | Socket drop/resubscribe (wifi off->on, screen open) | ✅ converged |
| 22 | Concurrent simultaneous adds, 3 per device | ✅ all 8 items on both, no dupes/losses |
| 23 | PIN lock never syncs | ✅ emulator `isLocked=1`, phone `0`, peer ungated, row not dirtied |
| 24 | Whole-list soft delete propagates | ✅ instant, tombstone kept, list gone from peer |
| 25 | Re-join idempotent (no duplicate membership/row) | ✅ (but no refresh — BUG 2) |
| 26 | Category travels opaquely and renders | ✅ `cat-baby-kids` both sides |
| 27 | Local-only lists never sync | ✅ `shareCode NULL`, `pendingSync 0`, absent from peer |
| 28 | Room v3 migration applied on both devices | ✅ `shareCode`/`deletedAt`/`pendingSync` present |
| 29 | No crashes / ANRs across the whole session | ✅ clean logcat |
| 30 | No rows stuck `pendingSync=1` at end | ✅ 0 on both devices |

Note on categories: the 12 default categories are seeded with identical static ids
(`cat-dairy-eggs`, ...) on both devices, so the "unknown id -> uncategorised" fallback only ever
applies to user-created categories.

---

# Performance measurements (2026-08-09)

Emulator = `sdk_gphone16k_arm64`, phone = Focus_5G. **All device numbers below are from the
`stagingDebug` build** — see caveat at the end.

## Sync latency (the number that matters for this feature)

End-to-end: tap a checkbox on one device -> the change is committed in the other device's Room DB.
Measured by polling the receiver's DB (probe cost 0.12s emulator / 0.31s phone, so true latency is
slightly lower than shown).

| Direction | n | median | min | max |
|---|---|---|---|---|
| phone -> emulator | 6 | **0.68s** | 0.40s | 0.82s |
| emulator -> phone | 4 | **0.89s** | 0.57s | 0.92s |

- **Initial replication on join:** 10 items landed in **<= 0.4s**.
- **Push ack** (local write -> `pendingSync` cleared, i.e. the upload leg alone): median ~0.65s;
  3 of 5 trials completed faster than the 0.12s probe could even observe the dirty flag.

Realtime sync is comfortably sub-second in both directions.

## App startup

| | cold (3 runs) | warm |
|---|---|---|
| Emulator | 969 / 1001 / 980 ms | 52 ms |
| Phone | 2233 / 2309 / 2274 ms | 84 ms |

Warm start is excellent. Phone cold start ~2.3s is the one number worth re-checking on release.

## Frame rendering during list scroll (10-item list, 6 up/down swipes)

| | frames | janky | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| Emulator | 153 | 42.5% | 19ms | 32ms | 34ms | 61ms |
| Phone | 341 | 24.9% | 8ms | 65ms | 73ms | 89ms |

Phone also logged 36 missed vsyncs and 84 slow-UI-thread frames. p90 of 65ms is well over the
16.7ms budget for 60fps, i.e. visible stutter — but a debug build is the wrong place to conclude
that from (below).

## Memory / storage

- TOTAL PSS: emulator **144 MB**, phone **207 MB** (debug build; Compose tooling inflates this).
- Room DB: **44–52 KB** for 9 lists / 26–37 items. Negligible.
- Tombstones: 3–4 rows retained and never purged — by design for v1, but they only grow.

## CAVEAT — these are debug-build numbers

`debuggable=true` disables ART optimisations and Compose runs with extra debug checks, so
**startup, jank and memory are all materially worse than what users get**. Sync latency is
network-bound and essentially unaffected, so those numbers stand.

I tried to measure a `prodRelease` build (different applicationId -> installs side-by-side, no
risk to the staging login or data), but the install was blocked by the permission classifier.
Re-running startup + gfxinfo on that APK is what would turn the startup and jank figures into
real ones. Note `isMinifyEnabled = false` on release, so R8 shrinking is not in play either way.

---

# Pre-closed-testing drill (2026-08-11)

Fresh `stagingDebug` build installed on both devices. Accounts: emulator `TestUser`,
phone `contact` (two distinct accounts — better coverage than before).

## Gates

| Gate | Result |
|---|---|
| Unit tests (`testStagingDebugUnitTest`) | PASS |
| Instrumented on emulator (`connectedStagingDebugAndroidTest`) | **27/27, 0 failures** |
| — includes `migrate1To2_*` and `migrate2To3_*` | **closes task 3.4** (never run on a device before) |
| Release bundle (`bundleProdRelease`) | builds, signed (cert valid to 2053), 14.2 MB |
| `lintProdRelease` | PASS |
| Crashes / ANRs across the drill | none |

## End-to-end, two devices — 32/32

Share+join (2), live add/tick/untick/edit/delete+tombstone (7), rename regression across
realtime + self-echo + catch-up (7), offline queue + conflict LWW + reconnect (6),
catch-up on reopen/foreground/process-death + concurrent writes + byte-identical replica diff
+ sync hygiene + whole-list delete (10).

Sync latency re-measured: **median 0.82s** phone -> emulator.

## Signed-out surfaces — finally testable (tasks 8.1 / 8.2 / 8.4)

The emulator was signed out (see note below), which allowed the checks that were blocked all along:

- PASS — signed-out user can still create/use local lists
- PASS — share is auth-gated; **no share code generated, `shareCode NULL`, row not dirty, nothing uploaded**
- PASS — join is auth-gated
- PASS — app survives background/foreground/restart signed out, no crashes, no errors in logcat
- PASS — a list created while signed out survives signing in
- **FAIL — task 8.4 confirmed on device:** tapping *Share live* OR *Join a shared list* while signed
  out shows **"Sign in to use voice" / "Create an account to capture shopping lists by voice."**
  `share_auth_title` ("Sign in to share") and `share_auth_body` exist in strings.xml, unused.
  This is the one user-visible defect closed testers will hit.

## Backend status

Railway recovered. `POST /transcribe` -> `401 {"error":"Unauthorized"}` in 1.2s (correctly
auth-gated); `GET /` -> Express "Cannot GET /". Voice-to-text should work again; the earlier
failure was the platform outage, not app code. Voice itself was not re-tested (excluded).

## Note on emulator data

`connectedStagingDebugAndroidTest` uninstalls the app under test when it finishes, which wiped
the emulator's session and lists. Data was reseeded and the account re-signed-in as `TestUser`.
Run instrumented tests on a throwaway device/AVD, not one holding state you care about.
