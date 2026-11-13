package com.babegetthis.android.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.babegetthis.android.core.data.local.dao.CategoryDao
import com.babegetthis.android.core.data.local.model.CategoryEntity
import com.babegetthis.android.feature.gotthis.data.local.dao.GotThisStateDao
import com.babegetthis.android.feature.gotthis.data.local.dao.PurchaseDao
import com.babegetthis.android.feature.gotthis.data.local.model.HintImpressionEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ItemMuteEntity
import com.babegetthis.android.feature.gotthis.data.local.model.PurchaseEntity
import com.babegetthis.android.feature.gotthis.data.local.model.ReminderFollowUpEntity
import com.babegetthis.android.feature.gotthis.data.local.model.TickOutboxEntity
import com.babegetthis.android.feature.shoppingitems.data.local.dao.ShoppingItemDao
import com.babegetthis.android.feature.shoppingitems.data.local.model.ShoppingItemEntity
import com.babegetthis.android.feature.shoppinglist.data.local.dao.ShoppingListDao
import com.babegetthis.android.feature.shoppinglist.data.local.model.ShoppingListEntity

@Database(
    entities = [
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        CategoryEntity::class,
        PurchaseEntity::class,
        TickOutboxEntity::class,
        ItemMuteEntity::class,
        HintImpressionEntity::class,
        ReminderFollowUpEntity::class,
    ],
    version = 6,
    // exportSchema stays false: Room 2.8.4's schema-JSON exporter uses a
    // kotlinx-serialization build that AbstractMethodErrors against this
    // project's serialization plugin on the KSP classpath. Migrations are
    // guarded by MigrationTest (runs each migration against a hand-built
    // old-version table) instead of a diffed schema baseline.
    //
    // Re-tried 2026-11-06 on Kotlin 2.0.21 / KSP 2.0.21-1.0.28 (issue #24):
    //   - KSP1: still fails. room-compiler brings serialization-core 1.8.1 but
    //     the Kotlin compiler's own copy wins in the processor classloader, and
    //     its GeneratedSerializer has no default typeParametersSerializers().
    //     Whether it passes depends on daemon classloader state, so a green
    //     run once is not evidence — use --rerun from a fresh daemon.
    //   - KSP2 (ksp.useKSP2=true): different failure, "KSTypeArgument.type
    //     should not have been null ... STAR" from Room/Hilt, a KSP2 bug at
    //     this version.
    // Next attempt belongs with a Kotlin 2.1+/KSP 2.1.x upgrade, not alone.
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun gotThisStateDao(): GotThisStateDao
}
