package com.babegetthis.android.core.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** The single unique reminder job. Enqueuing replaces any job already scheduled. */
interface WorkEnqueuer {
    suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long)
    suspend fun cancelUnique()
}

/** [WorkEnqueuer] over WorkManager: one unique job, replaced on every enqueue. */
class WorkManagerEnqueuer(private val workManager: WorkManager) : WorkEnqueuer {

    override suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long) {
        workManager.enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            buildRequest(listId, generation, delayMillis),
        ).await()
    }

    override suspend fun cancelUnique() {
        workManager.cancelUniqueWork(UNIQUE_NAME).await()
    }

    // The generation goes in as a Long: the worker reads it with getLong and
    // treats the -1 default as a malformed job.
    internal fun buildRequest(listId: String, generation: Long, delayMillis: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_LIST_ID to listId,
                    ReminderWorker.KEY_GENERATION to generation,
                ),
            )
            .build()

    companion object {
        const val UNIQUE_NAME = "shopping_reminder"
    }
}
