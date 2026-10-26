package com.cooper.binge.companion.archive.resolve

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/** One file of an Archive item, with the fields the mapper reads. */
data class ArchiveFile(
    val name: String,
    val format: String?,
    val sizeBytes: Long,
    val height: Int?,
    val lengthSeconds: Double?,
)

/** An Archive item's metadata, as `/metadata/{identifier}` answers it. */
data class ArchiveItem(
    val identifier: String,
    val mediaType: String?,
    val title: String?,
    val licenseUrl: String?,
    val language: String?,
    val accessRestricted: Boolean,
    val dark: Boolean,
    val files: List<ArchiveFile>,
)

object ArchiveMetadata {
    /**
     * Reads `/metadata/{identifier}`. An item that does not exist answers `{}`, and a dark one has
     * no metadata either; both read as dark, so the reader drops them. A body that is not a JSON
     * object is an [IOException], a failed load.
     */
    fun parse(
        identifier: String,
        json: String,
    ): ArchiveItem {
        val root =
            try {
                Json.parseToJsonElement(json) as? JsonObject
            } catch (e: SerializationException) {
                throw IOException("Unreadable metadata for $identifier", e)
            } ?: throw IOException("Metadata for $identifier is not an object")
        val metadata = root["metadata"] as? JsonObject
        return ArchiveItem(
            identifier = identifier,
            mediaType = metadata?.get("mediatype").firstString(),
            title = metadata?.get("title").firstString(),
            licenseUrl = metadata?.get("licenseurl").firstString(),
            language = metadata?.get("language").firstString(),
            accessRestricted = metadata?.get("access-restricted-item").firstString() == "true",
            dark = metadata == null || root["is_dark"].isTrue() || metadata["is_dark"].isTrue(),
            files = (root["files"] as? JsonArray).orEmpty().mapNotNull(::file),
        )
    }

    private fun file(element: JsonElement): ArchiveFile? {
        val fields = element as? JsonObject ?: return null
        val name = fields["name"].firstString() ?: return null
        return ArchiveFile(
            name = name,
            format = fields["format"].firstString(),
            sizeBytes = fields["size"].firstString()?.toLongOrNull() ?: 0,
            height = fields["height"].firstString()?.toIntOrNull()?.takeIf { it > 0 },
            lengthSeconds = fields["length"].firstString()?.let(::seconds),
        )
    }

    /** A length is seconds ("5752.82") or a clock ("95:52", "01:35:06"). */
    internal fun seconds(value: String): Double? {
        if (':' !in value) return value.toDoubleOrNull()?.takeIf { it > 0 }
        val parts = value.split(':').map { it.toDoubleOrNull() ?: return null }
        return parts.fold(0.0) { total, part -> total * SECONDS_PER_MINUTE + part }.takeIf { it > 0 }
    }

    private fun JsonElement?.firstString(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonArray -> firstOrNull().firstString()
            else -> null
        }?.takeIf { it.isNotBlank() }

    private fun JsonElement?.isTrue(): Boolean = firstString() == "true"

    private const val SECONDS_PER_MINUTE = 60
}
