package io.github.scottcooper92.binge.archive.index

import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The on-device index: Probe's answers and the resolver's first two routes.
 *
 * A fresh install starts from the seed the APK ships ([seed]), loaded on the first lookup. The
 * weekly refresh replaces it. A failed refresh is logged and changes nothing else: the previous
 * index stays, and nothing is ever deleted except by a successful replace.
 */
class IndexRepository(
    private val dao: IndexDao,
    private val seed: () -> IndexSnapshot?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seeding = Mutex()

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

    /** The most recent refresh, successful or not: a failed one is what the status screen warns about. */
    fun latestRefresh(): Flow<RefreshLogEntity?> = dao.latestRefresh()

    /** How many films the index can place. Read without seeding: the status screen shows what is there. */
    fun filmCount(): Flow<Int> = dao.filmCount()

    /** The last successful refresh, seed or network; what the status screen shows as freshness. */
    fun lastSuccess(): Flow<RefreshLogEntity?> = dao.lastSuccess()

    /** Replaces the whole index with [snapshot] and logs it as a successful refresh. */
    suspend fun replace(
        snapshot: IndexSnapshot,
        source: RefreshSource,
    ) {
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
     * offered already, so it is never loaded over a newer index. That holds because every writer
     * calls this first: lookups do, and so does the refresh worker, before it fetches or logs a
     * failure. A failed refresh therefore never leaves the seed unloaded.
     */
    suspend fun ensureSeeded() {
        if (seeded) return
        seeding.withLock {
            if (seeded) return
            if (dao.refreshCount() == 0) seed()?.let { replace(it, RefreshSource.SEED) }
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
