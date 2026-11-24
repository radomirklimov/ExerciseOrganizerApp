package com.organizer.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.db.entities.WorkoutEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MigrationV13ToV14Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-v13-v14-test.db"

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

        db.execSQL("INSERT INTO category VALUES (1, 'Gym', 'gym.png', NULL, 0)")
        db.execSQL("INSERT INTO category VALUES (2, 'Chest', NULL, 1, 0)")
        db.execSQL("INSERT INTO exercise VALUES (1, 'Push Up', '[\"do it\"]', '[\"push.png\"]', 0, 'REPS')")
        db.execSQL("INSERT INTO exercise VALUES (2, 'Squat', '[\"sit\"]', '[\"squat.png\"]', 0, 'REPS_WEIGHT')")
        db.execSQL("INSERT INTO exercise VALUES (3, 'My Lift', '[\"lift\"]', '[\"mine.jpg\"]', 1, 'REPS_WEIGHT')")
        db.execSQL("INSERT INTO exercise_category VALUES (1, 1, 2, 0)")
        db.execSQL("INSERT INTO workout VALUES (1, 'Workout A', 'notes A')")
        db.execSQL("INSERT INTO workout VALUES (2, 'Workout B', 'notes B')")
        db.execSQL("INSERT INTO workout_exercise VALUES (1, 1, 0)")
        db.execSQL("INSERT INTO workout_exercise VALUES (1, 2, 1)")
        db.execSQL("INSERT INTO workout_exercise VALUES (2, 3, 0)")
        db.execSQL("INSERT INTO planned_set VALUES (1, 1, 1, 10, 20.0, NULL)")
        db.execSQL("INSERT INTO planned_set VALUES (1, 2, 1, 5, 60.0, NULL)")
        db.execSQL("INSERT INTO exercise_progress VALUES (1, 1, 1, 1, 10, 20.0, NULL, 1000, 1, 'METRIC')")
        db.execSQL("INSERT INTO exercise_progress VALUES (2, 1, 1, 2, 8, 22.5, NULL, 2000, 1, 'METRIC')")
        db.execSQL("INSERT INTO scheduled_workout VALUES (1, 1, 1, NULL, 540, 1, 30, 'morning')")
        db.execSQL("INSERT INTO workout_completion VALUES (20261120, 1, 2, 2, 3000)")
        db.execSQL("INSERT INTO favorite_exercise VALUES (1, 4000)")
        db.execSQL("INSERT INTO favorite_exercise VALUES (3, 5000)")
        db.execSQL("INSERT INTO starter_template VALUES (10, 'Template', '', 'Chest', NULL, 1, '[\"Chest\"]')")
        db.execSQL("INSERT INTO starter_template_exercise VALUES (10, 1, 0)")
        db.execSQL("PRAGMA user_version = 13")
        db.close()
        return file
    }

    private fun openMigrated(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        dbName,
    ).addMigrations(OWNER_AND_SYNC_FOUNDATION_MIGRATION_13_14).build()

    @Test
    fun migrationPreservesAllV13DataAndAddsStableSyncMetadata() = runBlocking {
        createV13Database()
        val db = openMigrated()

        val workouts = db.workoutDao.getAll(OwnerProvider.GUEST).first()
        assertEquals(listOf(2L, 1L), workouts.map { it.workoutId })
        assertTrue(workouts.all { it.ownerFirebaseUid == OwnerProvider.GUEST })
        assertTrue(workouts.all { it.syncId.isNotBlank() })

        val exercises = db.exerciseDao.getAll(OwnerProvider.GUEST).first()
        val custom = exercises.first { it.exerciseId == 3L }
        val publicExercise = exercises.first { it.exerciseId == 1L }
        assertEquals(OwnerProvider.GUEST, custom.ownerFirebaseUid)
        assertNotNull(custom.syncId)
        assertEquals("public", publicExercise.ownerFirebaseUid)
        assertNull(publicExercise.syncId)

        assertEquals(3, db.workoutExerciseDao.getAll(OwnerProvider.GUEST).first().size)
        assertEquals(2, db.exerciseProgressDao.observeSets(OwnerProvider.GUEST, 1L, 1L).first().size)
        assertEquals(1, db.plannedSetDao.observePlannedSets(OwnerProvider.GUEST, 1L, 1L).first().size)
        assertEquals(1, db.plannedSetDao.observePlannedSets(OwnerProvider.GUEST, 1L, 2L).first().size)
        assertEquals(1, db.scheduledWorkoutDao.getAllOnce(OwnerProvider.GUEST).size)
        assertEquals(1, db.workoutCompletionDao.observeAll(OwnerProvider.GUEST).first().size)
        assertEquals(listOf(1L, 3L), db.favoriteExerciseDao.observeFavoriteIds(OwnerProvider.GUEST).first())

        // Owner-scoped uniqueness: same name is allowed for a different owner.
        db.workoutDao.insert(WorkoutEntity(name = "Workout A", notes = "", ownerFirebaseUid = "user-a"))

        val before = db.workoutDao.getAll(OwnerProvider.GUEST).first().map { it.workoutId to it.syncId }.toMap()

        db.close()
        val reopened = openMigrated()
        val after = reopened.workoutDao.getAll(OwnerProvider.GUEST).first().map { it.workoutId to it.syncId }.toMap()
        assertEquals(before, after)

        reopened.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
            assertTrue("foreign key violations found", !it.moveToFirst())
        }
        reopened.close()
    }
}