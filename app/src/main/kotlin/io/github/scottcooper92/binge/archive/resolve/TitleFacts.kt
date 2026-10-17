package io.github.scottcooper92.binge.archive.resolve

import io.github.scottcooper92.binge.archive.index.SparqlTsv
import io.github.scottcooper92.binge.archive.index.isImdbId

/** What Wikidata says about one TMDB movie id: where to look, and how to check what is found. */
data class TitleFacts(
    val archiveIds: List<String>,
    val imdbId: String?,
    val title: String?,
    val year: Int?,
    val runtimeSeconds: Double?,
) {
    companion object {
        val NONE = TitleFacts(emptyList(), null, null, null, null)
    }
}

object WikidataTitle {
    private const val MINUTE = "http://www.wikidata.org/entity/Q7727"
    private const val SECOND = "http://www.wikidata.org/entity/Q11574"
    private const val HOUR = "http://www.wikidata.org/entity/Q25235"
    private const val SECONDS_PER_MINUTE = 60.0
    private const val SECONDS_PER_HOUR = 3600.0
    private const val YEAR_LENGTH = 4

    /** P724 Archive ids, P345 IMDb id, the English label, P577 dates and P2047 durations with units. */
    fun query(tmdbId: Int): String =
        """
        SELECT ?ia ?imdb ?title ?date ?amount ?unit WHERE {
          ?item wdt:P4947 "$tmdbId" .
          OPTIONAL { ?item wdt:P724 ?ia }
          OPTIONAL { ?item wdt:P345 ?imdb }
          OPTIONAL { ?item rdfs:label ?title FILTER(LANG(?title) = "en") }
          OPTIONAL { ?item wdt:P577 ?date }
          OPTIONAL { ?item p:P2047/psv:P2047 [ wikibase:quantityAmount ?amount ; wikibase:quantityUnit ?unit ] }
        }
        """.trimIndent()

    /** Folds the query's rows, one per combination of values, into one [TitleFacts]. */
    fun parse(tsv: String): TitleFacts {
        val rows = SparqlTsv.parse(tsv)
        if (rows.isEmpty()) return TitleFacts.NONE
        return TitleFacts(
            archiveIds = rows.mapNotNull { it["ia"]?.takeIf(String::isNotBlank) }.distinct(),
            imdbId = rows.firstNotNullOfOrNull { row -> row["imdb"]?.takeIf { it.isImdbId() } },
            title = rows.firstNotNullOfOrNull { it["title"]?.takeIf(String::isNotBlank) },
            // The earliest release: a film often has a premiere and later national releases.
            year = rows.mapNotNull { it["date"]?.take(YEAR_LENGTH)?.toIntOrNull() }.minOrNull(),
            runtimeSeconds = rows.firstNotNullOfOrNull(::runtime),
        )
    }

    private fun runtime(row: Map<String, String>): Double? {
        val amount = row["amount"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return when (row["unit"]) {
            MINUTE -> amount * SECONDS_PER_MINUTE
            SECOND -> amount
            HOUR -> amount * SECONDS_PER_HOUR
            else -> null
        }
    }
}
