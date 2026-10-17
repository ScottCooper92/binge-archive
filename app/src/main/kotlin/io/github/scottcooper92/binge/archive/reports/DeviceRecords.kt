package io.github.scottcooper92.binge.archive.reports

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * A source the user told the host did not work: which Archive item, why, and when. Kept on this
 * device only, and never sent anywhere.
 */
@Entity(tableName = "source_report")
data class SourceReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identifier: String,
    val problem: String,
    @ColumnInfo(name = "reported_at") val reportedAt: Long,
)

/**
 * A title this app resolved for a host, for the status screen's "Recently resolved". Kept on this
 * device only: never shown to the host, never sent anywhere.
 */
@Entity(tableName = "recent_title")
data class RecentTitleEntity(
    @PrimaryKey @ColumnInfo(name = "tmdb_id") val tmdbId: Int,
    val title: String,
    /** The Archive item of the first source, for a link to its page. */
    val identifier: String,
    @ColumnInfo(name = "resolved_at") val resolvedAt: Long,
)

@Dao
interface DeviceRecordsDao {
    @Insert
    suspend fun insertReport(report: SourceReportEntity)

    @Query("SELECT DISTINCT identifier FROM source_report WHERE reported_at >= :since")
    suspend fun reportedSince(since: Long): List<String>

    @Query("SELECT DISTINCT identifier FROM source_report WHERE reported_at >= :since ORDER BY identifier")
    fun reportedSinceFlow(since: Long): Flow<List<String>>

    @Query("DELETE FROM source_report")
    suspend fun clearReports()

    /** Records [title] and keeps only the [keep] most recent. */
    @Transaction
    suspend fun recordResolved(
        title: RecentTitleEntity,
        keep: Int,
    ) {
        upsertRecent(title)
        trimRecent(keep)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(title: RecentTitleEntity)

    @Query("DELETE FROM recent_title WHERE tmdb_id NOT IN (SELECT tmdb_id FROM recent_title ORDER BY resolved_at DESC LIMIT :keep)")
    suspend fun trimRecent(keep: Int)

    @Query("SELECT * FROM recent_title ORDER BY resolved_at DESC")
    fun recent(): Flow<List<RecentTitleEntity>>

    @Query("DELETE FROM recent_title")
    suspend fun clearRecent()
}

/**
 * What this device remembers of its own use: the user's source reports and the titles it recently
 * resolved. Its own database, so the index stays public data only.
 */
@Database(entities = [SourceReportEntity::class, RecentTitleEntity::class], version = 1)
abstract class DeviceRecordsDatabase : RoomDatabase() {
    abstract fun dao(): DeviceRecordsDao

    companion object {
        fun open(context: Context): DeviceRecordsDatabase =
            Room.databaseBuilder(context, DeviceRecordsDatabase::class.java, "device-records.db").build()
    }
}

/**
 * The device's own records. An item reported in the last [DERANK_DAYS] days goes after every other
 * item in this device's answers; it is not removed, since one bad report should not take the only copy
 * of a film away. "Recently resolved" keeps the last [RECENT_KEEP] titles.
 */
class DeviceRecords(
    private val dao: DeviceRecordsDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun record(
        identifier: String,
        problem: String,
    ) {
        dao.insertReport(SourceReportEntity(identifier = identifier, problem = problem, reportedAt = clock()))
    }

    suspend fun deranked(): Set<String> = dao.reportedSince(derankSince()).toSet()

    /** The items pushed down now. The window is fixed when collection starts, which a screen does often. */
    fun reported(): Flow<List<String>> = dao.reportedSinceFlow(derankSince())

    suspend fun clearReports() = dao.clearReports()

    suspend fun recordResolved(
        tmdbId: Int,
        title: String,
        identifier: String,
    ) {
        dao.recordResolved(RecentTitleEntity(tmdbId, title, identifier, clock()), keep = RECENT_KEEP)
    }

    fun recent(): Flow<List<RecentTitleEntity>> = dao.recent()

    suspend fun clearRecent() = dao.clearRecent()

    private fun derankSince(): Long = clock() - TimeUnit.DAYS.toMillis(DERANK_DAYS)

    companion object {
        const val DERANK_DAYS = 30L
        const val RECENT_KEEP = 10
    }
}
