package com.organizer.data.remote

import com.organizer.data.remote.model.Category
import com.organizer.data.remote.model.Exercise
import com.organizer.data.remote.model.ExerciseCategory
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface ApiService {

    @GET("api/v1/sports")
    suspend fun getSports(): List<Category>

    @GET("api/v1/categories")
    suspend fun getCategories(): List<Category>

    @GET("api/v1/exercises")
    suspend fun getExercises(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): List<Exercise>

    @GET("api/v1/exercise-category")
    suspend fun getExerciseCategory(): List<ExerciseCategory>

    @Streaming
    @GET
    suspend fun downloadFile(
        @Url url: String
    ): Response<ResponseBody>
}
