package com.organizer.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        Log.d(TAG, "doWork:start attempt=$runAttemptCount")
        val result = syncManager.syncNow()
        val outcome = when {
            result.ownerChanged || result.skipped -> Result.success()
            result.moreWork || result.retryable -> Result.retry()
            result.error != null -> Result.retry()
            else -> Result.success()
        }
        Log.d(
            TAG,
            "doWork:end pulled=${result.pulled} pushed=${result.pushed} " +
                "skipped=${result.skipped} ownerChanged=${result.ownerChanged} " +
                "retryable=${result.retryable} moreWork=${result.moreWork} " +
                "outcome=${if (outcome == Result.success()) "SUCCESS" else "RETRY"}",
        )
        outcome
    } catch (ex: Exception) {
        Log.w(TAG, "doWork:exception ${ex.javaClass.simpleName}")
        Result.retry()
    }

    companion object {
        private const val TAG = "SyncWorker"
    }
}
