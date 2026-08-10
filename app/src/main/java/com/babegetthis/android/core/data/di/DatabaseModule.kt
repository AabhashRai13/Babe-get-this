package com.babegetthis.android.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.data.local.DEFAULT_CATEGORIES
import com.babegetthis.android.core.data.local.MIGRATION_1_2
import com.babegetthis.android.core.data.local.MIGRATION_2_3
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        // Provider<AppDatabase> = lazy reference to the database being created.
        // We can't use AppDatabase directly here because it hasn't been built yet
        // when the callback fires. Provider delays access until it's ready.
        // Like a late final in Dart — it exists but isn't initialized yet.
        databaseProvider: Provider<AppDatabase>,
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "babe_get_this.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        // Use the SAME database instance, not a new one
                        databaseProvider.get().categoryDao().insertAll(DEFAULT_CATEGORIES)
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideShoppingListDao(database: AppDatabase): ShoppingListDao {
        return database.shoppingListDao()
    }

    @Provides
    fun provideShoppingItemDao(database: AppDatabase): ShoppingItemDao {
        return database.shoppingItemDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }
}
