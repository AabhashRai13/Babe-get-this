package com.babegetthis.android.feature.gotthis.data.local.model

import androidx.room.Entity

// Per-phone, never synced. Guests use "" as accountId. Added in DB v6.
@Entity(tableName = "item_mutes", primaryKeys = ["accountId", "itemKey"])
data class ItemMuteEntity(val accountId: String, val itemKey: String, val keptInARow: Int, val mutedUntil: Long?)
