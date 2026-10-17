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
 * not load. A title-route candidate must also have a video file within 8% of the film's runtime;
 * [ItemFinder] has already checked that, and [Found.preloaded] carries the metadata it read.
 *
 * One item failing to load drops that item while others are still read. If no item is admitted and
 * any load failed, the resolve fails (transient, so the host may retry): the item that failed might
 * have been the licit one, so "no licit source" is not an answer the app can give.
 */
class ItemReader(
    private val http: ResolverHttp,
) {
    suspend fun read(found: Found): List<LicensedItem> {
        if (found.candidates.isEmpty()) return emptyList()
        val loads =
            found.preloaded.let { preloaded ->
                val (ready, missing) = found.candidates.partition { it.identifier in preloaded }
                ready.map { it to Result.success(preloaded.getValue(it.identifier)) } + loadAll(http, missing)
            }
        val loaded = loads.mapNotNull { (candidate, result) -> result.getOrNull()?.let { candidate to it } }
        val admitted = loaded.mapNotNull { (candidate, item) -> admit(candidate, item, found) }
        if (admitted.isEmpty()) {
            val failure = loads.firstNotNullOfOrNull { it.second.exceptionOrNull() }
            if (failure != null) throw IOException("No candidate item could be read", failure)
        }
        return admitted
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

    companion object {
        /** Each candidate's metadata, or the [IOException] that stopped it loading, read in parallel. */
        internal suspend fun loadAll(
            http: ResolverHttp,
            candidates: List<Candidate>,
        ): List<Pair<Candidate, Result<ArchiveItem>>> =
            coroutineScope {
                candidates
                    .map { candidate ->
                        async {
                            candidate to
                                try {
                                    Result.success(ArchiveMetadata.parse(candidate.identifier, http.metadata(candidate.identifier)))
                                } catch (e: IOException) {
                                    Result.failure(e)
                                }
                        }
                    }.awaitAll()
            }

        /** Whether a video file of [item] runs within [RUNTIME_TOLERANCE] of the film's runtime. */
        internal fun runsLike(
            item: ArchiveItem,
            runtimeSeconds: Double?,
        ): Boolean {
            val runtime = runtimeSeconds ?: return false
            return item.files.any { file ->
                val length = file.lengthSeconds ?: return@any false
                VideoFormats.of(file.format, file.name) != null && abs(length - runtime) <= runtime * RUNTIME_TOLERANCE
            }
        }

        private const val MOVIES = "movies"

        /** Deliberately loose: Archive transfers differ in frame rate, leaders and intertitles. */
        const val RUNTIME_TOLERANCE = 0.08
    }
}
