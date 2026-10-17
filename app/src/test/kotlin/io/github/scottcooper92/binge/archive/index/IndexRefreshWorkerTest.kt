package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class IndexRefreshWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = inMemoryIndex()
    private val repository = IndexRepository(database.indexDao(), seed = { sampleSnapshot }, clock = { 5_000L })

    @After
    fun close() {
        database.close()
    }

    private fun worker(fetch: () -> IndexSnapshot) =
        TestListenableWorkerBuilder<IndexRefreshWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = IndexRefreshWorker(appContext, workerParameters, repository, fetch, Dispatchers.Unconfined)
                },
            ).build()

    @Test
    fun `a successful fetch replaces the seed`() =
        runTest {
            repository.ensureSeeded()
            val fresh = IndexSnapshot(listOf(WikidataLink(LinkedMediaType.MOVIE, 42, "fresh", true)), sampleSnapshot.archiveItems, emptyList())

            assertEquals(ListenableWorker.Result.success(), worker { fresh }.doWork())

            assertEquals(listOf("fresh"), repository.lookup(movie(42))?.wikidataArchiveIds)
            assertEquals(null, repository.lookup(movie(10331)))
            assertEquals(RefreshSource.NETWORK, repository.lastSuccess().first()?.source)
        }

    @Test
    fun `a failed fetch keeps the seed, logs the failure and retries`() =
        runTest {
            repository.ensureSeeded()

            assertEquals(ListenableWorker.Result.retry(), worker { throw IOException("offline") }.doWork())

            assertNotNull(repository.lookup(movie(10331)))
            assertEquals(RefreshSource.SEED, repository.lastSuccess().first()?.source)
            val dao = database.indexDao()
            assertEquals("offline", dao.latestRefresh().first()?.message)
        }

    @Test
    fun `a failed first fetch still leaves the seed answering lookups`() =
        runTest {
            assertEquals(ListenableWorker.Result.retry(), worker { throw IOException("offline") }.doWork())

            assertNotNull(repository.lookup(movie(10331)))
            assertEquals(RefreshSource.SEED, repository.lastSuccess().first()?.source)
        }

    @Test
    fun `the refresh is scheduled weekly, on any network, once`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val workManager = WorkManager.getInstance(context)

        IndexRefreshWorker.schedule(workManager)
        IndexRefreshWorker.schedule(workManager)

        val work = workManager.getWorkInfosForUniqueWork(IndexRefreshWorker.UNIQUE_NAME).get().single()
        assertEquals(TimeUnit.DAYS.toMillis(7), work.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }
}
