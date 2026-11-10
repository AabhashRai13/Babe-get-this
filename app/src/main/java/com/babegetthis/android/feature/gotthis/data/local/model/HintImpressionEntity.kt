package com.babegetthis.android.feature.gotthis.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Per-phone, never synced. Written when a hint first applies to an item.
// Guests use "" as accountId. Added in DB v6.
@Entity(tableName = "hint_impressions")
data class HintImpressionEntity(
    @PrimaryKey val itemId: String,
    val accountId: String,
    val canonicalKey: String,
    val shownAt: Long,
    val holdout: Boolean,
)
