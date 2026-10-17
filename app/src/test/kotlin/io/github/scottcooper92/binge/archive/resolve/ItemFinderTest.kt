package io.github.scottcooper92.binge.archive.resolve

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ItemFinderTest {
    private val archive = FakeArchive()

    @After
    fun close() {
        archive.close()
    }

    private fun facts(
        ia: String = "",
        imdb: String = "",
        runtime: String = "",
    ) = "?ia\t?imdb\t?title\t?date\t?amount\t?unit\n" +
        "\"$ia\"\t\"$imdb\"\t\"Some \\\"Film\\\"\"@en\t\"1950-01-01T00:00:00Z\"\t$runtime\t<http://www.wikidata.org/entity/Q7727>\n"

    private fun withVideo(lengthSeconds: Int) =
        """{"metadata":{"mediatype":"movies"},"files":[{"name":"film.mp4","format":"h.264","length":"$lengthSeconds"}]}"""

    @Test
    fun `Wikidata's Archive ids come first, best-downloaded first`() =
        runTest {
            archive.sparql = { resolveFixture("wikidata-653.tsv") }
            archive.search = { resolveFixture("search-downloads-653.json") }

            val found = ItemFinder(archive.http()).find(653)

            assertEquals(Route.WIKIDATA, found.route)
            assertEquals(
                listOf(
                    "Nosferatu_unlicensed_transfer",
                    "Nosferatu_DVD_quality",
                    "PhantasmagoriaTheater-Nosferatu1922909",
                    "Nosferatu_dark_item",
                ),
                found.candidates.map { it.identifier },
            )
            assertEquals(0L, found.candidates.last().downloads)
            assertTrue(archive.searches.single().startsWith("identifier:(\"Nosferatu_DVD_quality\" OR "))
        }

    @Test
    fun `a failed download-count search keeps Wikidata's items, in Wikidata's order`() =
        runTest {
            archive.sparql = { facts(ia = "first_item") + facts(ia = "second_item").lines()[1] + "\n" }
            archive.searchStatus = 503
            val logged = mutableListOf<IOException>()

            val found = ItemFinder(archive.http()) { _, e -> logged += e }.find(1)

            assertEquals(Route.WIKIDATA, found.route)
            assertEquals(listOf("first_item", "second_item"), found.candidates.map { it.identifier })
            assertEquals(listOf(0L, 0L), found.candidates.map { it.downloads })
            assertEquals(1, logged.size)
        }

    @Test
    fun `with no Archive id on Wikidata, the IMDb id is searched`() =
        runTest {
            archive.sparql = { facts(imdb = "tt0000001", runtime = "60") }
            archive.search = { searchResponse("by_imdb" to 10) }

            val found = ItemFinder(archive.http()).find(1)

            assertEquals(Route.IMDB, found.route)
            assertEquals(listOf("by_imdb"), found.candidates.map { it.identifier })
            assertEquals("mediatype:movies AND external-identifier:\"urn:imdb:tt0000001\"", archive.searches.single())
        }

    @Test
    fun `with nothing by IMDb, the title and year are searched`() =
        runTest {
            archive.sparql = { facts(imdb = "tt0000001", runtime = "60") }
            archive.search = { query -> if (query.contains("title:")) searchResponse("by_title" to 3) else searchResponse() }
            archive.metadataBodies["by_title"] = withVideo(lengthSeconds = 3500)

            val found = ItemFinder(archive.http()).find(1)

            assertEquals(Route.TITLE, found.route)
            assertEquals(listOf("by_title"), found.candidates.map { it.identifier })
            assertEquals(listOf("by_title"), found.preloaded.keys.toList())
            assertEquals("mediatype:movies AND title:(\"Some  Film\") AND year:1950", archive.searches.last())
        }

    @Test
    fun `a title match that does not run like the film is not found`() =
        runTest {
            archive.sparql = { facts(runtime = "60") }
            archive.search = { searchResponse("trailer" to 3L) }
            archive.metadataBodies["trailer"] = withVideo(lengthSeconds = 120)

            val found = ItemFinder(archive.http()).find(1)

            assertEquals(null, found.route)
            assertEquals(emptyList<Candidate>(), found.candidates)
        }

    @Test
    fun `a title match that fails to load fails the find rather than finding nothing`() {
        archive.sparql = { facts(runtime = "60") }
        archive.search = { searchResponse("trailer" to 3L, "flaky" to 2L) }
        archive.metadataBodies["trailer"] = withVideo(lengthSeconds = 120)
        archive.metadataStatus["flaky"] = 503

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { ItemFinder(archive.http()).find(1) }
        }
    }

    @Test
    fun `a title match that runs like the film counts without a licence check`() =
        runTest {
            archive.sparql = { facts(runtime = "60") }
            archive.search = { searchResponse("unlicensed" to 3L, "flaky" to 2L) }
            archive.metadataBodies["unlicensed"] = withVideo(lengthSeconds = 3600)
            archive.metadataStatus["flaky"] = 503

            val found = ItemFinder(archive.http()).find(1)

            assertEquals(Route.TITLE, found.route)
            assertEquals(listOf("unlicensed", "flaky"), found.candidates.map { it.identifier })
            assertEquals(setOf("unlicensed"), found.preloaded.keys)
        }

    @Test
    fun `an unlicensed title match beside one that failed to load fails the read, not answers empty`() {
        archive.sparql = { facts(runtime = "60") }
        archive.search = { searchResponse("unlicensed" to 3L, "flaky" to 2L) }
        archive.metadataBodies["unlicensed"] = withVideo(lengthSeconds = 3600)
        archive.metadataStatus["flaky"] = 503

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking {
                val http = archive.http()
                ItemReader(http).readEach(ItemFinder(http).find(1)).toList()
            }
        }
    }

    @Test
    fun `with no runtime to check against, the title route is skipped`() =
        runTest {
            archive.sparql = { facts() }
            archive.search = { searchResponse("by_title" to 3) }

            val found = ItemFinder(archive.http()).find(1)

            assertEquals(null, found.route)
            assertEquals(emptyList<Candidate>(), found.candidates)
            assertEquals(emptyList<String>(), archive.searches)
        }

    @Test
    fun `a search answer that is not one fails the find`() {
        assertThrows(IOException::class.java) { ItemFinder.parseSearch("<html>") }
        assertThrows(IOException::class.java) { ItemFinder.parseSearch("{}") }
        assertEquals(
            listOf(Candidate("a", 0)),
            ItemFinder.parseSearch("""{"response":{"docs":[{"identifier":"a"},{"downloads":3},"x"]}}"""),
        )
    }
}
