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

    @Query("SELECT * FROM exercise")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise")
    fun getAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE isCustom = 1 ORDER BY exerciseId DESC")
    fun getCustomExercises(): Flow<List<ExerciseEntity>>

    @Query("""SELECT e.* FROM exercise e 
        JOIN exercise_category ec ON e.exerciseId = ec.exerciseId
        WHERE ec.categoryId = :categoryId
        ORDER BY ec.sortOrder, ec.exerciseCategoryId""")
    fun observeExercisesByCategory(categoryId: Long): Flow<List<ExerciseEntity>>

    @Query("""SELECT e.* FROM exercise e
        JOIN workout_exercise we ON e.exerciseId = we.exerciseId
        WHERE we.workoutId = :workoutId
        ORDER BY we.orderIndex ASC""")
    fun observeExercisesByWorkout(workoutId: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id")
    fun getById(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id")
    suspend fun getByIdOnce(id: Long): ExerciseEntity?

    @Delete
    suspend fun delete(exercise: ExerciseEntity)
}
