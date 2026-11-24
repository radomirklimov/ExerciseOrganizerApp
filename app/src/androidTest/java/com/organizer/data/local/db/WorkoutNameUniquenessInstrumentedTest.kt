package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.repo.WorkoutRepository
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncOutboxWriter
import com.organizer.sync.SyncPayloadFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutNameUniquenessInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FixedOwner : OwnerProvider() {
        override fun currentOwner(): String = "user-a"
    }

    private fun openDb(): AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun repository(db: AppDatabase) = WorkoutRepository(
        db.workoutDao,
        FixedOwner(),
        RoomSyncTransactionRunner(db),
        SyncOutboxWriter(db.syncOutboxDao, SyncPayloadFactory(Json { ignoreUnknownKeys = true })),
    )

    @Test
    fun deletedWorkoutDoesNotBlockRecreation() = runBlocking {
        val db = openDb()
        try {
            val repo = repository(db)
            val first = repo.createWorkout("Push Day")
            repo.deleteWorkout(first)
            val second = repo.createWorkout("push day")

            assertTrue(second != first)
            val all = db.openHelper.readableDatabase.query("SELECT workoutId, normalizedName, deletedAt FROM workout").use { c ->
                val rows = mutableListOf<Triple<Long, String?, Long?>>()
                while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1), if (c.isNull(2)) null else c.getLong(2))
                rows
            }
            assertEquals(2, all.size)
            assertNotNull(all.first { it.first == first }.third)
            assertNull(all.first { it.first == second }.third)
            assertNull(all.first { it.first == first }.second)
            assertEquals("push day", all.first { it.first == second }.second)

            val duplicate = runCatching {
                db.workoutDao.insert(
                    WorkoutEntity(
                        name = "  PUSH DAY  ",
                        ownerFirebaseUid = "user-a",
                        normalizedName = "  push day  ".trim().lowercase(),
                    )
                )
            }
            assertTrue(duplicate.isFailure)

            db.workoutDao.insert(
                WorkoutEntity(
                    name = "Push Day",
                    ownerFirebaseUid = "user-b",
                    normalizedName = "push day",
                )
            )
            assertEquals(1, db.workoutDao.getAll("user-a").first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationBackfillsNormalizedNamesAndDropsOldIndex() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("workout-name-migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE workout (
                                workoutId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL,
                                notes TEXT NOT NULL,
                                ownerFirebaseUid TEXT NOT NULL,
                                syncId TEXT NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                deletedAt INTEGER,
                                syncStatus TEXT NOT NULL,
                                serverVersion INTEGER NOT NULL
                            )"""
                        )
                        db.execSQL("CREATE UNIQUE INDEX index_workout_ownerFirebaseUid_name ON workout (ownerFirebaseUid, name)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        try {
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO workout (name, notes, ownerFirebaseUid, syncId, updatedAt, deletedAt, syncStatus, serverVersion) " +
                    "VALUES ('Active', '', 'user-a', 'a', $now, NULL, 'SYNCED', 1)"
            )
            db.execSQL(
                "INSERT INTO workout (name, notes, ownerFirebaseUid, syncId, updatedAt, deletedAt, syncStatus, serverVersion) " +
                    "VALUES ('Deleted', '', 'user-a', 'd', $now, $now, 'PENDING_DELETE', 1)"
            )

            ACTIVE_WORKOUT_NAME_UNIQUENESS_MIGRATION_14_15.migrate(db)

            val normalized = mutableMapOf<String, Pair<String?, Long?>>()
            db.query("SELECT name, normalizedName, deletedAt FROM workout").use { cursor ->
                while (cursor.moveToNext()) {
                    normalized[cursor.getString(0)] = cursor.getString(1) to if (cursor.isNull(2)) null else cursor.getLong(2)
                }
            }
            assertEquals("active", normalized["Active"]!!.first)
            assertNull(normalized["Deleted"]!!.first)
            assertNotNull(normalized["Deleted"]!!.second)

            val names = db.query("PRAGMA index_list('workout')").use { cursor ->
                val result = mutableListOf<String>()
                while (cursor.moveToNext()) result += cursor.getString(cursor.getColumnIndex("name"))
                result
            }
            assertTrue(names.contains("index_workout_ownerFirebaseUid_normalizedName"))
            assertTrue(!names.contains("index_workout_ownerFirebaseUid_name"))
        } finally {
            db.close()
        }
    }
}