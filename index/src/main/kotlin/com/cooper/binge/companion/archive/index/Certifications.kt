package com.cooper.binge.companion.archive.index

/**
 * A film's certification in one region, as TMDB writes it for that region ("PG-13", "12A", "12").
 * [region] is an upper-case ISO 3166-1 alpha-2 code.
 */
data class FilmCertification(
    val tmdbId: Int,
    val region: String,
    val certification: String,
)

/**
 * Which Wikidata properties carry a film's certification, and what TMDB calls each value.
 *
 * Wikidata has one property per national rating system, and its value is an item ("PG-13", "FSK 12"),
 * not a string. `ListServedTitles` matches the host's `allowed_certifications` exactly, and the host
 * sends TMDB's own strings for the region, so each value item is mapped to the TMDB string here. A
 * value this table does not list is dropped, and the film reads as unrated in that region.
 *
 * The property ids and value items were read from Wikidata itself. The TMDB strings are the ones
 * TMDB's certification list has for each region: Germany's are plain numbers ("12", not "FSK 12"),
 * Spain's all-ages value is "APTA", and TMDB has no Japanese list, so Japan is not here.
 */
internal object Certifications {
    /** Region to the Wikidata property that holds its film certification. */
    val PROPERTIES: Map<String, String> =
        linkedMapOf(
            "US" to "P1657", // MPA film rating
            "GB" to "P2629", // BBFC rating
            "DE" to "P1981", // FSK film rating
            "FR" to "P2758", // CNC film rating (France)
            "AU" to "P3156", // Australian Classification
            "NL" to "P2684", // Kijkwijzer rating
            "ES" to "P3306", // ICAA rating
            "BR" to "P3216", // ClassInd rating
        )

    /** Region to Wikidata value item (a Q-id) to the TMDB certification string for that region. */
    val TMDB_STRINGS: Map<String, Map<String, String>> =
        mapOf(
            "US" to
                mapOf(
                    "Q18665330" to "G",
                    "Q18665334" to "PG",
                    "Q18665339" to "PG-13",
                    "Q18665344" to "R",
                    "Q18665349" to "NC-17",
                ),
            "GB" to
                mapOf(
                    "Q23301853" to "U",
                    "Q23301854" to "PG",
                    "Q23301855" to "12A",
                    "Q23301856" to "12",
                    "Q4550895" to "15",
                    "Q4557532" to "18",
                    "Q7274429" to "R18",
                ),
            "DE" to
                mapOf(
                    "Q20644794" to "0",
                    "Q20644795" to "6",
                    "Q20644796" to "12",
                    "Q20644797" to "16",
                    "Q20644798" to "18",
                ),
            "FR" to
                mapOf(
                    "Q23817729" to "U",
                    "Q23817740" to "12",
                    "Q23817741" to "16",
                    "Q23817742" to "18",
                ),
            "AU" to
                mapOf(
                    "Q26708075" to "G",
                    "Q26708076" to "PG",
                    "Q26708077" to "M",
                    "Q26708078" to "MA15+",
                    "Q26708079" to "R18+",
                    "Q26708080" to "X18+",
                    "Q26708081" to "RC",
                ),
            "NL" to
                mapOf(
                    "Q23649980" to "AL",
                    "Q23649981" to "6",
                    "Q23649982" to "9",
                    "Q23649983" to "12",
                    "Q23649984" to "16",
                ),
            "ES" to
                mapOf(
                    "Q27253939" to "APTA",
                    "Q27253940" to "7",
                    "Q27253945" to "12",
                    "Q27253947" to "16",
                    "Q27253952" to "18",
                    "Q27253956" to "X",
                ),
            "BR" to
                mapOf(
                    "Q26678731" to "L",
                    "Q26678732" to "10",
                    "Q26678733" to "12",
                    "Q26678734" to "14",
                    "Q26678735" to "16",
                    "Q26678736" to "18",
                ),
        )

    /** The TMDB string for a Wikidata value item in [region], or null when there is none. */
    fun tmdbString(
        region: String,
        valueItem: String,
    ): String? = TMDB_STRINGS[region]?.get(valueItem)
}
