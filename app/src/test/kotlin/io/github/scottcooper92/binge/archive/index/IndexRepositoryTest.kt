package io.github.scottcooper92.binge.archive.index

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IndexRepositoryTest {
    private val database = inMemoryIndex()
    private val dao = database.indexDao()
    private var now = 1_000L
    private var seedLoads = 0

    private fun repository(seed: IndexSnapshot? = sampleSnapshot) =
        IndexRepository(dao, seed = { seed.also { seedLoads++ } }, clock = { now })

    @After
    fun close() {
        database.close()
    }

    @Test
    fun `a fresh install answers the issue's two films from the seed`() =
        runTest {
            val index = repository()

            assertEquals(
                IndexHit(
                    isFilm = true,
                    wikidataArchiveIds = listOf("Night.Of.The.Living.Dead_1080p"),
                    imdbArchiveIds = listOf("night_of_the_living_dead_imdb"),
                ),
                index.lookup(movie(10331)),
            )
            assertEquals(
                listOf("Nosferatu_DVD_quality", "PhantasmagoriaTheater-Nosferatu1922909"),
                index.lookup(movie(653))?.wikidataArchiveIds,
            )
        }

    @Test
    fun `an id the index does not know is null`() =
        runTest {
            assertNull(repository().lookup(movie(1)))
        }

    @Test
    fun `a series and a TMDB movie id of a known non-film kind are known, and not films`() =
        runTest {
            val index = repository()

            assertEquals(
                IndexHit(isFilm = false, wikidataArchiveIds = listOf("The_Prisoner"), imdbArchiveIds = emptyList()),
                index.lookup(series(894)),
            )
            assertFalse(checkNotNull(index.lookup(movie(146742))).isFilm)
            assertNull(index.lookup(series(653)))
        }

    @Test
    fun `a movie Wikidata classes as no known non-film kind is a film`() =
        runTest {
            // The Execution of Mary Stuart (1895) is only a "video" on Wikidata, and a film all the same (#26).
            assertTrue(checkNotNull(repository().lookup(movie(34372))).isFilm)
        }

    @Test
    fun `an Archive item found only through its IMDb id is found by TMDB id`() =
        runTest {
            assertEquals(
                IndexHit(isFilm = true, wikidataArchiveIds = emptyList(), imdbArchiveIds = listOf("silent-aelita-queen-of-mars")),
                repository().lookup(movie(2973)),
            )
        }

    @Test
    fun `an unspecified media type is not looked up`() =
        runTest {
            assertNull(
                repository().lookup(
                    com.binge.companion.contracts.v1.MediaId
                        .getDefaultInstance(),
                ),
            )
        }

    @Test
    fun `the seed is loaded once, and logged as the first success`() =
        runTest {
            val index = repository()
            index.lookup(movie(10331))
            index.lookup(movie(653))

            assertEquals(1, seedLoads)
            val log = checkNotNull(index.lastSuccess().first())
            assertEquals(RefreshSource.SEED, log.source)
            assertEquals(6, log.linkCount)
            assertEquals(3, log.archiveItemCount)
        }

    @Test
    fun `the seed never replaces an index a refresh already wrote`() =
        runTest {
            repository().replace(
                IndexSnapshot(listOf(WikidataLink(LinkedMediaType.MOVIE, 1, "only", true)), emptyList(), emptyList()),
                RefreshSource.NETWORK,
            )

            val index = repository()
            assertNull(index.lookup(movie(10331)))
            assertEquals(listOf("only"), index.lookup(movie(1))?.wikidataArchiveIds)
            assertEquals(0, seedLoads)
        }

    @Test
    fun `a build without a seed starts empty, and is not asked again`() =
        runTest {
            val index = repository(seed = null)

            assertNull(index.lookup(movie(10331)))
            assertNull(index.lookup(movie(653)))
            assertEquals(1, seedLoads)
            assertNull(index.lastSuccess().first())
        }

    @Test
    fun `a failed refresh keeps the previous index and its freshness`() =
        runTest {
            val index = repository()
            index.ensureSeeded()

            now = 2_000L
            index.recordFailure("HTTP 503 from query.wikidata.org")

            assertTrue(index.lookup(movie(10331)) != null)
            assertEquals(1_000L, index.lastSuccess().first()?.finishedAt)
            val last = checkNotNull(dao.lastRefresh())
            assertFalse(last.succeeded)
            assertEquals("HTTP 503 from query.wikidata.org", last.message)
        }

    @Test
    fun `a refresh replaces the whole index`() =
        runTest {
            val index = repository()
            index.ensureSeeded()

            now = 3_000L
            index.replace(
                IndexSnapshot(listOf(WikidataLink(LinkedMediaType.MOVIE, 7, "new", true)), emptyList(), emptyList()),
                RefreshSource.NETWORK,
            )

            assertNull(index.lookup(movie(10331)))
            assertNull(index.lookup(movie(2973)))
            assertEquals(listOf("new"), index.lookup(movie(7))?.wikidataArchiveIds)
            assertEquals(RefreshSource.NETWORK, index.lastSuccess().first()?.source)
            assertEquals(3_000L, index.lastSuccess().first()?.finishedAt)
        }

    @Test
    fun `the film count and the latest refresh are read for the status screen`() =
        runTest {
            val index = repository()
            // 653, 10331, 34372 and 2973 (through its IMDb id), plus the 10331 the IMDb route also reaches:
            // four films. 146742, a television series episode, is not one.
            assertEquals(4, index.filmCount().first())

            now = 2_000L
            index.recordFailure("offline")

            assertEquals(4, index.filmCount().first())
            assertFalse(checkNotNull(index.latestRefresh().first()).succeeded)
        }

    @Test
    fun `each status screen read loads the seed first, on a fresh install`() =
        runTest {
            assertEquals(4, repository().filmCount().first())
        }

    @Test
    fun `the last refresh read loads the seed first`() =
        runTest {
            assertEquals(RefreshSource.SEED, checkNotNull(repository().lastSuccess().first()).source)
        }

    @Test
    fun `the latest refresh read loads the seed first`() =
        runTest {
            assertTrue(checkNotNull(repository().latestRefresh().first()).succeeded)
            assertEquals(1, seedLoads)
        }

    @Test
    fun `the seed is read on the seed context, not the caller's thread`() {
        val executor =
            java.util.concurrent.Executors
                .newSingleThreadExecutor { Thread(it, "seed-io") }
        try {
            var readOn: String? = null
            val index =
                IndexRepository(
                    dao,
                    seed = { sampleSnapshot.also { readOn = Thread.currentThread().name } },
                    seedContext = executor.asCoroutineDispatcher(),
                )

            kotlinx.coroutines.runBlocking { index.ensureSeeded() }

            assertTrue("read on $readOn", readOn.orEmpty().startsWith("seed-io"))
        } finally {
            executor.shutdownNow()
        }
    }
}
