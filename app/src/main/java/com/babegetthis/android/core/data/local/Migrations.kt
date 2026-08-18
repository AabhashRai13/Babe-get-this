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

// v2 -> v3: sync columns for realtime list sharing. Additive only — every
// existing row stays a plain local-only list (shareCode NULL, no tombstone,
// nothing pending), so users who never share see zero behavior change.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN shareCode TEXT")
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN deletedAt INTEGER")
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0")
    }
}

// v3 -> v4: expand the category set from 12 grocery categories to 48 general-shopping
// categories (see docs/technical-decisions/006-category-taxonomy.md). No schema change —
// only seed data. onCreate() only seeds fresh installs, so existing users need this to
// backfill the new rows. INSERT OR IGNORE keeps any category the user already has
// (including custom ones), and the two renamed defaults are patched by id. Existing
// items keep their categoryId; the two renamed ids are stable, so nothing re-maps.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        DEFAULT_CATEGORIES.forEach { c ->
            db.execSQL(
                "INSERT OR IGNORE INTO categories (id, name, isDefault) VALUES (?, ?, ?)",
                arrayOf(c.id, c.name, if (c.isDefault) 1 else 0),
            )
        }
        // Renamed-in-place defaults: rows already exist under the old name, so the
        // insert above ignored them. Force the new name by id.
        db.execSQL("UPDATE categories SET name = 'Rice, Grains & Pasta' WHERE id = 'cat-pantry-dry-goods'")
        db.execSQL("UPDATE categories SET name = 'Personal Care & Beauty' WHERE id = 'cat-toiletries-personal-care'")
    }
}
