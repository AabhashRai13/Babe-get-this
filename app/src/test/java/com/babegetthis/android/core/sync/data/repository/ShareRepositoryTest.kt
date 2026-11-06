package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeListOwnershipStore
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.unreportedSafeCall
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class ShareRepositoryTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private val ownership = FakeListOwnershipStore()
    private var userId: String? = "user-1"
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints, ownership, {
            userId
        }, mockk(relaxed = true), unreportedSafeCall())
    }
    private val repository by lazy {
        ShareRepository(dbRule.listDao, remote, engine, { userId }, unreportedSafeCall(), Random(seed = 7))
    }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    // Sharing only happens signed in, so every list here is an account list
    // (docs/technical-decisions/008); onServer says whether it has synced yet.
    private fun accountList(id: String = "list-1", onServer: Boolean = true, pendingSync: Boolean = false) =
        ShoppingListEntity(
            id = id,
            name = "Groceries",
            createdAt = 1L,
            updatedAt = 2L,
            accountId = "user-1",
            onServer = onServer,
            pendingSync = pendingSync,
        )

    private fun localItem(id: String = "item-1") = ShoppingItemEntity(
        id = id,
        listId = "list-1",
        name = "Milk",
        quantity = "1",
        createdAt = 1L,
        updatedAt = 2L,
    )

    // --- share --------------------------------------------------------------

    @Test
    fun `sharing a list already on the server sets its code and keeps its items`() = runTest {
        dbRule.listDao.insertList(accountList())
        dbRule.itemDao.insertItem(localItem("item-1"))
        dbRule.itemDao.insertItem(localItem("item-2"))

        val code = repository.share("list-1").data()

        assertTrue("code from the safe alphabet", code.matches(Regex("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$")))
        assertEquals(listOf("list-1" to code), remote.shareCodeRequests)
        assertTrue("the row already exists", remote.insertedLists.isEmpty())
        assertTrue("items already synced with the list", remote.upsertedItems.isEmpty())
        assertEquals(code, dbRule.listDao.getListRaw("list-1")!!.shareCode)
        assertNotNull("no REPLACE cascade", dbRule.itemDao.getItemRaw("item-1"))
        assertNotNull(dbRule.itemDao.getItemRaw("item-2"))
    }

    @Test
    fun `sharing a list not yet on the server pushes it first`() = runTest {
        dbRule.listDao.insertList(accountList(onServer = false, pendingSync = true))

        val code = repository.share("list-1").data()

        assertEquals(listOf("list-1"), remote.insertedLists.map { it.id })
        assertEquals(listOf("list-1" to code), remote.shareCodeRequests)
        assertTrue(dbRule.listDao.getListRaw("list-1")!!.onServer)
    }

    @Test
    fun `sharing offline fails cleanly and shows no code`() = runTest {
        dbRule.listDao.insertList(accountList(onServer = false, pendingSync = true))
        remote.failUpserts = true

        val error = repository.share("list-1").error()

        assertEquals(AppError.NetworkError("Couldn't share — check your connection."), error)
        assertTrue(remote.shareCodeRequests.isEmpty())
        assertNull(dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `when another phone shared first, its code wins`() = runTest {
        dbRule.listDao.insertList(accountList())
        remote.serverShareCode = "ZZZ999"

        val code = repository.share("list-1").data()

        assertEquals("ZZZ999", code)
        assertEquals("ZZZ999", dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share fails cleanly when the server row cannot be read`() = runTest {
        dbRule.listDao.insertList(accountList())
        remote.shareCodeRowMissing = true

        val error = repository.share("list-1").error()

        assertTrue(error is AppError.UnknownError)
        assertNull(dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share is idempotent — the code is static per list`() = runTest {
        dbRule.listDao.insertList(accountList())

        val first = repository.share("list-1").data()
        val second = repository.share("list-1").data()

        assertEquals(first, second)
        assertEquals("no second request on reopen", 1, remote.shareCodeRequests.size)
    }

    @Test
    fun `share requires sign-in and uploads nothing without it`() = runTest {
        userId = null
        dbRule.listDao.insertList(accountList(onServer = false, pendingSync = true))

        val error = repository.share("list-1").error()

        assertTrue(error is AppError.AuthError)
        assertTrue(remote.insertedLists.isEmpty())
        assertTrue(remote.shareCodeRequests.isEmpty())
        assertNull(dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share of a missing list is a NotFoundError`() = runTest {
        assertTrue(repository.share("ghost").error() is AppError.NotFoundError)
    }

    @Test
    fun `a failed share leaves the list unshared so it can be retried`() = runTest {
        dbRule.listDao.insertList(accountList())
        remote.failUpserts = true

        val result = repository.share("list-1")

        assertTrue(result is Result.Error)
        assertNull("server first, local second", dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    // --- join ---------------------------------------------------------------

    @Test
    fun `join trims the code, becomes a member, and lands the replica in Room`() = runTest {
        remote.joinResult = "list-9"
        remote.listRows = listOf(
            ListRow(id = "list-9", name = "Their list", shareCode = "ABC234", updatedAt = "2026-08-01T10:00:00Z"),
        )
        remote.itemRows = listOf(
            ItemRow(
                id = "item-9",
                listId = "list-9",
                name = "Milk",
                quantity = "1",
                updatedAt = "2026-08-01T10:00:00Z",
            ),
        )

        val listId = repository.join("  abc234  ").data()

        assertEquals("list-9", listId)
        assertEquals("abc234", remote.joinCalledWith)
        assertEquals("Their list", dbRule.listDao.getListRaw("list-9")!!.name)
        assertEquals("Milk", dbRule.itemDao.getItemRaw("item-9")!!.name)
    }

    @Test
    fun `re-join refreshes the replica instead of no-oping from a current sync point`() = runTest {
        // Already a member with an already-current high-water mark — the exact
        // state where plain catchUp fetches nothing (BUG 2 from device testing).
        remote.joinResult = "list-9"
        syncPoints.set("list-9", "2026-08-08T12:00:00Z")
        remote.listRows = listOf(
            ListRow(id = "list-9", name = "Their list", shareCode = "ABC234", updatedAt = "2026-08-01T10:00:00Z"),
        )

        repository.join("ABC234")

        assertNull("full pull — the stored mark must not gate the fetch", remote.lastListSince)
        assertEquals("Their list", dbRule.listDao.getListRaw("list-9")!!.name)
    }

    @Test
    fun `join with an unknown code is a NotFoundError`() = runTest {
        remote.joinResult = null

        assertTrue(repository.join("XXXXXX").error() is AppError.NotFoundError)
    }

    @Test
    fun `join surfaces a failed initial pull`() = runTest {
        remote.joinResult = "list-9"
        remote.failFetches = true

        assertTrue(repository.join("ABC234") is Result.Error)
    }
}
