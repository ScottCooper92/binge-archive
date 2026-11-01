package com.cooper.binge.companion.archive.reports

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceRecordsTest {
    @Test
    fun `the device records database opens empty`() =
        runTest {
            val opened = DeviceRecordsDatabase.open(ApplicationProvider.getApplicationContext())
            try {
                assertEquals(emptyList<RecentTitleEntity>(), opened.dao().recent().first())
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
    fun `clearing empties the recent list`() =
        runTest {
            records.recordResolved(1, "Film", "item")

            records.clearRecent()

            assertEquals(emptyList<RecentTitleEntity>(), records.recent().first())
        }
}
