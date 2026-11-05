package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.ExerciseCategoryEntity

@Dao
interface ExerciseCategoryDao {
    @Query("SELECT * FROM exercise_category")
    suspend fun getAllOnce(): List<ExerciseCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exerciseCategories: List<ExerciseCategoryEntity>)

    @Update
    suspend fun updateAll(exerciseCategories: List<ExerciseCategoryEntity>)

    @Query("SELECT exerciseId FROM exercise_category WHERE :categoryId == categoryId ORDER BY sortOrder ASC, exerciseCategoryId ASC")
    suspend fun getExerciseIdsByCategoryId(categoryId: Long): List<Long>

    @Delete
    suspend fun delete(exerciseCategory: ExerciseCategoryEntity)
}
