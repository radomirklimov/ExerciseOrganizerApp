package com.organizer.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.organizer.constants.AppConfig
import com.organizer.data.local.dao.CategoryDao
import com.organizer.data.local.dao.ExerciseCategoryDao
import com.organizer.data.local.dao.ExerciseDao
import com.organizer.data.local.dao.WorkoutCompletionDao
import com.organizer.data.local.dao.WorkoutDao
import com.organizer.data.local.dao.WorkoutExerciseDao
import com.organizer.data.local.dao.ExerciseProgressDao
import com.organizer.data.local.dao.FavoriteExerciseDao
import com.organizer.data.local.dao.PlannedSetDao
import com.organizer.data.local.dao.ScheduledWorkoutDao
import com.organizer.data.local.dao.StarterTemplateDao
import com.organizer.data.local.dao.SyncCursorDao
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.db.AppDatabase
import com.organizer.data.local.db.ALL_MIGRATIONS
import com.organizer.data.local.db.DISPLAY_ORDER_MIGRATION_1_2
import com.organizer.data.local.db.OWNER_AND_SYNC_FOUNDATION_MIGRATION_13_14
import com.organizer.data.local.db.ACTIVE_WORKOUT_NAME_UNIQUENESS_MIGRATION_14_15
import com.organizer.data.local.db.PULL_STAGING_MIGRATION_15_16
import com.organizer.data.local.db.OUTBOX_ACKNOWLEDGEMENT_MIGRATION_16_17
import com.organizer.data.local.db.EXERCISE_TYPE_MIGRATION_2_3
import com.organizer.data.local.db.EXERCISE_PROGRESS_MIGRATION_3_4
import com.organizer.data.local.db.EXERCISE_PROGRESS_UNIT_MIGRATION_12_13
import com.organizer.data.local.db.FAVORITE_EXERCISE_MIGRATION_7_8
import com.organizer.data.local.db.PLANNED_SETS_AND_SESSIONS_MIGRATION_10_11
import com.organizer.data.local.db.SCHEDULED_WORKOUT_MIGRATION_8_9
import com.organizer.data.local.db.SCHEDULED_WORKOUT_ONE_TIME_MIGRATION_9_10
import com.organizer.data.local.db.STARTER_TEMPLATES_MIGRATION_5_6
import com.organizer.data.local.db.STARTER_TEMPLATE_CATEGORIES_MIGRATION_6_7
import com.organizer.data.local.db.WORKOUT_COMPLETION_MIGRATION_11_12
import com.organizer.data.local.db.WORKOUT_NOTES_MIGRATION_4_5
import com.google.firebase.auth.FirebaseAuth
import com.organizer.data.remote.ApiService
import com.organizer.data.remote.AuthInterceptor
import com.organizer.sync.FirebaseSyncTokenProvider
import com.organizer.sync.RoomSyncTransactionRunner
import com.organizer.sync.SyncTokenProvider
import com.organizer.sync.SyncTransactionRunner
import com.organizer.data.remote.SyncApi
import com.organizer.data.remote.UserApi
import com.organizer.data.local.storage.FileStorage
import com.organizer.data.local.storage.FileStore
import com.organizer.data.media.MediaDownloadQueue
import com.organizer.data.remote.repo.FileRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
    fun provideJson(): Json = json

    @Provides
    @Singleton
    fun provideSyncTransactionRunner(impl: RoomSyncTransactionRunner): SyncTransactionRunner = impl

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideSyncTokenProvider(impl: FirebaseSyncTokenProvider): SyncTokenProvider = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AppConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(client)
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
    fun provideUserApi(
        retrofit: Retrofit
    ): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSyncApi(
        retrofit: Retrofit
    ): SyncApi {
        return retrofit.create(SyncApi::class.java)
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
        ).addMigrations(*ALL_MIGRATIONS).build()
    }

    @Provides
    fun provideFileStore(
        impl: FileStorage
    ): FileStore {
        return impl
    }

    @Provides
    @Singleton
    fun provideMediaDownloadQueue(
        store: FileStore,
        remote: FileRemoteDataSource
    ): MediaDownloadQueue {
        // Application-scoped: downloads survive navigation and outlive any
        // single screen or data-sync coroutine.
        return MediaDownloadQueue(
            store = store,
            remote = remote,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        )
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

    @Provides
    fun provideExerciseProgressDao(
        database: AppDatabase
    ): ExerciseProgressDao {
        return database.exerciseProgressDao
    }

    @Provides
    fun provideStarterTemplateDao(
        database: AppDatabase
    ): StarterTemplateDao {
        return database.starterTemplateDao
    }

    @Provides
    fun provideFavoriteExerciseDao(
        database: AppDatabase
    ): FavoriteExerciseDao {
        return database.favoriteExerciseDao
    }

    @Provides
    fun providePlannedSetDao(
        database: AppDatabase
    ): PlannedSetDao {
        return database.plannedSetDao
    }

    @Provides
    fun provideScheduledWorkoutDao(
        database: AppDatabase
    ): ScheduledWorkoutDao {
        return database.scheduledWorkoutDao
    }

    @Provides
    fun provideWorkoutCompletionDao(
        database: AppDatabase
    ): WorkoutCompletionDao {
        return database.workoutCompletionDao
    }

    @Provides
    fun provideSyncCursorDao(
        database: AppDatabase
    ): SyncCursorDao {
        return database.syncCursorDao
    }

    @Provides
    fun provideSyncOutboxDao(
        database: AppDatabase
    ): SyncOutboxDao {
        return database.syncOutboxDao
    }

    @Provides
    fun provideSyncStagedDao(
        database: AppDatabase
    ): SyncStagedDao {
        return database.syncStagedDao
    }
}
