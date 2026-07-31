package com.babegetthis.android.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.data.local.AppDatabase
import org.junit.rules.ExternalResource

// A fresh in-memory AppDatabase per test.
//
// Repository tests run against real SQL rather than mocked DAOs, because the
// behavior worth testing there IS the SQL: the CASCADE on list delete, the
// atomic check-and-delete in deleteListIfEmpty, the item-count aggregation in
// getAllListsWithItemCount. A mocked DAO would assert that we call the method we
// wrote, which proves nothing.
//
// Foreign keys are enabled explicitly — SQLite has them OFF by default, and the
// undo-delete flow depends entirely on the CASCADE firing.
class InMemoryDatabaseRule : ExternalResource() {

    lateinit var db: AppDatabase
        private set

    val listDao get() = db.shoppingListDao()
    val itemDao get() = db.shoppingItemDao()
    val categoryDao get() = db.categoryDao()

    override fun before() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        )
            // Room's default is a background executor; tests need writes to land
            // before the next assertion reads them.
            .allowMainThreadQueries()
            .setQueryCallback({ _, _ -> }, Runnable::run)
            .build()
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
    }

    override fun after() {
        db.close()
    }
}
