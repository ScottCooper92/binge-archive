package com.cooper.binge.companion.archive.resolve

import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.IndexSnapshot
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.index.movie
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a debug build writes about a resolve (#94), against the same fake Archive the resolver's own tests use. */
@RunWith(RobolectricTestRunner::class)
class ResolverLogTest {
    private val archive = FakeArchive()
    private val database = inMemoryIndex()
    private val lines = mutableListOf<String>()

    private val resolver =
        archive.http().let { http ->
            val log = ResolveLog { lines += it() }
            Resolver(
                IndexRepository(database.indexDao(), seed = { IndexSnapshot(emptyList(), emptyList(), emptyList()) }),
                ItemFinder(http),
                ItemReader(http, log = log),
                SourceMapper(testStrings, clock = { 1_000_000L }),
                coverage = { },
                log = log,
            )
        }

    @After
    fun close() {
        archive.close()
        database.close()
    }

    @Test
    fun `a film nothing places logs its start, no candidates and a not found answer`() =
        runTest {
            archive.sparql = { "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" }

            resolver.resolve(movie(1), episode = null)

            assertEquals("resolve start tmdb=1 level=PUBLIC_DOMAIN_AND_CC_BY titleMatches=true", lines[0])
            assertEquals("candidates route=none count=0 fromIndex=false ids=[]", lines[1])
            assertTrue(lines[2].startsWith("answer not_found after "))
            assertEquals(3, lines.size)
        }

    @Test
    fun `a film with sources logs its candidates, what it dropped and what it sent`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            (resolver.resolve(movie(653), episode = null) as ResolveOutcome.Sources).sources.toList()

            assertTrue(lines.any { it.startsWith("candidates route=wikidata count=") })
            assertTrue(lines.any { it.startsWith("read id=") && it.endsWith(" ok") })
            assertTrue(lines.any { it.startsWith("dropped id=") })
            assertTrue(lines.last().startsWith("answer sources=1 items=1 after "))
            assertTrue(lines.none { "://" in it })
        }

    @Test
    fun `a failed Wikidata query logs the failure by class and rethrows it`() =
        runTest {
            archive.sparql = { "" }
            archive.sparqlStatus = 500

            val failure = runCatching { resolver.resolve(movie(1), episode = null) }.exceptionOrNull()

            assertTrue(failure is java.io.IOException)
            assertTrue(lines.last().startsWith("answer failed "))
            assertTrue(lines.none { "://" in it })
        }
}
