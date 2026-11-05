package com.babegetthis.android.core.reminder.data.local

import androidx.room.withTransaction
import com.babegetthis.android.core.data.local.AppDatabase

/** A list's reminder-relevant state, read at a single instant. */
data class ReminderContent(
    val listId: String,
    val listName: String,
    val isLocked: Boolean,
    val pickedCount: Int,
    /** Names of the items not yet picked up, in the items screen's order (shop, category, name; nulls last). */
    val uncheckedNames: List<String>,
) {
    val uncheckedCount: Int get() = uncheckedNames.size

    /** At least one item picked up and at least one not. */
    val isPartlyShopped: Boolean get() = pickedCount >= 1 && uncheckedCount >= 1
}

/**
 * Reads the list row and its live items in one Room transaction so the counts,
 * names and lock flag all come from the same instant.
 */
class ReminderContentReader(private val db: AppDatabase) {

    /** Null when the list row is missing or tombstoned (`deletedAt != null`). */
    suspend fun snapshot(listId: String): ReminderContent? = db.withTransaction {
        // getListRaw returns tombstones too, so the deletedAt check is ours to make.
        val list = db.shoppingListDao().getListRaw(listId)?.takeIf { it.deletedAt == null }
            ?: return@withTransaction null
        // Ordered as the items screen shows them, so the names in the nudge are
        // the first ones the user sees on tapping it.
        val (picked, unchecked) = db.shoppingItemDao().getItemsByListIdOnceOrdered(listId).partition { it.isPickedUp }
        ReminderContent(
            listId = list.id,
            listName = list.name,
            isLocked = list.isLocked,
            pickedCount = picked.size,
            uncheckedNames = unchecked.map { it.name },
        )
    }
}

/** The single definition of "partly shopped" shared by the scheduler and the worker. */
class ReminderEligibility(private val reader: ReminderContentReader) {
    suspend fun isPartlyShopped(listId: String): Boolean = reader.snapshot(listId)?.isPartlyShopped == true
}
