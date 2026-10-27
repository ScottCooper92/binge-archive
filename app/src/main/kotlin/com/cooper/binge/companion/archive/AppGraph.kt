package com.cooper.binge.companion.archive

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.archive.index.ArchiveHttp
import com.cooper.binge.companion.archive.index.IndexDatabase
import com.cooper.binge.companion.archive.index.IndexFetcher
import com.cooper.binge.companion.archive.index.IndexRefreshWorker
import com.cooper.binge.companion.archive.index.IndexRefresher
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.WorkManagerIndexRefresher
import com.cooper.binge.companion.archive.index.readSeed
import com.cooper.binge.companion.archive.reports.DeviceRecords
import com.cooper.binge.companion.archive.reports.DeviceRecordsDatabase
import com.cooper.binge.companion.archive.reports.PlaybackProgressStore
import com.cooper.binge.companion.archive.resolve.ItemFinder
import com.cooper.binge.companion.archive.resolve.ItemReader
import com.cooper.binge.companion.archive.resolve.LogcatCoverageLog
import com.cooper.binge.companion.archive.resolve.LogcatPhaseLog
import com.cooper.binge.companion.archive.resolve.PhaseTimings
import com.cooper.binge.companion.archive.resolve.Resolver
import com.cooper.binge.companion.archive.resolve.ResolverHttp
import com.cooper.binge.companion.archive.resolve.SourceMapper
import com.cooper.binge.companion.archive.resolve.SourceStrings
import com.cooper.binge.companion.archive.settings.SourceSettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The app's object graph, built by hand: a handful of objects, so no DI framework. One of each,
 * for the life of the process, held by [ArchiveApp].
 */
class AppGraph(
    context: Context,
    /** Where blocking network and disk work runs. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    /** The one HTTP client, for the index and the resolver alike. */
    val http: OkHttpClient by lazy { ArchiveHttp.client(BuildConfig.VERSION_NAME) }

    private val database: IndexDatabase by lazy { IndexDatabase.open(appContext) }

    val index: IndexRepository by lazy {
        IndexRepository(database.indexDao(), seed = { appContext.assets.readSeed() }, seedContext = io)
    }

    private val deviceRecords: DeviceRecordsDatabase by lazy { DeviceRecordsDatabase.open(appContext) }

    /** What this device remembers of its own use: source reports and recently resolved titles. */
    val records: DeviceRecords by lazy { DeviceRecords(deviceRecords.dao()) }

    /** The watch progress the host reports, in the same database. */
    val progress: PlaybackProgressStore by lazy { PlaybackProgressStore(deviceRecords) }

    /**
     * The user's Sources settings, on the device. One store per process, as DataStore requires; the
     * Service runs in this app's process, so the screens and the resolver share it. A corrupt file
     * reads as the defaults.
     */
    val sourceSettings: SourceSettingsStore by lazy {
        SourceSettingsStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("source_settings") },
            ),
        )
    }

    /** The status screen's "Refresh now", on the same worker as the weekly refresh. */
    val refresher: IndexRefresher by lazy { WorkManagerIndexRefresher(WorkManager.getInstance(appContext)) }

    /** Resolves a title to sources: the index, then Wikidata and the Archive. */
    val resolver: Resolver by lazy {
        val http = ResolverHttp(http)
        val timings = PhaseTimings(LogcatPhaseLog)
        Resolver(
            index,
            ItemFinder(http, timings),
            ItemReader(http, timings),
            SourceMapper(SourceStrings.from(appContext)),
            LogcatCoverageLog,
            deranked = records::deranked,
            recordResolved = records::recordResolved,
            settings = sourceSettings::current,
            timings = timings,
        )
    }

    /**
     * Gets the first `Resolve` of a cold process off the critical path: opens the index and loads the seed,
     * reads the settings and builds the resolver and its HTTP client, all on [io], before a host binds.
     * A failure here is only a missed head start: the first `Resolve` does the same work itself.
     */
    suspend fun warmUp() {
        val timings = PhaseTimings(LogcatPhaseLog)
        withContext(io) {
            attempt { timings.time("warm_index") { index.ensureSeeded() } }
            attempt { timings.time("warm_settings") { sourceSettings.current() } }
            attempt { timings.time("warm_resolver") { resolver } }
        }
    }

    /** Builds the workers this app schedules, with their collaborators from this graph. */
    val workerFactory: WorkerFactory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? =
                when (workerClassName) {
                    IndexRefreshWorker::class.java.name ->
                        IndexRefreshWorker(
                            appContext,
                            workerParameters,
                            index,
                            IndexFetcher(http)::fetch,
                            io,
                            prune = records::pruneReports,
                        )
                    else -> null
                }
        }
}
