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
)

/**
 * Stage 1: finds the Archive items for a TMDB movie id. Three routes, in order, stopping at the
 * first that finds any items:
 *
 * 1. Wikidata: the Archive identifiers (P724) on the item with this TMDB id.
 * 2. IMDb: Archive items tagged with the film's IMDb id (P345).
 * 3. Title and year: an Archive title search. Its matches are loose, so [ItemReader] keeps one only
 *    if a video file runs within 8% of the Wikidata runtime. With no runtime, this route is skipped.
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
            val year = facts.year?.let { " AND year:$it" }.orEmpty()
            val candidates = search("mediatype:movies AND title:(\"${quoted(title)}\")$year")
            if (candidates.isNotEmpty()) return Found(Route.TITLE, candidates, facts)
        }
        return Found(null, emptyList(), facts)
    }

    private suspend fun downloads(identifiers: List<String>): Map<String, Long> {
        val query = identifiers.joinToString(" OR ", prefix = "identifier:(", postfix = ")") { "\"${quoted(it)}\"" }
        return parseSearch(http.search(query, rows = identifiers.size)).associate { it.identifier to it.downloads }
    }

    private suspend fun search(query: String): List<Candidate> = parseSearch(http.search(query, rows = MAX_CANDIDATES))

    companion object {
        /** At most this many items are read for one resolve. */
        const val MAX_CANDIDATES = 5

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
