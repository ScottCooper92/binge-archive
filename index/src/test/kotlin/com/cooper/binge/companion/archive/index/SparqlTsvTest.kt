package com.cooper.binge.companion.archive.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SparqlTsvTest {
    @Test
    fun `typed, tagged and escaped literals keep only their value`() {
        val row =
            listOf(
                "\"true\"^^<http://www.w3.org/2001/XMLSchema#boolean>",
                "\"Nosferatu\"@de",
                "\"say \\\"hi\\\"\"",
                "<http://www.wikidata.org/entity/Q1>",
            )
        val tsv = "?a\t?b\t?c\t?d\n" + row.joinToString("\t") + "\n"

        assertEquals(
            listOf(mapOf("a" to "true", "b" to "Nosferatu", "c" to "say \"hi\"", "d" to "http://www.wikidata.org/entity/Q1")),
            SparqlTsv.parse(tsv),
        )
    }

    @Test
    fun `an empty body or a short row is a failure`() {
        assertThrows(IOException::class.java) { SparqlTsv.parse("") }
        assertThrows(IOException::class.java) { SparqlTsv.parse("?a\t?b\n\"1\"\n") }
        assertThrows(IOException::class.java) { SparqlTsv.parse("?a\n\"\n") }
    }
}
