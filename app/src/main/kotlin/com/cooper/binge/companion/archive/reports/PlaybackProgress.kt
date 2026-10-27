package com.cooper.binge.companion.archive.reports

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.withTransaction
import com.binge.companion.contracts.stream.v1.ContinueWatchingEntry
import com.binge.companion.contracts.stream.v1.PlaybackProgress
import com.binge.companion.contracts.stream.v1.ReportPlaybackRequest
import com.binge.companion.contracts.v1.EpisodeRef
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.applyReport
import com.google.protobuf.Timestamp
import java.time.Instant

/**
 * Where the user got to in one title, or one episode of a series, as the host reported it. Kept on
 * this device only, and sent to no one but the host that reports it. A movie is keyed with
 * [season] and [episode] both [NO_EPISODE]; season 0 is a series' specials, so it cannot stand for
 * "none".
 */
@Entity(tableName = "playback_progress", primaryKeys = ["media_type", "tmdb_id", "season", "episode"])
data class PlaybackProgressEntity(
    /** The `MediaType` number. */
    @ColumnInfo(name = "media_type") val mediaType: Int,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int,
    val season: Int,
    val episode: Int,
    @ColumnInfo(name = "position_millis") val positionMillis: Long,
    @ColumnInfo(name = "runtime_millis") val runtimeMillis: Long,
    /** Epoch milliseconds: when the latest report arrived. */
    @ColumnInfo(name = "last_played") val lastPlayed: Long,
    val completed: Boolean,
) {
    companion object {
        /** The season and episode of a movie's row. */
        const val NO_EPISODE = -1
    }
}

@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PlaybackProgressEntity)

    @Query(
        "SELECT * FROM playback_progress " +
            "WHERE media_type = :mediaType AND tmdb_id = :tmdbId AND season = :season AND episode = :episode",
    )
    suspend fun get(
        mediaType: Int,
        tmdbId: Int,
        season: Int,
        episode: Int,
    ): PlaybackProgressEntity?

    @Query(
        "DELETE FROM playback_progress " +
            "WHERE media_type = :mediaType AND tmdb_id = :tmdbId AND season = :season AND episode = :episode",
    )
    suspend fun delete(
        mediaType: Int,
        tmdbId: Int,
        season: Int,
        episode: Int,
    )

    /** Forgets every episode of one series (or the one row of a movie). */
    @Query("DELETE FROM playback_progress WHERE media_type = :mediaType AND tmdb_id = :tmdbId")
    suspend fun deleteSeries(
        mediaType: Int,
        tmdbId: Int,
    )

    /**
     * One row per title, most recently played first: a movie's row, and a series' most recently
     * played episode (on a tie, the later episode). Ties between titles go by key, so pages never
     * overlap. `NOT EXISTS` rather than a window function, which minSdk 26's SQLite lacks.
     */
    @Query(
        "SELECT * FROM playback_progress AS p WHERE NOT EXISTS (" +
            "SELECT 1 FROM playback_progress AS q " +
            "WHERE q.media_type = p.media_type AND q.tmdb_id = p.tmdb_id AND (" +
            "q.last_played > p.last_played OR (q.last_played = p.last_played AND " +
            "(q.season > p.season OR (q.season = p.season AND q.episode > p.episode))))" +
            ") ORDER BY p.last_played DESC, p.media_type, p.tmdb_id LIMIT :limit OFFSET :offset",
    )
    suspend fun inProgress(
        limit: Int,
        offset: Int,
    ): List<PlaybackProgressEntity>
}

/**
 * The user's watch progress for the titles this app resolves (CAPABILITY_PLAYBACK_PROGRESS), in
 * `device-records.db`. The host keeps no copy: clearing this app's data loses it, as the contract
 * intends. Reports are applied by the SDK's [applyReport], stamped with [clock].
 */
class PlaybackProgressStore(
    private val database: DeviceRecordsDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.progressDao()

    /** The progress held for [media], and [episode] for a series; null for none. */
    suspend fun get(
        media: MediaId,
        episode: EpisodeRef?,
    ): PlaybackProgress? {
        val key = Key.of(media, episode)
        return dao.get(key.mediaType, key.tmdbId, key.season, key.episode)?.toProto()
    }

    /** Applies one checked report to what is held for its title, in one transaction. */
    suspend fun report(request: ReportPlaybackRequest) {
        val episode = if (request.hasEpisode()) request.episode else null
        val key = Key.of(request.media, episode)
        database.withTransaction {
            val held = dao.get(key.mediaType, key.tmdbId, key.season, key.episode)?.toProto()
            val next = applyReport(held, request, Instant.ofEpochMilli(clock()))
            if (next == null) {
                dao.delete(key.mediaType, key.tmdbId, key.season, key.episode)
            } else {
                dao.upsert(key.entity(next))
            }
        }
    }

    /** Forgets [media]'s progress: one episode, or with no [episode] every episode of a series. */
    suspend fun forget(
        media: MediaId,
        episode: EpisodeRef?,
    ) {
        val key = Key.of(media, episode)
        if (episode == null) {
            dao.deleteSeries(key.mediaType, key.tmdbId)
        } else {
            dao.delete(key.mediaType, key.tmdbId, key.season, key.episode)
        }
    }

    /** At most [limit] titles from [offset], most recently played first: ids and progress only. */
    suspend fun continueWatching(
        limit: Int,
        offset: Int,
    ): List<ContinueWatchingEntry> =
        dao.inProgress(limit, offset).map { row ->
            ContinueWatchingEntry
                .newBuilder()
                .setMedia(
                    MediaId
                        .newBuilder()
                        .setMediaTypeValue(row.mediaType)
                        .setTmdbId(row.tmdbId),
                ).apply {
                    if (row.mediaType == MediaType.MEDIA_TYPE_TV.number) {
                        setEpisode(EpisodeRef.newBuilder().setSeasonNumber(row.season).setEpisodeNumber(row.episode))
                    }
                }.setProgress(row.toProto())
                .build()
        }

    private data class Key(
        val mediaType: Int,
        val tmdbId: Int,
        val season: Int,
        val episode: Int,
    ) {
        fun entity(progress: PlaybackProgress) =
            PlaybackProgressEntity(
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
                positionMillis = progress.positionMillis,
                runtimeMillis = progress.runtimeMillis,
                lastPlayed = progress.lastPlayed.toEpochMilli(),
                completed = progress.completed,
            )

        companion object {
            /** A movie's key has no episode whatever the request carries; a checked request carries none. */
            fun of(
                media: MediaId,
                episode: EpisodeRef?,
            ): Key {
                val ref = episode?.takeIf { media.mediaType == MediaType.MEDIA_TYPE_TV }
                return Key(
                    mediaType = media.mediaTypeValue,
                    tmdbId = media.tmdbId,
                    season = ref?.seasonNumber ?: PlaybackProgressEntity.NO_EPISODE,
                    episode = ref?.episodeNumber ?: PlaybackProgressEntity.NO_EPISODE,
                )
            }
        }
    }
}

private fun PlaybackProgressEntity.toProto(): PlaybackProgress =
    PlaybackProgress
        .newBuilder()
        .setPositionMillis(positionMillis)
        .setRuntimeMillis(runtimeMillis)
        .setLastPlayed(Instant.ofEpochMilli(lastPlayed).let { Timestamp.newBuilder().setSeconds(it.epochSecond).setNanos(it.nano) })
        .setCompleted(completed)
        .build()

private fun Timestamp.toEpochMilli(): Long = Instant.ofEpochSecond(seconds, nanos.toLong()).toEpochMilli()
