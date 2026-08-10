package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
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

// Real in-memory Room (same rule the repository tests use) so LWW and the
// guarded flag-clears are exercised against real SQL, with a fake remote.
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private var userId: String? = "user-1"
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints) { userId }
    }

    private val t10 = "2026-08-01T10:00:00Z"
    private val t11 = "2026-08-01T11:00:00Z"
    private val t12 = "2026-08-01T12:00:00Z"

    // --- helpers ------------------------------------------------------------

    private fun listEntity(
        id: String = "list-1",
        shareCode: String? = "ABC123",
        updatedAt: Long = 1_000L,
        pendingSync: Boolean = false,
        deletedAt: Long? = null,
    ) = ShoppingListEntity(
        id = id, name = "Groceries", createdAt = 500L, updatedAt = updatedAt,
        isLocked = false, shareCode = shareCode, deletedAt = deletedAt, pendingSync = pendingSync,
    )

    private fun itemEntity(
        id: String = "item-1",
        listId: String = "list-1",
        updatedAt: Long = 1_000L,
        pendingSync: Boolean = false,
        deletedAt: Long? = null,
    ) = ShoppingItemEntity(
        id = id, listId = listId, name = "Milk", quantity = "1",
        isPickedUp = false, categoryId = null, shop = null, note = null,
        createdAt = 500L, updatedAt = updatedAt, deletedAt = deletedAt, pendingSync = pendingSync,
    )

    private fun listRow(
        id: String = "list-1",
        name: String = "Groceries",
        updatedAt: String? = t10,
        deletedAt: String? = null,
    ) = ListRow(id = id, name = name, shareCode = "ABC123", createdBy = "user-1",
        updatedAt = updatedAt, deletedAt = deletedAt)

    private fun itemRow(
        id: String = "item-1",
        listId: String = "list-1",
        name: String = "Milk",
        updatedAt: String? = t10,
        deletedAt: String? = null,
    ) = ItemRow(id = id, listId = listId, name = name, quantity = "1",
        updatedAt = updatedAt, deletedAt = deletedAt)

    // --- push ---------------------------------------------------------------

    @Test
    fun `push uploads dirty shared rows and clears their flags`() = runTest {
        dbRule.listDao.insertList(listEntity(pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))

        val result = engine.push()

        assertTrue(result is Result.Success)
        assertEquals(listOf("list-1"), remote.upsertedLists.map { it.id })
        assertEquals(listOf("item-1"), remote.upsertedItems.map { it.id })
        assertFalse(dbRule.listDao.getListRaw("list-1")!!.pendingSync)
        assertFalse(dbRule.itemDao.getItemRaw("item-1")!!.pendingSync)
    }

    @Test
    fun `push sends nothing when signed out and keeps the queue`() = runTest {
        userId = null
        dbRule.listDao.insertList(listEntity(pendingSync = true))

        engine.push()

        assertTrue(remote.upsertedLists.isEmpty())
        assertTrue(dbRule.listDao.getListRaw("list-1")!!.pendingSync)
    }

    @Test
    fun `push ignores dirty local-only lists`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, pendingSync = true))

        engine.push()

        assertTrue(remote.upsertedLists.isEmpty())
    }

    @Test
    fun `push failure keeps every flag set`() = runTest {
        dbRule.listDao.insertList(listEntity(pendingSync = true))
        remote.failUpserts = true

        val result = engine.push()

        assertTrue(result is Result.Error)
        assertTrue(dbRule.listDao.getListRaw("list-1")!!.pendingSync)
    }

    @Test
    fun `push failure after lists keeps item flags but clears list flags`() = runTest {
        dbRule.listDao.insertList(listEntity(pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))
        remote.failItemUpserts = true

        val result = engine.push()

        assertTrue(result is Result.Error)
        assertFalse("lists made it up", dbRule.listDao.getListRaw("list-1")!!.pendingSync)
        assertTrue("items retry next kick", dbRule.itemDao.getItemRaw("item-1")!!.pendingSync)
    }

    @Test
    fun `push does not clear the flag of a row edited mid-push`() = runTest {
        dbRule.listDao.insertList(listEntity())
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true, updatedAt = 1_000L))
        remote.onUpsertItems = {
            // A concurrent local edit lands while the batch is in flight.
            dbRule.itemDao.insertItem(itemEntity(pendingSync = true, updatedAt = 2_000L))
        }

        engine.push()

        assertTrue(
            "newer edit must stay queued",
            dbRule.itemDao.getItemRaw("item-1")!!.pendingSync,
        )
    }

    @Test
    fun `push sends tombstones like any other edit`() = runTest {
        dbRule.listDao.insertList(listEntity())
        dbRule.itemDao.insertItem(itemEntity())
        dbRule.itemDao.softDeleteItem("item-1", now = 2_000L)

        engine.push()

        assertEquals(1, remote.upsertedItems.size)
        assertTrue(remote.upsertedItems.single().deletedAt != null)
        assertFalse(dbRule.itemDao.getItemRaw("item-1")!!.pendingSync)
    }

    // --- catchUp ------------------------------------------------------------

    @Test
    fun `catchUp lands brand-new rows as clean Room writes`() = runTest {
        remote.listRows = listOf(listRow())
        remote.itemRows = listOf(itemRow())

        val result = engine.catchUp("list-1")

        assertTrue(result is Result.Success)
        val list = dbRule.listDao.getListRaw("list-1")!!
        val item = dbRule.itemDao.getItemRaw("item-1")!!
        assertEquals(t10.isoToMillis(), list.updatedAt)
        assertFalse(list.pendingSync)
        assertEquals("Milk", item.name)
        assertFalse(item.pendingSync)
    }

    @Test
    fun `catchUp passes the stored sync point and records the newest stamp`() = runTest {
        syncPoints.set("list-1", t10)
        remote.listRows = listOf(listRow(updatedAt = t11))
        remote.itemRows = listOf(itemRow(updatedAt = t12))

        engine.catchUp("list-1")

        assertEquals(t10, remote.lastListSince)
        assertEquals(t10, remote.lastItemSince)
        assertEquals("newest stamp wins", t12, syncPoints.get("list-1"))
    }

    @Test
    fun `catchUp with nothing new leaves the sync point alone`() = runTest {
        engine.catchUp("list-1")

        assertNull(syncPoints.get("list-1"))
    }

    @Test
    fun `catchUp skips rows older than the local copy`() = runTest {
        dbRule.listDao.insertList(listEntity(updatedAt = t12.isoToMillis()))
        remote.listRows = listOf(listRow(name = "Stale name", updatedAt = t10))

        engine.catchUp("list-1")

        assertEquals("Groceries", dbRule.listDao.getListRaw("list-1")!!.name)
    }

    @Test
    fun `catchUp never overwrites dirty local rows`() = runTest {
        dbRule.listDao.insertList(listEntity(updatedAt = 1_000L, pendingSync = true))
        remote.listRows = listOf(listRow(name = "Remote name", updatedAt = t12))

        engine.catchUp("list-1")

        val local = dbRule.listDao.getListRaw("list-1")!!
        assertEquals("our push will out-timestamp it", "Groceries", local.name)
        assertTrue(local.pendingSync)
    }

    // Regression: applying an inbound list row used to go through
    // insertList(REPLACE) = DELETE + INSERT, which fires shopping_items'
    // ON DELETE CASCADE. One renamed list from a peer wiped that list's items
    // on every device, unrecoverably (hard delete, not a tombstone).
    @Test
    fun `catchUp applying a changed list row keeps the list's items`() = runTest {
        dbRule.listDao.insertList(listEntity(updatedAt = t10.isoToMillis()))
        dbRule.itemDao.insertItem(itemEntity(updatedAt = t10.isoToMillis()))
        remote.listRows = listOf(listRow(name = "Renamed by partner", updatedAt = t12))

        engine.catchUp("list-1")

        assertEquals("Renamed by partner", dbRule.listDao.getListRaw("list-1")!!.name)
        assertNotNull("items must survive a list-row update", dbRule.itemDao.getItemRaw("item-1"))
    }

    @Test
    fun `catchUp skips items whose list is not local yet`() = runTest {
        remote.itemRows = listOf(itemRow(listId = "list-elsewhere"))

        val result = engine.catchUp("list-elsewhere")

        assertTrue(result is Result.Success)
        assertNull(dbRule.itemDao.getItemRaw("item-1"))
    }

    @Test
    fun `catchUp ignores rows without a server stamp`() = runTest {
        remote.listRows = listOf(listRow(updatedAt = null))

        engine.catchUp("list-1")

        assertNull(dbRule.listDao.getListRaw("list-1"))
    }

    @Test
    fun `catchUp applies remote tombstones and hides the item`() = runTest {
        dbRule.listDao.insertList(listEntity())
        dbRule.itemDao.insertItem(itemEntity())
        remote.itemRows = listOf(itemRow(updatedAt = t11, deletedAt = t11))

        engine.catchUp("list-1")

        assertTrue(dbRule.itemDao.getItemRaw("item-1")!!.deletedAt != null)
        assertTrue(dbRule.itemDao.getItemsByListIdOnce("list-1").isEmpty())
    }

    @Test
    fun `catchUp is idempotent`() = runTest {
        remote.listRows = listOf(listRow())
        remote.itemRows = listOf(itemRow())

        engine.catchUp("list-1")
        val after = dbRule.itemDao.getItemRaw("item-1")
        engine.catchUp("list-1")

        assertEquals(after, dbRule.itemDao.getItemRaw("item-1"))
    }

    // --- catchUpAllShared ---------------------------------------------------

    @Test
    fun `catchUpAllShared discovers server lists this device has never seen`() = runTest {
        // Nothing local at all — a fresh sign-in on a new device.
        remote.listRows = listOf(listRow(id = "discovered-1"))
        remote.itemRows = listOf(itemRow(id = "item-9", listId = "discovered-1"))

        val result = engine.catchUpAllShared()

        assertTrue(result is Result.Success)
        assertEquals("Groceries", dbRule.listDao.getListRaw("discovered-1")!!.name)
        assertEquals("items ride in via the per-list catch-up",
            "Milk", dbRule.itemDao.getItemRaw("item-9")!!.name)
    }

    @Test
    fun `fullCatchUp drops the high-water mark and pulls from scratch`() = runTest {
        syncPoints.set("list-1", t12) // already current — plain catchUp would no-op
        remote.listRows = listOf(listRow(updatedAt = t10))

        val result = engine.fullCatchUp("list-1")

        assertTrue(result is Result.Success)
        assertNull("since must be dropped for the fetch", remote.lastListSince)
        assertEquals("Groceries", dbRule.listDao.getListRaw("list-1")!!.name)
        assertEquals("mark re-established from what came back", t10, syncPoints.get("list-1"))
    }

    @Test
    fun `evictSharedReplicas removes shared lists only, after a final push`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "shared-1"))
        dbRule.itemDao.insertItem(itemEntity(id = "i1", listId = "shared-1", pendingSync = true))
        dbRule.listDao.insertList(listEntity(id = "local-1", shareCode = null))
        syncPoints.set("shared-1", t10)

        val result = engine.evictSharedReplicas()

        assertTrue(result is Result.Success)
        assertEquals("queued edit pushed before eviction", listOf("i1"),
            remote.upsertedItems.map { it.id })
        assertNull("shared replica gone", dbRule.listDao.getListRaw("shared-1"))
        assertNull("its items gone via CASCADE", dbRule.itemDao.getItemRaw("i1"))
        assertNotNull("local-only list untouched", dbRule.listDao.getListRaw("local-1"))
        assertTrue("sync points wiped", syncPoints.points.isEmpty())
    }

    @Test
    fun `evictSharedReplicas still evicts when the final push cannot run`() = runTest {
        userId = null // signed-out-ish: push no-ops, eviction must proceed
        dbRule.listDao.insertList(listEntity(id = "shared-1"))

        engine.evictSharedReplicas()

        assertNull(dbRule.listDao.getListRaw("shared-1"))
    }

    @Test
    fun `catchUpAllShared visits every live shared list and nothing else`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "shared-1"))
        dbRule.listDao.insertList(listEntity(id = "shared-2"))
        dbRule.listDao.insertList(listEntity(id = "local-1", shareCode = null))
        dbRule.listDao.insertList(listEntity(id = "dead-1", deletedAt = 9_000L))

        val result = engine.catchUpAllShared()

        assertTrue(result is Result.Success)
        assertEquals(setOf("shared-1", "shared-2"), remote.fetchedListIds.toSet())
    }

}
