package com.organizer.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.SyncCursorDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.dao.WorkoutCompletionDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.db.entities.PlannedSetEntity
import com.organizer.data.local.db.entities.ScheduledWorkoutEntity
import com.organizer.data.local.db.entities.SyncCursorEntity
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.local.db.entities.SyncStagedRecordEntity
import com.organizer.data.local.db.entities.StarterTemplateEntity
import com.organizer.data.local.db.entities.StarterTemplateExerciseEntity
import com.organizer.data.local.db.entities.WorkoutCompletionEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import com.organizer.data.mapper.ListConverters

@Database(
    version = 18,
    entities = [
        CategoryEntity::class,
        ExerciseEntity::class,
        ExerciseCategoryEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseProgressEntity::class,
        FavoriteExerciseEntity::class,
        StarterTemplateEntity::class,
        StarterTemplateExerciseEntity::class,
        ScheduledWorkoutEntity::class,
        PlannedSetEntity::class,
        WorkoutCompletionEntity::class,
        SyncCursorEntity::class,
        SyncOutboxEntity::class,
        SyncStagedRecordEntity::class
    ],
    exportSchema = false
)
@TypeConverters(ListConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract val categoryDao: CategoryDao
    abstract val exerciseDao: ExerciseDao
    abstract val exerciseCategoryDao: ExerciseCategoryDao
    abstract val workoutDao: WorkoutDao
    abstract val workoutExerciseDao: WorkoutExerciseDao
    abstract val exerciseProgressDao: ExerciseProgressDao
    abstract val favoriteExerciseDao: FavoriteExerciseDao
    abstract val starterTemplateDao: StarterTemplateDao
    abstract val scheduledWorkoutDao: ScheduledWorkoutDao
    abstract val plannedSetDao: PlannedSetDao
    abstract val workoutCompletionDao: WorkoutCompletionDao
    abstract val syncCursorDao: SyncCursorDao
    abstract val syncOutboxDao: SyncOutboxDao
    abstract val syncStagedDao: SyncStagedDao
}

/**
 * Milestone 1: owner isolation, stable sync IDs and sync metadata.
 * Existing rows are preserved; UUIDs are generated only for rows that lack one.
 */
/** Adds acknowledgement metadata to outbox rows and a record-level lookup index. */
val OUTBOX_ACKNOWLEDGEMENT_MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 13->14 already creates these columns when sync_outbox is first built.
        // Adding them unconditionally here broke upgrades from v13-v15.
        db.addColumnIfMissing("sync_outbox", "serverVersion", "INTEGER")
        db.addColumnIfMissing("sync_outbox", "serverUpdatedAt", "INTEGER")
        db.addColumnIfMissing("sync_outbox", "acknowledgedAt", "INTEGER")
        db.addColumnIfMissing("sync_outbox", "terminalErrorCode", "TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sync_outbox_ownerFirebaseUid_entityType_recordSyncId " +
                "ON sync_outbox (ownerFirebaseUid, entityType, recordSyncId)"
        )
    }
}

/**
 * Durable transmission marker. Once a row has been handed to the network it may
 * already exist on the server, so it must never be coalesced into a later local
 * edit under the same mutationId. Recovery keeps the marker set.
 */
val TRANSMISSION_MARKER_MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("sync_outbox", "everTransmitted", "INTEGER NOT NULL DEFAULT 0")
        // A v17 row may already have reached the server and been returned to
        // PENDING_UPLOAD by recovery. The old schema cannot distinguish it from
        // a never-sent row, so treat every pre-existing row as transmitted.
        // New rows still get DEFAULT 0 and remain eligible for coalescing.
        db.execSQL("UPDATE sync_outbox SET everTransmitted = 1")
    }
}

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String) {
    val exists = query("PRAGMA table_info('$table')").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
            .any { it == column }
    }
    if (!exists) execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
}

