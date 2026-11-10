package com.cooper.binge.companion.archive.resolve

import com.cooper.binge.companion.archive.index.ArchiveHttp
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The Wikidata query is hedged: an identical second request is sent beside a stalled or failed first. Real time, on a
 * MockWebServer, with a short hedge delay and budget so each case runs in about a second.
 */
class ResolverHttpHedgeTest {
    private val server = MockWebServer()
    private val attempts = AtomicInteger()

    /** What the nth request (from 1) answers: a status, and how long its headers are held back. */
    private var script: (Int) -> Pair<Int, Long> = { 200 to 0L }

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val (code, delayMillis) = script(attempts.incrementAndGet())
                    return MockResponse
                        .Builder()
                        .code(code)
                        .body("answer-$code")
                        .headersDelay(delayMillis, TimeUnit.MILLISECONDS)
                        .build()
                }
            }
        server.start()
    }

    @After
    fun stop() {
        server.close()
    }

    private fun http(
        budgetMillis: Long = BUDGET,
        hedgeMillis: Long = HEDGE,
    ) = ResolverHttp(
        client = ArchiveHttp.client("test"),
        wikidataEndpoint = server.url("/sparql"),
        sparqlBudgetMillis = budgetMillis,
        hedgeDelayMillis = hedgeMillis,
    )

    private fun timed(block: suspend () -> String): Pair<String, Long> {
        val start = System.nanoTime()
        val result = runBlocking { block() }
        return result to (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    @Test
    fun `a query that answers at once sends no second request`() {
        val (body, _) = timed { http().sparql("q") }

        assertEquals("answer-200", body)
        // Give a stray second request time to arrive, then count.
        Thread.sleep(HEDGE * 2)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `a stalled first query is answered by the second, sent after the hedge delay`() {
        script = { n -> if (n == 1) 200 to STALL else 200 to 0L }

        val (body, took) = timed { http().sparql("q") }

        assertEquals("answer-200", body)
        assertEquals(2, attempts.get())
        assertTrue("took $took ms, the stalled first would take $STALL", took < STALL)
    }

    @Test
    fun `a first query that answers after the hedge but before the second wins, and the second is cancelled`() {
        script = { n -> if (n == 1) 200 to SLOW else 503 to STALL }

        val (body, _) = timed { http().sparql("q") }

        assertEquals("answer-200", body)
        assertEquals(2, attempts.get())
    }

    @Test
    fun `a 5xx first query fires the second at once, without waiting for the hedge delay`() {
        script = { n -> if (n == 1) 503 to 0L else 200 to 0L }

        val (body, took) = timed { http(hedgeMillis = LONG_HEDGE).sparql("q") }

        assertEquals("answer-200", body)
        assertEquals(2, attempts.get())
        assertTrue("took $took ms, the hedge delay is $LONG_HEDGE", took < LONG_HEDGE)
    }

    @Test
    fun `a first query that fails other than with a 5xx is not retried`() {
        script = { 404 to 0L }

        val failure = assertThrows(HttpStatusException::class.java) { timed { http().sparql("q") } }

        assertEquals(404, failure.code)
        Thread.sleep(HEDGE * 2)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `when both fail with a 5xx the first one's failure is thrown`() {
        script = { n -> if (n == 1) 502 to 0L else 503 to 0L }

        val failure = assertThrows(HttpStatusException::class.java) { timed { http(hedgeMillis = LONG_HEDGE).sparql("q") } }

        assertEquals(502, failure.code)
        assertEquals(2, attempts.get())
    }

    @Test
    fun `when both stall the query fails at the one budget, not two`() {
        script = { 200 to STALL }

        val start = System.nanoTime()
        assertThrows(IOException::class.java) { timed { http().sparql("q") } }
        val took = (System.nanoTime() - start) / NANOS_PER_MILLI

        assertEquals(2, attempts.get())
        assertTrue("took $took ms against a budget of $BUDGET", took < BUDGET + HEDGE * 2)
    }

    @Test
    fun `a 5xx that arrives with the budget already spent is not retried into a call with no timeout`() {
        script = { 503 to BUDGET_SPENT_DELAY }

        val failure =
            assertThrows(
                IOException::class.java,
            ) { timed { http(budgetMillis = BUDGET_SPENT_DELAY + 1, hedgeMillis = LONG_HEDGE).sparql("q") } }

        assertTrue(failure.message.orEmpty().isNotEmpty())
        assertTrue(attempts.get() <= 2)
    }

    private companion object {
        const val BUDGET = 1_500L
        const val HEDGE = 150L
        const val LONG_HEDGE = 5_000L
        const val STALL = 3_000L
        const val SLOW = 500L
        const val BUDGET_SPENT_DELAY = 100L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
