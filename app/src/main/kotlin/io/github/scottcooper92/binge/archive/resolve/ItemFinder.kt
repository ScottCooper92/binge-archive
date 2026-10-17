package io.github.scottcooper92.binge.archive.resolve

import android.util.Log
import io.github.scottcooper92.binge.archive.index.IndexHit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException

/** How a candidate was found. Fixed order; the first route that finds anything is the only one used. */
enum class Route { WIKIDATA, IMDB, TITLE }

/** An Archive item worth reading, with its download count for ordering. */
data class Candidate(
    val identifier: String,
    val downloads: Long,
)

/** What [ItemFinder] found: the route, its candidates (best-downloaded first), and the facts used. */
data class Found(
    val route: Route?,
    val candidates: List<Candidate>,
    val facts: TitleFacts,
    /** Metadata the finder already read, by identifier, so [ItemReader] does not fetch it again. */
    val preloaded: Map<String, ArchiveItem> = emptyMap(),
    /** True when Wikidata failed and the candidates are the index's Archive ids instead. */
    val fromIndex: Boolean = false,
    /** True when the title route would have run and the user's settings turned it off. */
    val titleSkipped: Boolean = false,
)

/**
 * Stage 1: finds the Archive items for a TMDB movie id. Three routes, in order, stopping at the
 * first that finds any items:
 *
 * 1. Wikidata: the Archive identifiers (P724) on the item with this TMDB id.
 * 2. IMDb: Archive items tagged with the film's IMDb id (P345).
 * 3. Title and year: an Archive title search. Its matches are loose, so a match counts as found
 *    only if a video file runs within 8% of the Wikidata runtime, checked before any licence check.
 *    With no runtime, or with title matches off in the user's settings, this route is skipped. If no match qualifies and any failed to load, the find
 *    fails with an `IOException`: the failed one might have been the film.
 *
 * Wikidata is asked first even for a film the index knows, since only it gives the title, year and
 * runtime. When the query fails, the index's Archive ids stand in: its Wikidata ids as route 1, else
 * its IMDb-tagged ids as route 2, with [TitleFacts.NONE]. Neither route checks a runtime, so nothing
 * is lost but the label's title and year and [SourceMapper]'s minimum length. The licence check is
 * [ItemReader]'s and still applies. With no index ids to fall back on, the failure is the find's.
 * On the Wikidata route the search only orders the candidates. If it fails, they keep Wikidata's
 * order, the failure goes to [log], and the find goes on.
 */
