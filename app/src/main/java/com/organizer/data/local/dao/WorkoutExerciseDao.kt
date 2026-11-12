package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.WorkoutExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workoutExerciseEntity: WorkoutExerciseEntity)

    @Query("""UPDATE workout_exercise SET orderIndex = :orderIndex WHERE workoutId = :workoutId AND exerciseId = :exerciseId""")
    suspend fun updateOrderIndex(workoutId: Long, exerciseId: Long, orderIndex: Int)

    @Transaction
    suspend fun updateExerciseOrder(workoutId: Long, exercises: List<ExerciseEntity>) {
        exercises.forEachIndexed { index, exercise ->
            updateOrderIndex(workoutId, exercise.exerciseId, index)
        }
    }

    @Query("SELECT exerciseId FROM workout_exercise WHERE workoutId = :workoutId ORDER BY orderIndex ASC")
    fun getExerciseIdsByWorkout(workoutId: Long): Flow<List<Long>>

    @Query("""SELECT MAX(orderIndex) FROM workout_exercise WHERE workoutId = :workoutId""")
    suspend fun getMaxOrderIndex(workoutId: Long): Int?

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    fun getWorkoutExercise(workoutId: Long, exerciseId: Long): Flow<WorkoutExerciseEntity?>

    @Delete
    suspend fun delete(workoutExercise: WorkoutExerciseEntity)
}
