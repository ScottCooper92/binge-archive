package com.cooper.binge.companion.archive.index

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The weekly refresh: forget expired source reports, settle the seed decision, then fetch a whole snapshot, then swap it in. If the fetch fails, the failure is
 * logged, the previous index stays, and WorkManager retries with backoff.
 */
class IndexRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: IndexRepository,
    private val fetch: () -> IndexSnapshot,
    private val io: CoroutineDispatcher,
    /** Housekeeping that runs with the weekly refresh: the device's expired source reports. */
    private val prune: suspend () -> Unit = {},
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            attempt { prune() }.onFailure { Log.w(TAG, "Could not prune the expired source reports", it) }
            repository.ensureSeeded()
            val snapshot = withContext(io) { fetch() }
            repository.replace(snapshot, RefreshSource.NETWORK)
            Result.success()
        } catch (e: IOException) {
            repository.recordFailure(e.message ?: e.javaClass.simpleName)
            Result.retry()
        }

    companion object {
        const val UNIQUE_NAME = "index-refresh"
        private const val TAG = "IndexRefresh"
        private const val PERIOD_DAYS = 7L
        private const val BACKOFF_HOURS = 1L

        /** Schedules the weekly refresh, on any network. An existing schedule is kept. */
        fun schedule(workManager: WorkManager) {
            val request =
                PeriodicWorkRequestBuilder<IndexRefreshWorker>(PERIOD_DAYS, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_HOURS, TimeUnit.HOURS)
                    .build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
