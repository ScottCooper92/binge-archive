package com.cooper.binge.companion.archive.index

/**
 * What the index knows about one TMDB id.
 *
 * [isFilm] is false for a series, and for a TMDB movie id whose Wikidata classes are all known
 * non-film kinds (a television series or episode, a miniseries, a literary work, a film series);
 * the resolver answers those differently from a film. A movie Wikidata classes as anything else,
 * or not at all, is true. [wikidataArchiveIds] are the Archive items
 * Wikidata links to the title, and [imdbArchiveIds] those the Archive itself tags with the title's
 * IMDb id. The resolver reads the two lists only when its live Wikidata query fails: they stand
 * in for routes 1 and 2, in that order, so a Wikidata outage does not fail a film the index knows.
 */
data class IndexHit(
    val isFilm: Boolean,
    val wikidataArchiveIds: List<String>,
    val imdbArchiveIds: List<String>,
)
