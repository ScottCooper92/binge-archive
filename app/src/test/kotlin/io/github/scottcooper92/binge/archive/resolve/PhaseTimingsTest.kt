package io.github.scottcooper92.binge.archive.resolve

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class PhaseTimingsTest {
    private val phases = mutableListOf<Pair<String, Long>>()
    private var nowNanos = 0L
    private val timings = PhaseTimings({ name, millis -> phases += name to millis }, clock = { nowNanos })

    @Test
    fun `a phase is logged with the time its block took`() =
        runTest {
            val answer =
                timings.time("index_lookup") {
                    nowNanos += 42_000_000
                    "hit"
                }

            assertEquals("hit", answer)
            assertEquals(listOf("index_lookup" to 42L), phases)
        }

    @Test
    fun `a phase that throws is still logged, and the failure goes on`() =
        runTest {
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    timings.time("wikidata_query") {
                        nowNanos += 7_000_000
                        error("down")
                    }
                }
            }

            assertEquals(listOf("wikidata_query" to 7L), phases)
        }

    @Test
    fun `since measures from a mark`() {
        val mark = timings.mark()
        nowNanos += 1_500_000_000
        timings.since("first_source", mark)

        assertEquals(listOf("first_source" to 1_500L), phases)
    }

    @Test
    fun `phases are written to logcat under the coverage tag`() {
        LogcatPhaseLog.phase("warm_index", 12)

        assertEquals("phase warm_index 12ms", ShadowLog.getLogsForTag("Coverage").single().msg)
    }

    @Test
    fun `NONE logs nothing and still runs the block`() =
        runTest {
            assertEquals(3, PhaseTimings.NONE.time("anything") { 3 })
        }
}
