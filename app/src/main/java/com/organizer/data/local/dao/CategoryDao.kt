package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM category WHERE parentCategoryId IS NULL ORDER BY sortOrder, categoryId")
    fun getSports(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE parentCategoryId IS NULL ORDER BY sortOrder, categoryId")
    suspend fun getSportsOnce(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE parentCategoryId IS NOT NULL ORDER BY sortOrder, categoryId")
    suspend fun getCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE parentCategoryId IS NOT NULL ORDER BY sortOrder, categoryId")
    fun getCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE parentCategoryId = :categoryId ORDER BY sortOrder, categoryId")
    fun getSubcategories(categoryId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE categoryId = :id")
    suspend fun getByIdOnce(id: Long): CategoryEntity?

    @Delete
    suspend fun delete(category: CategoryEntity)
}
