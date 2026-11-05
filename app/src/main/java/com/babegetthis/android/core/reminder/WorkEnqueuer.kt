package com.babegetthis.android.core.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.Lazy
import java.util.concurrent.TimeUnit

/** The single unique reminder job. Enqueuing replaces any job already scheduled. */
interface WorkEnqueuer {
    suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long)
    suspend fun cancelUnique()
}

/**
 * [WorkEnqueuer] over WorkManager: one unique job, replaced on every enqueue.
 *
 * WorkManager is held lazily. This enqueuer is built while Hilt injects
 * BabeGetThisApp (through the scheduler), and resolving WorkManager then would
 * initialise it and read workManagerConfiguration before workerFactory is
 * necessarily injected. Resolving on first enqueue or cancel removes that
 * dependency on Hilt's field order.
 */
class WorkManagerEnqueuer(private val workManager: Lazy<WorkManager>) : WorkEnqueuer {

    override suspend fun enqueueUnique(listId: String, generation: Long, delayMillis: Long) {
        workManager.get().enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            buildRequest(listId, generation, delayMillis),
        ).await()
    }

    override suspend fun cancelUnique() {
        workManager.get().cancelUniqueWork(UNIQUE_NAME).await()
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
