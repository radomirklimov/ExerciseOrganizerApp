package com.organizer.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.sync.SyncOutboxStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SyncFullMigrationChainInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sync-full-chain-test.db"

    /** Minimal but complete v13 schema with real rows, mirroring the older fixture. */
    private fun createV13Database(): File {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        if (file.exists()) assertTrue(file.delete())

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("PRAGMA foreign_keys = ON")
        listOf(
            """CREATE TABLE category (categoryId INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, iconUrl TEXT, parentCategoryId INTEGER, sortOrder INTEGER NOT NULL, FOREIGN KEY (parentCategoryId) REFERENCES category (categoryId) ON DELETE SET NULL)""",
            """CREATE TABLE exercise (exerciseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, instructions TEXT NOT NULL, imageUrls TEXT NOT NULL, isCustom INTEGER NOT NULL, exerciseType TEXT NOT NULL)""",
            """CREATE TABLE exercise_category (exerciseCategoryId INTEGER PRIMARY KEY NOT NULL, exerciseId INTEGER NOT NULL, categoryId INTEGER NOT NULL, sortOrder INTEGER NOT NULL, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE, FOREIGN KEY (categoryId) REFERENCES category (categoryId) ON DELETE CASCADE)""",
            """CREATE TABLE workout (workoutId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL)""",
            """CREATE TABLE workout_exercise (workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, orderIndex INTEGER NOT NULL, PRIMARY KEY (workoutId, exerciseId), FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE)""",
            """CREATE TABLE exercise_progress (progressId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, reps INTEGER, weight REAL, durationSeconds INTEGER, recordedAt INTEGER NOT NULL, sessionId INTEGER NOT NULL, unitSystem TEXT NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE)""",
            """CREATE TABLE favorite_exercise (exerciseId INTEGER PRIMARY KEY NOT NULL, addedAt INTEGER NOT NULL, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE)""",
            """CREATE TABLE starter_template (templateId INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, notes TEXT NOT NULL, categoryName TEXT, iconUrl TEXT, exerciseCount INTEGER NOT NULL, categoryNames TEXT NOT NULL)""",
            """CREATE TABLE starter_template_exercise (templateId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, orderIndex INTEGER NOT NULL, PRIMARY KEY (templateId, exerciseId), FOREIGN KEY (templateId) REFERENCES starter_template (templateId) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE)""",
            """CREATE TABLE scheduled_workout (scheduleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, dayOfWeek INTEGER NOT NULL, scheduledDateKey INTEGER, startMinutes INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL, reminderMinutesBefore INTEGER NOT NULL, reminderNote TEXT NOT NULL, FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE)""",
            """CREATE TABLE planned_set (workoutId INTEGER NOT NULL, exerciseId INTEGER NOT NULL, setNumber INTEGER NOT NULL, reps INTEGER, weight REAL, durationSeconds INTEGER, PRIMARY KEY (workoutId, exerciseId, setNumber), FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE, FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE)""",
            """CREATE TABLE workout_completion (dateKey INTEGER NOT NULL, workoutId INTEGER NOT NULL, completedExercises INTEGER NOT NULL, totalExercises INTEGER NOT NULL, completedAt INTEGER NOT NULL, PRIMARY KEY (dateKey, workoutId), FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE)""",
        ).forEach(db::execSQL)
        listOf(
            "CREATE INDEX index_category_name ON category (name)",
            "CREATE INDEX index_category_parentCategoryId ON category (parentCategoryId)",
            "CREATE INDEX index_exercise_category_exerciseId ON exercise_category (exerciseId)",
            "CREATE INDEX index_exercise_category_categoryId ON exercise_category (categoryId)",
            "CREATE UNIQUE INDEX index_exercise_category_exerciseId_categoryId ON exercise_category (exerciseId, categoryId)",
            "CREATE INDEX index_exercise_name ON exercise (name)",
            "CREATE INDEX index_exercise_isCustom ON exercise (isCustom)",
            "CREATE INDEX index_starter_template_exercise_templateId ON starter_template_exercise (templateId)",
            "CREATE INDEX index_starter_template_exercise_exerciseId ON starter_template_exercise (exerciseId)",
        ).forEach(db::execSQL)
        db.execSQL("INSERT INTO exercise VALUES (1, 'Push Up', '[\"do it\"]', '[\"push.png\"]', 0, 'REPS')")
        db.execSQL("INSERT INTO exercise VALUES (3, 'My Lift', '[\"lift\"]', '[\"mine.jpg\"]', 1, 'REPS_WEIGHT')")
        db.execSQL("INSERT INTO workout VALUES (1, 'Workout A', 'notes A')")
        db.execSQL("INSERT INTO workout_exercise VALUES (1, 1, 0)")
        db.execSQL("INSERT INTO planned_set VALUES (1, 1, 1, 10, 20.0, NULL)")
        db.execSQL("INSERT INTO exercise_progress VALUES (1, 1, 1, 1, 10, 20.0, NULL, 1000, 1, 'METRIC')")
        db.execSQL("INSERT INTO scheduled_workout VALUES (1, 1, 1, NULL, 540, 1, 30, 'morning')")
        db.execSQL("INSERT INTO workout_completion VALUES (20261120, 1, 2, 2, 3000)")
        db.execSQL("INSERT INTO favorite_exercise VALUES (1, 4000)")
        db.execSQL("INSERT INTO favorite_exercise VALUES (3, 5000)")
        db.execSQL("PRAGMA user_version = 13")
        db.close()
        return file
    }

    @Test
    fun v13UpgradesThroughRegisteredChainToCurrent() = runBlocking {
        createV13Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val workouts = db.workoutDao.getAll(OwnerProvider.GUEST).first()
            assertEquals(1, workouts.size)
            assertEquals("Workout A", workouts.single().name)
            assertNotNull(workouts.single().syncId)
            assertEquals(1, db.workoutExerciseDao.getAll(OwnerProvider.GUEST).first().size)
            assertEquals(1, db.plannedSetDao.observePlannedSets(OwnerProvider.GUEST, 1L, 1L).first().size)
            assertEquals(2, db.favoriteExerciseDao.observeFavoriteIds(OwnerProvider.GUEST).first().size)

            val m = SyncOutboxEntity(
                mutationId = UUID.randomUUID().toString(),
                ownerFirebaseUid = "user-a",
                entityType = "WORKOUT",
                recordSyncId = UUID.randomUUID().toString(),
                operation = "UPSERT",
                payload = """{"schemaVersion":1,"name":"A"}""",
                baseVersion = 0,
            )
            db.syncOutboxDao.insert(m)
            val stored = db.syncOutboxDao.findByMutationId("user-a", m.mutationId)!!
            assertEquals(m.payload, stored.payload)
            assertEquals(m.baseVersion, stored.baseVersion)
            assertFalse(stored.everTransmitted)
        } finally {
            db.close()
        }
    }

    @Test
    fun outboxColumnsAlreadyPresentInV14AreNotAddedTwice() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("outbox-dual-migration-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE sync_outbox (
                               outboxId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                               mutationId TEXT NOT NULL,
                               ownerFirebaseUid TEXT NOT NULL,
                               entityType TEXT NOT NULL,
                               recordSyncId TEXT NOT NULL,
                               operation TEXT NOT NULL,
                               payload TEXT,
                               baseVersion INTEGER NOT NULL,
                               createdAt INTEGER NOT NULL,
                               retryCount INTEGER NOT NULL,
                               lastError TEXT,
                               syncStatus TEXT NOT NULL,
                               serverVersion INTEGER,
                               serverUpdatedAt INTEGER,
                               acknowledgedAt INTEGER,
                               terminalErrorCode TEXT)"""
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase
        try {
            db.execSQL(
                """INSERT INTO sync_outbox
                   (mutationId, ownerFirebaseUid, entityType, recordSyncId, operation,
                    baseVersion, createdAt, retryCount, syncStatus)
                   VALUES ('m1', 'user-a', 'WORKOUT', 's1', 'UPSERT', 0, 1000, 0, 'PENDING_UPLOAD')"""
            )
            OUTBOX_ACKNOWLEDGEMENT_MIGRATION_16_17.migrate(db)
            TRANSMISSION_MARKER_MIGRATION_17_18.migrate(db)

            val columns = db.query("PRAGMA table_info('sync_outbox')").use { c ->
                val names = mutableListOf<String>()
                while (c.moveToNext()) names += c.getString(c.getColumnIndex("name"))
                names
            }
            assertEquals(1, columns.count { it == "serverVersion" })
            assertEquals(1, columns.count { it == "acknowledgedAt" })
            assertEquals(1, columns.count { it == "everTransmitted" })

            db.query("SELECT mutationId, syncStatus, everTransmitted FROM sync_outbox").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("m1", c.getString(0))
                assertEquals("PENDING_UPLOAD", c.getString(1))
                // Pre-existing rows are conservatively marked as transmitted.
                assertEquals(1, c.getInt(2))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v17OutboxRowsAreBackfilledAndFreshRowsStillCoalesce() = runBlocking {
        val name = "sync-v17-backfill-test.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        if (file.exists()) assertTrue(file.delete())

        val owner = "user-a"
        val pendingSyncId = UUID.randomUUID().toString()
        val flightSyncId = UUID.randomUUID().toString()
        val pendingId = UUID.randomUUID().toString()
        val flightId = UUID.randomUUID().toString()

        // Build a complete current database, then downgrade only sync_outbox to
        // its genuine v17 shape (no everTransmitted column) with real rows.
        val seed = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        seed.syncOutboxDao.insert(
            SyncOutboxEntity(
                mutationId = pendingId,
                ownerFirebaseUid = owner,
                entityType = "WORKOUT",
                recordSyncId = pendingSyncId,
                operation = "UPSERT",
                payload = """{"schemaVersion":1,"name":"Pending"}""",
                baseVersion = 2,
                createdAt = 1000L,
                syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
            )
        )
        seed.syncOutboxDao.insert(
            SyncOutboxEntity(
                mutationId = flightId,
                ownerFirebaseUid = owner,
                entityType = "WORKOUT",
                recordSyncId = flightSyncId,
                operation = "DELETE",
                payload = """{"schemaVersion":1,"name":"Flight"}""",
                baseVersion = 3,
                createdAt = 2000L,
                lastError = "timeout",
                syncStatus = SyncOutboxStatus.IN_FLIGHT,
            )
        )
        seed.close()

        val raw = SQLiteDatabase.openDatabase(file.path, null, 0)
        raw.execSQL("ALTER TABLE sync_outbox RENAME TO sync_outbox_v18")
        listOf(
            "index_sync_outbox_mutationId",
            "index_sync_outbox_ownerFirebaseUid",
            "index_sync_outbox_ownerFirebaseUid_syncStatus",
            "index_sync_outbox_ownerFirebaseUid_entityType_recordSyncId",
        ).forEach { raw.execSQL("DROP INDEX IF EXISTS $it") }
        raw.execSQL(
            """CREATE TABLE sync_outbox (
               outboxId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               mutationId TEXT NOT NULL,
               ownerFirebaseUid TEXT NOT NULL,
               entityType TEXT NOT NULL,
               recordSyncId TEXT NOT NULL,
               operation TEXT NOT NULL,
               payload TEXT,
               baseVersion INTEGER NOT NULL,
               createdAt INTEGER NOT NULL,
               retryCount INTEGER NOT NULL,
               lastError TEXT,
               syncStatus TEXT NOT NULL,
               serverVersion INTEGER,
               serverUpdatedAt INTEGER,
               acknowledgedAt INTEGER,
               terminalErrorCode TEXT)"""
        )
        raw.execSQL(
            """INSERT INTO sync_outbox
               (outboxId, mutationId, ownerFirebaseUid, entityType, recordSyncId, operation,
                payload, baseVersion, createdAt, retryCount, lastError, syncStatus,
                serverVersion, serverUpdatedAt, acknowledgedAt, terminalErrorCode)
               SELECT outboxId, mutationId, ownerFirebaseUid, entityType, recordSyncId, operation,
                payload, baseVersion, createdAt, retryCount, lastError, syncStatus,
                serverVersion, serverUpdatedAt, acknowledgedAt, terminalErrorCode
               FROM sync_outbox_v18"""
        )
        raw.execSQL("DROP TABLE sync_outbox_v18")
        raw.execSQL("CREATE UNIQUE INDEX index_sync_outbox_mutationId ON sync_outbox (mutationId)")
        raw.execSQL("CREATE INDEX index_sync_outbox_ownerFirebaseUid ON sync_outbox (ownerFirebaseUid)")
        raw.execSQL("CREATE INDEX index_sync_outbox_ownerFirebaseUid_syncStatus ON sync_outbox (ownerFirebaseUid, syncStatus)")
        raw.execSQL(
            "CREATE INDEX index_sync_outbox_ownerFirebaseUid_entityType_recordSyncId " +
                "ON sync_outbox (ownerFirebaseUid, entityType, recordSyncId)"
        )
        raw.execSQL("PRAGMA user_version = 17")
        raw.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            val pending = db.syncOutboxDao.findByMutationId(owner, pendingId)!!
            val flight = db.syncOutboxDao.findByMutationId(owner, flightId)!!
            assertTrue(pending.everTransmitted)
            assertTrue(flight.everTransmitted)
            assertEquals(pendingId, pending.mutationId)
            assertEquals(owner, pending.ownerFirebaseUid)
            assertEquals("""{"schemaVersion":1,"name":"Pending"}""", pending.payload)
            assertEquals("UPSERT", pending.operation)
            assertEquals(2L, pending.baseVersion)
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, pending.syncStatus)
            assertEquals(flightId, flight.mutationId)
            assertEquals("""{"schemaVersion":1,"name":"Flight"}""", flight.payload)
            assertEquals("DELETE", flight.operation)
            assertEquals(3L, flight.baseVersion)
            assertEquals("timeout", flight.lastError)
            assertEquals(SyncOutboxStatus.IN_FLIGHT, flight.syncStatus)
            assertEquals(0, pending.retryCount)

            // Recovery must not clear the transmission marker.
            assertEquals(1, db.syncOutboxDao.recoverInFlight(owner))
            val recovered = db.syncOutboxDao.findByMutationId(owner, flightId)!!
            assertTrue(recovered.everTransmitted)
            assertEquals(SyncOutboxStatus.PENDING_UPLOAD, recovered.syncStatus)

            // Neither pre-existing row is eligible for coalescing.
            assertNull(db.syncOutboxDao.findOpenMutation(owner, "WORKOUT", pendingSyncId, "UPSERT"))
            assertNull(db.syncOutboxDao.findOpenMutation(owner, "WORKOUT", flightSyncId, "DELETE"))

            // A row created after migration defaults to 0 and can be coalesced.
            val freshSyncId = UUID.randomUUID().toString()
            val freshId = UUID.randomUUID().toString()
            db.syncOutboxDao.insert(
                SyncOutboxEntity(
                    mutationId = freshId,
                    ownerFirebaseUid = owner,
                    entityType = "WORKOUT",
                    recordSyncId = freshSyncId,
                    operation = "UPSERT",
                    payload = """{"schemaVersion":1,"name":"Fresh"}""",
                    syncStatus = SyncOutboxStatus.PENDING_UPLOAD,
                )
            )
            val fresh = db.syncOutboxDao.findByMutationId(owner, freshId)!!
            assertFalse(fresh.everTransmitted)
            assertNotNull(db.syncOutboxDao.findOpenMutation(owner, "WORKOUT", freshSyncId, "UPSERT"))
        } finally {
            db.close()
        }
    }
}
