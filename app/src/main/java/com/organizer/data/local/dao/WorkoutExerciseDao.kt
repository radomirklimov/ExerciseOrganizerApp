package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.SuggestedExerciseRow
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workoutExerciseEntity: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(workoutExercises: List<WorkoutExerciseEntity>)

    @Query("UPDATE workout_exercise SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE workoutId = :workoutId AND exerciseId = :exerciseId AND ownerFirebaseUid = :owner")
    suspend fun updateOrderIndex(owner: String, workoutId: Long, exerciseId: Long, orderIndex: Int, updatedAt: Long)

    @Transaction
    suspend fun updateExerciseOrder(owner: String, workoutId: Long, exercises: List<ExerciseEntity>, updatedAt: Long) {
        exercises.forEachIndexed { index, exercise ->
            updateOrderIndex(owner, workoutId, exercise.exerciseId, index, updatedAt)
        }
    }

    @Query("SELECT exerciseId FROM workout_exercise WHERE workoutId = :workoutId AND ownerFirebaseUid = :owner ORDER BY orderIndex ASC")
    fun getExerciseIdsByWorkout(owner: String, workoutId: Long): Flow<List<Long>>

    @Query("SELECT * FROM workout_exercise WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getAll(owner: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT MAX(orderIndex) FROM workout_exercise WHERE workoutId = :workoutId AND ownerFirebaseUid = :owner")
    suspend fun getMaxOrderIndex(owner: String, workoutId: Long): Int?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId AND exerciseId = :exerciseId AND ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun getWorkoutExercise(owner: String, workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?>

    @Query(
        """SELECT e.*, we.workoutId AS sourceWorkoutId FROM exercise e
        INNER JOIN workout_exercise we ON e.exerciseId = we.exerciseId
        WHERE we.ownerFirebaseUid = :owner AND e.deletedAt IS NULL"""
    )
    fun observeSuggestedExerciseRows(owner: String): Flow<List<SuggestedExerciseRow>>

    @Delete
    suspend fun delete(workoutExercise: WorkoutExerciseEntity)
}