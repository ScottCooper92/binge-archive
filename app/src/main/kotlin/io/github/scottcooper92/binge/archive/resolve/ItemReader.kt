package io.github.scottcooper92.binge.archive.resolve

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.math.abs

/** An item that passed every check, with the licence it may be returned under. */
data class LicensedItem(
    val item: ArchiveItem,
    val licence: Licence,
    val downloads: Long,
)

/**
 * Stage 2: reads each candidate's metadata and keeps only what may be returned. An item is dropped
 * when it has no allowlisted licence, is access-restricted or dark, is not a `movies` item, or does
 * not load. A title-route candidate must also have a video file within 8% of the film's runtime.
 *
 * One item failing to load drops that item. Every item failing is a failure of the resolve
 * (transient, so the host may retry), not an empty answer.
 */
class ItemReader(
    private val http: ResolverHttp,
) {
    suspend fun read(found: Found): List<LicensedItem> {
        if (found.candidates.isEmpty()) return emptyList()
        val loads =
            coroutineScope {
                found.candidates.map { candidate -> async { candidate to load(candidate) } }.awaitAll()
            }
        val loaded = loads.mapNotNull { (candidate, result) -> result.getOrNull()?.let { candidate to it } }
        if (loaded.isEmpty()) {
            throw IOException("No candidate item could be read", loads.firstNotNullOfOrNull { it.second.exceptionOrNull() })
        }
        return loaded.mapNotNull { (candidate, item) -> admit(candidate, item, found) }
    }

    /** One item's metadata, or the [IOException] that stopped it loading. */
    private suspend fun load(candidate: Candidate): Result<ArchiveItem> =
        try {
            Result.success(ArchiveMetadata.parse(candidate.identifier, http.metadata(candidate.identifier)))
        } catch (e: IOException) {
            Result.failure(e)
        }

    private fun admit(
        candidate: Candidate,
        item: ArchiveItem,
        found: Found,
    ): LicensedItem? {
        if (item.dark || item.accessRestricted || item.mediaType != MOVIES) return null
        val licence = LicenceAllowlist.match(item.licenseUrl) ?: return null
        if (found.route == Route.TITLE && !runsLike(item, found.facts.runtimeSeconds)) return null
        return LicensedItem(item, licence, candidate.downloads)
    }

    private fun runsLike(
        item: ArchiveItem,
        runtimeSeconds: Double?,
    ): Boolean {
        val runtime = runtimeSeconds ?: return false
        return item.files.any { file ->
            val length = file.lengthSeconds ?: return@any false
            VideoFormats.of(file.format, file.name) != null && abs(length - runtime) <= runtime * RUNTIME_TOLERANCE
        }
    }

    companion object {
        private const val MOVIES = "movies"

        /** Deliberately loose: Archive transfers differ in frame rate, leaders and intertitles. */
        const val RUNTIME_TOLERANCE = 0.08
    }
}
