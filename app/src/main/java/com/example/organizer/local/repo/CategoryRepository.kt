package com.example.organizer.local.repo

import com.example.organizer.local.dao.CategoryDao
import com.example.organizer.local.entity.CategoryEntity
import com.example.organizer.network.models.Category
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {
    suspend fun insert(categories: List<CategoryEntity>) {
        dao.insert(categories)
    }

    fun getAll(): Flow<List<CategoryEntity>> {
        return dao.getAll()
    }

    suspend fun getById(id: Long): CategoryEntity? {
        return dao.getById(id)
    }

    suspend fun delete(category: CategoryEntity){
        dao.delete(category)
    }
}
