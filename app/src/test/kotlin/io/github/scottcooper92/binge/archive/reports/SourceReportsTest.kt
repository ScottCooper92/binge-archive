package io.github.scottcooper92.binge.archive.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SourceReportsTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReportDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private var now = 0L
    private val reports = SourceReports(database.reportDao(), clock = { now })

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
    fun `the reports database opens empty`() =
        runTest {
            val opened = ReportDatabase.open(ApplicationProvider.getApplicationContext())
            try {
                assertEquals(emptyList<String>(), opened.reportDao().identifiersSince(0))
            } finally {
                opened.close()
            }
        }
}
