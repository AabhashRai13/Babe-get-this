# Guest and Account Lists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Signed-in users' lists become account data that syncs (and works offline); guest lists stay on the device; a Welcome screen offers "Sign in" or "Continue as guest".

**Architecture:** Every list owned by an account goes through the existing shared-list machinery (`SyncEngine`: Room replica, `pendingSync` outbox, last-write-wins). A new Room column `accountId` decides ownership (`NULL` = guest) and replaces `shareCode != null` as the "goes through sync" test; `shareCode` now only means "shared". A new `AuthState.SessionExpired` keeps the account's lists visible after a lost session.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Room 2.8, Hilt, supabase-kt 3.0 (Postgrest, Realtime), JUnit4 + Robolectric + MockK, Kover.

**Spec:** `docs/superpowers/specs/2026-10-14-guest-and-account-lists-design.md` — read it first; this plan cites its decision numbers (D1–D8).

## Global Constraints

- Coverage gate is **100% line coverage** on the logic layer (`**.ui.viewModels.*`, `**.ui.*ViewModel`, `**.data.repository.*`, `**.data.mapper.*`, `core.auth.data.*`, …): `./gradlew koverVerifyDevDebug`. Every new branch in those packages needs a test. Never widen the Kover exclude list; extract logic into a gated package instead.
- Android Lint is a blocking gate (`warningsAsErrors = true`): no hardcoded UI strings — all copy goes in `app/src/main/res/values/strings.xml`; counts use `<plurals>`.
- Formatting: `./gradlew spotlessApply` before every commit.
- No new dependencies (Kotlin is pinned to 2.0.21 by Supabase 3.0.x).
- Room `exportSchema = false`: every schema change is an additive `Migration` covered by `androidTest/.../MigrationTest.kt`.
- Test names: backticked sentences in `src/test`; camelCase in `src/androidTest` (minSdk 24 dexing).
- Telemetry parameters are bounded enums only (decision 007); events live in `AnalyticsEvent.kt` and map in `AnalyticsEventMapper.kt`.
- Supabase migration 003 is applied (staging, then prod) **before** the app ships; it must stay compatible with old app versions.
- User-facing copy is fixed by this plan (strings tables in Tasks 10–12); use it verbatim.
- Comments follow the codebase's existing style: explain *why*, reference the decision doc (`008`) where a rule comes from.

## Review Focus

1. **Signing in from the Welcome screen** — `Routes.SHOPPING_LIST` is not on the back stack, so today's `popBackStack(SHOPPING_LIST)` on login success/back returns `false` and strands the user on Login. Expected: success lands on the lists screen with Welcome removed; back returns to Welcome. (Task 10, journey test.)
2. **Editing while the session is expired, offline** — the user is in a store with an expired token. Expected: new lists and items get `accountId` = the data owner and `pendingSync = 1`, and push once they sign in again. (Task 3 + Task 4 tests.)
3. **First launch after the update with no signal** — an already-signed-in user's lists must be visible immediately (claim is local; it must not wait for discovery), including shared ones. (Task 6 test.)
4. **Undo after deleting an unshared account list** — the list is tombstoned (not hard-deleted) and undo clears the tombstone and re-pushes. It must neither re-insert from the domain model (that drops `accountId`) nor use Room's REPLACE insert (that cascade-deletes the items). (Task 4 test.)
5. **Account-wide catch-up at the chunk boundary** — 51 known lists must produce two item requests (50 + 1) and apply both. (Task 7 test.)

---

### Task 1: Supabase migration 003 — share code optional and write-once

**Files:**
- Create: `supabase/migrations/003_account_lists.sql`

**Interfaces:**
- Produces: server accepts `lists` rows with `share_code = null`; a set code can never change or be removed.

- [ ] **Step 1: Write the migration**

```sql
-- Account-owned lists (docs/technical-decisions/008, spec 2026-10-14).
-- Every signed-in user's list is now stored server-side; only SHARED lists
-- carry a code. Run AFTER 002, staging first. Compatible with old app
-- versions: they always send a code.

-- 1. Unshared lists have no code. UNIQUE already permits many NULLs.
alter table public.lists
  alter column share_code drop not null;

-- 2. Write-once code: NULL -> code is allowed exactly once (sharing a list
--    that already syncs); a set code can never change or be cleared, even by
--    a client pushing a stale row with share_code = null.
create or replace function public.protect_list_immutables()
returns trigger
language plpgsql
as $$
begin
  if new.created_by is not null then
    new.created_by := old.created_by;
  end if;
  new.share_code := coalesce(old.share_code, new.share_code);
  return new;
end;
$$;

-- 3. Negative tests (run manually in the SQL editor):
--   As user A: insert into lists (id, name, created_by) values (gen_random_uuid(), 'solo', auth.uid());
--     -> succeeds, share_code null.
--   As user B (not a member): select * from lists where id = '<A's solo list>';  -> zero rows.
--   As A: update lists set share_code = 'ABCDEF' where id = '<solo>';  -> code set.
--   As A: update lists set share_code = null where id = '<solo>';      -> code still 'ABCDEF'.
--   As A: update lists set share_code = 'ZZZZZZ' where id = '<solo>';  -> code still 'ABCDEF'.
```

- [ ] **Step 2: Hand off for staging** — the agent cannot reach Supabase. Ask the user to run the file in the **staging** SQL editor and the five negative checks; do not mark this task done until they confirm.

- [ ] **Step 3: Commit**

```bash
git add supabase/migrations/003_account_lists.sql
git commit -m "feat(db): let lists sync without a share code"
```

---