/** Adds durable staging for pulled records and pagination metadata for cursors. */
val PULL_STAGING_MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_cursor ADD COLUMN hasMore INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sync_cursor ADD COLUMN lastServerTimeMillis INTEGER")
        db.execSQL("ALTER TABLE sync_cursor ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_staged_record (
                stagedId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                entityType TEXT NOT NULL,
                syncId TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                serverUpdatedAt INTEGER NOT NULL,
                deleted INTEGER NOT NULL,
                payload TEXT,
                processingStatus TEXT NOT NULL,
                retryCount INTEGER NOT NULL,
                lastError TEXT,
                createdAt INTEGER NOT NULL,
                lastAttemptAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_staged_record_ownerFirebaseUid ON sync_staged_record (ownerFirebaseUid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_staged_record_ownerFirebaseUid_processingStatus ON sync_staged_record (ownerFirebaseUid, processingStatus)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_staged_record_ownerFirebaseUid_entityType_syncId ON sync_staged_record (ownerFirebaseUid, entityType, syncId)")
    }
}

/**
 * Active workout names are unique by owner using the app's normalisation rule
 * (trim + lowercase). Tombstones get a NULL normalised name, so they no longer
 * block recreating a workout with the same name.
 */
val ACTIVE_WORKOUT_NAME_UNIQUENESS_MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout ADD COLUMN normalizedName TEXT")
        db.execSQL(
            "UPDATE workout SET normalizedName = lower(trim(name)) WHERE deletedAt IS NULL"
        )
        db.execSQL("DROP INDEX IF EXISTS index_workout_ownerFirebaseUid_name")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_workout_ownerFirebaseUid_normalizedName " +
                "ON workout (ownerFirebaseUid, normalizedName)"
        )
        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) {
                throw IllegalStateException("Foreign key violation after v14 -> v15 migration")
            }
        }
    }
}

