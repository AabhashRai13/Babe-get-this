package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.auth.model.AuthState
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeListOwnershipStore
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Claiming the device's lists for the account that just signed in
// (docs/technical-decisions/008). Real in-memory Room — the claim is SQL, and
// the item flags and the CASCADE are what matter.
@RunWith(RobolectricTestRunner::class)
class SyncEngineClaimTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private val ownership = FakeListOwnershipStore()
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints, ownership, { "user-1" }, mockk(relaxed = true))
    }

    private suspend fun seedList(
        id: String,
        accountId: String? = null,
        shareCode: String? = null,
        onServer: Boolean = false,
        pendingSync: Boolean = false,
        isLocked: Boolean = false,
    ) = dbRule.listDao.insertList(
        ShoppingListEntity(
            id = id,
            name = "List $id",
            createdAt = 1L,
            updatedAt = 2L,
            shareCode = shareCode,
            pendingSync = pendingSync,
            accountId = accountId,
            onServer = onServer,
            isLocked = isLocked,
        ),
    )

    private suspend fun seedItem(id: String, listId: String, deletedAt: Long? = null) = dbRule.itemDao.insertItem(
        ShoppingItemEntity(
            id = id, listId = listId, name = "Milk", quantity = "1", isPickedUp = false, categoryId = null,
            shop = null, note = null, createdAt = 1L, updatedAt = 2L, deletedAt = deletedAt, pendingSync = false,
        ),
    )

    private suspend fun list(id: String) = dbRule.listDao.getListRaw(id)

    @Test
    fun `guest lists move into the account and are queued with their live items`() = runTest {
        seedList("guest")
        seedItem("live", "guest")
        seedItem("gone", "guest", deletedAt = 5L)

        val claimed = engine.claimDeviceLists("user-1")

        assertEquals(Result.Success(1), claimed)
        val row = list("guest")!!
        assertEquals("user-1", row.accountId)
        assertTrue("the list has to be uploaded", row.pendingSync)
        assertTrue(dbRule.itemDao.getItemRaw("live")!!.pendingSync)
        assertFalse("a deleted item stays deleted, unsent", dbRule.itemDao.getItemRaw("gone")!!.pendingSync)
    }

    @Test
    fun `another account's lists and their sync points are removed`() = runTest {
        seedList("theirs", accountId = "user-2", onServer = true)
        seedItem("their-item", "theirs")
        syncPoints.set("theirs", "2026-08-01T10:00:00Z")
        seedList("mine", accountId = "user-1", onServer = true)
        syncPoints.set("mine", "2026-08-01T10:00:00Z")

        engine.claimDeviceLists("user-1")

        assertNull(list("theirs"))
        assertNull("CASCADE took the items", dbRule.itemDao.getItemRaw("their-item"))
        assertNull(syncPoints.get("theirs"))
        assertNotNull(list("mine"))
        assertEquals("2026-08-01T10:00:00Z", syncPoints.get("mine"))
    }

    // An already-signed-in user opening the update in a store with no signal
    // must still see their shared lists: the claim is local, no server needed.
    @Test
    fun `the first claim after the update also claims shared replicas, offline`() = runTest {
        seedList("shared", shareCode = "ABC123", onServer = true)
        remote.failFetches = true
        remote.failUpserts = true

        val claimed = engine.claimDeviceLists("user-1")

        assertEquals("shared lists aren't news to the user", Result.Success(0), claimed)
        val row = list("shared")!!
        assertEquals("user-1", row.accountId)
        assertFalse("already on the server — nothing to upload", row.pendingSync)
        assertTrue(ownership.isAccountListsMigrated())
    }

    @Test
    fun `later claims leave orphaned shared replicas alone`() = runTest {
        ownership.markAccountListsMigrated()
        seedList("orphan", shareCode = "ABC123", onServer = true)

        engine.claimDeviceLists("user-1")

        assertNull("only discovery may claim it", list("orphan")!!.accountId)
    }

    @Test
    fun `claiming adds to the pending adoption notice`() = runTest {
        ownership.setAdoptionNotice(2)
        seedList("a")
        seedList("b")
        seedList("c")

        engine.claimDeviceLists("user-1")
        assertEquals(5, ownership.adoptionNotice.value)

        engine.claimDeviceLists("user-1") // nothing left to claim
        assertEquals(5, ownership.adoptionNotice.value)
    }

    @Test
    fun `claiming twice claims nothing the second time`() = runTest {
        seedList("guest")

        engine.claimDeviceLists("user-1")
        val second = engine.claimDeviceLists("user-1")

        assertEquals(Result.Success(0), second)
    }

    // --- onAuthState: what a sign-in state change means for local lists ----

    @Test
    fun `a live session claims and asks for a sync`() = runTest {
        seedList("guest")

        assertTrue(engine.onAuthState(AuthState.Authenticated("user-1")))
        assertEquals("user-1", list("guest")!!.accountId)
    }

    // A dead token still knows whose lists these are: claim them so they stay
    // on screen, but there is nothing to sync with.
    @Test
    fun `an expired session claims but does not sync`() = runTest {
        seedList("guest")

        assertFalse(engine.onAuthState(AuthState.SessionExpired("user-1")))
        assertEquals("user-1", list("guest")!!.accountId)
    }

    @Test
    fun `a signed-out first launch marks the migration done and claims nothing`() = runTest {
        seedList("guest")

        assertFalse(engine.onAuthState(AuthState.Unauthenticated))
        assertTrue(ownership.isAccountListsMigrated())
        assertNull(list("guest")!!.accountId)
    }

    @Test
    fun `loading does nothing`() = runTest {
        seedList("guest")

        assertFalse(engine.onAuthState(AuthState.Loading))
        assertFalse(ownership.isAccountListsMigrated())
        assertNull(list("guest")!!.accountId)
    }

    // A lock is per-device and never syncs; the remembered set is what re-locks
    // a list that sign-out evicted. Locks set BEFORE the update never went
    // through setLocked, so the first run has to record them.
    @Test
    fun `the first claim remembers locks set before the update`() = runTest {
        seedList("locked", isLocked = true)
        seedList("open")

        engine.claimDeviceLists("user-1")

        assertEquals(setOf("locked"), ownership.lockedListIds())
    }

    @Test
    fun `a signed-out first launch remembers locks too`() = runTest {
        seedList("locked", isLocked = true)

        engine.onAuthState(AuthState.Unauthenticated)

        assertEquals(setOf("locked"), ownership.lockedListIds())
    }

    // The first claim takes every ownerless shared replica without asking the
    // server. One left by a different account would fail RLS on every push and
    // stall the whole queue — so the server's first answer decides which stay.
    @Test
    fun `first-run claims the server doesn't vouch for are released after discovery`() = runTest {
        seedList("mine", shareCode = "AAA111", onServer = true)
        seedList("stray", shareCode = "BBB222", onServer = true, pendingSync = true)
        engine.claimDeviceLists("user-1")
        assertEquals("user-1", list("stray")!!.accountId)
        remote.listRows = listOf(
            com.babegetthis.android.core.sync.data.model.ListRow(
                id = "mine",
                name = "List mine",
                shareCode = "AAA111",
                updatedAt = "2026-08-01T10:00:00Z",
            ),
        )

        engine.catchUpAccount()

        assertEquals("user-1", list("mine")!!.accountId)
        assertNull("not this account's — back to ownerless", list("stray")!!.accountId)
        assertTrue(ownership.unverifiedListIds().isEmpty())

        engine.push()
        assertTrue("a released stray is never pushed", remote.upsertedLists.none { it.id == "stray" })
    }

    @Test
    fun `claims stay until the server actually answers`() = runTest {
        seedList("stray", shareCode = "BBB222", onServer = true)
        engine.claimDeviceLists("user-1")
        remote.failFetches = true

        engine.catchUpAccount()

        assertEquals("user-1", list("stray")!!.accountId)
        assertEquals(setOf("stray"), ownership.unverifiedListIds())
    }
}
