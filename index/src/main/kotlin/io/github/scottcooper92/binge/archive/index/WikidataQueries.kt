package io.github.scottcooper92.binge.archive.index

/**
 * The two SPARQL queries the index runs against the Wikidata Query Service.
 *
 * P4947 is the TMDB movie id, P4983 the TMDB TV series id, P724 the Internet Archive identifier and
 * P345 the IMDb id. "Is a film" is an instance of Q11424 (film) or of any subclass of it.
 */
internal object WikidataQueries {
    /**
     * Every item with a TMDB id and an Archive identifier, films and series alike, with each
     * film's classes (P31). One row per class.
     *
     * Whether a class is a film is a separate query, [FILM_CLASSES]. Walking the subclass tree here,
     * once per row, took 15 to 27 seconds; split, the two take under 10 each.
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

    /** The classes the linked films are instances of, and whether each is a kind of film. */
    val FILM_CLASSES: String =
        """
        SELECT DISTINCT ?class ?film WHERE {
          [] wdt:P4947 [] ; wdt:P724 [] ; wdt:P31 ?class .
          BIND(EXISTS { ?class wdt:P279* wd:Q11424 } AS ?film)
        }
        """.trimIndent()

    /** The TMDB movie id for each of [imdbIds], for the Archive items found by IMDb id. */
    fun imdbToTmdb(imdbIds: Collection<String>): String {
        val values = imdbIds.joinToString(" ") { "\"$it\"" }
        return """
            SELECT ?imdb ?tmdb ?film WHERE {
              VALUES ?imdb { $values }
              ?item wdt:P345 ?imdb ; wdt:P4947 ?tmdb .
              BIND(EXISTS { ?item wdt:P31/wdt:P279* wd:Q11424 } AS ?film)
            }
            """.trimIndent()
    }
}