class ItemFinder(
    private val http: ResolverHttp,
    private val log: (String, IOException) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {
    /**
     * Finds the items for [tmdbId]. [hit] is what the index knows of it, the fallback when Wikidata
     * fails. [titleMatches] is the user's setting for the title route.
     */
    suspend fun find(
        tmdbId: Int,
        hit: IndexHit? = null,
        titleMatches: Boolean = true,
    ): Found {
        val facts =
            try {
                WikidataTitle.parse(http.sparql(WikidataTitle.query(tmdbId)))
            } catch (e: IOException) {
                return fromIndex(hit) ?: throw e
            }
        if (facts.archiveIds.isNotEmpty()) return Found(Route.WIKIDATA, ranked(facts.archiveIds), facts)
        facts.imdbId?.let { imdb ->
            val candidates = search("mediatype:movies AND external-identifier:\"urn:imdb:$imdb\"")
            if (candidates.isNotEmpty()) return Found(Route.IMDB, candidates, facts)
        }
        return byTitle(facts, titleMatches) ?: Found(null, emptyList(), facts)
    }

    /**
     * Route 3, or null when it finds nothing or cannot run: with no title or runtime to check
     * against. With [titleMatches] off, it does not search, and says it was skipped.
     */
    private suspend fun byTitle(
        facts: TitleFacts,
        titleMatches: Boolean,
    ): Found? {
        val title = facts.title
        if (title == null || facts.runtimeSeconds == null) return null
        if (!titleMatches) return Found(null, emptyList(), facts, titleSkipped = true)
        val candidates = search(titleQuery(title, facts.year))
        return if (candidates.isEmpty()) null else confirmed(candidates, facts)
    }

    /**
     * The title matches that run like the film, plus any candidate that failed to load, or [Found]
     * with no route when none matches.
     */
    private suspend fun confirmed(
        candidates: List<Candidate>,
        facts: TitleFacts,
    ): Found {
        val loads = ItemReader.loadAll(http, candidates)
        val matches =
            loads.mapNotNull { (candidate, result) ->
                result.getOrNull()?.takeIf { ItemReader.runsLike(it, facts.runtimeSeconds) }?.let { candidate to it }
            }
        if (matches.isEmpty()) {
            val failure = loads.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            if (failure != null) throw IOException("No title match could be read", failure)
            return Found(null, emptyList(), facts)
        }
        // A candidate that failed to load stays in, unread: ItemReader tries it again, and fails the
        // resolve if nothing is admitted and it still does not load.
        val kept = loads.filter { (candidate, result) -> result.isFailure || matches.any { it.first == candidate } }
        return Found(Route.TITLE, kept.map { it.first }, facts, matches.associate { (c, item) -> c.identifier to item })
    }

    /** The index's Archive ids for [hit] as candidates, or null when it has none. */
    private suspend fun fromIndex(hit: IndexHit?): Found? {
        val (route, identifiers) =
            when {
                hit == null -> return null
                hit.wikidataArchiveIds.isNotEmpty() -> Route.WIKIDATA to hit.wikidataArchiveIds
                hit.imdbArchiveIds.isNotEmpty() -> Route.IMDB to hit.imdbArchiveIds
                else -> return null
            }
        return Found(route, ranked(identifiers), TitleFacts.NONE, fromIndex = true)
    }

    /** [identifiers] as candidates, best-downloaded first, at most [MAX_CANDIDATES]. */
    private suspend fun ranked(identifiers: List<String>): List<Candidate> {
        val downloads =
            try {
                downloads(identifiers)
            } catch (e: IOException) {
                log("Download counts unavailable; keeping the given order", e)
                emptyMap()
            }
        return identifiers
            .map { Candidate(it, downloads[it] ?: 0) }
            .sortedByDescending { it.downloads }
            .take(MAX_CANDIDATES)
    }

    private suspend fun downloads(identifiers: List<String>): Map<String, Long> {
        val query = identifiers.joinToString(" OR ", prefix = "identifier:(", postfix = ")") { "\"${quoted(it)}\"" }
        return parseSearch(http.search(query, rows = identifiers.size)).associate { it.identifier to it.downloads }
    }

    private suspend fun search(query: String): List<Candidate> = parseSearch(http.search(query, rows = MAX_CANDIDATES))

    companion object {
        private const val TAG = "ArchiveFinder"

        /** At most this many items are read for one resolve. */
        const val MAX_CANDIDATES = 5

        /** The title route's Archive search: `movies` items whose title holds [title], from [year] when known. */
        internal fun titleQuery(
            title: String,
            year: Int?,
        ): String = "mediatype:movies AND title:(\"${quoted(title)}\")" + year?.let { " AND year:$it" }.orEmpty()

        /** A phrase inside a Lucene query: drop what would end it or escape out of it. */
        internal fun quoted(text: String): String = text.replace("\\", " ").replace("\"", " ").trim()

        internal fun parseSearch(json: String): List<Candidate> {
            val docs =
                try {
                    ((Json.parseToJsonElement(json) as? JsonObject)?.get("response") as? JsonObject)?.get("docs") as? JsonArray
                } catch (e: SerializationException) {
                    throw IOException("Unreadable Archive search response", e)
                } ?: throw IOException("Archive search response has no docs")
            return docs.mapNotNull { doc ->
                val fields = doc as? JsonObject ?: return@mapNotNull null
                val identifier = (fields["identifier"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                Candidate(identifier, (fields["downloads"] as? JsonPrimitive)?.longOrNull ?: 0)
            }
        }
    }
}
