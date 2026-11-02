package com.cooper.binge.companion.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The build's seed step (#108): it retries a fetch that failed, and a lane that needs a seed (the device smoke) fails the build
 * with the reason when there is none, rather than shipping an empty index and failing a test three steps later.
 */
class SeedGeneratorTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val snapshot =
        IndexSnapshot(
            links = listOf(WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true)),
            archiveItems = emptyList(),
            imdbLinks = emptyList(),
        )
    private val slept = mutableListOf<Long>()
    private val stdout = StringBuilder()
    private val stderr = StringBuilder()

    private fun generate(
        output: File = File(temp.root, "index/seed.tsv"),
        requireSeed: Boolean = false,
        fetch: () -> IndexSnapshot,
    ): Int =
        SeedGenerator(
            fetch = fetch,
            sleep = { slept += it },
            out = { stdout.appendLine(it) },
            err = { stderr.appendLine(it) },
        ).generate(output, requireSeed)

    private fun failing(
        times: Int,
        message: String = "HTTP 502 from query.wikidata.org",
    ): () -> IndexSnapshot {
        var calls = 0
        return { if (++calls <= times) throw IOException(message) else snapshot }
    }

    @Test
    fun `a fetch that works writes the seed and says what it holds`() {
        val output = File(temp.root, "index/seed.tsv")

        assertEquals(0, generate(output) { snapshot })

        assertTrue(output.exists())
        assertTrue(stdout.toString(), "1 Wikidata links" in stdout.toString())
        assertFalse("no temp file is left behind", File(output.path + ".tmp").exists())
    }

    @Test
    fun `a failed fetch is retried with a growing wait, and the seed is written once one works`() {
        val output = File(temp.root, "index/seed.tsv")

        assertEquals(0, generate(output, fetch = failing(times = 2)))

        assertTrue(output.exists())
        assertEquals(listOf(2_000L, 4_000L), slept)
    }

    @Test
    fun `a fetch that keeps failing writes nothing, says so, and by default the build goes on`() {
        val output = File(temp.root, "index/seed.tsv")

        assertEquals(0, generate(output, fetch = failing(times = 99)))

        assertFalse(output.exists())
        assertTrue(stderr.toString(), "no index seed was generated" in stderr.toString())
        assertTrue("HTTP 502 from query.wikidata.org" in stderr.toString())
        assertEquals("one wait between each of the attempts, none after the last: $slept", listOf(2_000L, 4_000L), slept)
    }

    @Test
    fun `when a seed is required, a fetch that keeps failing fails the build and names the reason`() {
        val output = File(temp.root, "index/seed.tsv")

        assertEquals(1, generate(output, requireSeed = true, fetch = failing(times = 99)))

        assertFalse(output.exists())
        assertTrue(stderr.toString(), "no index seed was generated: HTTP 502 from query.wikidata.org" in stderr.toString())
    }

    @Test
    fun `when a seed is required and the fetch works, the build goes on`() {
        assertEquals(0, generate(requireSeed = true) { snapshot })
        assertTrue(File(temp.root, "index/seed.tsv").exists())
        assertEquals("", stderr.toString())
    }

    @Test
    fun `a fetch that fails once and then works is not an error even when a seed is required`() {
        assertEquals(0, generate(requireSeed = true, fetch = failing(times = 1)))
        assertEquals(listOf(2_000L), slept)
        assertEquals("", stderr.toString())
    }
}
