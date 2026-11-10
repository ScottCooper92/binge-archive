package com.cooper.binge.companion.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * One table per region, written out here on purpose: each Wikidata value item (checked against Wikidata) and the exact
 * string TMDB uses for it in that region. Binge sends TMDB's strings, so a change to a string is a change to what matches.
 */
class CertificationsTest {
    private fun assertRegion(
        region: String,
        property: String,
        expected: Map<String, String>,
    ) {
        assertEquals(property, Certifications.PROPERTIES[region])
        assertEquals(expected, Certifications.TMDB_STRINGS[region])
        expected.forEach { (item, tmdb) -> assertEquals(tmdb, Certifications.tmdbString(region, item)) }
    }

    @Test
    fun `US maps the MPA ratings`() =
        assertRegion(
            "US",
            "P1657",
            mapOf("Q18665330" to "G", "Q18665334" to "PG", "Q18665339" to "PG-13", "Q18665344" to "R", "Q18665349" to "NC-17"),
        )

    @Test
    fun `GB maps the BBFC certificates`() =
        assertRegion(
            "GB",
            "P2629",
            mapOf(
                "Q23301853" to "U",
                "Q23301854" to "PG",
                "Q23301855" to "12A",
                "Q23301856" to "12",
                "Q4550895" to "15",
                "Q4557532" to "18",
                "Q7274429" to "R18",
            ),
        )

    @Test
    fun `DE maps the FSK ratings to TMDB's plain numbers`() =
        assertRegion(
            "DE",
            "P1981",
            mapOf("Q20644794" to "0", "Q20644795" to "6", "Q20644796" to "12", "Q20644797" to "16", "Q20644798" to "18"),
        )

    @Test
    fun `FR maps the CNC categories`() =
        assertRegion("FR", "P2758", mapOf("Q23817729" to "U", "Q23817740" to "12", "Q23817741" to "16", "Q23817742" to "18"))

    @Test
    fun `AU maps the Australian classifications`() =
        assertRegion(
            "AU",
            "P3156",
            mapOf(
                "Q26708075" to "G",
                "Q26708076" to "PG",
                "Q26708077" to "M",
                "Q26708078" to "MA15+",
                "Q26708079" to "R18+",
                "Q26708080" to "X18+",
                "Q26708081" to "RC",
            ),
        )

    @Test
    fun `NL maps the Kijkwijzer ratings`() =
        assertRegion(
            "NL",
            "P2684",
            mapOf("Q23649980" to "AL", "Q23649981" to "6", "Q23649982" to "9", "Q23649983" to "12", "Q23649984" to "16"),
        )

    @Test
    fun `ES maps the ICAA ratings, with TMDB's APTA for all audiences`() =
        assertRegion(
            "ES",
            "P3306",
            mapOf(
                "Q27253939" to "APTA",
                "Q27253940" to "7",
                "Q27253945" to "12",
                "Q27253947" to "16",
                "Q27253952" to "18",
                "Q27253956" to "X",
            ),
        )

    @Test
    fun `BR maps the ClassInd ratings`() =
        assertRegion(
            "BR",
            "P3216",
            mapOf(
                "Q26678731" to "L",
                "Q26678732" to "10",
                "Q26678733" to "12",
                "Q26678734" to "14",
                "Q26678735" to "16",
                "Q26678736" to "18",
            ),
        )

    @Test
    fun `every region with a property has a table, and no other`() {
        assertEquals(Certifications.PROPERTIES.keys, Certifications.TMDB_STRINGS.keys)
    }

    @Test
    fun `a value item with no TMDB string, or a region with no table, maps to nothing`() {
        assertNull(Certifications.tmdbString("US", "Q47274658"))
        assertNull(Certifications.tmdbString("DE", "Q18665330"))
        assertNull(Certifications.tmdbString("JP", "Q23790275"))
    }

    @Test
    fun `each region's query names its own property and binds its region`() {
        Certifications.PROPERTIES.forEach { (region, property) ->
            val query = WikidataQueries.certifications(region)
            assertEquals(true, query.contains("wdt:$property ?value") && query.contains("BIND(\"$region\" AS ?region)"))
        }
        assertThrows(IllegalStateException::class.java) { WikidataQueries.certifications("JP") }
    }
}
