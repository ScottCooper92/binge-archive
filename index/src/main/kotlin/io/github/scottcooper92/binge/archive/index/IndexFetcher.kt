package io.github.scottcooper92.binge.archive.index

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Fetches a whole [IndexSnapshot]: the Wikidata links (with which of them may be films), the Archive
 * items with an IMDb id, and the TMDB ids Wikidata gives for those IMDb ids. Blocking; call it off the main thread.
 *
 * Any failure is an [IOException], and nothing partial is returned: a refresh either has the whole
 * snapshot or keeps the index it had.
 */
class IndexFetcher(
    private val client: OkHttpClient,
    private val wikidataEndpoint: HttpUrl = WIKIDATA_SPARQL.toHttpUrl(),
    private val archiveSearch: HttpUrl = ARCHIVE_SEARCH.toHttpUrl(),
) {
    fun fetch(): IndexSnapshot {
        val nonFilmClasses = WikidataParsers.nonFilmClasses(sparql(WikidataQueries.NON_FILM_CLASSES))
        val links = WikidataParsers.checkNotEmpty(WikidataParsers.links(sparql(WikidataQueries.LINKS), nonFilmClasses))
        val archiveItems = archiveImdbItems()
        val imdbIds = archiveItems.map { it.imdbId }.distinct().sorted()
        val imdbLinks =
            imdbIds.chunked(IMDB_BATCH).flatMap { batch ->
                WikidataParsers.imdbLinks(sparql(WikidataQueries.imdbToTmdb(batch)))
            }
        return IndexSnapshot(links = links, archiveItems = archiveItems, imdbLinks = imdbLinks)
    }

    /** POST, not GET: the IMDb join carries a few thousand ids, past what a URL should hold. */
    private fun sparql(query: String): String {
        val request =
            Request
                .Builder()
                .url(wikidataEndpoint)
                .header("Accept", "text/tab-separated-values")
                .post(FormBody.Builder().add("query", query).build())
                .build()
        return body(request)
    }

    private fun archiveImdbItems(): List<ArchiveImdbItem> {
        val items = mutableListOf<ArchiveImdbItem>()
        var page = 1
        var seen = 0
        do {
            val url =
                archiveSearch
                    .newBuilder()
                    .addQueryParameter("q", "mediatype:movies AND external-identifier:urn\\:imdb*")
                    .addQueryParameter("fl[]", "identifier")
                    .addQueryParameter("fl[]", "external-identifier")
                    .addQueryParameter("sort[]", "identifier asc")
                    .addQueryParameter("rows", ARCHIVE_ROWS.toString())
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("output", "json")
                    .build()
            val result = ArchiveParsers.imdbSearchPage(body(Request.Builder().url(url).build()))
            items += result.items
            seen += result.docs
            page++
        } while (result.docs > 0 && seen < result.numFound && page <= MAX_ARCHIVE_PAGES)
        return items.distinct()
    }

    private fun body(request: Request): String =
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from ${request.url.host}")
            response.body.string()
        }

    companion object {
        const val WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
        const val ARCHIVE_SEARCH = "https://archive.org/advancedsearch.php"

        /** Thousands of ids fit one POST; batches keep a growing list from outgrowing it. */
        private const val IMDB_BATCH = 2_000
        private const val ARCHIVE_ROWS = 10_000

        /** A runaway guard: the whole set is one page today. */
        private const val MAX_ARCHIVE_PAGES = 20
    }
}
