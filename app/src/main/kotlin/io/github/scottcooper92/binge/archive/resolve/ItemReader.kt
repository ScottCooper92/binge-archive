package io.github.scottcooper92.binge.archive.resolve

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * The candidates' metadata loads in parallel, and [readEach] hands on each admitted item as soon as it
 * and every candidate ahead of it have loaded, so a slow later item never holds back an earlier one.
 *
 * One item failing to load drops that item while others are still read. If no item is admitted and
 * any load failed, the resolve fails (transient, so the host may retry): the item that failed might
 * have been the licit one, so "no licit source" is not an answer the app can give.
 */
class ItemReader(
    private val http: ResolverHttp,
) {
    /** Each admitted item, in candidate order, as soon as it and every candidate ahead of it are read. */
    fun readEach(found: Found): Flow<LicensedItem> =
        flow {
            coroutineScope {
                val loads = found.candidates.map { candidate -> candidate to async { load(candidate, found.preloaded) } }
                var admitted = 0
                var failure: Throwable? = null
                for ((candidate, load) in loads) {
                    val result = load.await()
                    result.exceptionOrNull()?.let { if (failure == null) failure = it }
                    val item = result.getOrNull()?.let { admit(candidate, it, found) } ?: continue
                    admitted++
                    emit(item)
                }
                failure?.takeIf { admitted == 0 }?.let { throw IOException("No candidate item could be read", it) }
            }
        }

    private suspend fun load(
        candidate: Candidate,
        preloaded: Map<String, ArchiveItem>,
    ): Result<ArchiveItem> = preloaded[candidate.identifier]?.let { Result.success(it) } ?: loadOne(http, candidate)

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
                candidates.map { candidate -> async { candidate to loadOne(http, candidate) } }.awaitAll()
            }

        /** One candidate's metadata, or the [IOException] that stopped it loading. */
        private suspend fun loadOne(
            http: ResolverHttp,
            candidate: Candidate,
        ): Result<ArchiveItem> =
            try {
                Result.success(ArchiveMetadata.parse(candidate.identifier, http.metadata(candidate.identifier)))
            } catch (e: IOException) {
                Result.failure(e)
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
