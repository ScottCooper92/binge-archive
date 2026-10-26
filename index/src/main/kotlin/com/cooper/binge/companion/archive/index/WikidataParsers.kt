package com.cooper.binge.companion.archive.index

import java.io.IOException

/** Turns the two Wikidata query results into index rows. A row that does not parse is skipped. */
internal object WikidataParsers {
    /**
     * Folds the link query's rows, one per class, into one link per TMDB id and Archive identifier.
     * A film link is a film unless every one of its classes is in [nonFilmClasses]; a link with no
     * class at all is one too. A series never is.
     */
    fun links(
        tsv: String,
        nonFilmClasses: Set<String>,
    ): List<WikidataLink> {
        val links = LinkedHashMap<Triple<LinkedMediaType, Int, String>, Boolean>()
        SparqlTsv.parse(tsv).forEach { row ->
            val type =
                when (row["type"]) {
                    "movie" -> LinkedMediaType.MOVIE
                    "tv" -> LinkedMediaType.TV
                    else -> return@forEach
                }
            val tmdbId = row["tmdb"]?.toTmdbId() ?: return@forEach
            val archiveId = row["ia"]?.takeIf { it.isNotBlank() } ?: return@forEach
            val key = Triple(type, tmdbId, archiveId)
            val isFilm = type == LinkedMediaType.MOVIE && row["class"] !in nonFilmClasses
            links[key] = links[key] == true || isFilm
        }
        return links.map { (key, isFilm) -> WikidataLink(key.first, key.second, key.third, isFilm) }
    }

    /** The classes the non-film-class query marks as a known non-film kind. */
    fun nonFilmClasses(tsv: String): Set<String> =
        SparqlTsv
            .parse(tsv)
            .filter { it["nonFilm"].toBoolean() }
            .mapNotNull { it["class"]?.takeIf(String::isNotBlank) }
            .toSet()

    fun imdbLinks(tsv: String): List<ImdbTmdbLink> =
        SparqlTsv.parse(tsv).mapNotNull { row ->
            val imdbId = row["imdb"]?.takeIf { it.isImdbId() } ?: return@mapNotNull null
            val tmdbId = row["tmdb"]?.toTmdbId() ?: return@mapNotNull null
            ImdbTmdbLink(imdbId, tmdbId, isFilm = !row["nonFilm"].toBoolean())
        }

    fun checkNotEmpty(links: List<WikidataLink>): List<WikidataLink> {
        // The link query returns thousands of rows. None at all is a broken answer, not a fact, and
        // replacing the index with it would delete every link.
        if (links.isEmpty()) throw IOException("Wikidata returned no links")
        return links
    }
}

/** A TMDB id is a positive integer. Wikidata holds the odd malformed value; those are skipped. */
internal fun String.toTmdbId(): Int? = toIntOrNull()?.takeIf { it > 0 }

/** An IMDb title id: `tt` and at least seven digits. */
fun String.isImdbId(): Boolean = matches(Regex("tt\\d{7,}"))
