package com.cooper.binge.companion.archive.index

import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The on-device index: Probe's answers, the resolver's film check, and the resolver's fallback for
 * its first two routes when the live Wikidata query fails.
 *
 * A fresh install starts from the seed the APK ships ([seed]), loaded before the first read. The
 * weekly refresh replaces it. A failed refresh is logged and changes nothing else: the previous
 * index stays, and nothing is ever deleted except by a successful replace.
 */
class IndexRepository(
    private val dao: IndexDao,
    private val seed: () -> IndexSnapshot?,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Where the seed is read and parsed: a 450 KB asset, kept off the caller's thread. */
    private val seedContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val seeding = Mutex()

    /** Loads the seed for a lookup that would not wait for it; a failed load is left for the next lookup, never thrown into the process. */
    private val background = CoroutineScope(SupervisorJob())

    @Volatile
    private var seeded = false

    /** What the index knows about [media], or null when it knows nothing. */
    suspend fun lookup(media: MediaId): IndexHit? {
        ensureSeeded()
        val tmdbId = media.tmdbId
        return when (media.mediaType) {
            MediaType.MEDIA_TYPE_MOVIE -> movie(tmdbId)
            MediaType.MEDIA_TYPE_TV -> series(tmdbId)
            else -> null
        }
    }

    /**
     * [lookup], except that it never waits for the seed: until the seed is in, it starts loading it in the background and
     * answers null, so a first `Resolve` on a cold install goes straight to the network routes (which place a film the
     * index does not know) instead of spending the host's deadline on a load.
     */
    suspend fun lookupWithoutWaiting(media: MediaId): IndexHit? {
        if (!seeded) {
            background.launch { attempt { ensureSeeded() } }
            return null
        }
        return lookup(media)
    }

    /** The most recent refresh, successful or not: a failed one is what the status screen warns about. */
    fun latestRefresh(): Flow<RefreshLogEntity?> = dao.latestRefresh().onStart { ensureSeeded() }

    /** How many films the index can place. */
    fun filmCount(): Flow<Int> = dao.filmCount().onStart { ensureSeeded() }

    /** The last successful refresh, seed or network; what the status screen shows as freshness. */
    fun lastSuccess(): Flow<RefreshLogEntity?> = dao.lastSuccess().onStart { ensureSeeded() }

    /**
     * Replaces the whole index with [snapshot] and logs it as a successful refresh.
     *
     * A snapshot with fewer than half the Archive items the index holds is not a refresh: it throws
     * an [IOException] and the previous index stays.
     */
    suspend fun replace(
        snapshot: IndexSnapshot,
        source: RefreshSource,
    ) {
        val current = dao.archiveItemCount()
        if (snapshot.archiveItems.distinct().size * 2 < current) {
            throw IOException("The Archive search found ${snapshot.archiveItems.size} items, under half of the $current in the index")
        }
        val imdbLinks = snapshot.imdbLinks.groupBy { it.imdbId }
        val archiveItems =
            snapshot.archiveItems.flatMap { item ->
                imdbLinks[item.imdbId]
                    ?.map { ArchiveImdbEntity(archiveId = item.archiveId, imdbId = item.imdbId, tmdbId = it.tmdbId, isFilm = it.isFilm) }
                    ?: listOf(ArchiveImdbEntity(archiveId = item.archiveId, imdbId = item.imdbId, tmdbId = null, isFilm = null))
            }
        dao.replace(
            links = snapshot.links.distinct().map { WikidataLinkEntity(it.mediaType, it.tmdbId, it.archiveId, it.isFilm) },
            archiveItems = archiveItems,
            log =
                RefreshLogEntity(
                    finishedAt = clock(),
                    source = source,
                    succeeded = true,
                    linkCount = snapshot.links.size,
                    archiveItemCount = snapshot.archiveItems.size,
                    message = null,
                ),
        )
        seeded = true
    }

    /** Logs a failed refresh. The index itself is left exactly as it was. */
    suspend fun recordFailure(message: String) {
        dao.insertLog(
            RefreshLogEntity(
                finishedAt = clock(),
                source = RefreshSource.NETWORK,
                succeeded = false,
                linkCount = 0,
                archiveItemCount = 0,
                message = message,
            ),
        )
    }

    /**
     * Loads the seed if nothing has ever been loaded. A refresh in the log means the seed was
     * offered already, so it is never loaded over a newer index. That holds because every reader
     * and writer calls this first: lookups (and so Probe and the resolver), the status screen's
     * flows, and the refresh worker before it fetches or logs a failure. A failed refresh therefore
     * never leaves the seed unloaded, and nothing reads an index the seed has not reached yet.
     */
    suspend fun ensureSeeded() {
        if (seeded) return
        seeding.withLock {
            if (seeded) return
            if (dao.refreshCount() == 0) withContext(seedContext) { seed() }?.let { replace(it, RefreshSource.SEED) }
            seeded = true
        }
    }

    private suspend fun movie(tmdbId: Int): IndexHit? {
        val links = dao.links(LinkedMediaType.MOVIE, tmdbId)
        val archiveItems = dao.archiveItems(tmdbId)
        if (links.isEmpty() && archiveItems.isEmpty()) return null
        return IndexHit(
            isFilm = links.any { it.isFilm } || archiveItems.any { it.isFilm == true },
            wikidataArchiveIds = links.map { it.archiveId }.distinct(),
            imdbArchiveIds = archiveItems.map { it.archiveId }.distinct(),
        )
    }

    private suspend fun series(tmdbId: Int): IndexHit? {
        val links = dao.links(LinkedMediaType.TV, tmdbId)
        if (links.isEmpty()) return null
        return IndexHit(isFilm = false, wikidataArchiveIds = links.map { it.archiveId }.distinct(), imdbArchiveIds = emptyList())
    }
}
