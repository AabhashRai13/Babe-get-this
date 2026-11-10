package com.babegetthis.android.feature.gotthis.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.babegetthis.android.core.data.local.AppDatabase
import com.babegetthis.android.core.data.local.MIGRATION_1_2
import com.babegetthis.android.core.data.local.MIGRATION_2_3
import com.babegetthis.android.core.data.local.MIGRATION_3_4
import com.babegetthis.android.core.data.local.MIGRATION_4_5
import com.babegetthis.android.core.data.local.MIGRATION_5_6
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// MIGRATION_5_6 has no schema JSON to be diffed against (exportSchema is
// false), so this opens a hand-built version-5 file through Room: Room itself
// validates the migrated tables, columns and indexes against the entities and
// throws "Migration didn't properly handle" on any difference.
@RunWith(RobolectricTestRunner::class)
class Migration5To6Test {

    private val dbName = "migration-5-6-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    private fun buildV5() {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE shopping_lists (" +
                                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                                "isLocked INTEGER NOT NULL, shareCode TEXT, deletedAt INTEGER, " +
                                "pendingSync INTEGER NOT NULL, accountId TEXT, onServer INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE shopping_items (" +
                                "id TEXT NOT NULL PRIMARY KEY, listId TEXT NOT NULL, name TEXT NOT NULL, " +
                                "quantity TEXT NOT NULL, isPickedUp INTEGER NOT NULL, categoryId TEXT, " +
                                "shop TEXT, note TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                                "deletedAt INTEGER, pendingSync INTEGER NOT NULL, " +
                                "FOREIGN KEY(listId) REFERENCES shopping_lists(id) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE)",
                        )
                        db.execSQL("CREATE INDEX index_shopping_items_listId ON shopping_items (listId)")
                        db.execSQL(
                            "CREATE TABLE categories (" +
                                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, isDefault INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "INSERT INTO shopping_lists VALUES ('l1', 'Groceries', 1, 2, 0, NULL, NULL, 0, NULL, 0)",
                        )
                        db.execSQL(
                            "INSERT INTO shopping_items VALUES " +
                                "('i1', 'l1', 'Milk', '1', 1, NULL, NULL, NULL, 1, 2, NULL, 0)",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun `version 5 file opens through Room and every new table is readable`() = runTest {
        buildV5()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val item = db.shoppingItemDao().getItemRaw("i1")!!
            assertEquals("Milk", item.name)
            assertTrue(item.isPickedUp)
            assertNull(item.pickedUpAt)
            assertEquals("Groceries", db.shoppingListDao().getListRaw("l1")?.name)

            assertTrue(db.purchaseDao().all().isEmpty())
            assertEquals(0, db.purchaseDao().countOutbox("u1"))
            assertTrue(db.gotThisStateDao().mutes("u1").isEmpty())
            assertNull(db.gotThisStateDao().impression("i1"))
            assertTrue(db.gotThisStateDao().followUps("u1").isEmpty())
        } finally {
            db.close()
        }
    }
}
