package com.babegetthis.android.feature.shoppingitems.data

import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.feature.shoppingitems.data.mapper.toDomain
import com.babegetthis.android.feature.shoppingitems.data.mapper.toEntity
import com.babegetthis.android.feature.shoppingitems.data.repository.ShoppingItemRepository
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Mappers, DAO and repository for shoppingitems. DAO and repository run against
// a real in-memory database — the ordering SQL and the category join are the
// behavior worth testing, and a mock would only echo them back.
@RunWith(RobolectricTestRunner::class)
class ShoppingItemDataTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private var kicks = 0
    private val repository by lazy {
        ShoppingItemRepository(dbRule.itemDao, dbRule.categoryDao, dbRule.listDao) { kicks++ }
    }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    private suspend fun seedList(id: String = "list-1") =
        dbRule.listDao.insertList(TestData.listEntity(id = id))

    // --- mappers ---

    @Test
    fun `entity toDomain carries every stored field`() {
        val domain = TestData.itemEntity(
            id = "i1", listId = "l1", name = "Milk", quantity = "2",
            isPickedUp = true, categoryId = "c1", shop = "Aldi", note = "semi",
        ).toDomain()

        assertEquals("i1", domain.id)
        assertEquals("l1", domain.listId)
        assertEquals("Milk", domain.name)
        assertEquals("2", domain.quantity)
        assertTrue(domain.isPickedUp)
        assertEquals("c1", domain.categoryId)
        assertEquals("Aldi", domain.shop)
        assertEquals("semi", domain.note)
    }

    // categoryName is resolved by a join, not stored — it arrives as a parameter.
    @Test
    fun `entity toDomain leaves categoryName null unless supplied`() {
        assertNull(TestData.itemEntity(categoryId = "c1").toDomain().categoryName)
    }

    @Test
    fun `entity toDomain takes the supplied categoryName`() {
        assertEquals("Dairy", TestData.itemEntity().toDomain(categoryName = "Dairy").categoryName)
    }

    @Test
    fun `nullable fields survive as null`() {
        val domain = TestData.itemEntity(categoryId = null, shop = null, note = null).toDomain()

        assertNull(domain.categoryId)
        assertNull(domain.shop)
        assertNull(domain.note)
    }

    @Test
    fun `toEntity round-trips`() {
        val entity = TestData.itemEntity(shop = "Aldi", note = "semi", categoryId = "c1")

        assertEquals(entity, entity.toDomain().toEntity())
    }

    // categoryName is derived, so it has nowhere to go on the way back down.
    @Test
    fun `toEntity drops the derived categoryName`() {
        val entity = TestData.item(categoryName = "Dairy").toEntity()

        assertEquals(TestData.itemEntity(), entity)
    }

    // --- DAO ordering ---

    // shop (nulls last) -> category (nulls last) -> name.
    @Test
    fun `items are ordered by shop then category then name`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "no-shop", name = "Apple", shop = null))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "b-shop", name = "Zucchini", shop = "Bakery"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "a-shop", name = "Bread", shop = "Aldi"))

        val ids = dbRule.itemDao.getItemsByListId("list-1").first().map { it.id }

        assertEquals(listOf("a-shop", "b-shop", "no-shop"), ids)
    }

    @Test
    fun `within one shop, nulls sort after categorised items`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(
            TestData.itemEntity(id = "uncat", name = "Apple", shop = "Aldi", categoryId = null)
        )
        dbRule.itemDao.insertItem(
            TestData.itemEntity(id = "cat", name = "Zucchini", shop = "Aldi", categoryId = "c1")
        )

        val ids = dbRule.itemDao.getItemsByListId("list-1").first().map { it.id }

        assertEquals(listOf("cat", "uncat"), ids)
    }

    @Test
    fun `name breaks the tie last`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "z", name = "Zucchini", shop = "Aldi"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "a", name = "Apple", shop = "Aldi"))

        assertEquals(
            listOf("a", "z"),
            dbRule.itemDao.getItemsByListId("list-1").first().map { it.id },
        )
    }

    @Test
    fun `items are scoped to their list`() = runTest {
        seedList("l1")
        seedList("l2")
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", listId = "l1"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i2", listId = "l2"))

        assertEquals(listOf("i1"), dbRule.itemDao.getItemsByListId("l1").first().map { it.id })
    }

    @Test
    fun `updatePickedUpStatus writes the flag and a new timestamp`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", isPickedUp = false, updatedAt = 1L))

        dbRule.itemDao.updatePickedUpStatus("i1", isPickedUp = true, updatedAt = 999L, pendingSync = false)

        val stored = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertTrue(stored.isPickedUp)
        assertEquals(999L, stored.updatedAt)
    }

    @Test
    fun `insertItem replaces on a conflicting id`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", name = "First"))
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", name = "Second"))

        val stored = dbRule.itemDao.getItemsByListIdOnce("list-1")
        assertEquals(1, stored.size)
        assertEquals("Second", stored.single().name)
    }

    // --- repository ---

    @Test
    fun `addItem stores the item and returns its id`() = runTest {
        seedList()

        val id = repository.addItem("list-1", "Milk", "2").data()

        assertEquals("Milk", dbRule.itemDao.getItemsByListIdOnce("list-1").single().name)
        assertTrue(id.isNotBlank())
    }

    @Test
    fun `addItem stores every optional field`() = runTest {
        seedList()

        repository.addItem("list-1", "Milk", "2", categoryId = "c1", shop = "Aldi", note = "semi")

        val stored = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertEquals("c1", stored.categoryId)
        assertEquals("Aldi", stored.shop)
        assertEquals("semi", stored.note)
    }

    @Test
    fun `addItem leaves omitted optional fields null`() = runTest {
        seedList()

        repository.addItem("list-1", "Milk", "2")

        val stored = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertNull(stored.categoryId)
        assertNull(stored.shop)
        assertNull(stored.note)
    }

    @Test
    fun `addItem trims the name`() = runTest {
        seedList()

        repository.addItem("list-1", "  Milk  ", "2")

        assertEquals("Milk", dbRule.itemDao.getItemsByListIdOnce("list-1").single().name)
    }

    @Test
    fun `addItem rejects a blank name`() = runTest {
        seedList()

        val error = repository.addItem("list-1", "   ", "2").error()

        assertTrue(error is AppError.ValidationError)
        assertTrue(dbRule.itemDao.getItemsByListIdOnce("list-1").isEmpty())
    }

    @Test
    fun `addItem to a list that does not exist is a DatabaseError`() = runTest {
        val result = repository.addItem("no-such-list", "Milk", "1")

        assertTrue(result is Result.Error)
        assertTrue(result.error() is AppError.DatabaseError)
    }

    @Test
    fun `getItemsByListId resolves the category name`() = runTest {
        seedList()
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", name = "Dairy"))
        dbRule.itemDao.insertItem(TestData.itemEntity(categoryId = "c1"))

        assertEquals("Dairy", repository.getItemsByListId("list-1").first().single().categoryName)
    }

    @Test
    fun `an unknown category id resolves to no name`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(categoryId = "gone"))

        assertNull(repository.getItemsByListId("list-1").first().single().categoryName)
    }

    @Test
    fun `updateItem writes the change and bumps updatedAt`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", name = "Milk", updatedAt = 1L))
        val existing = repository.getItemsByListId("list-1").first().single()

        repository.updateItem(existing.copy(name = "Oat milk"))

        val stored = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertEquals("Oat milk", stored.name)
        assertNotEquals(1L, stored.updatedAt)
    }

    @Test
    fun `togglePickedUp flips the flag both ways`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", isPickedUp = false))

        repository.togglePickedUp("i1", true)
        assertTrue(dbRule.itemDao.getItemsByListIdOnce("list-1").single().isPickedUp)

        repository.togglePickedUp("i1", false)
        assertTrue(!dbRule.itemDao.getItemsByListIdOnce("list-1").single().isPickedUp)
    }

    @Test
    fun `deleteItem removes it`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1"))

        repository.deleteItem("i1")

        assertTrue(dbRule.itemDao.getItemsByListIdOnce("list-1").isEmpty())
    }

    @Test
    fun `restoreItem puts it back under its original id`() = runTest {
        seedList()
        dbRule.itemDao.insertItem(TestData.itemEntity(id = "i1", name = "Milk"))
        val original = repository.getItemsByListId("list-1").first().single()
        repository.deleteItem("i1")

        repository.restoreItem(original)

        val restored = dbRule.itemDao.getItemsByListIdOnce("list-1").single()
        assertEquals("i1", restored.id)
        assertEquals("Milk", restored.name)
    }
}
