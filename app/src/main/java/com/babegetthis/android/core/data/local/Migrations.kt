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
            "ALTER TABLE shopping_lists ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0",
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

// v4 -> v5: list ownership (docs/technical-decisions/008). Additive. accountId
// stays NULL on every row here: the signed-in user id lives in encrypted prefs,
// out of a migration's reach, so ownership is assigned at runtime by
// SyncEngine.claimDeviceLists. Existing shared replicas are already on the
// server, which is all onServer records.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN accountId TEXT")
        db.execSQL("ALTER TABLE shopping_lists ADD COLUMN onServer INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE shopping_lists SET onServer = 1 WHERE shareCode IS NOT NULL")
    }
}

// v5 -> v6: Got This. Additive: one nullable column on items and five new
// tables (purchase history, the tick outbox, and per-phone mutes, hint
// impressions and reminder follow-ups). Existing items start with no tick time.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN pickedUpAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS purchases (" +
                "id TEXT NOT NULL, " +
                "tickId TEXT NOT NULL, " +
                "accountId TEXT, " +
                "sourceItemId TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "itemKey TEXT NOT NULL, " +
                "categoryId TEXT, " +
                "boughtAt INTEGER NOT NULL, " +
                "deletedAt INTEGER, " +
                "pendingUpload INTEGER NOT NULL, " +
                "PRIMARY KEY(id))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_accountId_itemKey ON purchases (accountId, itemKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_tickId ON purchases (tickId)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS tick_outbox (" +
                "tickId TEXT NOT NULL, " +
                "accountId TEXT NOT NULL, " +
                "itemId TEXT NOT NULL, " +
                "listId TEXT NOT NULL, " +
                "tickedAt INTEGER NOT NULL, " +
                "name TEXT NOT NULL, " +
                "categoryId TEXT, " +
                "undoneAt INTEGER, " +
                "revision INTEGER NOT NULL, " +
                "PRIMARY KEY(tickId))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS item_mutes (" +
                "accountId TEXT NOT NULL, " +
                "itemKey TEXT NOT NULL, " +
                "keptInARow INTEGER NOT NULL, " +
                "mutedUntil INTEGER, " +
                "PRIMARY KEY(accountId, itemKey))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS hint_impressions (" +
                "itemId TEXT NOT NULL, " +
                "accountId TEXT NOT NULL, " +
                "canonicalKey TEXT NOT NULL, " +
                "shownAt INTEGER NOT NULL, " +
                "holdout INTEGER NOT NULL, " +
                "PRIMARY KEY(itemId))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reminder_followups (" +
                "accountId TEXT NOT NULL, " +
                "isoWeek TEXT NOT NULL, " +
                "postedAt INTEGER NOT NULL, " +
                "itemKeys TEXT NOT NULL, " +
                "holdout INTEGER NOT NULL, " +
                "PRIMARY KEY(accountId, isoWeek))",
        )
    }
}
