package io.github.scottcooper92.binge.archive

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import io.github.scottcooper92.binge.archive.index.IndexRefreshWorker

/** Holds the [AppGraph], hands WorkManager its worker factory, and schedules the weekly refresh. */
class ArchiveApp :
    Application(),
    Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(graph.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        IndexRefreshWorker.schedule(WorkManager.getInstance(this))
    }
}