### Task 2: Room schema v5 — `accountId` and `onServer`

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/data/local/model/ShoppingListEntity.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/data/local/Migrations.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/data/local/AppDatabase.kt` (version 5)
- Modify: `app/src/main/java/com/babegetthis/android/core/data/di/DatabaseModule.kt:44`
- Test: `app/src/androidTest/java/com/babegetthis/android/core/data/local/MigrationTest.kt`

**Interfaces:**
- Produces: `ShoppingListEntity.accountId: String? = null` and `ShoppingListEntity.onServer: Boolean = false` (declared after `pendingSync`); `val MIGRATION_4_5: Migration`.

- [ ] **Step 1: Write the failing migration test** (camelCase, follow the existing `migrate3To4` shape: build a v4 table by hand, run the migration, read back)

```kotlin
@Test
fun migrate4To5AddsOwnershipColumnsAndMarksSharedListsOnServer() {
    // v4 rows: one local-only list, one shared replica.
    // after MIGRATION_4_5:
    assertEquals(null, row("local").accountId)
    assertEquals(0, row("local").onServer)
    assertEquals(null, row("shared").accountId)
    assertEquals(1, row("shared").onServer)
    assertEquals("Groceries", row("local").name) // data intact
}
```

- [ ] **Step 2: Run it to verify it fails** (needs the `Medium_Phone_API_37.0` emulator running)

Run: `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.babegetthis.android.core.data.local.MigrationTest`
Expected: FAIL — `MIGRATION_4_5` unresolved.

- [ ] **Step 3: Implement** — add the two entity fields with comments (accountId: owner, `NULL` = guest list on this device; onServer: row exists server-side, decides INSERT vs upsert); `MIGRATION_4_5` runs `ALTER TABLE shopping_lists ADD COLUMN accountId TEXT`, `ALTER TABLE shopping_lists ADD COLUMN onServer INTEGER NOT NULL DEFAULT 0`, `UPDATE shopping_lists SET onServer = 1 WHERE shareCode IS NOT NULL`; bump `version = 5`; register in `addMigrations(...)`. Comment on the migration: `accountId` stays NULL here because the signed-in user id lives in encrypted prefs — ownership is assigned at runtime by `SyncEngine.claimDeviceLists`.

- [ ] **Step 4: Run it to verify it passes** — same command. Expected: PASS. Also `./gradlew testDevDebugUnitTest` stays green.

- [ ] **Step 5: Commit** — `git commit -m "feat(db): add list ownership columns (v5)"`

---

### Task 3: `AuthState.SessionExpired` and the data owner

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/model/AuthState.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/data/AuthStateManager.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/auth/data/TokenManager.kt`
- Modify: `app/src/main/java/com/babegetthis/android/BabeGetThisApp.kt:79` and `app/src/main/java/com/babegetthis/android/core/data/network/AuthAuthenticator.kt:24` (`logout()` → `expireSession()`)
- Test: `app/src/test/java/com/babegetthis/android/core/auth/data/AuthStateManagerTest.kt`

**Interfaces:**
- Produces:
  - `data class AuthState.SessionExpired(val userId: String) : AuthState()`
  - `AuthStateManager.expireSession()`
  - `AuthStateManager.dataOwnerId(): String?` — userId of `Authenticated` or `SessionExpired`, else null
  - `AuthStateManager.dataOwner: Flow<String?>` — same, ignoring `Loading`, `distinctUntilChanged()`
  - `TokenManager.clearToken()` — removes only `KEY_AUTH_TOKEN`

- [ ] **Step 1: Write the failing tests** (MockK relaxed `TokenManager`, as the file does today)

```kotlin
@Test fun `initialize with a user id but no token becomes SessionExpired`()
    // getToken() -> null, getUserId() -> "u1"  => SessionExpired("u1")
@Test fun `initialize with neither token nor user id becomes Unauthenticated`()
    // replaces today's "missing token" test, which now means SessionExpired
@Test fun `expireSession keeps the user id and clears only the token`()
    // after login(...,"u1",...): expireSession() => SessionExpired("u1"); verify { tokenManager.clearToken() }; verify(exactly = 0) { tokenManager.clear() }
@Test fun `expireSession is a no-op when no user id is stored`()
    // getUserId() -> null => state unchanged (Unauthenticated), clearToken not called
@Test fun `expireSession before initialize leaves state Loading`()
    // initialize() will then read token=null,userId="u1" and land in SessionExpired
@Test fun `explicit logout ends Unauthenticated whichever order the session event arrives in`()
    // order A: expireSession(); logout()  => Unauthenticated
    // order B: logout(); expireSession()  => Unauthenticated (getUserId() -> null after clear)
@Test fun `dataOwnerId is the user id while signed in or expired and null otherwise`()
@Test fun `dataOwner skips Loading and emits distinct owners`()
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "*AuthStateManagerTest*"`
Expected: FAIL — `SessionExpired`, `expireSession`, `dataOwnerId` unresolved.

- [ ] **Step 3: Implement.** `expireSession()`: read `tokenManager.getUserId()`; return if null; `tokenManager.clearToken()`; if the state isn't `Loading`, set `SessionExpired(userId)` (in `Loading`, `initialize()` derives it from storage). Update the class comment and the two call sites' comments: session loss now keeps the account's lists on screen (spec D6). Leave `AuthAuthenticator`'s early-return logic as is.

  Every `is AuthState.Authenticated` check elsewhere stays: server features (sync, share, join, voice) still need a live session. `SessionExpired` deliberately fails those checks.

- [ ] **Step 4: Run to verify they pass** — same command, then `./gradlew testDevDebugUnitTest`. Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "feat(auth): keep the account's identity when the session expires"`

---

### Task 4: List ownership in the list and item repositories

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/data/local/dao/ShoppingListDao.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/data/repository/ShoppingListRepository.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/data/repository/ShoppingItemRepository.kt`
- Test: `app/src/test/java/com/babegetthis/android/feature/shoppinglist/data/repository/ShoppingListRepositoryTest.kt`, `ShoppingListRepositorySharedTest.kt`, `DeleteListIfEmptyTest.kt`, `app/src/test/java/com/babegetthis/android/feature/shoppingitems/data/repository/ShoppingItemRepositorySharedTest.kt`
- Test helper: `app/src/test/java/com/babegetthis/android/testing/TestData.kt`

**Interfaces:**
- Consumes: Task 2 columns; Task 3 `AuthStateManager.dataOwner`, `dataOwnerId()`.
- Produces (DAO):
  - `fun getAccountListsWithItemCount(accountId: String): Flow<List<ShoppingListWithItemCount>>` — today's query plus `AND l.accountId = :accountId`
  - `fun getGuestListsWithItemCount(): Flow<List<ShoppingListWithItemCount>>` — plus `AND l.accountId IS NULL AND l.shareCode IS NULL`
  - `suspend fun softDeleteListIfEmpty(listId: String, now: Long): Int` — tombstone variant of `deleteListIfEmpty`, same atomic `NOT EXISTS` guard (live items only)
  - delete `getAllListsWithItemCount()` if no caller remains
