package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
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

// Account-wide catch-up (docs/technical-decisions/008): once every account
// list syncs, two requests per list on every app open doesn't scale. One
// discovery request, a full pull only for lists new to this phone, and the
// rest in batches since one account-wide sync point.
@RunWith(RobolectricTestRunner::class)
class SyncEngineAccountCatchUpTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private val ownership = FakeListOwnershipStore()
    private var userId: String? = "user-1"
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints, ownership, { userId }, mockk(relaxed = true))
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
        assertEquals(listOf(listOf("known")), remote.itemBatches)
        assertEquals(t10, remote.lastItemSince)
        assertNotNull(dbRule.itemDao.getItemRaw("fresh"))
        assertEquals(t11, syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT))
    }

    @Test
    fun `51 known lists are fetched in two batches`() = runTest {
        val ids = (0..50).map { "list-%02d".format(it) }
        ids.forEach { seedAccountList(it) }
        remote.listRows = ids.map { listRow(it) }
        remote.itemRows = listOf(itemRow("first", "list-00"), itemRow("last", "list-50"))

        engine.catchUpAccount()

        assertEquals(listOf(50, 1), remote.itemBatches.map { it.size })
        assertNotNull(dbRule.itemDao.getItemRaw("first"))
        assertNotNull(dbRule.itemDao.getItemRaw("last"))
    }

    @Test
    fun `the sync point does not move when a batch fails`() = runTest {
        val ids = (0..50).map { "list-%02d".format(it) }
        ids.forEach { seedAccountList(it) }
        syncPoints.set(SyncEngine.ACCOUNT_SYNC_POINT, t10)
        remote.listRows = ids.map { listRow(it) }
        remote.itemRows = listOf(itemRow("first", "list-00", updatedAt = t11))
        remote.failOnItemBatch = 2

        val result = engine.catchUpAccount()

        assertTrue(result is Result.Error)
        assertEquals(
            "the failed batch is retried from the same point",
            t10,
            syncPoints.get(SyncEngine.ACCOUNT_SYNC_POINT),
        )
    }

    @Test
    fun `tombstoned lists found by discovery do not pull their items`() = runTest {
        remote.listRows = listOf(listRow("deleted", deletedAt = t10))
        remote.itemRows = listOf(itemRow("x", "deleted"))

        engine.catchUpAccount()

        assertTrue(remote.itemFetchListIds.isEmpty())
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
}
