package com.babegetthis.android.core.data

import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.data.mapper.toDomain
import com.babegetthis.android.core.data.mapper.toEntity
import com.babegetthis.android.core.data.repository.CategoryRepository
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.testing.InMemoryDatabaseRule
import com.babegetthis.android.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryDataTest {

    @get:Rule val dbRule = InMemoryDatabaseRule()

    private val repository by lazy { CategoryRepository(dbRule.categoryDao) }

    private fun <T> Result<T>.data(): T = (this as Result.Success).data
    private fun <T> Result<T>.error(): AppError = (this as Result.Error).error

    // --- mappers ---

    @Test
    fun `entity toDomain carries every field`() {
        val domain = TestData.categoryEntity(id = "c1", name = "Dairy", isDefault = true).toDomain()

        assertEquals("c1", domain.id)
        assertEquals("Dairy", domain.name)
        assertTrue(domain.isDefault)
    }

    @Test
    fun `a user-created category maps as not default`() {
        assertFalse(TestData.categoryEntity(isDefault = false).toDomain().isDefault)
    }

    @Test
    fun `toEntity round-trips`() {
        val entity = TestData.categoryEntity(id = "c9", name = "Bakery", isDefault = false)

        assertEquals(entity, entity.toDomain().toEntity())
    }

    // --- DAO ---

    @Test
    fun `categories come back in alphabetical order`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", name = "Zucchini"))
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c2", name = "Apples"))
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c3", name = "Milk"))

        val names = dbRule.categoryDao.getAllCategories().first().map { it.name }

        assertEquals(listOf("Apples", "Milk", "Zucchini"), names)
    }

    // IGNORE, not REPLACE — re-seeding defaults on every launch must not clobber
    // a row the user has since interacted with.
    @Test
    fun `inserting a conflicting id is ignored rather than replacing`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", name = "First"))
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", name = "Second"))

        val stored = dbRule.categoryDao.getAllCategories().first()
        assertEquals(1, stored.size)
        assertEquals("First", stored.single().name)
    }

    @Test
    fun `insertAll seeds many at once`() = runTest {
        dbRule.categoryDao.insertAll(DEFAULT_CATEGORIES)

        assertEquals(DEFAULT_CATEGORIES.size, dbRule.categoryDao.getAllCategories().first().size)
    }

    @Test
    fun `seeding twice does not duplicate`() = runTest {
        dbRule.categoryDao.insertAll(DEFAULT_CATEGORIES)
        dbRule.categoryDao.insertAll(DEFAULT_CATEGORIES)

        assertEquals(DEFAULT_CATEGORIES.size, dbRule.categoryDao.getAllCategories().first().size)
    }

    @Test
    fun `a user category can be deleted`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", isDefault = false))

        dbRule.categoryDao.deleteCategory("c1")

        assertTrue(dbRule.categoryDao.getAllCategories().first().isEmpty())
    }

    // The DELETE is guarded by `AND isDefault = 0`, so a shipped default is
    // protected at the SQL level rather than by UI discipline.
    @Test
    fun `a default category cannot be deleted`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", isDefault = true))

        dbRule.categoryDao.deleteCategory("c1")

        assertEquals(1, dbRule.categoryDao.getAllCategories().first().size)
    }

    // --- repository ---

    @Test
    fun `addCategory stores it and returns the new id`() = runTest {
        val id = repository.addCategory("Dairy").data()

        val stored = repository.getAllCategories().first().single()
        assertEquals(id, stored.id)
        assertEquals("Dairy", stored.name)
        assertFalse("user-created categories are never default", stored.isDefault)
    }

    @Test
    fun `addCategory trims the name`() = runTest {
        repository.addCategory("  Dairy  ")

        assertEquals("Dairy", repository.getAllCategories().first().single().name)
    }

    @Test
    fun `addCategory rejects a blank name`() = runTest {
        val error = repository.addCategory("   ").error()

        assertTrue(error is AppError.ValidationError)
        assertTrue(repository.getAllCategories().first().isEmpty())
    }

    @Test
    fun `two categories may share a name`() = runTest {
        // Nothing enforces uniqueness; ids differ. Pinned so a future unique
        // constraint is a deliberate change.
        repository.addCategory("Dairy")
        repository.addCategory("Dairy")

        assertEquals(2, repository.getAllCategories().first().size)
    }

    @Test
    fun `getAllCategories is empty to start`() = runTest {
        assertTrue(repository.getAllCategories().first().isEmpty())
    }

    @Test
    fun `deleteCategory removes a user category`() = runTest {
        val id = repository.addCategory("Dairy").data()

        repository.deleteCategory(id)

        assertTrue(repository.getAllCategories().first().isEmpty())
    }

    // Latent inconsistency, pinned: the DAO refuses to delete a default, but the
    // repository still reports Success, so a caller cannot tell nothing happened.
    @Test
    fun `deleting a default reports success even though nothing is removed`() = runTest {
        dbRule.categoryDao.insertCategory(TestData.categoryEntity(id = "c1", isDefault = true))

        val result = repository.deleteCategory("c1")

        assertTrue(result is Result.Success)
        assertEquals(1, repository.getAllCategories().first().size)
    }

    @Test
    fun `deleting an id that does not exist also reports success`() = runTest {
        assertTrue(repository.deleteCategory("nope") is Result.Success)
    }

    // --- DEFAULT_CATEGORIES ---

    @Test
    fun `the seed set is not empty`() {
        assertTrue(DEFAULT_CATEGORIES.isNotEmpty())
    }

    // Fixed ids, so they stay stable across installs — voice transcription maps
    // backend category slugs onto exactly these.
    @Test
    fun `seed ids are unique`() {
        assertEquals(DEFAULT_CATEGORIES.size, DEFAULT_CATEGORIES.map { it.id }.toSet().size)
    }

    @Test
    fun `seed names are unique`() {
        assertEquals(DEFAULT_CATEGORIES.size, DEFAULT_CATEGORIES.map { it.name }.toSet().size)
    }

    @Test
    fun `every seed entry is flagged default`() {
        assertTrue(DEFAULT_CATEGORIES.all { it.isDefault })
    }

    @Test
    fun `no seed entry has a blank name or id`() {
        assertTrue(DEFAULT_CATEGORIES.none { it.id.isBlank() || it.name.isBlank() })
    }
}
