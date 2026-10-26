package com.cooper.binge.companion.archive.index

/**
 * The SPARQL queries the index runs against the Wikidata Query Service.
 *
 * P4947 is the TMDB movie id, P4983 the TMDB TV series id, P724 the Internet Archive identifier and
 * P345 the IMDb id.
 *
 * A TMDB movie is "not a film" only when every one of its classes (P31) is a known non-film kind:
 * an instance of a subclass of one of [NON_FILM_ROOTS] that is not also a subclass of Q11424
 * (film). Anything else, an item with no class at all included, may be a film and goes on to the
 * resolver's routes.
 */
internal object WikidataQueries {
    /**
     * Television series (Q5398426), television series episode (Q21191270), miniseries (Q1259759),
     * literary work (Q7725634) and film series (Q24856).
     */
    private const val NON_FILM_ROOTS = "wd:Q5398426 wd:Q21191270 wd:Q1259759 wd:Q7725634 wd:Q24856"

    /**
     * Whether `?class` is a known non-film kind. The film check matters: a television pilot is both
     * a film and a television series episode.
     */
    private const val IS_NON_FILM_CLASS =
        "EXISTS { VALUES ?root { $NON_FILM_ROOTS } ?class wdt:P279* ?root } " +
            "&& NOT EXISTS { ?class wdt:P279* wd:Q11424 }"

    /**
     * Every item with a TMDB id and an Archive identifier, films and series alike, with each
     * film's classes (P31). One row per class.
     *
     * Which classes are non-film is a separate query, [NON_FILM_CLASSES]. Walking the subclass tree
     * here, once per row, took 15 to 27 seconds; split, the two take under 10 each.
     */
    val LINKS: String =
        """
        SELECT ?type ?tmdb ?ia ?class WHERE {
          {
            ?item wdt:P4947 ?tmdb ; wdt:P724 ?ia .
            BIND("movie" AS ?type)
            OPTIONAL { ?item wdt:P31 ?class }
          } UNION {
            ?item wdt:P4983 ?tmdb ; wdt:P724 ?ia .
            BIND("tv" AS ?type)
          }
        }
        """.trimIndent()

    /** The classes the linked films are instances of, and whether each is a known non-film kind. */
    val NON_FILM_CLASSES: String =
        """
        SELECT DISTINCT ?class ?nonFilm WHERE {
          [] wdt:P4947 [] ; wdt:P724 [] ; wdt:P31 ?class .
          BIND($IS_NON_FILM_CLASS AS ?nonFilm)
        }
        """.trimIndent()

    /**
     * The TMDB movie id for each of [imdbIds], for the Archive items found by IMDb id, and whether
     * the item is not a film: it has classes, and every one is a known non-film kind.
     */
    fun imdbToTmdb(imdbIds: Collection<String>): String {
        val values = imdbIds.joinToString(" ") { "\"$it\"" }
        return """
            SELECT ?imdb ?tmdb (COUNT(?class) > 0 && SUM(?known) = COUNT(?class) AS ?nonFilm) WHERE {
              VALUES ?imdb { $values }
              ?item wdt:P345 ?imdb ; wdt:P4947 ?tmdb .
              OPTIONAL {
                ?item wdt:P31 ?class .
                BIND(IF($IS_NON_FILM_CLASS, 1, 0) AS ?known)
              }
            }
            GROUP BY ?imdb ?tmdb
            """.trimIndent()
    }
}
