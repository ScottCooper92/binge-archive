package io.github.scottcooper92.binge.archive.resolve

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
)

/**
 * Stage 1: finds the Archive items for a TMDB movie id. Three routes, in order, stopping at the
 * first that finds any items:
 *
 * 1. Wikidata: the Archive identifiers (P724) on the item with this TMDB id.
 * 2. IMDb: Archive items tagged with the film's IMDb id (P345).
 * 3. Title and year: an Archive title search. Its matches are loose, so a match counts as found
 *    only if a video file runs within 8% of the Wikidata runtime, checked before any licence check.
 *    With no runtime, this route is skipped. If no match qualifies and any failed to load, the find
 *    fails with an `IOException`: the failed one might have been the film.
 */
class ItemFinder(
    private val http: ResolverHttp,
) {
    suspend fun find(tmdbId: Int): Found {
        val facts = WikidataTitle.parse(http.sparql(WikidataTitle.query(tmdbId)))
        if (facts.archiveIds.isNotEmpty()) {
            val downloads = downloads(facts.archiveIds)
            val candidates = facts.archiveIds.map { Candidate(it, downloads[it] ?: 0) }.sortedByDescending { it.downloads }
            return Found(Route.WIKIDATA, candidates.take(MAX_CANDIDATES), facts)
        }
        facts.imdbId?.let { imdb ->
            val candidates = search("mediatype:movies AND external-identifier:\"urn:imdb:$imdb\"")
            if (candidates.isNotEmpty()) return Found(Route.IMDB, candidates, facts)
        }
        val title = facts.title
        if (title != null && facts.runtimeSeconds != null) {
            val candidates = search(titleQuery(title, facts.year))
            if (candidates.isNotEmpty()) return confirmed(candidates, facts)
        }
        return Found(null, emptyList(), facts)
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

    private suspend fun downloads(identifiers: List<String>): Map<String, Long> {
        val query = identifiers.joinToString(" OR ", prefix = "identifier:(", postfix = ")") { "\"${quoted(it)}\"" }
        return parseSearch(http.search(query, rows = identifiers.size)).associate { it.identifier to it.downloads }
    }

    private suspend fun search(query: String): List<Candidate> = parseSearch(http.search(query, rows = MAX_CANDIDATES))

    companion object {
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
