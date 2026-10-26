package com.cooper.binge.companion.archive.resolve

import com.binge.companion.contracts.stream.v1.LicenceKind
import com.binge.companion.contracts.stream.v1.Resolution
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.archive.index.IndexRepository
import com.cooper.binge.companion.archive.index.IndexSnapshot
import com.cooper.binge.companion.archive.index.LinkedMediaType
import com.cooper.binge.companion.archive.index.WikidataLink
import com.cooper.binge.companion.archive.index.inMemoryIndex
import com.cooper.binge.companion.archive.index.movie
import com.cooper.binge.companion.archive.index.series
import com.cooper.binge.companion.archive.settings.MinimumQuality
import com.cooper.binge.companion.archive.settings.SourceSettings
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

/**
 * The mappers' clock, pinned: a source carries the second it expires at, so two resolves compared with each other
 * differ whenever the wall clock crosses a second between them, which failed `main`'s CI once.
 */
private const val NOW_MILLIS = 1_000_000L

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
                    WikidataLink(LinkedMediaType.MOVIE, 34372, "Execution_of_Mary_1895", isFilm = true),
                    WikidataLink(LinkedMediaType.MOVIE, 146742, "silent-a-w-o-l-", isFilm = false),
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
                SourceMapper(testStrings, clock = { NOW_MILLIS }),
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

    private fun resolverWith(
        http: ResolverHttp = archive.http(),
        deranked: suspend () -> Set<String> = { emptySet() },
        recordResolved: suspend (Int, String, String) -> Unit = { _, _, _ -> },
        settings: SourceSettings = SourceSettings.DEFAULT,
        deviceLanguage: String = "en",
        timings: PhaseTimings = PhaseTimings.NONE,
    ) = Resolver(
        IndexRepository(database.indexDao(), seed = { seed }),
        ItemFinder(http, timings),
        ItemReader(http, timings),
        SourceMapper(testStrings, clock = { NOW_MILLIS }, deviceLanguage = { deviceLanguage }),
        misses::add,
        deranked = deranked,
        recordResolved = recordResolved,
        settings = { settings },
        timings = timings,
    )

    /** A film with a title, year and runtime and no linked Archive item: only the title route can place it. */
    private fun titleOnlyFilm() {
        archive.sparql = {
            "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" +
                "\"\"\t\"\"\t\"Nosferatu\"@en\t\"1922-01-01T00:00:00Z\"\t94\t<http://www.wikidata.org/entity/Q7727>\n"
        }
        archive.search = { searchResponse("PhantasmagoriaTheater-Nosferatu1922909" to 9L) }
    }

    @Test
    fun `the default settings resolve exactly as before`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val defaults = sources(resolverWith(settings = SourceSettings.DEFAULT).resolve(movie(10331), episode = null)).toList()
            val unset = sources(resolver.resolve(movie(10331), episode = null)).toList()

            assertEquals(unset, defaults)
            assertEquals(3, defaults.size)
        }

    @Test
    fun `title matches on finds a film only the title route can place`() =
        runTest {
            titleOnlyFilm()

            val sources = sources(resolverWith().resolve(movie(1), episode = null)).toList()

            assertEquals(listOf("PhantasmagoriaTheater-Nosferatu1922909"), sources.map { it.id.substringBefore('/') }.distinct())
        }

    @Test
    fun `title matches off leaves a film only the title route can place not found, and says why`() =
        runTest {
            titleOnlyFilm()

            val outcome = resolverWith(settings = SourceSettings(titleMatches = false)).resolve(movie(1), episode = null)

            assertEquals(ResolveOutcome.NotFound, outcome)
            assertTrue(archive.searches.none { it.contains("title:") })
            assertEquals(listOf(CoverageMiss(1, Route.TITLE, CoverageMiss.Reason.FILTERED_BY_SETTINGS)), misses)
        }

    @Test
    fun `title matches off on a film the index knows is OK and empty`() =
        runTest {
            titleOnlyFilm()

            val outcome = resolverWith(settings = SourceSettings(titleMatches = false)).resolve(movie(34372), episode = null)

            assertEquals(emptyList<Any>(), sources(outcome).toList())
            assertEquals(listOf(CoverageMiss(34372, Route.TITLE, CoverageMiss.Reason.FILTERED_BY_SETTINGS)), misses)
        }

    @Test
    fun `title matches off still finds Wikidata-linked items`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val sources = sources(resolverWith(settings = SourceSettings(titleMatches = false)).resolve(movie(10331), null)).toList()

            assertEquals(3, sources.size)
        }

    @Test
    fun `any licence, including none, adds the unlicensed transfer, labelled as such`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            val sources = sources(resolverWith(settings = SourceSettings(licences = LicenceLevel.ANY)).resolve(movie(653), null)).toList()

            assertEquals(
                listOf("Nosferatu_unlicensed_transfer", "PhantasmagoriaTheater-Nosferatu1922909"),
                sources.map { it.id.substringBefore('/') },
            )
            assertEquals("Nosferatu (1922)", sources.first().label)
            assertEquals("No licence stated", sources.first().licenceLabel)
            assertEquals(LicenceKind.LICENCE_KIND_NONE_STATED, sources.first().licenceKind)
            assertEquals("Public domain (US)", sources.last().licenceLabel)
            assertEquals(LicenceKind.LICENCE_KIND_OPEN, sources.last().licenceKind)
        }

    @Test
    fun `any licence admits a copy that reserves its rights, labelled with its own words`() =
        runTest {
            archive.sparql =
                {
                    resolveFixture("wikidata-653.tsv")
                        .lines()
                        .take(2)
                        .joinToString("\n")
                        .replace("Nosferatu_DVD_quality", "reserved")
                }
            archive.search = { searchResponse("reserved" to 1L) }
            archive.metadataBodies["reserved"] =
                """{"metadata":{"mediatype":"movies","licenseurl":"All rights reserved"},""" +
                """"files":[{"name":"film.mp4","format":"h.264","height":"480","length":"5600"}]}"""

            assertEquals(emptyList<Any>(), sources(resolverWith().resolve(movie(653), null)).toList())
            val any = sources(resolverWith(settings = SourceSettings(licences = LicenceLevel.ANY)).resolve(movie(653), null)).toList()

            assertEquals("Nosferatu (1922)", any.single().label)
            assertEquals("All rights reserved", any.single().licenceLabel)
            assertEquals(LicenceKind.LICENCE_KIND_RESTRICTED, any.single().licenceKind)
        }

    @Test
    fun `every Creative Commons licence still leaves out the unlicensed transfer`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            val settings = SourceSettings(licences = LicenceLevel.CREATIVE_COMMONS)
            val sources = sources(resolverWith(settings = settings).resolve(movie(653), null)).toList()

            assertEquals(listOf("PhantasmagoriaTheater-Nosferatu1922909"), sources.map { it.id.substringBefore('/') })
        }

    @Test
    fun `public domain only drops a CC BY copy, and a known film filtered to nothing is OK and empty`() =
        runTest {
            // Nosferatu's Wikidata answer, with one Archive item: a CC BY copy.
            archive.sparql =
                {
                    resolveFixture("wikidata-653.tsv")
                        .lines()
                        .take(2)
                        .joinToString("\n")
                        .replace("Nosferatu_DVD_quality", "Nosferatu_by")
                }
            archive.search = { searchResponse("Nosferatu_by" to 1L) }
            archive.metadataBodies["Nosferatu_by"] =
                """{"metadata":{"mediatype":"movies","licenseurl":"https://creativecommons.org/licenses/by/4.0/"},""" +
                """"files":[{"name":"film.mp4","format":"h.264","height":"480","length":"5600"}]}"""

            val byDefault = sources(resolverWith().resolve(movie(653), null)).toList()
            assertEquals("CC BY 4.0", byDefault.single().licenceLabel)
            assertEquals(emptyList<CoverageMiss>(), misses)

            val onlyPd = resolverWith(settings = SourceSettings(licences = LicenceLevel.PUBLIC_DOMAIN)).resolve(movie(653), null)
            assertEquals(emptyList<Any>(), sources(onlyPd).toList())
            assertEquals(listOf(CoverageMiss(653, Route.WIKIDATA, CoverageMiss.Reason.FILTERED_BY_SETTINGS)), misses)
        }

    @Test
    fun `a minimum quality drops the sources below it`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val settings = SourceSettings(minimumQuality = MinimumQuality.HD_1080)
            val sources = sources(resolverWith(settings = settings).resolve(movie(10331), null)).toList()

            assertEquals(listOf(Resolution.RESOLUTION_HD_1080), sources.map { it.quality.resolution })
            assertEquals(emptyList<CoverageMiss>(), misses)
        }

    @Test
    fun `subtitles in my language keep the English captions on an English device`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val settings = SourceSettings(subtitlesInMyLanguage = true)
            val sources = sources(resolverWith(settings = settings, deviceLanguage = "en").resolve(movie(10331), null)).toList()

            assertEquals(3, sources.size)
        }

    @Test
    fun `subtitles in my language on a Spanish device filter everything, and the miss says so`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val settings = SourceSettings(subtitlesInMyLanguage = true)
            val outcome = resolverWith(settings = settings, deviceLanguage = "es").resolve(movie(10331), null)

            assertEquals(emptyList<Any>(), sources(outcome).toList())
            assertEquals(listOf(CoverageMiss(10331, Route.WIKIDATA, CoverageMiss.Reason.FILTERED_BY_SETTINGS)), misses)
            assertEquals(emptyList<Triple<Int, String, String>>(), resolved)
        }

    @Test
    fun `the settings combine`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val settings =
                SourceSettings(
                    titleMatches = false,
                    licences = LicenceLevel.PUBLIC_DOMAIN,
                    minimumQuality = MinimumQuality.HD_720,
                    subtitlesInMyLanguage = true,
                )
            val sources = sources(resolverWith(settings = settings).resolve(movie(10331), null)).toList()

            assertEquals(listOf(Resolution.RESOLUTION_HD_1080, Resolution.RESOLUTION_HD_720), sources.map { it.quality.resolution })
        }

    @Test
    fun `the settings are read once per resolve, so a change applies to the next one`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }
            var current = SourceSettings.DEFAULT
            var reads = 0
            val resolver =
                archive.http().let { http ->
                    Resolver(
                        IndexRepository(database.indexDao(), seed = { seed }),
                        ItemFinder(http),
                        ItemReader(http),
                        SourceMapper(testStrings, clock = { NOW_MILLIS }),
                        misses::add,
                        settings = {
                            reads++
                            current
                        },
                    )
                }

            val first = sources(resolver.resolve(movie(10331), null))
            current = SourceSettings(minimumQuality = MinimumQuality.HD_1080)
            assertEquals(3, first.toList().size)
            assertEquals(1, sources(resolver.resolve(movie(10331), null)).toList().size)
            assertEquals(2, reads)
        }

    @Test
    fun `settings that cannot be read fail the resolve, for the host to retry`() =
        runTest {
            val unreadable =
                archive.http().let { http ->
                    Resolver(
                        IndexRepository(database.indexDao(), seed = { seed }),
                        ItemFinder(http),
                        ItemReader(http),
                        SourceMapper(testStrings, clock = { NOW_MILLIS }),
                        misses::add,
                        settings = { throw java.io.IOException("datastore") },
                    )
                }

            val failure = runCatching { unreadable.resolve(movie(10331), episode = null) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is java.io.IOException)
        }

    @Test
    fun `a failing deranking lookup still sends the sources`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { searchResponse("Nosferatu_DVD_quality" to 900L) }

            val resolver = resolverWith(deranked = { error("db closed") })
            val sources = sources(resolver.resolve(movie(653), episode = null)).toList()

            assertTrue(sources.isNotEmpty())
        }

    @Test
    fun `a failing recent-titles write still sends the sources`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { searchResponse("Nosferatu_DVD_quality" to 900L) }

            val resolver = resolverWith(recordResolved = { _, _, _ -> error("db closed") })
            val sources = sources(resolver.resolve(movie(653), episode = null)).toList()

            assertTrue(sources.isNotEmpty())
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
                        SourceMapper(testStrings, clock = { NOW_MILLIS }),
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
            assertEquals("Nosferatu (1922)", sources.single().label)
            assertEquals("Public domain (US)", sources.single().licenceLabel)
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
    fun `an id the index knows is a known non-film kind is an invalid argument`() =
        runTest {
            assertEquals(ResolveOutcome.NotAFilm, resolver.resolve(movie(146742), episode = null))
        }

    @Test
    fun `a movie Wikidata classes as no known non-film kind goes on to the routes`() =
        runTest {
            archive.sparql = { "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" }

            assertEquals(emptyList<Any>(), sources(resolver.resolve(movie(34372), episode = null)).toList())
            assertEquals(listOf(CoverageMiss(34372, null, CoverageMiss.Reason.NO_ITEMS)), misses)
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
    fun `a Wikidata failure on a film the index does not know fails the resolve, for the host to retry`() =
        runTest {
            archive.sparqlStatus = 503

            val failure = runCatching { resolver.resolve(movie(99), episode = null) }.exceptionOrNull()
            assertTrue(failure.toString(), failure is java.io.IOException)
            assertEquals(emptyList<String>(), archive.searches)
        }

    @Test
    fun `a Wikidata failure on a film the index knows resolves from the index's Archive ids`() =
        runTest {
            archive.sparqlStatus = 503
            archive.search = { resolveFixture("search-downloads-10331.json") }

            val sources = sources(resolver.resolve(movie(10331), episode = null)).toList()

            assertEquals(3, sources.size)
            assertTrue(sources.all { it.id.startsWith("Night.Of.The.Living.Dead_1080p/") })
            // With no Wikidata facts, the label is the item's own title, and no year.
            assertEquals("Night of the Living Dead", sources.first().label)
            assertEquals(listOf(CoverageMiss(10331, Route.WIKIDATA, CoverageMiss.Reason.WIKIDATA_FAILED)), misses)
        }

    @Test
    fun `a Wikidata query that stalls past its budget resolves from the index's Archive ids`() =
        runTest {
            archive.sparqlDelaySeconds = 30
            archive.search = { resolveFixture("search-downloads-10331.json") }
            // The production budget is 4 s; a short one keeps the stall out of the test's wall time.
            val stalled = resolverWith(http = archive.http(sparqlBudgetMillis = 300))

            val sources = sources(stalled.resolve(movie(10331), episode = null)).toList()

            assertEquals(3, sources.size)
            assertTrue(sources.all { it.id.startsWith("Night.Of.The.Living.Dead_1080p/") })
            assertEquals(listOf(CoverageMiss(10331, Route.WIKIDATA, CoverageMiss.Reason.WIKIDATA_FAILED)), misses)
        }

    @Test
    fun `a cold resolve answers its first source inside the host's deadline when the ranking search stalls`() =
        kotlinx.coroutines.runBlocking {
            archive.sparql = { resolveFixture("wikidata-10331.tsv") }
            archive.search = { resolveFixture("search-downloads-10331.json") }
            // A search that answers long after the host's 10 s deadline would; its own budget keeps it from gating the read.
            archive.searchDelayMillis = 30_000
            val phases = mutableListOf<Pair<String, Long>>()
            val cold = resolverWith(http = archive.http(rankingBudgetMillis = 300), timings = PhaseTimings({ n, ms -> phases += n to ms }))

            val first = withTimeout(COLD_START_BUDGET_MILLIS) { sources(cold.resolve(movie(10331), episode = null)).first() }

            assertTrue(first.id, first.id.startsWith("Night.Of.The.Living.Dead_1080p/"))
            assertEquals(PHASE_ORDER, phases.map { it.first }.distinct())
            val firstSourceMillis = phases.single { it.first == "first_source" }.second
            assertTrue("first source took $firstSourceMillis ms", firstSourceMillis < COLD_START_BUDGET_MILLIS)
        }

    private companion object {
        /** Well under the host's 10 s deadline, with room for a slow CI machine. */
        const val COLD_START_BUDGET_MILLIS = 5_000L
        val PHASE_ORDER =
            listOf("index_lookup", "settings_read", "wikidata_query", "ranking_search", "find_items", "metadata_read", "first_source")
    }
}
