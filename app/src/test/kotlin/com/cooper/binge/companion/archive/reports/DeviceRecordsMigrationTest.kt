package com.cooper.binge.companion.archive.reports

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * `device-records.db` holds what the user made, so a new version migrates it and loses nothing. A
 * version 1 file is built from the committed `1.json`, exactly as Room exported it, then opened by
 * today's database: Room runs the migration and checks the result against today's entities.
 */
@RunWith(RobolectricTestRunner::class)
class DeviceRecordsMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `version 1's reports and recent titles survive the move to version 2`() =
        runTest {
            val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
            SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
                schemaStatements(version = 1).forEach(v1::execSQL)
                v1.execSQL("INSERT INTO source_report (identifier, problem, reported_at) VALUES ('item', 'SOURCE_PROBLEM_OTHER', 5)")
                v1.execSQL("INSERT INTO recent_title (tmdb_id, title, identifier, resolved_at) VALUES (653, 'Nosferatu', 'item', 7)")
                v1.version = 1
            }

            val migrated = Room.databaseBuilder(context, DeviceRecordsDatabase::class.java, NAME).allowMainThreadQueries().build()
            try {
                assertEquals(listOf("item"), migrated.dao().reportedSince(0))
                assertEquals(listOf(RecentTitleEntity(653, "Nosferatu", "item", 7)), migrated.dao().recent().first())
                assertEquals(emptyList<PlaybackProgressEntity>(), migrated.progressDao().inProgress(limit = 10, offset = 0))
            } finally {
                migrated.close()
            }
        }

    /** The statements that build [version]'s schema, from its committed export. */
    private fun schemaStatements(version: Int): List<String> {
        val database =
            Json
                .parseToJsonElement(File(SCHEMAS, "$version.json").readText())
                .jsonObject
                .getValue("database")
                .jsonObject
        val tables =
            database.getValue("entities").jsonArray.flatMap { element ->
                val entity = element.jsonObject
                val table = entity.string("tableName")
                val indices = entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject.string("createSql") }
                (listOf(entity.string("createSql")) + indices).map { it.replace("\${TABLE_NAME}", table) }
            }
        return tables + database.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        const val NAME = "migration-test.db"

        /** Gradle runs the unit tests from the module directory. */
        val SCHEMAS = File("schemas/com.cooper.binge.companion.archive.reports.DeviceRecordsDatabase")
    }
}
