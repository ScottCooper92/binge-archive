package com.cooper.binge.companion.archive.index

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The status screen's "Refresh now": one refresh, outside the weekly schedule. */
interface IndexRefresher {
    /** Asks for a refresh on the next network. A request already waiting is kept, not doubled. */
    fun refreshNow()

    /** Whether a requested refresh is waiting or running. */
    val refreshing: Flow<Boolean>
}

/** [IndexRefresher] on WorkManager: the same worker the weekly schedule runs, as one unique job. */
class WorkManagerIndexRefresher(
    private val workManager: WorkManager,
) : IndexRefresher {
    override fun refreshNow() {
        val request =
            OneTimeWorkRequestBuilder<IndexRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        workManager.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override val refreshing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_NAME).map { infos ->
            infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
        }

    companion object {
        const val UNIQUE_NAME = "index-refresh-now"
    }
}
