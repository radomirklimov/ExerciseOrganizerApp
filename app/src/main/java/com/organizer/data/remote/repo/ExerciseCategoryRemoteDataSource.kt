package com.organizer.data.remote.repo

import com.organizer.data.local.db.entities.ExerciseCategoryEntity
import com.organizer.data.mapper.asEntity
import com.organizer.data.remote.ApiService
import jakarta.inject.Inject

class ExerciseCategoryRemoteDataSource @Inject constructor(
    private val api: ApiService,
) {
    suspend fun getAll(): List<ExerciseCategoryEntity> {
        return api.getExerciseCategory().map { it.asEntity() }
    }
}
