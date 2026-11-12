package com.organizer.data.remote.repo

import com.organizer.data.local.db.entities.CategoryEntity
import com.organizer.data.mapper.asEntity
import com.organizer.data.remote.ApiService
import jakarta.inject.Inject

class CategoryRemoteDataSource @Inject constructor(
    private val api: ApiService,
) {
    // get sports
    suspend fun getSports(): List<CategoryEntity> {
        return api.getSports().map { it.asEntity() }
    }

    // get all non-sport categories
    suspend fun getCategories(): List<CategoryEntity> {
        return api.getCategories().map { it.asEntity() }
    }
}
