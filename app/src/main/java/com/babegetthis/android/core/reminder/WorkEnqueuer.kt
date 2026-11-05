package com.babegetthis.android.core.reminder

/** The single unique reminder job. Enqueuing replaces any job already scheduled. */
interface WorkEnqueuer {
    suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long)
    suspend fun cancelUnique()
}
