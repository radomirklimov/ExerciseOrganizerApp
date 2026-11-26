package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.organizer.data.local.db.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    /** Catalog inserts allocate local PKs and must never replace a custom row. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPublicAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' AND isCustom = 0")
    suspend fun getAllPublicOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' AND isCustom = 0 AND publicExerciseId = :publicExerciseId")
    suspend fun findPublicByCatalogId(publicExerciseId: Long): ExerciseEntity?

    /** All names, including custom rows of every owner and soft-deleted rows. */
    @Query("SELECT name FROM exercise")
    suspend fun getAllNamesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun updateAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' OR (ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    suspend fun getAllOnce(owner: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE ownerFirebaseUid = 'public' OR (ownerFirebaseUid = :owner AND deletedAt IS NULL)")
    fun getAll(owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE isCustom = 1 AND ownerFirebaseUid = :owner AND deletedAt IS NULL ORDER BY exerciseId DESC")
    fun getCustomExercises(owner: String): Flow<List<ExerciseEntity>>

    @Query("""SELECT e.* FROM exercise e 
        JOIN exercise_category ec ON e.exerciseId = ec.exerciseId
        WHERE ec.categoryId = :categoryId
        AND (e.ownerFirebaseUid = 'public' OR e.ownerFirebaseUid = :owner)
        AND e.deletedAt IS NULL
        ORDER BY ec.sortOrder, ec.exerciseCategoryId""")
    fun observeExercisesByCategory(categoryId: Long, owner: String): Flow<List<ExerciseEntity>>

    /**
     * Exercises already attached to a workout are historical/routine references.
     * Deleted custom exercises remain resolvable here so existing workouts and
     * history can still display them.
     */
    @Query("""SELECT e.* FROM exercise e
        JOIN workout_exercise we ON e.exerciseId = we.exerciseId
        WHERE we.workoutId = :workoutId AND we.ownerFirebaseUid = :owner
        AND we.deletedAt IS NULL
        AND (e.ownerFirebaseUid = 'public' OR e.ownerFirebaseUid = :owner)
        ORDER BY we.orderIndex ASC""")
    fun observeExercisesByWorkout(workoutId: Long, owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id AND (ownerFirebaseUid = 'public' OR ownerFirebaseUid = :owner)")
    fun getById(owner: String, id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercise WHERE exerciseId = :id AND (ownerFirebaseUid = 'public' OR ownerFirebaseUid = :owner)")
    suspend fun getByIdOnce(owner: String, id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE syncId = :syncId AND ownerFirebaseUid = :owner AND isCustom = 1")
    suspend fun findCustomBySyncId(owner: String, syncId: String): ExerciseEntity?

    @Query(
        """UPDATE exercise
           SET name = :name, instructions = :instructions, imageUrls = :imageUrls,
               exerciseType = :exerciseType, serverVersion = :serverVersion,
               updatedAt = :updatedAt, deletedAt = NULL, syncStatus = 'SYNCED'
           WHERE syncId = :syncId AND ownerFirebaseUid = :owner AND isCustom = 1"""
    )
    suspend fun applyRemoteUpsert(
        owner: String,
        syncId: String,
        name: String,
        instructions: List<String>,
        imageUrls: List<String>,
        exerciseType: String,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE exercise
           SET deletedAt = :deletedAt, serverVersion = :serverVersion,
               updatedAt = :updatedAt, syncStatus = 'SYNCED'
           WHERE syncId = :syncId AND ownerFirebaseUid = :owner AND isCustom = 1"""
    )
    suspend fun applyRemoteDelete(
        owner: String,
        syncId: String,
        deletedAt: Long,
        serverVersion: Long,
        updatedAt: Long,
    )

    @Query(
        """UPDATE exercise
           SET serverVersion = :serverVersion,
               updatedAt = MAX(updatedAt, :serverUpdatedAt),
               syncStatus = :syncStatus
           WHERE ownerFirebaseUid = :owner
           AND syncId = :syncId
           AND isCustom = 1
           AND serverVersion < :serverVersion"""
    )
    suspend fun applyAcknowledgementMetadata(
        owner: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        syncStatus: String,
    )

    /** Idempotent soft delete; only owner-scoped custom exercises are affected. */
    @Query(
        """UPDATE exercise
           SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'PENDING_DELETE'
           WHERE exerciseId = :id
           AND isCustom = 1
           AND ownerFirebaseUid = :owner
           AND deletedAt IS NULL"""
    )
    suspend fun softDeleteCustom(owner: String, id: Long, deletedAt: Long, updatedAt: Long): Int

    @Delete
    suspend fun delete(exercise: ExerciseEntity)
}