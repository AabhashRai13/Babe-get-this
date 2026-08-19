package com.babegetthis.android.core.sync.data.repository

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.sync.data.model.ItemRow
import com.babegetthis.android.core.sync.data.model.ListRow
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity
import com.babegetthis.android.testing.FakeSharedListRemote
import com.babegetthis.android.testing.FakeSyncPointStore
import com.babegetthis.android.testing.InMemoryDatabaseRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random
import io.mockk.mockk

@RunWith(RobolectricTestRunner::class)
class ShareRepositoryTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val remote = FakeSharedListRemote()
    private val syncPoints = FakeSyncPointStore()
    private var userId: String? = "user-1"
    private val engine by lazy {
        SyncEngine(dbRule.listDao, dbRule.itemDao, remote, syncPoints, { userId }, mockk(relaxed = true))
    }
    private val repository by lazy {
        ShareRepository(dbRule.listDao, dbRule.itemDao, remote, engine, { userId }, Random(seed = 7))
    }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    private fun localList(id: String = "list-1") = ShoppingListEntity(
        id = id, name = "Groceries", createdAt = 1L, updatedAt = 2L,
    )

    private fun localItem(id: String = "item-1") = ShoppingItemEntity(
        id = id, listId = "list-1", name = "Milk", quantity = "1",
        createdAt = 1L, updatedAt = 2L,
    )

    // --- share --------------------------------------------------------------

    @Test
    fun `share uploads the list and items and persists a well-formed code`() = runTest {
        dbRule.listDao.insertList(localList())
        dbRule.itemDao.insertItem(localItem())

        val code = repository.share("list-1").data()

        assertTrue("code from the safe alphabet", code.matches(Regex("^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}$")))
        assertEquals("first share INSERTS, never upserts", code, remote.insertedLists.single().shareCode)
        assertEquals("item-1", remote.upsertedItems.single().id)
        assertEquals(code, dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share adopts the server's code when a half-finished share left one behind`() = runTest {
        dbRule.listDao.insertList(localList())
        remote.listAlreadyExists = true
        remote.listRows = listOf(
            ListRow(id = "list-1", name = "Groceries", shareCode = "OLDCOD", updatedAt = "2026-08-01T10:00:00Z"),
        )

        val code = repository.share("list-1").data()

        assertEquals("server row is the truth", "OLDCOD", code)
        assertEquals("OLDCOD", dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share fails cleanly when the leftover server row cannot be read`() = runTest {
        dbRule.listDao.insertList(localList())
        remote.listAlreadyExists = true

        val error = repository.share("list-1").error()

        assertTrue(error is AppError.UnknownError)
        assertNull(dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share is idempotent — the code is static per list`() = runTest {
        dbRule.listDao.insertList(localList())

        val first = repository.share("list-1").data()
        val second = repository.share("list-1").data()

        assertEquals(first, second)
        assertEquals("no re-upload on reopen", 1, remote.insertedLists.size)
    }

    @Test
    fun `share with no items skips the item upload`() = runTest {
        dbRule.listDao.insertList(localList())

        repository.share("list-1").data()

        assertTrue(remote.upsertedItems.isEmpty())
    }

    @Test
    fun `share requires sign-in and uploads nothing without it`() = runTest {
        userId = null
        dbRule.listDao.insertList(localList())

        val error = repository.share("list-1").error()

        assertTrue(error is AppError.AuthError)
        assertTrue(remote.insertedLists.isEmpty())
        assertNull(dbRule.listDao.getListRaw("list-1")!!.shareCode)
    }

    @Test
    fun `share of a missing list is a NotFoundError`() = runTest {
        assertTrue(repository.share("ghost").error() is AppError.NotFoundError)
    }

    @Test
    fun `failed upload leaves the list local-only so share can be retried`() = runTest {
        dbRule.listDao.insertList(localList())
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
            ItemRow(id = "item-9", listId = "list-9", name = "Milk", quantity = "1", updatedAt = "2026-08-01T10:00:00Z"),
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
