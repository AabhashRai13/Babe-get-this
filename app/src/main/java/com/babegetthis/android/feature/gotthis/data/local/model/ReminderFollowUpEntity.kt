package com.babegetthis.android.feature.gotthis.data.local.model

import androidx.room.Entity

// Per-phone, never synced. One row per posted or would-have-posted
// notification. Guests use "" as accountId. Added in DB v6.
@Entity(tableName = "reminder_followups", primaryKeys = ["accountId", "isoWeek"])
data class ReminderFollowUpEntity(
    val accountId: String,
    val isoWeek: String,
    val postedAt: Long,
    // The canonical keys named, joined with a newline.
    val itemKeys: String,
    val holdout: Boolean,
)
