package com.babegetthis.android.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v1 -> v2: add the per-list lock flag. Additive, so existing rows keep all
// their data and default to unlocked. Replaces the old
// fallbackToDestructiveMigration() — that would have wiped every list on this
// first-ever schema change, and there is no cloud backup to restore from.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE shopping_lists ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0"
        )
    }
}
