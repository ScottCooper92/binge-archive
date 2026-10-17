package io.github.scottcooper92.binge.archive.ui.status

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.archive.index.IndexRefresher
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.index.inMemoryIndex
import io.github.scottcooper92.binge.archive.index.sampleSnapshot
import io.github.scottcooper92.binge.archive.reports.DeviceRecords
import io.github.scottcooper92.binge.archive.reports.DeviceRecordsDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusViewModelTest {
    private val indexDatabase = inMemoryIndex()
    private val recordsDatabase =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeviceRecordsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val index = IndexRepository(indexDatabase.indexDao(), seed = { sampleSnapshot }, clock = { 5_000L })
    private val records = DeviceRecords(recordsDatabase.dao(), clock = { 9_000L })

    private class FakeRefresher(
        override val refreshing: Flow<Boolean> = MutableStateFlow(false),
    ) : IndexRefresher {
        var requests = 0

        override fun refreshNow() {
            requests++
        }
    }

    @Before
    fun main() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        indexDatabase.close()
        recordsDatabase.close()
    }

    private suspend fun StatusViewModel.ready(predicate: (StatusUiState.Ready) -> Boolean = { true }): StatusUiState.Ready =
        withTimeout(5_000) { uiState.first { it is StatusUiState.Ready && predicate(it) } as StatusUiState.Ready }

    @Test
    fun `the index, a pending refresh and the device's records make one Ready state`() =
        runBlocking {
            index.ensureSeeded()
            index.recordFailure("offline")
            records.recordResolved(10331, "Night of the Living Dead", "Night.Of.The.Living.Dead_1080p")
            records.record("Nosferatu_DVD_quality", "SOURCE_PROBLEM_UNPLAYABLE")

            val ready = StatusViewModel(index, records, FakeRefresher(MutableStateFlow(true))).ready()

            assertEquals(3, ready.filmCount)
            assertEquals(5_000L, ready.lastRefreshedAt)
            assertTrue(ready.lastRefreshFailed)
            assertTrue(ready.refreshing)
            assertEquals(listOf(RecentTitle(10331, "Night of the Living Dead", "Night.Of.The.Living.Dead_1080p", 9_000L)), ready.recent)
            assertEquals(listOf("Nosferatu_DVD_quality"), ready.reported)
        }

    @Test
    fun `refresh now asks the refresher, and clearing empties each list`() =
        runBlocking {
            records.recordResolved(653, "Nosferatu", "PhantasmagoriaTheater-Nosferatu1922909")
            records.record("x", "SOURCE_PROBLEM_OTHER")
            val refresher = FakeRefresher()
            val viewModel = StatusViewModel(index, records, refresher)
            viewModel.ready { it.recent.isNotEmpty() && it.reported.isNotEmpty() }

            viewModel.refreshNow()
            viewModel.clearRecent()
            viewModel.clearReported()

            assertEquals(1, refresher.requests)
            val cleared = viewModel.ready { it.recent.isEmpty() && it.reported.isEmpty() }
            assertEquals(emptyList<RecentTitle>(), cleared.recent)
        }

    @Test
    fun `a read that fails is the Error state, and retry reads again`() =
        runBlocking {
            var fail = true
            val refresher = FakeRefresher(flow { if (fail) error("unreadable") else emit(false) })
            val viewModel = StatusViewModel(index, records, refresher)

            withTimeout(5_000) { viewModel.uiState.first { it == StatusUiState.Error } }
            fail = false
            viewModel.retry()

            val ready = viewModel.ready()
            assertEquals(null, ready.lastRefreshedAt)
        }
}
