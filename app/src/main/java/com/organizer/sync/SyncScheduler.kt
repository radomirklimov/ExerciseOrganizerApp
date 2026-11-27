package com.organizer.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Request a sync after a local outbox mutation is committed. */
fun interface SyncTrigger {
    fun requestSyncAfterLocalChange()
}

/** Idempotent WorkManager scheduling for automatic cloud sync. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncTrigger {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueImmediate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        // APPEND_OR_REPLACE keeps a currently running chain intact but ensures
        // a newly authenticated owner still gets an immediate sync: KEEP would
        // silently drop this request when the previous owner's run is active.
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    /**
     * Local-change trigger. A short initial delay lets the surrounding Room
     * transaction commit before the worker starts; rapid edits coalesce under
     * the same unique work name, and APPEND_OR_REPLACE guarantees a follow-up
     * run if a sync is already active when the mutation is written.
     */
    override fun requestSyncAfterLocalChange() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInitialDelay(LOCAL_CHANGE_DELAY_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "cloud_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "cloud_sync_immediate"
        const val PERIODIC_INTERVAL_MINUTES = 15L
        const val LOCAL_CHANGE_DELAY_SECONDS = 1L
    }
}
