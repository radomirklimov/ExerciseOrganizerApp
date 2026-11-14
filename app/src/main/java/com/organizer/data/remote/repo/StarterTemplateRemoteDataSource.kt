package com.organizer.data.remote.repo

import com.organizer.data.remote.ApiService
import com.organizer.data.remote.model.WorkoutTemplate
import jakarta.inject.Inject

class StarterTemplateRemoteDataSource @Inject constructor(
    private val api: ApiService,
) {
    suspend fun getTemplates(): List<WorkoutTemplate> = api.getStarterWorkouts()
}