val OWNER_AND_SYNC_FOUNDATION_MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL("PRAGMA foreign_keys = OFF")

        createFinalTables(db)
        copyLegacyRows(db, now)

        listOf(
            "favorite_exercise", "workout_completion", "scheduled_workout",
            "planned_set", "exercise_progress", "workout_exercise",
            "workout", "exercise",
        ).forEach { table ->
            db.execSQL("DROP TABLE $table")
            db.execSQL("ALTER TABLE ${table}_new RENAME TO $table")
        }

        createIndexes(db)
        createSyncTables(db)

        db.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) {
                throw IllegalStateException("Foreign key violation after v13 -> v14 migration")
            }
        }
        db.execSQL("PRAGMA foreign_keys = ON")
    }

    /** Final schemas match Room's generated v14 expectations: no SQL defaults,
     *  non-null syncId for user-owned tables, nullable syncId only for the
     *  mixed public/custom exercise table. */
    private fun createFinalTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE workout_new (
                workoutId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                notes TEXT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE exercise_new (
                exerciseId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                instructions TEXT NOT NULL,
                imageUrls TEXT NOT NULL,
                isCustom INTEGER NOT NULL,
                exerciseType TEXT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE workout_exercise_new (
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                PRIMARY KEY (workoutId, exerciseId),
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE exercise_progress_new (
                progressId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                reps INTEGER,
                weight REAL,
                durationSeconds INTEGER,
                recordedAt INTEGER NOT NULL,
                sessionId INTEGER NOT NULL,
                unitSystem TEXT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE planned_set_new (
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                reps INTEGER,
                weight REAL,
                durationSeconds INTEGER,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                PRIMARY KEY (workoutId, exerciseId, setNumber),
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE scheduled_workout_new (
                scheduleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                scheduledDateKey INTEGER,
                startMinutes INTEGER NOT NULL,
                reminderEnabled INTEGER NOT NULL,
                reminderMinutesBefore INTEGER NOT NULL,
                reminderNote TEXT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE workout_completion_new (
                dateKey INTEGER NOT NULL,
                workoutId INTEGER NOT NULL,
                completedExercises INTEGER NOT NULL,
                totalExercises INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                PRIMARY KEY (dateKey, workoutId),
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE favorite_exercise_new (
                favoriteId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                exerciseId INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                syncId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncStatus TEXT NOT NULL,
                serverVersion INTEGER NOT NULL,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    private fun copyLegacyRows(db: SupportSQLiteDatabase, now: Long) {
        db.query("SELECT workoutId, name, notes FROM workout").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO workout_new VALUES (?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(c.getLong(0), c.getString(1), c.getString(2), newId(), now)
                )
            }
        }

        db.query("SELECT exerciseId, name, instructions, imageUrls, isCustom, exerciseType FROM exercise").use { c ->
            while (c.moveToNext()) {
                val custom = c.getInt(4) == 1
                db.execSQL(
                    "INSERT INTO exercise_new VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4),
                        c.getString(5), if (custom) "guest" else "public",
                        if (custom) newId() else null, now,
                    )
                )
            }
        }

        db.query("SELECT workoutId, exerciseId, orderIndex FROM workout_exercise").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO workout_exercise_new VALUES (?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(c.getLong(0), c.getLong(1), c.getInt(2), newId(), now)
                )
            }
        }

        db.query(
            "SELECT progressId, workoutId, exerciseId, setNumber, reps, weight, durationSeconds, recordedAt, sessionId, unitSystem FROM exercise_progress"
        ).use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO exercise_progress_new VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(
                        c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3),
                        if (c.isNull(4)) null else c.getInt(4),
                        if (c.isNull(5)) null else c.getDouble(5),
                        if (c.isNull(6)) null else c.getInt(6),
                        c.getLong(7), c.getLong(8), c.getString(9), newId(), now,
                    )
                )
            }
        }

        db.query("SELECT workoutId, exerciseId, setNumber, reps, weight, durationSeconds FROM planned_set").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO planned_set_new VALUES (?, ?, ?, ?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(
                        c.getLong(0), c.getLong(1), c.getInt(2),
                        if (c.isNull(3)) null else c.getInt(3),
                        if (c.isNull(4)) null else c.getDouble(4),
                        if (c.isNull(5)) null else c.getInt(5),
                        newId(), now,
                    )
                )
            }
        }

        db.query(
            "SELECT scheduleId, workoutId, dayOfWeek, scheduledDateKey, startMinutes, reminderEnabled, reminderMinutesBefore, reminderNote FROM scheduled_workout"
        ).use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO scheduled_workout_new VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(
                        c.getLong(0), c.getLong(1), c.getInt(2),
                        if (c.isNull(3)) null else c.getInt(3),
                        c.getInt(4), c.getInt(5), c.getInt(6), c.getString(7), newId(), now,
                    )
                )
            }
        }

        db.query("SELECT dateKey, workoutId, completedExercises, totalExercises, completedAt FROM workout_completion").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO workout_completion_new VALUES (?, ?, ?, ?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(
                        c.getInt(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getLong(4), newId(), now,
                    )
                )
            }
        }

        db.query("SELECT exerciseId, addedAt FROM favorite_exercise").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO favorite_exercise_new (exerciseId, addedAt, ownerFirebaseUid, syncId, updatedAt, deletedAt, syncStatus, serverVersion) VALUES (?, ?, 'guest', ?, ?, NULL, 'LOCAL_ONLY', 0)",
                    arrayOf<Any?>(c.getLong(0), c.getLong(1), newId(), now)
                )
            }
        }
    }

    private fun createIndexes(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_ownerFirebaseUid_name ON workout (ownerFirebaseUid, name)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_syncId ON workout (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_ownerFirebaseUid ON workout (ownerFirebaseUid)")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_name ON exercise (name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_isCustom ON exercise (isCustom)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_syncId ON exercise (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_ownerFirebaseUid ON exercise (ownerFirebaseUid)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_workoutId ON workout_exercise (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_exerciseId ON workout_exercise (exerciseId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_exercise_syncId ON workout_exercise (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_exercise_ownerFirebaseUid ON workout_exercise (ownerFirebaseUid)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_workoutId ON exercise_progress (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_exerciseId ON exercise_progress (exerciseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_recordedAt ON exercise_progress (recordedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_sessionId ON exercise_progress (sessionId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_progress_syncId ON exercise_progress (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_ownerFirebaseUid ON exercise_progress (ownerFirebaseUid)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_set_workoutId ON planned_set (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_set_exerciseId ON planned_set (exerciseId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_planned_set_syncId ON planned_set (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_set_ownerFirebaseUid ON planned_set (ownerFirebaseUid)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_workout_workoutId ON scheduled_workout (workoutId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_workout_syncId ON scheduled_workout (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_workout_ownerFirebaseUid ON scheduled_workout (ownerFirebaseUid)")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_completion_workoutId ON workout_completion (workoutId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_completion_syncId ON workout_completion (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_completion_ownerFirebaseUid ON workout_completion (ownerFirebaseUid)")

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_exercise_syncId ON favorite_exercise (syncId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_exercise_ownerFirebaseUid ON favorite_exercise (ownerFirebaseUid)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_favorite_exercise_ownerFirebaseUid_exerciseId ON favorite_exercise (ownerFirebaseUid, exerciseId)")
    }

    private fun createSyncTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_cursor (
                cursorId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ownerFirebaseUid TEXT NOT NULL,
                cursor TEXT,
                lastSyncedAt INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_cursor_ownerFirebaseUid ON sync_cursor (ownerFirebaseUid)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_outbox (
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
                terminalErrorCode TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_mutationId ON sync_outbox (mutationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_ownerFirebaseUid ON sync_outbox (ownerFirebaseUid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_ownerFirebaseUid_syncStatus ON sync_outbox (ownerFirebaseUid, syncStatus)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_sync_outbox_ownerFirebaseUid_entityType_recordSyncId " +
                "ON sync_outbox (ownerFirebaseUid, entityType, recordSyncId)"
        )
    }

    private fun newId(): String = java.util.UUID.randomUUID().toString()
}

val PLANNED_SETS_AND_SESSIONS_MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS planned_set (
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                reps INTEGER,
                weight REAL,
                durationSeconds INTEGER,
                PRIMARY KEY (workoutId, exerciseId, setNumber),
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_set_workoutId ON planned_set (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planned_set_exerciseId ON planned_set (exerciseId)")

        // Sets recorded before this version belong to session 0. New
        // executions get their own id, so they start from "Set 1" again.
        db.execSQL(
            "ALTER TABLE exercise_progress ADD COLUMN sessionId INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_exercise_progress_sessionId " +
                "ON exercise_progress (sessionId)"
        )
    }
}

val EXERCISE_PROGRESS_UNIT_MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recorded weights keep the unit they were tracked in; new sets stamp
        // their own unit when created.
        db.execSQL("ALTER TABLE exercise_progress ADD COLUMN unitSystem TEXT NOT NULL DEFAULT 'METRIC'")
    }
}

val WORKOUT_COMPLETION_MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS workout_completion (
                dateKey INTEGER NOT NULL,
                workoutId INTEGER NOT NULL,
                completedExercises INTEGER NOT NULL,
                totalExercises INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                PRIMARY KEY (dateKey, workoutId),
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workout_completion_workoutId " +
                "ON workout_completion (workoutId)"
        )
    }
}

