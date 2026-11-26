package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.ExerciseEntity
import com.organizer.data.local.db.entities.FavoriteExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteExerciseDao {
    @Query("SELECT exerciseId FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND deletedAt IS NULL")
    fun observeFavoriteIds(owner: String): Flow<List<Long>>

    @Query(
        """SELECT e.* FROM exercise e
        INNER JOIN favorite_exercise f ON e.exerciseId = f.exerciseId
        WHERE f.ownerFirebaseUid = :owner AND f.deletedAt IS NULL
        ORDER BY f.addedAt DESC"""
    )
    fun observeFavoriteExercises(owner: String): Flow<List<ExerciseEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NULL)")
    fun observeIsFavorite(owner: String, exerciseId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NULL)")
    suspend fun isFavorite(owner: String, exerciseId: Long): Boolean

    @Query("SELECT * FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId")
    suspend fun findByOwnerAndExercise(owner: String, exerciseId: Long): FavoriteExerciseEntity?

    @Query("SELECT * FROM favorite_exercise WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun findBySyncId(owner: String, syncId: String): FavoriteExerciseEntity?

    @Query("UPDATE favorite_exercise SET syncId = :newSyncId, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND syncId = :oldSyncId")
    suspend fun updateSyncId(owner: String, exerciseId: Long, oldSyncId: String, newSyncId: String, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(favorite: FavoriteExerciseEntity)

    @Query("UPDATE favorite_exercise SET deletedAt = :deletedAt, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NULL")
    suspend fun softDelete(owner: String, exerciseId: Long, deletedAt: Long, updatedAt: Long = deletedAt)

    @Query("UPDATE favorite_exercise SET deletedAt = NULL, syncStatus = 'PENDING_UPLOAD', updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND exerciseId = :exerciseId AND deletedAt IS NOT NULL")
    suspend fun restoreLocal(owner: String, exerciseId: Long, updatedAt: Long)

    @Query("UPDATE favorite_exercise SET syncStatus = :syncStatus, serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyAcknowledgementMetadata(owner: String, syncId: String, serverVersion: Long, updatedAt: Long, syncStatus: String)

    @Query("UPDATE favorite_exercise SET deletedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteDelete(owner: String, syncId: String, deletedAt: Long, serverVersion: Long, updatedAt: Long): Int

    @Query("UPDATE favorite_exercise SET deletedAt = NULL, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteUpsert(owner: String, syncId: String, serverVersion: Long, updatedAt: Long): Int
}