package com.cooper.binge.companion.archive.index

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class IndexFetcherTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private var linksBody = fixture("wikidata-links.tsv")
    private var archiveStatus = 200
    private var archiveBody = fixture("archive-imdb-search.json")

    @Before
    fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val body = request.body?.utf8().orEmpty()
                    return when {
                        request.url.encodedPath == "/archive" ->
                            MockResponse
                                .Builder()
                                .code(archiveStatus)
                                .body(if (request.url.queryParameter("page") == "1") archiveBody else EMPTY_PAGE)
                                .build()
                        body.contains("P345") -> MockResponse(body = fixture("wikidata-imdb.tsv"))
                        body.contains("DISTINCT") -> MockResponse(body = fixture("wikidata-non-film-classes.tsv"))
                        else -> MockResponse(body = linksBody)
                    }
                }
            }
        server.start()
    }

    @After
    fun stop() {
        server.close()
    }

    private fun fetcher() =
        IndexFetcher(
            client = ArchiveHttp.client("test"),
            wikidataEndpoint = server.url("/sparql"),
            archiveSearch = server.url("/archive"),
        )

    @Test
    fun `a refresh fetches the links, the Archive's IMDb items and their TMDB ids`() {
        val snapshot = fetcher().fetch()

        assertEquals(7, snapshot.links.size)
        assertEquals(
            setOf(34372, 653, 10331, 490096),
            snapshot.links
                .filter { it.isFilm }
                .map { it.tmdbId }
                .toSet(),
        )
        assertEquals(3, snapshot.archiveItems.size)
        assertEquals(setOf(2973, 124525, 3009), snapshot.imdbLinks.map { it.tmdbId }.toSet())
    }

    @Test
    fun `the IMDb join asks for exactly the Archive's IMDb ids`() {
        fetcher().fetch()

        val join =
            requests
                .single {
                    it.body
                        ?.utf8()
                        .orEmpty()
                        .contains("P345")
                }.body!!
                .utf8()
        listOf("tt0014646", "tt0048123", "tt0057427").forEach { assertTrue(it, join.contains(it)) }
    }

    @Test
    fun `every request names this app, and SPARQL goes as a POST asking for TSV`() {
        fetcher().fetch()

        requests.forEach { assertEquals(ArchiveHttp.userAgent("test"), it.headers["User-Agent"]) }
        val sparql = requests.filter { it.url.encodedPath == "/sparql" }
        assertTrue(sparql.all { it.method == "POST" && it.headers["Accept"] == "text/tab-separated-values" })
    }

    @Test
    fun `an Archive failure fails the whole refresh`() {
        archiveStatus = 503

        assertThrows(IOException::class.java) { fetcher().fetch() }
    }

    @Test
    fun `no links at all fails the refresh`() {
        linksBody = "?type\t?tmdb\t?ia\t?class\n"

        assertThrows(IOException::class.java) { fetcher().fetch() }
    }

    @Test
    fun `an empty Archive answer fails the refresh`() {
        archiveBody = """{"response":{"numFound":0,"start":0,"docs":[]}}"""

        assertThrows(IOException::class.java) { fetcher().fetch() }
    }

    @Test
    fun `an Archive answer that ends short of numFound fails the refresh`() {
        archiveBody = fixture("archive-imdb-search.json").replace("\"numFound\": 4", "\"numFound\": 9")

        val e = assertThrows(IOException::class.java) { fetcher().fetch() }

        assertTrue(e.message, e.message!!.contains("4 of 9"))
    }

    @Test
    fun `the client has the fixed timeouts`() {
        val client: OkHttpClient = ArchiveHttp.client("test")

        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
    }

    private companion object {
        const val EMPTY_PAGE = """{"response":{"numFound":9,"start":0,"docs":[]}}"""
    }
}
