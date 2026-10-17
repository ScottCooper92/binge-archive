package io.github.scottcooper92.binge.archive

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import io.github.scottcooper92.binge.archive.index.ArchiveHttp
import io.github.scottcooper92.binge.archive.index.IndexDatabase
import io.github.scottcooper92.binge.archive.index.IndexFetcher
import io.github.scottcooper92.binge.archive.index.IndexRefreshWorker
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.index.readSeed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    val index: IndexRepository by lazy { IndexRepository(database.indexDao(), seed = { appContext.assets.readSeed() }) }

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
                        IndexRefreshWorker(appContext, workerParameters, index, IndexFetcher(http)::fetch, io)
                    else -> null
                }
        }
}
