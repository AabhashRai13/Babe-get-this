package com.babegetthis.android.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.data.local.model.CategoryEntity
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity

@Database(
    entities = [
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        CategoryEntity::class,
    ],
    version = 2,
    // exportSchema stays false: Room 2.8.4's schema-JSON exporter uses a
    // kotlinx-serialization build that AbstractMethodErrors against this
    // project's serialization plugin on the KSP classpath. The migration is
    // guarded by MigrationTest (runs MIGRATION_1_2 against a real v1 table)
    // instead of a diffed schema baseline.
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun categoryDao(): CategoryDao
}
