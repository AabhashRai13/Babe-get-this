package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.gotthis.data.remote.PurchaseRow
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeGotThisPrefs
import com.babegetthis.android.testing.FakeGotThisRemote
import com.babegetthis.android.testing.FakeListOwnershipStore
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.gotThisSyncFor
import com.babegetthis.android.testing.unreportedSafeCall
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

// Account-wide catch-up (docs/technical-decisions/008): once every account
// list syncs, two requests per list on every app open doesn't scale. One
// discovery request, one account-wide item request since one sync point, and
// a full pull only for lists new to this phone.
@RunWith(RobolectricTestRunner::class)
class SyncEngineAccountCatchUpTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private val ownership = FakeListOwnershipStore()
    private var userId: String? = "user-1"
    private val gotThisRemote = FakeGotThisRemote()
    private val gotThisPrefs = FakeGotThisPrefs()
    private val engine by lazy {
        SyncEngine(
            dbRule.listDao,
            dbRule.itemDao,
            remote,
            syncPoints,
            ownership,
            gotThisSyncFor(dbRule, gotThisRemote, gotThisPrefs, syncPoints),
            { userId },
            mockk(relaxed = true),
            unreportedSafeCall(),
        )
    }

    private val t10 = "2026-08-01T10:00:00Z"
    private val t11 = "2026-08-01T11:00:00Z"
    private val t12 = "2026-08-01T12:00:00Z"

    private suspend fun seedAccountList(id: String) = dbRule.listDao.insertList(
        ShoppingListEntity(
            id = id,
            name = "List $id",
            createdAt = 1L,
            updatedAt = 2L,
            accountId = "user-1",
            onServer = true,
        ),
    )

    private fun listRow(id: String, updatedAt: String = t10, deletedAt: String? = null, name: String = "Server name") =
        ListRow(id = id, name = name, createdBy = "user-1", updatedAt = updatedAt, deletedAt = deletedAt)

    private fun itemRow(id: String, listId: String, updatedAt: String = t10) =
        ItemRow(id = id, listId = listId, name = "Milk", quantity = "1", updatedAt = updatedAt)

    @Test
    fun `discovery adds new lists with their full item history`() = runTest {
        remote.listRows = listOf(listRow("new"))
        remote.itemRows = listOf(itemRow("a", "new"), itemRow("b", "new"), itemRow("c", "new"))

        val result = engine.catchUpAccount()

        assertTrue(result is Result.Success)
        val row = dbRule.listDao.getListRaw("new")!!
        assertEquals("user-1", row.accountId)
        assertTrue(row.onServer)
        assertEquals(listOf("new"), remote.itemFetchListIds)
        assertNull("a new list is pulled from the start", remote.lastItemSince)
        assertEquals(3, dbRule.itemDao.getItemsByListIdOnce("new").size)
    }

    // Joining adds a membership row; the list's own updated_at doesn't move.
    @Test
    fun `a list joined on another phone is discovered even when older than the sync point`() = runTest {
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t12)
        remote.listRows = listOf(listRow("joined", updatedAt = t10))
        remote.itemRows = listOf(itemRow("old-item", "joined", updatedAt = t10))

        engine.catchUpAccount()

        assertNotNull(dbRule.listDao.getListRaw("joined"))
        assertNotNull(dbRule.itemDao.getItemRaw("old-item"))
    }

    @Test
    fun `known lists fetch only items changed since the account sync point`() = runTest {
        seedAccountList("known")
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t10)
        remote.listRows = listOf(listRow("known"))
        remote.itemRows = listOf(itemRow("fresh", "known", updatedAt = t11))

        engine.catchUpAccount()

        assertTrue("no full pull for a list already here", remote.itemFetchListIds.isEmpty())
        assertEquals(listOf(t10), remote.accountItemSinces)
        assertNotNull(dbRule.itemDao.getItemRaw("fresh"))
        assertEquals(t11, syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT))
    }

    // One request however many lists: the server scopes it to the account the
    // same way discovery is scoped, so no list ids travel in the URL.
    @Test
    fun `known lists are caught up in a single account-wide request`() = runTest {
        val ids = (0..50).map { "list-%02d".format(it) }
        ids.forEach { seedAccountList(it) }
        remote.listRows = ids.map { listRow(it) }
        remote.itemRows = listOf(itemRow("first", "list-00"), itemRow("last", "list-50"))

        engine.catchUpAccount()

        assertEquals(1, remote.accountItemSinces.size)
        assertNotNull(dbRule.itemDao.getItemRaw("first"))
        assertNotNull(dbRule.itemDao.getItemRaw("last"))
    }

    @Test
    fun `the sync point does not move when the account request fails`() = runTest {
        seedAccountList("known")
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t10)
        remote.listRows = listOf(listRow("known", updatedAt = t12))
        remote.failAccountItems = true

        val result = engine.catchUpAccount()

        assertTrue(result is Result.Error)
        assertEquals("retried from the same point", t10, syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT))
    }

    // Nothing to catch up on yet, and the account-wide request would return
    // every item of every list only for the full pulls to fetch them again.
    @Test
    fun `no account-wide item request is made before any list is known`() = runTest {
        remote.listRows = listOf(listRow("new", updatedAt = t11))
        remote.itemRows = listOf(itemRow("a", "new"))

        engine.catchUpAccount()

        assertTrue(remote.accountItemSinces.isEmpty())
        assertNotNull(dbRule.itemDao.getItemRaw("a"))
        assertEquals(
            "the list rows read first of all still move the point",
            t11,
            syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT),
        )
    }

    // Known lists first: a change to a new list right after its full pull is
    // then newer than anything the sync point was built from, so the next pass
    // fetches it. Read the other way round, a batch read after the pull could
    // carry the point past that change, and the account pass would skip it for
    // good.
    @Test
    fun `the account-wide request is read before any full pull`() = runTest {
        seedAccountList("known")
        remote.listRows = listOf(listRow("known"), listRow("new"))

        engine.catchUpAccount()

        assertEquals(listOf("account", "items:new"), remote.itemReads)
    }

    @Test
    fun `the sync point moves on a pass with no item changes`() = runTest {
        seedAccountList("known")
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t10)
        remote.listRows = listOf(listRow("known", updatedAt = t12))

        engine.catchUpAccount()

        assertEquals(t12, syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT))
    }

    @Test
    fun `tombstoned lists found by discovery are neither stored nor pulled`() = runTest {
        remote.listRows = listOf(listRow("deleted", deletedAt = t10))
        remote.itemRows = listOf(itemRow("x", "deleted"))

        engine.catchUpAccount()

        assertTrue(remote.itemFetchListIds.isEmpty())
        assertNull(
            "a deletion of a list this phone never had is nothing to store",
            dbRule.listDao.getListRaw("deleted"),
        )
    }

    @Test
    fun `a known list's tombstone is applied`() = runTest {
        seedAccountList("known")
        remote.listRows = listOf(listRow("known", updatedAt = t12, deletedAt = t12))

        engine.catchUpAccount()

        assertNotNull(dbRule.listDao.getListRaw("known")!!.deletedAt)
    }

    // A request without a session gets an empty answer too (RLS filters, it
    // doesn't fail), and a kick can run before the stored session has loaded:
    // that answer must not throw every first-run claim away.
    @Test
    fun `an empty discovery keeps the first-run claims`() = runTest {
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = "claimed",
                name = "Claimed",
                createdAt = 1L,
                updatedAt = 2L,
                shareCode = "ABC123",
                accountId = "user-1",
                onServer = true,
            ),
        )
        ownership.setUnverifiedListIds(setOf("claimed"))
        remote.listRows = emptyList()

        engine.catchUpAccount()

        assertEquals("user-1", dbRule.listDao.getListRaw("claimed")!!.accountId)
        assertEquals(setOf("claimed"), ownership.unverifiedListIds())
    }

    // A shared replica whose owner was lost before lists had owners: the server
    // returning it for this account is the proof of membership.
    @Test
    fun `an orphaned replica is claimed when discovery returns it, even if dirty`() = runTest {
        dbRule.listDao.insertList(
            ShoppingListEntity(
                id = "orphan",
                name = "Local name",
                createdAt = 1L,
                updatedAt = 5_000_000_000_000L,
                shareCode = "ABC123",
                pendingSync = true,
                onServer = true,
            ),
        )
        remote.listRows = listOf(listRow("orphan", name = "Server name"))

        engine.catchUpAccount()

        val row = dbRule.listDao.getListRaw("orphan")!!
        assertEquals("user-1", row.accountId)
        assertEquals("the unpushed local edit still wins", "Local name", row.name)
        assertTrue(row.pendingSync)
    }

    @Test
    fun `a list that comes back after sign-out keeps its lock`() = runTest {
        ownership.rememberLock("locked", true)
        remote.listRows = listOf(listRow("locked"), listRow("open"))

        engine.catchUpAccount()

        assertTrue(dbRule.listDao.getListRaw("locked")!!.isLocked)
        assertFalse(dbRule.listDao.getListRaw("open")!!.isLocked)
    }

    @Test
    fun `catch-up is a no-op without a live session`() = runTest {
        userId = null
        remote.listRows = listOf(listRow("new"))

        assertTrue(engine.catchUpAccount() is Result.Success)
        assertTrue(engine.catchUp("new") is Result.Success)

        assertNull(dbRule.listDao.getListRaw("new"))
        assertTrue(remote.fetchedListIds.isEmpty())
    }

    // If a new list's row landed before its history, a failed pull would make
    // it "known" next time, and only items newer than the account sync point
    // would ever arrive — the rest of a list joined elsewhere, gone for good.
    @Test
    fun `a new list whose history fails to load is retried in full`() = runTest {
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t12)
        remote.listRows = listOf(listRow("joined"))
        remote.itemRows = listOf(itemRow("old-item", "joined", updatedAt = t10))
        remote.failItemFetchFor = "joined"

        assertTrue(engine.catchUpAccount() is Result.Error)
        assertNull("no row without its history", dbRule.listDao.getListRaw("joined"))

        remote.failItemFetchFor = null
        engine.catchUpAccount()

        assertNotNull(dbRule.itemDao.getItemRaw("old-item"))
    }

    private fun purchaseRow(tickId: String, boughtAt: String) = PurchaseRow(
        id = "p-$tickId",
        userId = "user-1",
        tickId = tickId,
        sourceItemId = "item-$tickId",
        name = "Milk",
        boughtAt = boughtAt,
        updatedAt = boughtAt,
    )

    @Test
    fun `purchases are fetched only once the items have landed`() = runTest {
        seedAccountList("known")
        gotThisRemote.purchaseRows = listOf(purchaseRow("t1", t11))
        remote.listRows = listOf(listRow("joined"))
        remote.failItemFetchFor = "joined"

        assertTrue(engine.catchUpAccount() is Result.Error)
        assertTrue(gotThisRemote.calls.isEmpty())

        remote.failItemFetchFor = null
        assertTrue(engine.catchUpAccount() is Result.Success)

        assertEquals(listOf("fetchResetAt", "fetchPurchases"), gotThisRemote.calls)
        assertEquals("t1", dbRule.purchaseDao.live("user-1").single().tickId)
    }

    // The reviewer's case: the clear call fails, the pull succeeds, and the
    // server still returns what the person cleared.
    @Test
    fun `a failed clear followed by a catch-up does not bring back a cleared purchase`() = runTest {
        seedAccountList("known")
        gotThisPrefs.setPendingClear("user-1", t12.isoToMillis())
        gotThisRemote.failClear = true
        gotThisRemote.purchaseRows = listOf(purchaseRow("t10", t10), purchaseRow("t11", t11))

        engine.push()
        assertEquals(listOf("clearHistory"), gotThisRemote.calls)
        assertEquals(t12.isoToMillis(), gotThisPrefs.pendingClear("user-1"))
        engine.catchUpAccount()

        assertTrue(dbRule.purchaseDao.live("user-1").isEmpty())
    }
}
