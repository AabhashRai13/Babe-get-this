package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.mapper.isoToMillis
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeListOwnershipStore
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
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
    private val ownership = FakeListOwnershipStore()
    private var userId: String? = "user-1"
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints, ownership, { userId }, mockk(relaxed = true))
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
        accountId: String? = "user-1",
        onServer: Boolean = true,
    ) = ShoppingListEntity(
        id = id,
        name = "Groceries",
        createdAt = 500L,
        updatedAt = updatedAt,
        isLocked = false,
        shareCode = shareCode,
        deletedAt = deletedAt,
        pendingSync = pendingSync,
        accountId = accountId,
        onServer = onServer,
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
    ) = ListRow(
        id = id,
        name = name,
        shareCode = "ABC123",
        createdBy = "user-1",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun itemRow(
        id: String = "item-1",
        listId: String = "list-1",
        name: String = "Milk",
        updatedAt: String? = t10,
        deletedAt: String? = null,
    ) = ItemRow(
        id = id,
        listId = listId,
        name = name,
        quantity = "1",
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    // --- push ---------------------------------------------------------------

    @Test
    fun `push uploads dirty shared rows and clears their flags`() = runTest {
        dbRule.listDao.insertList(listEntity(pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))

        val result = engine.push()

        assertTrue(result is Result.Success)
        assertEquals(listOf("list-1"), remote.upsertedLists.map { it.id })
        assertTrue("already on the server: upsert, not insert", remote.insertedLists.isEmpty())
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
    fun `guest lists are never pushed`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, accountId = null, onServer = false, pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))

        engine.push()

        assertTrue(remote.insertedLists.isEmpty())
        assertTrue(remote.upsertedLists.isEmpty())
        assertTrue(remote.upsertedItems.isEmpty())
    }

    // --- push: every account list, shared or not (docs/technical-decisions/008)

    @Test
    fun `a list not yet on the server is inserted, then marked onServer`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, onServer = false, pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))

        val result = engine.push()

        assertTrue(result is Result.Success)
        assertEquals(listOf("list-1"), remote.insertedLists.map { it.id })
        assertEquals(null, remote.insertedLists.single().shareCode)
        assertTrue("a brand-new row can't be upserted (RLS)", remote.upsertedLists.isEmpty())
        val row = dbRule.listDao.getListRaw("list-1")!!
        assertTrue(row.onServer)
        assertFalse(row.pendingSync)
        assertEquals(listOf("item-1"), remote.upsertedItems.map { it.id })
    }

    // A previous push reached the server but the app died before recording it,
    // and the row may have changed since (renamed, deleted): the version on the
    // server is the OLD one, so the duplicate goes through the upsert like any
    // row already there. Clearing its flag on the strength of the first INSERT
    // would leave the server on that first version for good.
    @Test
    fun `an insert that finds the row already there upserts it in the same push`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, onServer = false, pendingSync = true))
        remote.listAlreadyExists = true

        val result = engine.push()

        assertTrue(result is Result.Success)
        assertEquals(listOf("list-1"), remote.upsertedLists.map { it.id })
        val row = dbRule.listDao.getListRaw("list-1")!!
        assertTrue(row.onServer)
        assertFalse(row.pendingSync)
    }

    // A list created and deleted before any push: nobody else can have it, so
    // its tombstone would only create a permanent server row (plus membership
    // and items) that every discovery returns forever.
    @Test
    fun `a list deleted before it reached the server is dropped, not uploaded`() = runTest {
        dbRule.listDao.insertList(
            listEntity(shareCode = null, onServer = false, pendingSync = true, deletedAt = 2_000L),
        )
        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))

        engine.push()

        assertTrue(remote.insertedLists.isEmpty())
        assertTrue(remote.upsertedLists.isEmpty())
        assertTrue(remote.upsertedItems.isEmpty())
        assertNull(dbRule.listDao.getListRaw("list-1"))
        assertNull("CASCADE took the items", dbRule.itemDao.getItemRaw("item-1"))
    }

    @Test
    fun `a deletion that never reached the server is dropped even without a session`() = runTest {
        userId = null
        dbRule.listDao.insertList(
            listEntity(shareCode = null, onServer = false, pendingSync = true, deletedAt = 2_000L),
        )

        engine.push()

        assertNull(dbRule.listDao.getListRaw("list-1"))
    }

    @Test
    fun `a deletion of a list already on the server is uploaded as a tombstone`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, onServer = true, pendingSync = true, deletedAt = 2_000L))

        engine.push()

        assertEquals(listOf("list-1"), remote.upsertedLists.map { it.id })
        assertNotNull(remote.upsertedLists.single().deletedAt)
        assertNotNull(dbRule.listDao.getListRaw("list-1"))
    }

    @Test
    fun `a failed insert leaves the list off the server and queued`() = runTest {
        dbRule.listDao.insertList(listEntity(shareCode = null, onServer = false, pendingSync = true))
        remote.failUpserts = true

        val result = engine.push()

        assertTrue(result is Result.Error)
        val row = dbRule.listDao.getListRaw("list-1")!!
        assertFalse(row.onServer)
        assertTrue(row.pendingSync)
    }

    @Test
    fun `another account's pending lists and items are not pushed`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "theirs", accountId = "user-2", pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(id = "their-item", listId = "theirs", pendingSync = true))

        engine.push()

        assertTrue(remote.upsertedLists.isEmpty())
        assertTrue(remote.upsertedItems.isEmpty())
        assertTrue(dbRule.listDao.getListRaw("theirs")!!.pendingSync)
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

    // Discovery returns every row on every pass, and gte refetches the row at
    // the sync point: a row already applied must not be written again (each
    // write re-runs the lists screen's query).
    @Test
    fun `catchUp does not rewrite a row it has already applied`() = runTest {
        dbRule.listDao.insertList(listEntity(updatedAt = t10.isoToMillis()))
        remote.listRows = listOf(listRow(name = "Same stamp, so already applied", updatedAt = t10))

        engine.catchUp("list-1")

        assertEquals("Groceries", dbRule.listDao.getListRaw("list-1")!!.name)
    }

    @Test
    fun `tombstones of rows this phone never had are not stored`() = runTest {
        remote.listRows = listOf(listRow(id = "gone", updatedAt = t10, deletedAt = t10), listRow())
        remote.itemRows = listOf(itemRow(id = "gone-item", updatedAt = t11, deletedAt = t11), itemRow(id = "live"))

        engine.catchUp("list-1")

        assertNull(dbRule.listDao.getListRaw("gone"))
        assertNull(dbRule.itemDao.getItemRaw("gone-item"))
        assertNotNull(dbRule.itemDao.getItemRaw("live"))
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

    // Explicit sign-out and account deletion take every account list off the
    // phone (docs/technical-decisions/008); guest lists are the device's.
    @Test
    fun `eviction removes account lists and orphaned replicas but keeps guest lists`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "shared-1"))
        dbRule.itemDao.insertItem(itemEntity(id = "i1", listId = "shared-1", pendingSync = true))
        dbRule.listDao.insertList(listEntity(id = "solo-1", shareCode = null))
        dbRule.listDao.insertList(listEntity(id = "orphan-1", accountId = null))
        dbRule.listDao.insertList(listEntity(id = "guest-1", shareCode = null, accountId = null, onServer = false))
        syncPoints.set("shared-1", t10)

        val result = engine.evictAccountLists()

        assertTrue(result is Result.Success)
        assertEquals("queued edit pushed before eviction", listOf("i1"), remote.upsertedItems.map { it.id })
        assertNull(dbRule.listDao.getListRaw("shared-1"))
        assertNull("its items gone via CASCADE", dbRule.itemDao.getItemRaw("i1"))
        assertNull(dbRule.listDao.getListRaw("solo-1"))
        assertNull(dbRule.listDao.getListRaw("orphan-1"))
        assertNotNull("guest list untouched", dbRule.listDao.getListRaw("guest-1"))
        assertTrue("sync points wiped", syncPoints.points.isEmpty())
    }

    @Test
    fun `eviction still happens when the final push cannot run`() = runTest {
        userId = null // signed-out-ish: push no-ops, eviction must proceed
        dbRule.listDao.insertList(listEntity(id = "shared-1"))

        engine.evictAccountLists()

        assertNull(dbRule.listDao.getListRaw("shared-1"))
    }

    @Test
    fun `eviction still happens when the final push fails`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "shared-1", pendingSync = true))
        remote.failUpserts = true

        val result = engine.evictAccountLists()

        assertTrue(result is Result.Success)
        assertNull(dbRule.listDao.getListRaw("shared-1"))
    }

    // The sign-out runs inside the eviction: a sync kick waiting on the lock
    // would otherwise run between the two, with the session still live, and
    // land the lists again.
    @Test
    fun `eviction runs the sign-out after the lists are gone, in the same step`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "shared-1"))
        var listsLeftAtSignOut: Int? = null

        engine.evictAccountLists {
            listsLeftAtSignOut = listOfNotNull(dbRule.listDao.getListRaw("shared-1")).size
        }

        assertEquals(0, listsLeftAtSignOut)
    }

    // Eviction while discovery is mid-request: without the lock the catch-up
    // would land the account's lists again after they had been evicted.
    @Test
    fun `an eviction waits for a catch-up in flight instead of racing it`() = runTest {
        remote.listRows = listOf(listRow(id = "l1"))
        val gate = CompletableDeferred<Unit>()
        remote.fetchAllGate = gate

        val catchUp = launch { engine.catchUpAccount() }
        remote.fetchAllEntered.await()
        val evict = launch { engine.evictAccountLists() }
        gate.complete(Unit)
        catchUp.join()
        evict.join()

        assertNull(dbRule.listDao.getListRaw("l1"))
    }

    @Test
    fun `unsynced changes are reported until they are pushed`() = runTest {
        dbRule.listDao.insertList(listEntity())
        assertFalse(engine.hasUnsyncedChanges("user-1"))

        dbRule.itemDao.insertItem(itemEntity(pendingSync = true))
        assertTrue(engine.hasUnsyncedChanges("user-1"))

        engine.push()
        assertFalse(engine.hasUnsyncedChanges("user-1"))
    }

    @Test
    fun `a queued list edit counts as unsynced`() = runTest {
        dbRule.listDao.insertList(listEntity(pendingSync = true))

        assertTrue(engine.hasUnsyncedChanges("user-1"))
    }

    // A released first-run claim or another account's leftover is pending but
    // can never be pushed by this account: warning about it on every sign-out
    // would be a warning nothing could ever clear.
    @Test
    fun `rows this account can never push do not count as unsynced`() = runTest {
        dbRule.listDao.insertList(listEntity(id = "stray", accountId = null, pendingSync = true))
        dbRule.itemDao.insertItem(itemEntity(id = "stray-item", listId = "stray", pendingSync = true))
        dbRule.listDao.insertList(listEntity(id = "theirs", accountId = "user-2", pendingSync = true))

        assertFalse(engine.hasUnsyncedChanges("user-1"))
    }
}