- Produces (repositories): both gain constructor param `authStateManager: AuthStateManager`. Private `isSynced(listId)` = `getListRaw(listId)?.accountId != null` replaces `isShared` wherever the meaning is "goes through sync".
- Produces (test helper): `fun authStateFor(userId: String?, expired: Boolean = false): AuthStateManager` in `TestData.kt`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ShoppingListRepositoryTest
@Test fun `guests see only guest lists`()                     // rows: guest, account(u1), orphan(accountId null + shareCode) => only guest
@Test fun `a signed-in user sees only their account's lists`() // rows: guest, account(u1), account(u2) => only u1
@Test fun `an expired session still sees the account's lists`()
@Test fun `a guest's new list has no owner and is not queued`()          // accountId null, pendingSync false, no pushSoon
@Test fun `a signed-in user's new list is owned and queued`()            // accountId "u1", pendingSync true, pushSoon called
@Test fun `a voice list made while expired is owned and queued with its items`() // Review Focus 2
// ShoppingListRepositorySharedTest — existing "shared" fixtures get accountId = "u1"; add:
@Test fun `deleting an unshared account list tombstones it and undo clears the tombstone`() // Review Focus 4: accountId kept, deletedAt null, pendingSync true, and its 2 items still in Room
// DeleteListIfEmptyTest
@Test fun `an empty unshared account list is tombstoned, not deleted`()   // returns true, row still present with deletedAt set, pushSoon
@Test fun `a shared list is never auto-deleted`()                        // unchanged rule, now keyed on shareCode explicitly
// ShoppingItemRepositorySharedTest — fixtures get accountId; add:
@Test fun `items of an unshared account list are queued for sync`()
@Test fun `items of a guest list are not queued`()
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew testDevDebugUnitTest --tests "*ShoppingListRepository*" --tests "*DeleteListIfEmptyTest*" --tests "*ShoppingItemRepositorySharedTest*"`
Expected: FAIL (constructor mismatch / wrong lists returned).

- [ ] **Step 3: Implement.**
  - `getAllLists()` = `authStateManager.dataOwner.flatMapLatest { owner -> if (owner == null) dao.getGuestListsWithItemCount() else dao.getAccountListsWithItemCount(owner) }` mapped to domain.
  - `createList` / `createListWithItems`: entity `.copy(accountId = owner, pendingSync = owner != null)`, items likewise; `pushSoon()` when owned.
  - `deleteListIfEmpty`: `shareCode != null` → `false`; account → `softDeleteListIfEmpty(...) > 0` then `pushSoon()`; guest → today's hard delete.
  - `addItemsToList`, `updateListName`, `deleteListAndCaptureItems`, `restoreListWithItems`, and every `ShoppingItemRepository` write: switch `shareCode != null` → `isSynced`. Update their comments ("account lists", not "shared lists").
  - `restoreListWithItems`' synced path clears the tombstone with **`listDao.updateList`**. Today it calls `insertList(raw.copy(...))`, a REPLACE, which is DELETE + INSERT and cascade-deletes the list's items (`REALTIME_TEST_FINDINGS.md` BUG 1). The items-still-there assertion above pins this.
  - Keep `getShareCode(listId)` — it still drives the realtime subscription and `leavingDeletesList`.

- [ ] **Step 4: Run to verify they pass** — same command, then `./gradlew testDevDebugUnitTest koverVerifyDevDebug`. Expected: PASS, coverage 100%.

- [ ] **Step 5: Commit** — `git commit -m "feat(lists): route sync by account ownership, not share code"`

---

### Task 5: Push every account list (INSERT first time, upsert after)

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/data/model/SyncRows.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/data/mapper/SyncMappers.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/data/repository/SyncEngine.kt` (`push`)
- Modify: `ShoppingListDao.kt`, `app/src/main/java/com/babegetthis/android/feature/shoppingitems/data/local/dao/ShoppingItemDao.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/di/SyncModule.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/sync/data/repository/SyncEngineTest.kt`, `app/src/test/java/com/babegetthis/android/core/sync/data/mapper/SyncMappersTest.kt`

**Interfaces:**
- Produces:
  - `ListRow.shareCode: String? = null` (still `@SerialName("share_code")`)
  - `fun ShoppingListEntity.toRow(createdBy: String): ListRow` — no longer requires a share code
  - DAO: `suspend fun getPendingAccountLists(accountId: String): List<ShoppingListEntity>` (replaces `getPendingSyncLists` in push); `suspend fun markListOnServer(id: String)`
  - Item DAO: `suspend fun getPendingSyncItemsForAccount(accountId: String): List<ShoppingItemEntity>` — `SELECT i.* FROM shopping_items i JOIN shopping_lists l ON l.id = i.listId WHERE i.pendingSync = 1 AND l.accountId = :accountId`
  - `SyncModule`: the `currentUserId` lambda for `SyncEngine` and `ShareRepository` becomes `{ (authStateManager.authState.value as? AuthState.Authenticated)?.userId }` — an expired session must not attempt pushes.

- [ ] **Step 1: Write the failing tests** — update `SyncEngineTest`'s `listEntity(...)` helper to take `accountId: String? = "user-1"` and `onServer: Boolean = true`; existing push tests keep passing through those defaults. Add:

```kotlin
@Test fun `a list not yet on the server is inserted, then marked onServer`()
    // listEntity(onServer = false, pendingSync = true, shareCode = null)
    // => remote.insertedLists has it, upsertedLists empty, getListRaw(...).onServer == true, pendingSync false
@Test fun `an insert that finds the row already there still counts as on the server`()
    // remote.listAlreadyExists = true => onServer true, no upsert, no error
@Test fun `a list already on the server is upserted`()
@Test fun `another account's pending lists and items are not pushed`()
    // pending list + item with accountId "user-2" => nothing sent
