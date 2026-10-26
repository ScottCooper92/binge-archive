package com.cooper.binge.companion.archive.index

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException

/** One page of the Archive's advancedsearch answer for items with an IMDb id. */
internal data class ArchiveSearchPage(
    val numFound: Int,
    val docs: Int,
    val items: List<ArchiveImdbItem>,
)

internal object ArchiveParsers {
    private const val IMDB_URN = "urn:imdb:"

    /**
     * Reads an advancedsearch page. `external-identifier` is a string when an item has one and an
     * array when it has several (`urn:rottentomatoes:…` beside the IMDb one), so both are read.
     */
    fun imdbSearchPage(json: String): ArchiveSearchPage {
        val response = responseObject(json)
        val docs = response["docs"] as? JsonArray ?: malformed("no `docs`")
        val numFound = (response["numFound"] as? JsonPrimitive)?.intOrNull ?: malformed("no `numFound`")
        return ArchiveSearchPage(numFound = numFound, docs = docs.size, items = docs.flatMap(::imdbItems))
    }

    private fun responseObject(json: String): JsonObject {
        val root =
            try {
                Json.parseToJsonElement(json)
            } catch (e: SerializationException) {
                throw IOException("Unreadable Archive search response", e)
            }
        return (root as? JsonObject)?.get("response") as? JsonObject ?: malformed("no `response`")
    }

    private fun imdbItems(doc: JsonElement): List<ArchiveImdbItem> {
        val fields = doc as? JsonObject ?: return emptyList()
        val archiveId = fields["identifier"].string() ?: return emptyList()
        return fields["external-identifier"]
            .strings()
            .filter { it.startsWith(IMDB_URN) }
            .map { it.removePrefix(IMDB_URN) }
            .filter { it.isImdbId() }
            .distinct()
            .map { ArchiveImdbItem(archiveId, it) }
    }

    private fun malformed(what: String): Nothing = throw IOException("Archive search response has $what")

    private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement?.strings(): List<String> =
        when (this) {
            is JsonPrimitive -> listOfNotNull(string())
            is JsonArray -> mapNotNull { it.string() }
            else -> emptyList()
        }
}
