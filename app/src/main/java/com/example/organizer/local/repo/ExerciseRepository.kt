package com.example.organizer.local.repo

import com.example.organizer.local.dao.CategoryDao
import com.example.organizer.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val dao: CategoryDao) {
    suspend fun insert(exercise: CategoryEntity) {
        dao.insert(exercise)
    }

    fun getAll(): Flow<List<CategoryEntity>> {
        return dao.getAll()
    }

    suspend fun delete(exercise: CategoryEntity){
        dao.delete(exercise)
    }
}
