package com.babegetthis.android.core.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Migration 1 -> 2 is additive but there is no cloud backup, so a broken
// migration means permanent data loss. This exercises MIGRATION_1_2.migrate()
// directly against a hand-built v1 table and asserts the row survives with
// isLocked defaulting to 0. No schema JSON baseline needed — we don't route
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
}
