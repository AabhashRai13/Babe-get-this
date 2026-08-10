package com.babegetthis.android.core.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Migrations are additive but there is no cloud backup, so a broken migration
// means permanent data loss. Each test exercises one migration's migrate()
// directly against a hand-built old-version table and asserts rows survive
// with correct defaults. No schema JSON baseline needed — we don't route
// through Room's open/validate machinery, just the migration SQL that could
// actually destroy data.
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun cleanup() {
        context.deleteDatabase(testDb)
    }

    @Test
    fun migrate1To2_keepsExistingListsAndDefaultsUnlocked() {
        context.deleteDatabase(testDb)

        // Build the exact v1 schema and seed a list.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE shopping_lists (" +
                                "id TEXT NOT NULL PRIMARY KEY, " +
                                "name TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, " +
                                "updatedAt INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO shopping_lists (id, name, createdAt, updatedAt) " +
                                "VALUES ('list-a', 'Groceries', 100, 200)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        MIGRATION_1_2.migrate(db)

        db.query("SELECT name, createdAt, isLocked FROM shopping_lists WHERE id = 'list-a'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Groceries", c.getString(0))
            assertEquals(100L, c.getLong(1))
            assertEquals(0, c.getInt(2)) // new column defaults to unlocked
        }
        db.close()
    }

    @Test
    fun migrate2To3_keepsDataAndDefaultsToLocalOnlyNotDirty() {
        context.deleteDatabase(testDb)

        // Build the exact v2 schema (v1 + isLocked) and seed a list + item.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(testDb)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE shopping_lists (" +
                                "id TEXT NOT NULL PRIMARY KEY, " +
                                "name TEXT NOT NULL, " +
                                "createdAt INTEGER NOT NULL, " +
                                "updatedAt INTEGER NOT NULL, " +
                                "isLocked INTEGER NOT NULL DEFAULT 0)"
                        )
                        db.execSQL(
                            "CREATE TABLE shopping_items (" +
                                "id TEXT NOT NULL PRIMARY KEY, " +
                                "listId TEXT NOT NULL, " +
                                "name TEXT NOT NULL, " +
                                "quantity TEXT NOT NULL, " +
                                "isPickedUp INTEGER NOT NULL DEFAULT 0, " +
                                "categoryId TEXT, " +
                                "shop TEXT, " +
                                "note TEXT, " +
                                "createdAt INTEGER NOT NULL, " +
                                "updatedAt INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO shopping_lists (id, name, createdAt, updatedAt, isLocked) " +
                                "VALUES ('list-a', 'Groceries', 100, 200, 1)"
                        )
                        db.execSQL(
                            "INSERT INTO shopping_items (id, listId, name, quantity, createdAt, updatedAt) " +
                                "VALUES ('item-a', 'list-a', 'Milk', '2', 100, 200)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        MIGRATION_2_3.migrate(db)

        // List survives; new columns default to "local-only, alive, not dirty".
        db.query(
            "SELECT name, isLocked, shareCode, deletedAt, pendingSync " +
                "FROM shopping_lists WHERE id = 'list-a'"
        ).use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Groceries", c.getString(0))
            assertEquals(1, c.getInt(1)) // lock preserved
            assertTrue(c.isNull(2)) // shareCode NULL = local-only
            assertTrue(c.isNull(3)) // no tombstone
            assertEquals(0, c.getInt(4)) // nothing pending
        }
        db.query(
            "SELECT name, quantity, deletedAt, pendingSync " +
                "FROM shopping_items WHERE id = 'item-a'"
        ).use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Milk", c.getString(0))
            assertEquals("2", c.getString(1))
            assertTrue(c.isNull(2))
            assertEquals(0, c.getInt(3))
        }
        db.close()
    }
}