@Test fun `guest lists are never pushed`()
// SyncMappersTest
@Test fun `an unshared list maps to a row with a null share code`()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew testDevDebugUnitTest --tests "*SyncEngineTest*" --tests "*SyncMappersTest*"`. Expected: FAIL.

- [ ] **Step 3: Implement** `push()`: for `getPendingAccountLists(userId)`, partition by `onServer`; for `!onServer` rows call `remote.insertList(row)` one at a time (`false` = already exists — success), then `markListOnServer(id)` and the guarded `markListSynced`; upsert the rest as today. Items from `getPendingSyncItemsForAccount(userId)`. Keep lists strictly before items and the breadcrumb.

- [ ] **Step 4: Run to verify they pass** — same command, then the full unit suite + `koverVerifyDevDebug`.

- [ ] **Step 5: Commit** — `git commit -m "feat(sync): push every account list, inserting new ones first"`

---

### Task 6: Claim device lists on sign-in (`ListOwnershipStore` + `onAuthState`)

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/sync/data/repository/ListOwnershipStore.kt` (interface)
- Create: `app/src/main/java/com/babegetthis/android/core/sync/data/local/PrefsListOwnershipStore.kt` (plain prefs file `list_ownership`)
- Modify: `SyncEngine.kt`, `ShoppingListDao.kt`, `app/src/main/java/com/babegetthis/android/core/sync/SyncTrigger.kt`, `SyncModule.kt`
- Modify: `app/src/test/java/com/babegetthis/android/testing/SyncFakes.kt` (add `FakeListOwnershipStore`)
- Test: Create `app/src/test/java/com/babegetthis/android/core/sync/data/repository/SyncEngineClaimTest.kt`

**Interfaces:**
- Produces:

```kotlin
interface ListOwnershipStore {
    val adoptionNotice: StateFlow<Int>          // lists moved into the account, not yet acknowledged
    fun setAdoptionNotice(count: Int)
    fun isAccountListsMigrated(): Boolean
    fun markAccountListsMigrated()
    fun lockedListIds(): Set<String>            // used by Task 7 and Task 8
    fun rememberLock(listId: String, locked: Boolean)
    fun forgetAllLocks()
}
```

  - `SyncEngine` constructor gains `ownership: ListOwnershipStore` (after `syncPoints`). Every existing `SyncEngine(...)` construction in tests (`SyncEngineTest`, `ShareRepositoryTest`) passes a `FakeListOwnershipStore()`.
  - `suspend fun SyncEngine.claimDeviceLists(userId: String): Result<Int>` — returns the number of guest lists claimed and adds it to `adoptionNotice`.
  - `suspend fun SyncEngine.onAuthState(state: AuthState): Boolean` — `Authenticated` → claim, return `true` (caller kicks); `SessionExpired` → claim, return `false`; `Unauthenticated` → `markAccountListsMigrated()`, return `false`; `Loading` → `false`.
  - DAO: `suspend fun getOtherAccountListIds(userId: String): List<String>`; `@Transaction suspend fun claimDeviceLists(userId: String, includeSharedOrphans: Boolean): Int` built from: `DELETE … WHERE accountId IS NOT NULL AND accountId != :userId`; `UPDATE shopping_items SET pendingSync = 1 WHERE deletedAt IS NULL AND listId IN (SELECT id FROM shopping_lists WHERE accountId IS NULL AND shareCode IS NULL)` (**before** the list update, or the subquery matches nothing); `UPDATE shopping_lists SET accountId = :userId, pendingSync = 1 WHERE accountId IS NULL AND shareCode IS NULL` (its row count is the return value); and, when `includeSharedOrphans`, `UPDATE shopping_lists SET accountId = :userId WHERE accountId IS NULL AND shareCode IS NOT NULL`.
  - `SyncTrigger.register`: `authStateManager.authState.collect { if (engine.onAuthState(it)) kick() }`.

  Spec clarification: claim also runs for `SessionExpired`. Without it, a user whose token expired before updating would open the new version to an empty screen, because their lists still have `accountId = NULL` while the screen filters on their id (D6).

- [ ] **Step 1: Write the failing tests** (`SyncEngineClaimTest`, Robolectric + `InMemoryDatabaseRule` + fakes, same shape as `SyncEngineTest`)

```kotlin
@Test fun `guest lists move into the account and are queued with their live items`()
    // guest list + 2 items (one tombstoned) => accountId "u1", list pendingSync, live item pendingSync, tombstoned item untouched; returns 1
@Test fun `another account's lists and their sync points are removed`()
@Test fun `the first claim after the update also claims shared replicas, offline`()   // Review Focus 3
    // ownership not migrated; shared replica accountId null, pendingSync false => accountId "u1", pendingSync still false; remote never called
@Test fun `later claims leave orphaned shared replicas alone`()
    // ownership migrated => shared replica keeps accountId null
