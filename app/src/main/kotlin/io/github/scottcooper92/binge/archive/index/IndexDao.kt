package io.github.scottcooper92.binge.archive.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexDao {
    @Query("SELECT * FROM wikidata_link WHERE media_type = :mediaType AND tmdb_id = :tmdbId ORDER BY archive_id")
    suspend fun links(
        mediaType: LinkedMediaType,
        tmdbId: Int,
    ): List<WikidataLinkEntity>

    @Query("SELECT * FROM archive_imdb WHERE tmdb_id = :tmdbId ORDER BY archive_id")
    suspend fun archiveItems(tmdbId: Int): List<ArchiveImdbEntity>

    /** Swaps the whole index in one transaction, so a reader sees the old one or the new one. */
    @Transaction
    suspend fun replace(
        links: List<WikidataLinkEntity>,
        archiveItems: List<ArchiveImdbEntity>,
        log: RefreshLogEntity,
    ) {
        deleteLinks()
        deleteArchiveItems()
        insertLinks(links)
        insertArchiveItems(archiveItems)
        insertLog(log)
    }

    @Insert
    suspend fun insertLog(log: RefreshLogEntity)

    @Query("SELECT COUNT(*) FROM refresh_log")
    suspend fun refreshCount(): Int

    @Query("SELECT * FROM refresh_log WHERE succeeded = 1 ORDER BY finished_at DESC, id DESC LIMIT 1")
    fun lastSuccess(): Flow<RefreshLogEntity?>

    @Query("SELECT * FROM refresh_log ORDER BY id DESC LIMIT 1")
    suspend fun lastRefresh(): RefreshLogEntity?

    @Query("DELETE FROM wikidata_link")
    suspend fun deleteLinks()

    @Query("DELETE FROM archive_imdb")
    suspend fun deleteArchiveItems()

    @Insert
    suspend fun insertLinks(links: List<WikidataLinkEntity>)

    @Insert
    suspend fun insertArchiveItems(items: List<ArchiveImdbEntity>)
}