val SCHEDULED_WORKOUT_MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS scheduled_workout (
                scheduleId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                dayOfWeek INTEGER NOT NULL,
                startMinutes INTEGER NOT NULL,
                reminderEnabled INTEGER NOT NULL,
                reminderMinutesBefore INTEGER NOT NULL,
                reminderNote TEXT NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_workout_workoutId " +
                "ON scheduled_workout (workoutId)"
        )
    }
}

val SCHEDULED_WORKOUT_ONE_TIME_MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Existing entries keep repeating weekly; only new "This day only"
        // entries store a date.
        db.execSQL(
            "ALTER TABLE scheduled_workout ADD COLUMN scheduledDateKey INTEGER"
        )
    }
}

val FAVORITE_EXERCISE_MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorite_exercise (
                exerciseId INTEGER NOT NULL PRIMARY KEY,
                addedAt INTEGER NOT NULL,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
    }
}

val STARTER_TEMPLATE_CATEGORIES_MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE starter_template ADD COLUMN categoryNames TEXT NOT NULL DEFAULT '[]'"
        )
    }
}

val STARTER_TEMPLATES_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS starter_template (
                templateId INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                notes TEXT NOT NULL,
                categoryName TEXT,
                iconUrl TEXT,
                exerciseCount INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS starter_template_exercise (
                templateId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                PRIMARY KEY (templateId, exerciseId),
                FOREIGN KEY (templateId) REFERENCES starter_template (templateId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_starter_template_exercise_templateId ON starter_template_exercise (templateId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_starter_template_exercise_exerciseId ON starter_template_exercise (exerciseId)")
    }
}

val WORKOUT_NOTES_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}

val EXERCISE_PROGRESS_MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS exercise_progress (
                progressId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                setNumber INTEGER NOT NULL,
                reps INTEGER,
                weight REAL,
                durationSeconds INTEGER,
                recordedAt INTEGER NOT NULL,
                FOREIGN KEY (workoutId) REFERENCES workout (workoutId) ON DELETE CASCADE,
                FOREIGN KEY (exerciseId) REFERENCES exercise (exerciseId) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_workoutId ON exercise_progress (workoutId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_exerciseId ON exercise_progress (exerciseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_progress_recordedAt ON exercise_progress (recordedAt)")
    }
}

val EXERCISE_TYPE_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN exerciseType TEXT NOT NULL DEFAULT 'REPS_WEIGHT'")
    }
}

