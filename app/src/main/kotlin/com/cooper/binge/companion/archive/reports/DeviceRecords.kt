package com.cooper.binge.companion.archive.reports

import android.content.Context
import androidx.room.AutoMigration
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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

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
 * What this device remembers of its own use: the titles it recently resolved and the watch progress the
 * host reports. Its own database, so the index stays public data only. It holds what the user made, so
 * it migrates and is never rebuilt: version 2 added `playback_progress`, and version 3 dropped the
 * `source_report` table, which source reporting no longer fills.
 */
@Database(
    entities = [RecentTitleEntity::class, PlaybackProgressEntity::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class DeviceRecordsDatabase : RoomDatabase() {
    abstract fun dao(): DeviceRecordsDao

    abstract fun progressDao(): PlaybackProgressDao

    companion object {
        fun open(context: Context): DeviceRecordsDatabase =
            Room
                .databaseBuilder(context, DeviceRecordsDatabase::class.java, "device-records.db")
                .addMigrations(DROP_SOURCE_REPORT)
                .build()
    }
}

/** Version 3 drops the `source_report` table: reports are no longer taken, and the user's titles and progress are untouched. */
internal val DROP_SOURCE_REPORT =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `source_report`")
        }
    }

/** The device's own records. "Recently resolved" keeps the last [RECENT_KEEP] titles. */
class DeviceRecords(
    private val dao: DeviceRecordsDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun recordResolved(
        tmdbId: Int,
        title: String,
        identifier: String,
    ) {
        dao.recordResolved(RecentTitleEntity(tmdbId, title, identifier, clock()), keep = RECENT_KEEP)
    }

    fun recent(): Flow<List<RecentTitleEntity>> = dao.recent()

    suspend fun clearRecent() = dao.clearRecent()

    companion object {
        const val RECENT_KEEP = 10
    }
}
