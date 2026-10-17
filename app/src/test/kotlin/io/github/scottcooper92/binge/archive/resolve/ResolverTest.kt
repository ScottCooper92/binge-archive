package io.github.scottcooper92.binge.archive.resolve

import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.github.scottcooper92.binge.archive.index.IndexRepository
import io.github.scottcooper92.binge.archive.index.IndexSnapshot
import io.github.scottcooper92.binge.archive.index.LinkedMediaType
import io.github.scottcooper92.binge.archive.index.WikidataLink
import io.github.scottcooper92.binge.archive.index.inMemoryIndex
import io.github.scottcooper92.binge.archive.index.movie
import io.github.scottcooper92.binge.archive.index.series
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The resolver end to end, against the index and a fake Wikidata and Archive serving real responses. */
@RunWith(RobolectricTestRunner::class)
class ResolverTest {
    private val archive = FakeArchive()
    private val database = inMemoryIndex()
    private val misses = mutableListOf<CoverageMiss>()
    private val resolved = mutableListOf<Triple<Int, String, String>>()

    private val seed =
        IndexSnapshot(
            links =
                listOf(
                    WikidataLink(LinkedMediaType.MOVIE, 10331, "Night.Of.The.Living.Dead_1080p", isFilm = true),
                    WikidataLink(LinkedMediaType.MOVIE, 653, "Nosferatu_DVD_quality", isFilm = true),
                    WikidataLink(LinkedMediaType.MOVIE, 34372, "Execution_of_Mary_1895", isFilm = false),
                    WikidataLink(LinkedMediaType.TV, 894, "The_Prisoner", isFilm = false),
                ),
            archiveItems = emptyList(),
            imdbLinks = emptyList(),
        )

    private val resolver =
        archive.http().let { http ->
            Resolver(
                IndexRepository(database.indexDao(), seed = { seed }),
                ItemFinder(http),
                ItemReader(http),
                SourceMapper(testStrings),
                misses::add,
                recordResolved = { tmdbId, title, identifier -> resolved += Triple(tmdbId, title, identifier) },
            )
        }

    @After
    fun close() {
        archive.close()
        database.close()
    }

    private suspend fun sources(outcome: ResolveOutcome): Flow<com.binge.companion.contracts.stream.v1.StreamSource> =
        (outcome as ResolveOutcome.Sources).sources

    @Test
    fun `a slow second item does not delay the first item's sources`() =
        runBlocking {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = {
                searchResponse(
                    "PhantasmagoriaTheater-Nosferatu1922909" to 900L,
                    "Nosferatu_DVD_quality" to 500L,
                    "Nosferatu_unlicensed_transfer" to 100L,
                    "Nosferatu_dark_item" to 10L,
                )
            }
            val slow = java.util.concurrent.CountDownLatch(1)
            archive.metadataGates["Nosferatu_DVD_quality"] = slow

            val flow = sources(resolver.resolve(movie(653), episode = null))
            val first = withTimeout(10_000) { flow.first() }

            assertTrue(first.id, first.id.startsWith("PhantasmagoriaTheater-Nosferatu1922909/"))
            assertEquals(1L, slow.count)
            slow.countDown()
        }

    @Test
    fun `an item this device reported is sent after every other, even when it reads first`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { searchResponse("Nosferatu_DVD_quality" to 900L, "PhantasmagoriaTheater-Nosferatu1922909" to 500L) }
            archive.metadataBodies["Nosferatu_DVD_quality"] =
                """{"metadata":{"mediatype":"movies","licenseurl":"http://creativecommons.org/publicdomain/zero/1.0/"},""" +
                """"files":[{"name":"dvd.mp4","format":"h.264","height":"480","length":"5600"}]}"""
            val reported =
                archive.http().let { http ->
                    Resolver(
                        IndexRepository(database.indexDao(), seed = { seed }),
                        ItemFinder(http),
                        ItemReader(http),
                        SourceMapper(testStrings),
                        misses::add,
                        deranked = { setOf("Nosferatu_DVD_quality") },
                    )
                }

            val sources = sources(reported.resolve(movie(653), episode = null)).toList()

