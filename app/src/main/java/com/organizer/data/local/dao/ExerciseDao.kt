package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun updateAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' OR (ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    suspend fun getAllOnce(owner: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' OR (ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    fun getAll(owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE isCustom = 1 AND ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY exerciseId DESC")
    fun getCustomExercises(owner: String): Flow<List<ExerciseEntity>>

    @Query("""SELECT e.* FROM exercise e 
        JOIN exercise_category ec ON e.exerciseId = ec.exerciseId
        WHERE ec.categoryId = :categoryId
        AND (e.ownerFirebaseUid = 'public' OR e.ownerFirebaseUid = :owner)
        AND e.deletedAt IS NULL
        ORDER BY ec.sortOrder, ec.exerciseCategoryId""")
    fun observeExercisesByCategory(categoryId: Long, owner: String): Flow<List<ExerciseEntity>>

    /**
     * Exercises already attached to a workout are historical/routine references.
     * Deleted custom exercises remain resolvable here so existing workouts and
     * history can still display them.
     */
    @Query("""SELECT e.* FROM exercise e
        JOIN workout_exercise we ON e.exerciseId = we.exerciseId
        WHERE we.workoutId = :workoutId AND we.ownerFirebaseUid = :owner
        AND (e.ownerFirebaseUid = 'public' OR e.ownerFirebaseUid = :owner)
        ORDER BY we.orderIndex ASC""")
    fun observeExercisesByWorkout(workoutId: Long, owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id AND (ownerFirebaseUid = 'public' OR ownerFirebaseUid = :owner)")
    fun getById(owner: String, id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id AND (ownerFirebaseUid = 'public' OR ownerFirebaseUid = :owner)")
    suspend fun getByIdOnce(owner: String, id: Long): ExerciseEntity?

    /** Idempotent soft delete; only owner-scoped custom exercises are affected. */
    @Query(
        """UPDATE exercise
           SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'PENDING_DELETE'
           WHERE exerciseId = :id
           AND isCustom = 1
           AND ownerFirebaseUid = :owner
           AND deletedAt IS NULL"""
    )
    suspend fun softDeleteCustom(owner: String, id: Long, deletedAt: Long, updatedAt: Long): Int

    @Delete
    suspend fun delete(exercise: ExerciseEntity)
}