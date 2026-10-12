package com.organizer.data.remote.repo

import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.mapper.asEntity
import com.organizer.data.remote.ApiService
import jakarta.inject.Inject

class ExerciseRemoteDataSource @Inject constructor(
    private val api: ApiService,
) {
    // Backend paginates /exercises (?page=&size=, max 100, plain array):
    // keep requesting the next page until an empty (or partial) page arrives.
    suspend fun getAll(): List<ExerciseEntity> {
        val result = mutableListOf<ExerciseEntity>()
        var page = 0
        while (true) {
            val batch = api.getExercises(page = page, size = PAGE_SIZE)
            if (batch.isEmpty()) break
            result += batch.map { it.asEntity() }
            if (batch.size < PAGE_SIZE) break
            page++
        }
        return result
    }

    companion object {
        const val PAGE_SIZE = 100
    }
}