@Test fun `claiming adds to the pending adoption notice`()              // existing notice 2 + claimed 3 => 5; claimed 0 => unchanged
@Test fun `claiming twice claims nothing the second time`()
@Test fun `onAuthState kicks only for a live session`()                 // Authenticated -> true; SessionExpired -> false but claims; Loading -> false
@Test fun `a signed-out first launch marks the migration done`()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew testDevDebugUnitTest --tests "*SyncEngineClaimTest*"`. Expected: FAIL.

- [ ] **Step 3: Implement** `claimDeviceLists`: remove sync points for `getOtherAccountListIds(userId)`; call the DAO transaction with `includeSharedOrphans = !ownership.isAccountListsMigrated()`; then `markAccountListsMigrated()`; if the count is > 0, `setAdoptionNotice(adoptionNotice.value + count)`. Breadcrumb `"sync: claimed N lists"` (count only). `PrefsListOwnershipStore` mirrors `PrefsSyncPointStore` (a `MutableStateFlow` seeded from prefs backs `adoptionNotice`). Wire both into `SyncModule`.

- [ ] **Step 4: Run to verify they pass** — same, then full suite + `koverVerifyDevDebug`.

- [ ] **Step 5: Commit** — `git commit -m "feat(sync): move guest lists into the account on sign-in"`

---

### Task 7: Account-wide catch-up and the orphan claim in `apply()`

**Files:**
- Modify: `SharedListRemote.kt`, `SupabaseSharedListRemote.kt`, `SyncEngine.kt`, `SyncMappers.kt`, `ShoppingListDao.kt`, `SyncTrigger.kt` (`kick` calls `catchUpAccount`)
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppingitems/ui/viewModels/ShoppingItemsViewModel.kt:216-225`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/data/repository/ShoppingListRepository.kt` (add `isAccountList`)
- Modify: `SyncFakes.kt` (`fetchItemsForLists` in `FakeSharedListRemote`, recording each call's id batch in `itemBatches: MutableList<List<String>>`)
- Test: Create `app/src/test/java/com/babegetthis/android/core/sync/data/repository/SyncEngineAccountCatchUpTest.kt`; modify `SyncMappersTest.kt`, `ShoppingItemsViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6 `ListOwnershipStore.lockedListIds()`.
- Produces:
  - `suspend fun SharedListRemote.fetchItemsForLists(listIds: List<String>, sinceIso: String?): List<ItemRow>` — Supabase: `filter { isIn("list_id", listIds); if (sinceIso != null) gte("updated_at", sinceIso) }`
  - `fun ListRow.toEntity(local: ShoppingListEntity?, accountId: String, lockedIds: Set<String>): ShoppingListEntity` — sets `accountId`, `onServer = true`, `isLocked = local?.isLocked ?: (id in lockedIds)`, `shareCode` from the row (nullable)
  - DAO: `suspend fun getAccountListIds(accountId: String): List<String>` (live lists; replaces `getSharedListIds`); `suspend fun claimList(id: String, accountId: String)` — `UPDATE … SET accountId = :accountId, onServer = 1 WHERE id = :id AND accountId IS NULL`
  - `suspend fun SyncEngine.catchUpAccount(): Result<Unit>` (replaces `catchUpAllShared`); `const val ACCOUNT_SYNC_POINT = "account"` (sync-point key; list ids are UUIDs, so it cannot collide)
  - `suspend fun ShoppingListRepository.isAccountList(listId: String): Boolean`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `discovery adds new lists with their full item history`()
    // remote has list L2 (unknown locally) + 3 items => all applied, accountId "user-1", onServer true
@Test fun `a list joined on another phone is discovered even when older than the sync point`()
    // account point t12; remote list L2 updated t10 with items at t10 => present after catch-up
@Test fun `known lists fetch only items changed since the account sync point`()
    // remote.lastItemSince == stored point; point advances to newest item updated_at
@Test fun `51 known lists are fetched in two batches`()                    // Review Focus 5: itemBatches sizes [50, 1], both applied
@Test fun `the sync point does not move when a batch fails`()
@Test fun `tombstoned lists found by discovery do not pull their items`()
@Test fun `an orphaned replica is claimed when discovery returns it, even if dirty`()
    // local: accountId null, shareCode set, pendingSync true => accountId "user-1"; local name kept (dirty wins)
@Test fun `a list that comes back after sign-out keeps its lock`()           // lockedIds contains id, no local row => isLocked true
@Test fun `catch-up is a no-op without a live session`()
// SyncMappersTest
@Test fun `a server row becomes an owned, on-server entity`()
// ShoppingItemsViewModelTest
@Test fun `opening an unshared account list catches up once without subscribing`()
@Test fun `opening a guest list does not touch sync`()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew testDevDebugUnitTest --tests "*SyncEngineAccountCatchUpTest*" --tests "*SyncMappersTest*" --tests "*ShoppingItemsViewModelTest*"`. Expected: FAIL.

- [ ] **Step 3: Implement `catchUpAccount()`** — the one algorithm here the tests don't fully pin:

```kotlin
val userId = currentUserId() ?: return@safeCall
val known = listDao.getAccountListIds(userId).toSet()
val discovered = remote.fetchAllLists()            // all rows, not "since": a join doesn't bump updated_at
apply(userId, discovered, emptyList())
discovered.filter { it.id !in known && it.deletedAt == null }
    .forEach { apply(userId, emptyList(), remote.fetchItems(it.id, sinceIso = null)) }
val since = syncPoints.get(ACCOUNT_SYNC_POINT)
var newest: String? = null
known.toList().chunked(50).forEach { batch ->
    val rows = remote.fetchItemsForLists(batch, since)
    apply(userId, emptyList(), rows)
    newest = (listOfNotNull(newest) + rows.mapNotNull { it.updatedAt }).maxOrNull()
}
newest?.let { syncPoints.set(ACCOUNT_SYNC_POINT, it) }   // only after every batch succeeded
```

  `apply(userId, …)`: before `shouldSkip`, if `local != null && local.accountId == null`, call `listDao.claimList(row.id, userId)` (orphan claim — the server answering is the membership proof); build entities with the new `toEntity(local, userId, ownership.lockedListIds())`. `catchUp(listId)` gets `val userId = currentUserId() ?: return@safeCall` and passes it to `apply`. Delete `catchUpAllShared`.

  Items VM: in the existing `collectLatest { shared -> … }`, add `else if (listRepository.isAccountList(listId)) syncEngine.catchUp(listId)`. Solo lists catch up once and don't subscribe to realtime.

- [ ] **Step 4: Run to verify they pass** — same, then full suite + `koverVerifyDevDebug`.

- [ ] **Step 5: Commit** — `git commit -m "feat(sync): catch up the whole account in a few requests"`

---

### Task 8: Locks survive sign-out

**Files:**
- Modify: `ShoppingListRepository.kt` (`setLocked`, `unlockAll`; constructor gains `ownership: ListOwnershipStore`)
- Test: `ShoppingListRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 6 `ListOwnershipStore.rememberLock`, `forgetAllLocks`; Task 7 re-applies locks in `apply()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `locking a list remembers it for when the list comes back`()     // fake.lockedListIds() contains id; unlock removes it
@Test fun `removing the PIN forgets every remembered lock`()               // unlockAll => fake.lockedListIds() empty
```

- [ ] **Step 2: Run** — `./gradlew testDevDebugUnitTest --tests "*ShoppingListRepositoryTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement** — call `ownership.rememberLock(listId, locked)` after the DAO write in `setLocked`, and `ownership.forgetAllLocks()` in `unlockAll`.
- [ ] **Step 4: Run** — same, then `koverVerifyDevDebug`. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(pin): keep list locks across sign-out and sign-in"`

