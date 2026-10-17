package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The weekly refresh: settle the seed decision, then fetch a whole snapshot, then swap it in. If the fetch fails, the failure is
 * logged, the previous index stays, and WorkManager retries with backoff.
 */
class IndexRefreshWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: IndexRepository,
    private val fetch: () -> IndexSnapshot,
    private val io: CoroutineDispatcher,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
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
