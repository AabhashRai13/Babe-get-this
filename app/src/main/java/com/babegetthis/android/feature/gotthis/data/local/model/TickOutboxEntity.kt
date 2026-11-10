package com.babegetthis.android.feature.gotthis.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// A tick event the server has not accepted yet. Added in DB v6.
@Entity(tableName = "tick_outbox")
data class TickOutboxEntity(
    @PrimaryKey val tickId: String,
    // The account whose push sends it.
    val accountId: String,
    val itemId: String,
    val listId: String,
    val tickedAt: Long,
    // Snapshot at tick time; the name is cut to the server's 500 characters.
    val name: String,
    val categoryId: String?,
    // Set by an undo.
    val undoneAt: Long?,
    // Bumped on every change, for compare-and-delete after a push.
    val revision: Int,
)