---

### Task 9: Share a list that already syncs

**Files:**
- Modify: `app/src/main/java/com/babegetthis/android/core/sync/data/repository/ShareRepository.kt`
- Test: `app/src/test/java/com/babegetthis/android/core/sync/data/repository/ShareRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 5 `SyncEngine.push()`, `onServer`, nullable `ListRow.shareCode`, the Authenticated-only `currentUserId`.
- Produces: `suspend fun share(listId: String): Result<String>` — signature unchanged.

- [ ] **Step 1: Write the failing tests** — existing fixtures become account lists (`accountId = "user-1"`). Replace `share uploads the list and items and persists a well-formed code` and `share with no items skips the item upload` (the separate item upload goes away), and add:

```kotlin
@Test fun `sharing a list already on the server sets its code and keeps its items`()
    // onServer = true, 2 items => one upsertLists row carrying the generated code, no insertList, no upsertItems;
    // local shareCode == server's code; both items still in Room (no REPLACE cascade)
@Test fun `sharing a list not yet on the server pushes it first`()
    // onServer = false, pendingSync = true => insertList called before the code upsert; onServer true afterwards
@Test fun `sharing offline fails cleanly and shows no code`()
    // onServer = false, remote.failUpserts = true => Result.Error(AppError.NetworkError("Couldn't share — check your connection.")); shareCode still null
@Test fun `when another phone shared first, its code wins`()
    // remote.listRows returns the row with code "ZZZ999" on read-back => local shareCode "ZZZ999"