            assertEquals(
                listOf("PhantasmagoriaTheater-Nosferatu1922909", "Nosferatu_DVD_quality"),
                sources.map { it.id.substringBefore('/') }.distinct(),
            )
        }

    @Test
    fun `Night of the Living Dead resolves to its h264 encodes with SubRip tracks`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val sources = sources(resolver.resolve(movie(10331), episode = null)).toList()

            assertEquals(
                listOf(Resolution.RESOLUTION_HD_1080, Resolution.RESOLUTION_HD_720, Resolution.RESOLUTION_SD),
                sources.map { it.quality.resolution },
            )
            assertTrue(
                sources.all {
                    it.quality.videoCodec == "H.264" &&
                        it.subtitlesList
                            .single()
                            .url
                            .endsWith(".srt")
                },
            )
            assertEquals(emptyList<CoverageMiss>(), misses)
            assertEquals(listOf(Triple(10331, "Night of the Living Dead", "Night.Of.The.Living.Dead_1080p")), resolved)
        }

    @Test
    fun `Nosferatu's unlicensed transfer and dark item are excluded`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            val sources = sources(resolver.resolve(movie(653), episode = null)).toList()

            assertEquals(listOf("PhantasmagoriaTheater-Nosferatu1922909"), sources.map { it.id.substringBefore('/') })
            assertEquals("Nosferatu (1922) · Public domain (US)", sources.single().label)
        }

    @Test
    fun `a known film with no licit copy completes empty, and the miss is logged by route`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv").lines().filter { !it.contains("Phantasmagoria") }.joinToString("\n") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            assertEquals(emptyList<Any>(), sources(resolver.resolve(movie(653), episode = null)).toList())
            assertEquals(listOf(CoverageMiss(653, Route.WIKIDATA, CoverageMiss.Reason.NO_PLAYABLE_FILE)), misses)
        }

    @Test
    fun `a known film whose items are all unlicensed logs no licit item`() =
        runTest {
            archive.sparql =
                {
                    resolveFixture(
                        "wikidata-653.tsv",
                    ).lines().filter { !it.contains("Nosferatu_DVD") && !it.contains("Phantasmagoria") }.joinToString("\n")
                }
            archive.search = { resolveFixture("search-downloads-653.json") }

            assertEquals(emptyList<Any>(), sources(resolver.resolve(movie(653), episode = null)).toList())
            assertEquals(CoverageMiss.Reason.NO_LICIT_ITEM, misses.single().reason)
        }

    @Test
    fun `a known film with no Archive item anywhere logs no items`() =
        runTest {
            archive.sparql = { "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" }

            assertEquals(emptyList<Any>(), sources(resolver.resolve(movie(10331), episode = null)).toList())
            assertEquals(listOf(CoverageMiss(10331, null, CoverageMiss.Reason.NO_ITEMS)), misses)
        }

    @Test
    fun `a film neither the index nor any route can place is not found`() =
        runTest {
            archive.sparql = { "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" }

            assertEquals(ResolveOutcome.NotFound, resolver.resolve(movie(1), episode = null))
            assertEquals(listOf(CoverageMiss(1, null, CoverageMiss.Reason.NOT_IN_CATALOGUE)), misses)
        }

    @Test
    fun `a title match that is not the film is not found`() =
        runTest {
            archive.sparql = {
                "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" +
                    "\"\"\t\"\"\t\"Dune\"@en\t\"2021-01-01T00:00:00Z\"\t155\t<http://www.wikidata.org/entity/Q7727>\n"
            }
            archive.search = { searchResponse("dune_trailer" to 9L) }
            archive.metadataBodies["dune_trailer"] =
                """{"metadata":{"mediatype":"movies"},"files":[{"name":"t.mp4","format":"h.264","length":"150"}]}"""

            assertEquals(ResolveOutcome.NotFound, resolver.resolve(movie(1), episode = null))
            assertEquals(listOf(CoverageMiss(1, null, CoverageMiss.Reason.NOT_IN_CATALOGUE)), misses)
        }

    @Test
    fun `a film missing from the index still resolves through the network routes`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val sources = sources(resolver.resolve(movie(99), episode = null)).toList()

            assertEquals(3, sources.size)
            assertEquals(listOf(CoverageMiss(99, Route.WIKIDATA, CoverageMiss.Reason.NOT_IN_INDEX)), misses)
        }

    @Test
    fun `an id the index knows that is not a film is an invalid argument`() =
        runTest {
            assertEquals(ResolveOutcome.NotAFilm, resolver.resolve(movie(34372), episode = null))
        }

    @Test
    fun `a valid series is not carried, so it is not found`() =
        runTest {
            val episode =
                EpisodeRef
                    .newBuilder()
                    .setSeasonNumber(1)
                    .setEpisodeNumber(1)
                    .build()

            assertEquals(ResolveOutcome.NotFound, resolver.resolve(series(894), episode))
            assertEquals(ResolveOutcome.NotFound, resolver.resolve(series(1), episode))
            assertEquals(emptyList<CoverageMiss>(), misses)
        }

    @Test
    fun `unusable requests are invalid before anything is looked up`() =
        runTest {
            val episode =
                EpisodeRef
                    .newBuilder()
                    .setSeasonNumber(1)
                    .setEpisodeNumber(1)
                    .build()
            val badEpisode =
                EpisodeRef
                    .newBuilder()
                    .setSeasonNumber(1)
                    .setEpisodeNumber(0)
                    .build()
            val unspecified =
                MediaId
                    .newBuilder()
                    .setMediaType(MediaType.MEDIA_TYPE_UNSPECIFIED)
                    .setTmdbId(1)
                    .build()

            listOf(
                resolver.resolve(movie(0), null),
                resolver.resolve(movie(10331), episode),
                resolver.resolve(series(894), null),
                resolver.resolve(series(894), badEpisode),
                resolver.resolve(unspecified, null),
            ).forEach { assertTrue(it.toString(), it is ResolveOutcome.Invalid) }
            assertEquals(emptyList<CoverageMiss>(), misses)
        }

    @Test
    fun `cancelling the resolve cancels the request in flight`() =
        kotlinx.coroutines.runBlocking {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.sparqlDelaySeconds = 30
            val started = System.nanoTime()
            val job = launch(kotlinx.coroutines.Dispatchers.IO) { sources(resolver.resolve(movie(10331), episode = null)).toList() }
            kotlinx.coroutines.delay(500)
            job.cancelAndJoin()

            val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0
            assertTrue("took $elapsedSeconds s to stop", elapsedSeconds < 5)
        }

    @Test
    fun `a Wikidata failure fails the resolve, for the host to retry`() =
        runTest {
            archive.sparql = { throw IllegalStateException("down") }

            val failure = runCatching { sources(resolver.resolve(movie(10331), episode = null)).toList() }.exceptionOrNull()
            assertTrue(failure.toString(), failure is java.io.IOException)
        }
}
