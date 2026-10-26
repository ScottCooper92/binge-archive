package com.cooper.binge.companion.archive.resolve

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class CoverageLogTest {
    @Test
    fun `a miss is written to logcat on the device, by route`() {
        LogcatCoverageLog.miss(CoverageMiss(653, Route.IMDB, CoverageMiss.Reason.NO_LICIT_ITEM))

        val entry = ShadowLog.getLogsForTag("Coverage").single()
        assertEquals("miss tmdb=653 route=imdb reason=no_licit_item", entry.msg)
    }
}