```

- [ ] **Step 2: Run** — `./gradlew testDevDebugUnitTest --tests "*ShareRepositoryTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement** — after the existing code-already-set and sign-in checks: if `!list.onServer`, run `syncEngine.push()` and re-read the row; if it's still not on the server, throw `AppErrorException(AppError.NetworkError("Couldn't share — check your connection."))`. Then `remote.upsertLists(listOf(row with the generated code))`, read it back with `remote.fetchList(listId, sinceIso = null)` and adopt the server's code (the write-once trigger from Task 1 keeps the first one). Save it with **`listDao.updateList`**, not `insertList`: Room's REPLACE is DELETE + INSERT and cascade-deletes the list's items (`REALTIME_TEST_FINDINGS.md` BUG 1; today's `share()` has this exact line). Drop the separate `upsertItems` call and the `insertList` first-share path, and update the class comments.
- [ ] **Step 4: Run** — same, then `koverVerifyDevDebug`.
- [ ] **Step 5: Commit** — `git commit -m "feat(share): share a list that already syncs by adding its code"`

---

### Task 10: Welcome screen

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/onboarding/data/repository/WelcomeRepository.kt`
- Create: `app/src/main/java/com/babegetthis/android/core/onboarding/di/OnboardingModule.kt`
- Create: `app/src/main/java/com/babegetthis/android/feature/welcome/ui/WelcomeViewModel.kt`, `WelcomeScreen.kt`
- Modify: `app/src/main/java/com/babegetthis/android/navigation/BgtNavGraph.kt`, `app/src/main/java/com/babegetthis/android/MainActivity.kt`
- Modify: `app/src/main/java/com/babegetthis/android/core/telemetry/model/AnalyticsEvent.kt`, `core/telemetry/data/AnalyticsEventMapper.kt`, `core/telemetry/model/Screen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/androidTest/java/com/babegetthis/android/testing/ResetAppStateRule.kt`
- Test: Create `app/src/test/.../core/onboarding/data/repository/WelcomeRepositoryTest.kt`, `app/src/test/.../feature/welcome/ui/WelcomeViewModelTest.kt`, `app/src/test/.../feature/welcome/ui/WelcomeScreenTest.kt`, `app/src/androidTest/.../journey/WelcomeJourneyTest.kt`; modify `AnalyticsEventMapperTest.kt`

**Interfaces:**
- Produces:

```kotlin
class WelcomeRepository(private val prefs: SharedPreferences, private val isUpdateInstall: () -> Boolean) {
    fun shouldShowWelcome(authState: AuthState): Boolean // Unauthenticated && !welcome_done
    fun markDone()
    fun reset()                                          // used by Task 11
}
```

  - Prefs file `welcome`, key `welcome_done`. On the first read with the key absent, persist `isUpdateInstall()` as its value, so the answer doesn't flip when a fresh install later gets an update. `OnboardingModule` provides `isUpdateInstall = { pkg.firstInstallTime != pkg.lastUpdateTime }` from `PackageManager.getPackageInfo(packageName, 0)`.
  - Only "Continue as guest" calls `markDone()`. Signed-in users never see Welcome, and sign-out calls `reset()`, so sign-in has nothing to record.
  - `WelcomeViewModel(welcome: WelcomeRepository, analytics: AnalyticsRepository)`: `onContinueAsGuest()` = `markDone()` + `track(WelcomeChoiceMade(WelcomeChoice.Guest))`; `onSignIn()` = `track(WelcomeChoiceMade(WelcomeChoice.SignIn))`.
  - Telemetry: `enum class WelcomeChoice { Guest, SignIn }`; `data class AnalyticsEvent.WelcomeChoiceMade(val choice: WelcomeChoice)`; GA4 name `welcome_choice`, param `choice` = `guest` | `sign_in` (map it like `ShareJoinFailed`'s reason). Catalog comment, question it answers: *what share of new installs try the app as a guest vs sign in?* Also `Screen.Welcome("welcome")` and `route.startsWith("welcome") -> Welcome`.
  - `Routes.WELCOME = "welcome"`; `BgtNavGraph(authStateManager, navController, startAtWelcome: Boolean)`. `MainActivity` passes `welcomeRepository.shouldShowWelcome(authStateManager.authState.value)` right after `initialize()`.
  - `NavHostController.leaveAuth()` (private, in `BgtNavGraph.kt`): `if (!popBackStack(Routes.SHOPPING_LIST, false)) navigate(Routes.SHOPPING_LIST) { popUpTo(Routes.WELCOME) { inclusive = true } }`. Login, Register and ForgotPassword **success** call it. Their **back/close** becomes `if (!popBackStack(Routes.SHOPPING_LIST, false)) popBackStack()`.
  - `ResetAppStateRule` gains `welcomeDone: Boolean = true` and writes it to the `welcome` prefs file with `commit()`, so existing journeys still start on the lists screen.

- Copy (`strings.xml`; the title reuses `app_name`):

| key | text |
|---|---|
| `welcome_sign_in` | Sign in |
| `welcome_sign_in_body` | Save your lists to your account, share them, and add items by voice. |
| `welcome_continue_as_guest` | Continue as guest |
| `welcome_guest_body` | Your lists stay on this phone only. |

- [ ] **Step 1: Write the failing tests**

```kotlin
// WelcomeRepositoryTest (Robolectric prefs)
@Test fun `a fresh install signed out sees Welcome`()
@Test fun `an updated install does not, and keeps not seeing it after the next update`()
@Test fun `a signed-in or expired user never sees Welcome`()
@Test fun `continuing as guest hides Welcome and signing out shows it again`()   // markDone(); reset()
// WelcomeViewModelTest
@Test fun `continue as guest marks Welcome done and reports guest`()
@Test fun `sign in reports sign_in and leaves Welcome pending`()
// WelcomeScreenTest (Compose, src/test)
@Test fun `both choices are shown and call back`()
// AnalyticsEventMapperTest
@Test fun `welcome choice maps to welcome_choice with a bounded choice param`()
// WelcomeJourneyTest (androidTest, welcomeDone = false) — Review Focus 1
@Test fun continueAsGuestLandsOnLists()
@Test fun signInFromWelcomeLandsOnListsAndBackFromLoginReturnsToWelcome()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew testDevDebugUnitTest --tests "*Welcome*" --tests "*AnalyticsEventMapperTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement** — the interfaces above. Lay `WelcomeScreen` out like `LoginScreen`: app name, then two stacked choice blocks, each a button with its body line, with the primary button on Sign in.
- [ ] **Step 4: Run to verify they pass** — unit command above, then `./gradlew connectedDevDebugAndroidTest` (whole e2e suite — the start destination changed), then `koverVerifyDevDebug` and `./gradlew lintProdDebug`.
- [ ] **Step 5: Commit** — `git commit -m "feat(onboarding): add the Sign in / Continue as guest welcome screen"`

---

### Task 11: Sign-out and account deletion

**Files:**
- Modify: `SyncEngine.kt` (`evictAccountLists`, `hasUnsyncedChanges`), `ShoppingListDao.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/profile/ui/ProfileViewModel.kt`, `ProfileBottomSheet.kt`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/ui/ShoppingListScreen.kt`, `BgtNavGraph.kt` (pass `onNavigateToWelcome`)
- Modify: `strings.xml`
- Test: `SyncEngineTest.kt`, `app/src/test/.../feature/profile/ui/ProfileViewModelTest.kt`, `ProfileAndVoiceSheetTest.kt`

**Interfaces:**
- Consumes: Task 10 `WelcomeRepository.reset()`.
- Produces:
  - `suspend fun SyncEngine.evictAccountLists(): Result<Unit>` (replaces `evictSharedReplicas`; DAO `deleteAccountLists()` = `DELETE FROM shopping_lists WHERE accountId IS NOT NULL OR shareCode IS NOT NULL`, replacing `deleteSharedLists`)
  - `suspend fun SyncEngine.hasUnsyncedChanges(): Boolean` — `listDao.countPendingSync() + itemDao.countPendingSync() > 0`
  - `ProfileUiState.showUnsyncedSignOutWarning: Boolean = false`
  - `ProfileViewModel`: constructor gains `welcome: WelcomeRepository`; `val signedOut: SharedFlow<Unit>`; `fun logout()` (push; if `hasUnsyncedChanges()` → show the warning and stop; else complete); `fun confirmSignOutAnyway()`; `fun dismissUnsyncedSignOutWarning()`. Completing = `evictAccountLists()` → `authRepository.logout()` → on success `welcome.reset()` + emit `signedOut`. `deleteAccount()` success also calls `welcome.reset()` and emits `signedOut`.
  - `ProfileBottomSheet(onDismiss, onToast, onSignedOut: () -> Unit)`. `ShoppingListScreen` passes `onNavigateToWelcome`, which navigates to `Routes.WELCOME` with `popUpTo(0)`.

- Copy:

| key | text |
|---|---|
| `sign_out_unsynced_title` | Sign out? |
| `sign_out_unsynced_body` | Some changes haven't been saved to your account yet. If you sign out now, they'll be lost. |
| `sign_out_unsynced_confirm` | Sign out anyway |
| (dismiss) | reuse the existing Cancel string |

- [ ] **Step 1: Write the failing tests**

```kotlin
// SyncEngineTest
@Test fun `eviction removes account lists and orphaned replicas but keeps guest lists`()
@Test fun `unsynced changes are reported until they are pushed`()
// ProfileViewModelTest
@Test fun `signing out with everything synced evicts, resets Welcome and emits signedOut`()
@Test fun `signing out with unsynced changes shows the warning and does nothing else`()   // no evict, no authRepository.logout
@Test fun `sign out anyway completes the sign-out`()
@Test fun `dismissing the warning keeps the user signed in`()
@Test fun `deleting the account resets Welcome and emits signedOut`()
// ProfileAndVoiceSheetTest
@Test fun `the unsynced warning offers cancel and sign out anyway`()
```

- [ ] **Step 2: Run to verify they fail** — `./gradlew testDevDebugUnitTest --tests "*SyncEngineTest*" --tests "*ProfileViewModelTest*" --tests "*ProfileAndVoiceSheetTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement** — the interfaces above. Update the eviction comments to cite decision 008 and say why the warning now exists: once every list is an account list, an offline sign-out isn't the rare case decision 004 accepted.
- [ ] **Step 4: Run to verify they pass** — same, then the full suite, `koverVerifyDevDebug`, and `lintProdDebug`.
- [ ] **Step 5: Commit** — `git commit -m "feat(auth): warn before signing out with unsynced changes"`

---

### Task 12: Session-expired banner and the adoption notice

**Files:**
- Create: `app/src/main/java/com/babegetthis/android/core/ui/Links.kt` — `const val PRIVACY_POLICY_URL` moved from `SettingsScreen.kt:57`
- Modify: `app/src/main/java/com/babegetthis/android/feature/shoppinglist/ui/viewModels/ShoppingListViewModel.kt`, `ShoppingListScreen.kt`, `SettingsScreen.kt`, `strings.xml`
- Test: `ShoppingListViewModelTest.kt`, `app/src/test/.../feature/shoppinglist/ui/ShoppingListScreenTest.kt`

**Interfaces:**
- Consumes: Task 3 `AuthState.SessionExpired`; Task 6 `ListOwnershipStore.adoptionNotice` / `setAdoptionNotice`.
- Produces: `ShoppingListViewModel` constructor gains `ownership: ListOwnershipStore`; `val isSessionExpired: StateFlow<Boolean>`; `val adoptionNotice: StateFlow<Int>`; `fun dismissAdoptionNotice()` (sets 0).

- Copy:

| key | text |
|---|---|
| `session_expired_banner` | You've been signed out. Sign in again to keep your lists in sync. |
| `session_expired_sign_in` | Sign in |
| `adoption_notice` (plurals) | one: `Your list is now saved to your account, so it's backed up and shows up on any phone you sign in on.` · other: `Your %d lists are now saved to your account, so they're backed up and show up on any phone you sign in on.` |
| `adoption_notice_ok` | OK |
| `adoption_notice_privacy` | Privacy policy |

- [ ] **Step 1: Write the failing tests**

```kotlin
// ShoppingListViewModelTest
@Test fun `isSessionExpired follows the auth state`()
@Test fun `the adoption notice shows the claimed count until dismissed`()
// ShoppingListScreenTest
@Test fun `an expired session shows the banner and its button opens sign-in`()
@Test fun `the adoption dialog shows the count and OK dismisses it`()
@Test fun `neither shows for a guest`()
```

- [ ] **Step 2: Run** — `./gradlew testDevDebugUnitTest --tests "*ShoppingListViewModelTest*" --tests "*ShoppingListScreenTest*"`. Expected: FAIL.
- [ ] **Step 3: Implement** — a non-blocking banner above the list content while `isSessionExpired`; the button calls the existing `onNavigateToLogin`. An `AlertDialog` while `adoptionNotice > 0`: OK dismisses; Privacy policy opens `PRIVACY_POLICY_URL` the way Settings does and also dismisses.
- [ ] **Step 4: Run** — same, then `koverVerifyDevDebug` and `lintProdDebug`.
- [ ] **Step 5: Commit** — `git commit -m "feat(lists): session-expired banner and lists-moved notice"`

---

### Task 13: Decision record, privacy policy, TODO

**Files:**
- Create: `docs/technical-decisions/008-account-owned-lists.md`
- Modify: `docs/technical-decisions/004-logout-semantics-for-shared-lists.md` (status line)
- Modify: `docs/publishing/privacy-policy.md`
- Modify: `TODO.md`

- [ ] **Step 1: Write 008** in 004's house style: status ACCEPTED 2026-10-14; context (Got This needs per-person history); decisions D1–D8 with the rejected alternative and the reason for each (copy them from the spec's table); the new ownership table (guest / signed in / session expired / sign-out / sign-in) replacing 004's; the accepted trade-offs from the spec.
- [ ] **Step 2: Mark 004** — under its status line add: `**Superseded in part 2026-10-14 by [008](008-account-owned-lists.md):** "local-only lists" now means guest lists only; a signed-in user's lists are account data.`
- [ ] **Step 3: Rewrite the privacy policy's storage statements** (lines 7, 28, 52, 74, 82, 98–99, 106 today). New facts: lists of signed-in users are stored on our server so they sync across your devices; guest lists never leave the phone; deleting your account deletes the lists no one else has joined. Add a dated "Changes" line, and remove every sentence that says lists stay on the device unless shared.
- [ ] **Step 4: TODO.md** — under v2, add **Got This** (per-person purchase history, "you already bought this", running-low reminders), noting that it depends on this change and needs "picked up by" attribution on shared lists (tradeoff 001); reference spec decision D5; note it supersedes the "Store room" idea.
- [ ] **Step 5: Commit** — `git commit -m "docs: record account-owned lists and update the privacy policy"`

