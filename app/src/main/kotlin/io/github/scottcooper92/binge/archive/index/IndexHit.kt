package io.github.scottcooper92.binge.archive.index

/**
 * What the index knows about one TMDB id.
 *
 * [isFilm] is false for a series, and for a TMDB movie id whose Wikidata item is not a film; the
 * resolver answers those differently from a film. [wikidataArchiveIds] are the Archive items
 * Wikidata links to the title, and [imdbArchiveIds] those the Archive itself tags with the title's
 * IMDb id.
 */
data class IndexHit(
    val isFilm: Boolean,
    val wikidataArchiveIds: List<String>,
    val imdbArchiveIds: List<String>,
)
