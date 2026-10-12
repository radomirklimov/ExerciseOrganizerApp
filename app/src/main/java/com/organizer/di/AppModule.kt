package com.organizer.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.organizer.constants.AppConfig
import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.db.AppDatabase
import com.organizer.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import kotlin.jvm.java

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL.trimEnd('/') + "/")
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(
        retrofit: Retrofit
    ): ApiService {
        return retrofit.create(ApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppConfig.DATABASE_NAME
        ).build()
    }

    @Provides
    fun provideCategoryDao(
        database: AppDatabase
    ): CategoryDao {
        return database.categoryDao
    }

    @Provides
    fun provideExerciseDao(
        database: AppDatabase
    ): ExerciseDao {
        return database.exerciseDao
    }

    @Provides
    fun provideExerciseCategoryDao(
        database: AppDatabase
    ): ExerciseCategoryDao {
        return database.exerciseCategoryDao
    }

    @Provides
    fun provideWorkoutDao(
        database: AppDatabase
    ): WorkoutDao {
        return database.workoutDao
    }

    @Provides
    fun provideWorkoutExerciseDao(
        database: AppDatabase
    ): WorkoutExerciseDao {
        return database.workoutExerciseDao
    }
}
