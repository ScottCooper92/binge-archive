package io.github.scottcooper92.binge.archive.reports

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.TimeUnit

/**
 * A source the user told the host did not work: which Archive item, why, and when. Kept on this
 * device only, and never sent anywhere. Its own database, so the index stays public data only.
 */
@Entity(tableName = "source_report")
data class SourceReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identifier: String,
    val problem: String,
    @ColumnInfo(name = "reported_at") val reportedAt: Long,
)

@Dao
interface SourceReportDao {
    @Insert
    suspend fun insert(report: SourceReportEntity)

    @Query("SELECT DISTINCT identifier FROM source_report WHERE reported_at >= :since")
    suspend fun identifiersSince(since: Long): List<String>
}

@Database(entities = [SourceReportEntity::class], version = 1)
abstract class ReportDatabase : RoomDatabase() {
    abstract fun reportDao(): SourceReportDao

    companion object {
        fun open(context: Context): ReportDatabase = Room.databaseBuilder(context, ReportDatabase::class.java, "reports.db").build()
    }
}

/**
 * The user's reports, and the items they push down. An item reported in the last [DERANK_DAYS] days
 * goes after every other item in this device's answers. It is not removed: one bad report should not
 * take the only copy of a film away.
 */
class SourceReports(
    private val dao: SourceReportDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun record(
        identifier: String,
        problem: String,
    ) {
        dao.insert(SourceReportEntity(identifier = identifier, problem = problem, reportedAt = clock()))
    }

    suspend fun deranked(): Set<String> = dao.identifiersSince(clock() - TimeUnit.DAYS.toMillis(DERANK_DAYS)).toSet()

    companion object {
        const val DERANK_DAYS = 30L
    }
}
