package com.example.organizer.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.organizer.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg categories: CategoryEntity)

    @Query("SELECT * FROM CategoryEntity")
    fun getAll(): Flow<List<CategoryEntity>>

    @Delete
    suspend fun delete(categories: CategoryEntity)
}