---

### Task 14: `delete_user` purges solo lists — BLOCKED

**Blocked until the user pastes the current `delete_user` SQL** (Supabase dashboard → Database → Functions). It is not in the repo.

**Files:**
- Create: `supabase/migrations/004_delete_user_purges_solo_lists.sql`
- Modify: `ProfileBottomSheet.kt` delete-confirmation copy, `strings.xml`

- [ ] **Step 1: Write the migration** — `create or replace function public.delete_user()` = the pasted body, with this added **before** the user row is deleted:

```sql
-- Lists the caller is the ONLY member of die with the account (items
-- cascade). Lists others joined stay with them (002 keeps created_by NULL).
delete from public.lists l
where exists (select 1 from public.list_members m where m.list_id = l.id and m.user_id = auth.uid())
  and not exists (select 1 from public.list_members m where m.list_id = l.id and m.user_id <> auth.uid());
```

  Append negative tests: deleting a user removes their solo lists and keeps lists that another member still has.

- [ ] **Step 2: Copy** — add `delete_account_lists_note`: "Lists you haven't shared are deleted from our server." under the existing delete-account warning. Update the Compose test that asserts on that dialog.
- [ ] **Step 3: Hand off for staging** (user runs it and the negative tests), then commit — `git commit -m "feat(db): delete solo lists with the account"`

---

### Release gate (user, after all tasks)

- [ ] `./gradlew testDevDebugUnitTest koverVerifyDevDebug lintProdDebug spotlessCheck` and `connectedDevDebugAndroidTest` all green.
- [ ] Migrations 003 and 004 applied on staging; two-device manual test on the staging flavor:
  - guest → sign in → lists appear on the second phone
  - airplane-mode edits sync on reconnect
  - sign out, then sign back in (locks kept)
  - share a solo list
  - expired session shows the banner
  - account deletion removes the server rows
- [ ] Privacy policy published on babegetthis.com; Play Console Data safety form updated (shopping-list content: collected, linked to account, for app functionality, deletable).
- [ ] Migrations applied on prod **before** the release goes out.
