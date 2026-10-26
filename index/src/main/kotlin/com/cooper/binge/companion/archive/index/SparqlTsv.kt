package com.cooper.binge.companion.archive.index

import java.io.IOException

/**
 * Reads the Wikidata Query Service's `text/tab-separated-values` output: a header of `?name`
 * columns, then one row per line. TSV, not JSON, because the full link query is about a tenth of
 * the size, and the same text is what the seed is built from.
 *
 * Each cell is an RDF term. A literal is quoted, `"10331"`, and may carry a datatype,
 * `"true"^^<…#boolean>`, or a language tag, `"x"@en`. Only the quoted value is kept.
 */
object SparqlTsv {
    fun parse(tsv: String): List<Map<String, String>> {
        val lines = tsv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) throw IOException("Empty SPARQL response")
        val header = lines.first().split('\t').map { it.removePrefix("?") }
        return lines.drop(1).map { line ->
            val cells = line.split('\t')
            if (cells.size != header.size) throw IOException("SPARQL row has ${cells.size} cells, expected ${header.size}")
            header.zip(cells.map(::literal)).toMap()
        }
    }

    private fun literal(cell: String): String {
        if (!cell.startsWith('"')) return cell.removeSurrounding("<", ">")
        val end = cell.lastIndexOf('"')
        if (end <= 0) throw IOException("Unterminated literal: $cell")
        return cell.substring(1, end).replace("\\\"", "\"").replace("\\\\", "\\")
    }
}
