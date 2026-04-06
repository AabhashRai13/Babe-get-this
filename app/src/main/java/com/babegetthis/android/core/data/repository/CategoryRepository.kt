package com.babegetthis.android.core.data.repository

import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.data.mapper.toDomain
import com.babegetthis.android.core.data.mapper.toEntity
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
            name = name,
            isDefault = false,
        )
        categoryDao.insertCategory(category.toEntity())
        id
    }

    suspend fun deleteCategory(id: String): Result<Unit> = safeCall {
        categoryDao.deleteCategory(id)
    }
}
