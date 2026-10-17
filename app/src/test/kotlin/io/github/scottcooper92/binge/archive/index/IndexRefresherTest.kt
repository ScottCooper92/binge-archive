package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IndexRefresherTest {
    @Test
    fun `refresh now queues one network refresh, and says it is waiting`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
            val workManager = WorkManager.getInstance(context)
            val refresher = WorkManagerIndexRefresher(workManager)

            refresher.refreshNow()
            refresher.refreshNow()

            val work = workManager.getWorkInfosForUniqueWork(WorkManagerIndexRefresher.UNIQUE_NAME).get().single()
            assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
            assertTrue(refresher.refreshing.first())
        }
}
