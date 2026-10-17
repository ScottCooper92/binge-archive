package io.github.scottcooper92.binge.archive.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class DeviceRecordsTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeviceRecordsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = 0L
    private val reports = DeviceRecords(database.dao(), clock = { now })

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `a reported item is pushed down for thirty days, then no longer`() =
        runTest {
            reports.record("item", "SOURCE_PROBLEM_UNPLAYABLE")

            now = TimeUnit.DAYS.toMillis(29)
            assertEquals(setOf("item"), reports.deranked())
            now = TimeUnit.DAYS.toMillis(31)
            assertEquals(emptySet<String>(), reports.deranked())
        }

    @Test
    fun `pruning forgets the reports older than thirty days and keeps the rest`() =
        runTest {
            reports.record("old", "SOURCE_PROBLEM_UNPLAYABLE")
            now = TimeUnit.DAYS.toMillis(20)
            reports.record("recent", "SOURCE_PROBLEM_UNPLAYABLE")
            now = TimeUnit.DAYS.toMillis(31)

            reports.pruneReports()

            assertEquals(listOf("recent"), database.dao().reportedSince(0))
        }

    @Test
    fun `the device records database opens empty`() =
        runTest {
            val opened = DeviceRecordsDatabase.open(ApplicationProvider.getApplicationContext())
            try {
                assertEquals(emptyList<String>(), opened.dao().reportedSince(0))
            } finally {
                opened.close()
            }
        }
}

@RunWith(RobolectricTestRunner::class)
class RecentTitlesTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DeviceRecordsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = 0L
    private val records = DeviceRecords(database.dao(), clock = { now })

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `the last ten titles are kept, newest first, one row per film`() =
        runTest {
            (1..12).forEach { id ->
                now = id.toLong()
                records.recordResolved(id, "Film $id", "item_$id")
            }
            now = 13
            records.recordResolved(5, "Film 5", "item_5_again")

            val recent = records.recent().first()
            assertEquals(listOf(5, 12, 11, 10, 9, 8, 7, 6, 4, 3), recent.map { it.tmdbId })
            assertEquals("item_5_again", recent.first().identifier)
        }

    @Test
    fun `clearing empties the recent list and the reports`() =
        runTest {
            records.recordResolved(1, "Film", "item")
            records.record("item", "SOURCE_PROBLEM_OTHER")

            records.clearRecent()
            records.clearReports()

            assertEquals(emptyList<RecentTitleEntity>(), records.recent().first())
            assertEquals(emptyList<String>(), records.reported().first())
        }
}
