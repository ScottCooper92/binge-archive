package com.cooper.binge.companion.archive.index

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
        certifications: List<FilmCertificationEntity>,
        log: RefreshLogEntity,
    ) {
        deleteLinks()
        deleteArchiveItems()
        deleteCertifications()
        insertIndex(links, archiveItems, certifications)
        insertLog(log)
    }

    @Insert
    suspend fun insertLog(log: RefreshLogEntity)

    /** Archive items in the index, however many TMDB ids each has. */
    @Query("SELECT COUNT(*) FROM (SELECT DISTINCT archive_id, imdb_id FROM archive_imdb)")
    suspend fun archiveItemCount(): Int

    @Query("SELECT COUNT(*) FROM refresh_log")
    suspend fun refreshCount(): Int

    @Query("SELECT * FROM refresh_log WHERE succeeded = 1 ORDER BY finished_at DESC, id DESC LIMIT 1")
    fun lastSuccess(): Flow<RefreshLogEntity?>

    @Query("SELECT * FROM refresh_log ORDER BY id DESC LIMIT 1")
    fun latestRefresh(): Flow<RefreshLogEntity?>

    /**
     * Films the index can place: a Wikidata movie link that is not a known non-film kind, or an
     * Archive item tagged with the IMDb id of one. The same rule as `IndexHit.isFilm`.
     */
    @Query(
        "SELECT COUNT(*) FROM (" +
            "SELECT tmdb_id FROM wikidata_link WHERE media_type = 'MOVIE' AND is_film = 1 " +
            "UNION SELECT tmdb_id FROM archive_imdb WHERE tmdb_id IS NOT NULL AND is_film = 1)",
    )
    fun filmCount(): Flow<Int>

    /**
     * One page of the films `ListServedTitles` lists: Wikidata movie links that are not a known non-film
     * kind, past [after], in TMDB id order. A film is listed when one of its certifications for [region]
     * is in [allowed], or, with [includeUnrated], when it has none for [region] at all. An empty [allowed]
     * admits no certification.
     */
    @Query(
        "SELECT DISTINCT l.tmdb_id FROM wikidata_link l " +
            "WHERE l.media_type = 'MOVIE' AND l.is_film = 1 AND l.tmdb_id > :after AND (" +
            "EXISTS (SELECT 1 FROM film_certification c WHERE c.tmdb_id = l.tmdb_id AND c.region = :region " +
            "AND c.certification IN (:allowed)) " +
            "OR (:includeUnrated = 1 AND NOT EXISTS (SELECT 1 FROM film_certification c " +
            "WHERE c.tmdb_id = l.tmdb_id AND c.region = :region))) " +
            "ORDER BY l.tmdb_id LIMIT :limit",
    )
    suspend fun servedTitles(
        region: String,
        allowed: List<String>,
        includeUnrated: Boolean,
        after: Int,
        limit: Int,
    ): List<Int>

    @Query("DELETE FROM film_certification")
    suspend fun deleteCertifications()

    @Query("DELETE FROM wikidata_link")
    suspend fun deleteLinks()

    @Query("DELETE FROM archive_imdb")
    suspend fun deleteArchiveItems()

    @Insert
    suspend fun insertIndex(
        links: List<WikidataLinkEntity>,
        items: List<ArchiveImdbEntity>,
        certifications: List<FilmCertificationEntity>,
    )
}
