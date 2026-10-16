package io.github.scottcooper92.binge.archive.index

/** The TMDB id space a Wikidata link is in. TMDB numbers films and series separately. */
enum class LinkedMediaType { MOVIE, TV }

/**
 * A Wikidata item that carries both a TMDB id and an Internet Archive identifier.
 *
 * [isFilm] is whether the item is a film (an instance of a subclass of Wikidata's "film"). A TMDB
 * movie id can belong to something that is not one, and the resolver answers those differently.
 */
data class WikidataLink(
    val mediaType: LinkedMediaType,
    val tmdbId: Int,
    val archiveId: String,
    val isFilm: Boolean,
)

/** An Archive `movies` item whose metadata names an IMDb id, as `urn:imdb:tt…`. */
data class ArchiveImdbItem(
    val archiveId: String,
    val imdbId: String,
)

/** The TMDB movie id Wikidata gives for an IMDb id, so an [ArchiveImdbItem] can be found by TMDB id. */
data class ImdbTmdbLink(
    val imdbId: String,
    val tmdbId: Int,
    val isFilm: Boolean,
)

/**
 * Everything the index holds, as fetched in one refresh. It is public metadata only: no user data
 * is ever part of it.
 */
data class IndexSnapshot(
    val links: List<WikidataLink>,
    val archiveItems: List<ArchiveImdbItem>,
    val imdbLinks: List<ImdbTmdbLink>,
)