val DISPLAY_ORDER_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE category ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE category SET sortOrder = (
                SELECT COUNT(*) FROM category AS earlier
                WHERE earlier.categoryId < category.categoryId
                  AND (earlier.parentCategoryId = category.parentCategoryId
                       OR (earlier.parentCategoryId IS NULL AND category.parentCategoryId IS NULL))
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE exercise_category ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE exercise_category SET sortOrder = (
                SELECT COUNT(*) FROM exercise_category AS earlier
                WHERE earlier.categoryId = exercise_category.categoryId
                  AND earlier.exerciseCategoryId < exercise_category.exerciseCategoryId
            )
        """.trimIndent())
    }
}
/** Single source of truth for Room upgrades, shared by the app and migration tests. */
val ALL_MIGRATIONS = arrayOf(
    DISPLAY_ORDER_MIGRATION_1_2,
    EXERCISE_TYPE_MIGRATION_2_3,
    EXERCISE_PROGRESS_MIGRATION_3_4,
    WORKOUT_NOTES_MIGRATION_4_5,
    STARTER_TEMPLATES_MIGRATION_5_6,
    STARTER_TEMPLATE_CATEGORIES_MIGRATION_6_7,
    FAVORITE_EXERCISE_MIGRATION_7_8,
    SCHEDULED_WORKOUT_MIGRATION_8_9,
    SCHEDULED_WORKOUT_ONE_TIME_MIGRATION_9_10,
    PLANNED_SETS_AND_SESSIONS_MIGRATION_10_11,
    WORKOUT_COMPLETION_MIGRATION_11_12,
    EXERCISE_PROGRESS_UNIT_MIGRATION_12_13,
    OWNER_AND_SYNC_FOUNDATION_MIGRATION_13_14,
    ACTIVE_WORKOUT_NAME_UNIQUENESS_MIGRATION_14_15,
    PULL_STAGING_MIGRATION_15_16,
    OUTBOX_ACKNOWLEDGEMENT_MIGRATION_16_17,
    TRANSMISSION_MARKER_MIGRATION_17_18,
)
