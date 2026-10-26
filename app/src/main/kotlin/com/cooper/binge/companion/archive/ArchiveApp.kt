package com.cooper.binge.companion.archive

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.cooper.binge.companion.archive.index.IndexRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the [AppGraph], hands WorkManager its worker factory, schedules the weekly refresh and warms the graph. */
class ArchiveApp :
    Application(),
    Configuration.Provider {
    val graph: AppGraph by lazy { AppGraph(this) }

    /** Runs the start-up warm-up, which moves its blocking work to the graph's own io dispatcher; it lives as long as the process. */
    private val warmUp = CoroutineScope(SupervisorJob())

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(graph.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        IndexRefreshWorker.schedule(WorkManager.getInstance(this))
        warmUp.launch { graph.warmUp() }
    }
}
