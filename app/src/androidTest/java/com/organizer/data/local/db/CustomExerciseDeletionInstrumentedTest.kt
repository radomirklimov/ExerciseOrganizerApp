package com.organizer.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.ExerciseProgressEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import com.organizer.data.local.db.entities.WorkoutEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomExerciseDeletionInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun softDeletingCustomExercisePreservesAllDependentData() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val owner = "user-a"
            val now = System.currentTimeMillis()

            db.exerciseDao.insert(
                ExerciseEntity(
                    exerciseId = 1L,
                    name = "My Lift",
                    instructions = listOf("lift"),
                    imageUrls = listOf("mine.jpg"),
                    isCustom = true,
                    ownerFirebaseUid = owner,
                    syncId = "exercise-uuid",
                    syncStatus = "SYNCED",
                    serverVersion = 3,
                )
            )
            db.exerciseDao.insert(
                ExerciseEntity(
                    exerciseId = 2L,
                    name = "Push Up",
                    instructions = emptyList(),
                    imageUrls = emptyList(),
                    isCustom = false,
                    ownerFirebaseUid = "public",
                )
            )
            db.workoutDao.insert(
                WorkoutEntity(
                    workoutId = 10L,
                    name = "Workout A",
                    ownerFirebaseUid = owner,
                    syncId = "workout-uuid",
                )
            )
            db.workoutExerciseDao.insert(
                WorkoutExerciseEntity(
                    workoutId = 10L,
                    exerciseId = 1L,
                    orderIndex = 0,
                    ownerFirebaseUid = owner,
                    syncId = "link-uuid",
                )
            )
            db.openHelper.writableDatabase.execSQL(
                """INSERT INTO planned_set
                   (workoutId, exerciseId, setNumber, reps, weight, durationSeconds,
                    ownerFirebaseUid, syncId, updatedAt, deletedAt, syncStatus, serverVersion)
                   VALUES (10, 1, 1, 8, 60.0, NULL, '$owner', 'plan-uuid', $now, NULL, 'SYNCED', 1)"""
            )
            db.exerciseProgressDao.insert(
                ExerciseProgressEntity(
                    progressId = 20L,
                    workoutId = 10L,
                    exerciseId = 1L,
                    setNumber = 1,
                    reps = 8,
                    weight = 60.0,
                    recordedAt = now,
                    ownerFirebaseUid = owner,
                    syncId = "progress-uuid",
                )
            )
            db.favoriteExerciseDao.insert(
                FavoriteExerciseEntity(
                    favoriteId = 30L,
                    exerciseId = 1L,
                    ownerFirebaseUid = owner,
                    syncId = "favorite-uuid",
                )
            )

            assertEquals(1, db.exerciseDao.softDeleteCustom(owner, 1L, now, now))

            val stored = db.exerciseDao.getByIdOnce(owner, 1L)!!
            assertEquals(now, stored.deletedAt)
            assertEquals("exercise-uuid", stored.syncId)
            assertEquals(owner, stored.ownerFirebaseUid)
            assertEquals("My Lift", stored.name)
            assertEquals(3L, stored.serverVersion)

            assertTrue(db.exerciseDao.getCustomExercises(owner).first().isEmpty())
            assertTrue(db.exerciseDao.getAll(owner).first().none { it.exerciseId == 1L })
            assertEquals(1L, db.exerciseDao.getById(owner, 1L).first()?.exerciseId)
            assertEquals(1L, db.exerciseDao.observeExercisesByWorkout(10L, owner).first().single().exerciseId)
            assertEquals(1, db.workoutExerciseDao.getAll(owner).first().size)

            val plannedCount = db.openHelper.readableDatabase
                .query("SELECT COUNT(*) FROM planned_set WHERE exerciseId = 1").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            assertEquals(1, plannedCount)
            assertEquals(1, db.exerciseProgressDao.observeSets(owner, 10L, 1L).first().size)
            assertEquals(listOf(1L), db.favoriteExerciseDao.observeFavoriteIds(owner).first())

            // Idempotent and owner/public safe.
            assertEquals(0, db.exerciseDao.softDeleteCustom(owner, 1L, now + 1, now + 1))
            assertEquals(0, db.exerciseDao.softDeleteCustom("user-b", 1L, now + 1, now + 1))
            assertEquals(0, db.exerciseDao.softDeleteCustom(owner, 2L, now + 1, now + 1))
            assertNull(db.exerciseDao.getByIdOnce(owner, 2L)?.deletedAt)
        } finally {
            db.close()
        }
    }
}