package com.babegetthis.android.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babegetthis.android.core.data.local.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

// @Dao = Data Access Object. This is where you write your database queries.
// Like writing raw SQL in sqflite, but Room validates them at compile time.
// Flow = a Kotlin Stream. Whenever the data changes, the UI updates automatically.

@Dao
interface CategoryDao {

    // Flow means this is "live" — any insert/delete auto-updates listeners
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity)

    // Bulk insert for pre-populating defaults
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    // Only allow deleting user-created categories
    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteCategory(id: String)
}
