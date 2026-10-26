package com.cooper.binge.companion.archive.index

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A [WikidataLink], one row per TMDB id and Archive identifier. */
@Entity(tableName = "wikidata_link", primaryKeys = ["media_type", "tmdb_id", "archive_id"])
data class WikidataLinkEntity(
    @ColumnInfo(name = "media_type") val mediaType: LinkedMediaType,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int,
    @ColumnInfo(name = "archive_id") val archiveId: String,
    @ColumnInfo(name = "is_film") val isFilm: Boolean,
)

/**
 * An Archive item found by its IMDb id, with the TMDB movie id Wikidata gives for that IMDb id. The
 * TMDB columns are null when Wikidata has none: such an item cannot be found yet, and is kept so
 * coverage can be measured. One row per item and TMDB id.
 */
@Entity(tableName = "archive_imdb", indices = [Index("tmdb_id")])
data class ArchiveImdbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "archive_id") val archiveId: String,
    @ColumnInfo(name = "imdb_id") val imdbId: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int?,
    @ColumnInfo(name = "is_film") val isFilm: Boolean?,
)

/** Where a refresh's data came from. */
enum class RefreshSource { SEED, NETWORK }

/** One refresh, successful or not. The status screen's freshness is the last successful one. */
@Entity(tableName = "refresh_log")
data class RefreshLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    val source: RefreshSource,
    val succeeded: Boolean,
    @ColumnInfo(name = "link_count") val linkCount: Int,
    @ColumnInfo(name = "archive_item_count") val archiveItemCount: Int,
    val message: String?,
)
