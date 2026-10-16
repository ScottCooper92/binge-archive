package io.github.scottcooper92.binge.archive.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The on-device index. Public metadata only; it holds nothing about the user. */
@Database(
    entities = [WikidataLinkEntity::class, ArchiveImdbEntity::class, RefreshLogEntity::class],
    version = 1,
)
abstract class IndexDatabase : RoomDatabase() {
    abstract fun indexDao(): IndexDao

    companion object {
        fun open(context: Context): IndexDatabase =
            Room
                .databaseBuilder(context, IndexDatabase::class.java, "index.db")
                // The index is rebuilt from the network every week, so a schema change may start it
                // over rather than carry a migration for data nobody owns.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
