package com.babegetthis.android.core.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.data.mapper.toDomain
import com.babegetthis.android.core.data.mapper.toEntity
import com.babegetthis.android.core.error.AppError
import com.babegetthis.android.core.error.AppErrorException
import com.babegetthis.android.core.error.Result
import com.babegetthis.android.core.error.safeCall
import com.babegetthis.android.core.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) {
    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun addCategory(name: String): Result<String> = safeCall {
        val id = UUID.randomUUID().toString()
        val category = Category(
            id = id,
            // Trimmed and rejected when blank, matching ShoppingListRepository and
            // ShoppingItemRepository. This is reached from the add-item dialog's
            // inline "new category" field, so a stray space would otherwise create
            // a category with no visible name that can never be picked out again.
            name = name.requireCategoryName(),
            isDefault = false,
        )
        categoryDao.insertCategory(category.toEntity())
        id
    }

    // NOTE: the DAO's DELETE is guarded by `AND isDefault = 0`, so asking to
    // delete a shipped default is a silent no-op that still reports Success. The
    // UI never offers it, so this is a latent inconsistency rather than a bug —
    // pinned by a test so it stays known.
    suspend fun deleteCategory(id: String): Result<Unit> = safeCall {
        categoryDao.deleteCategory(id)
    }

    private fun String.requireCategoryName(): String {
        val trimmed = trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.ValidationError("Category name can't be empty."))
        }
        return trimmed
    }
}
