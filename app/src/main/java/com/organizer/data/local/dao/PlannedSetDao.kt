package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.PlannedSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedSetDao {

    @Query(
        "SELECT * FROM planned_set WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND deletedAt IS NULL " +
            "ORDER BY setNumber ASC"
    )
    fun observePlannedSets(owner: String, workoutId: Long, exerciseId: Long): Flow<List<PlannedSetEntity>>

    @Query("SELECT * FROM planned_set WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun findBySyncId(owner: String, syncId: String): PlannedSetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlannedSetEntity)

    @Query("UPDATE planned_set SET setNumber = :setNumber, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE ownerFirebaseUid = :owner AND syncId = :syncId")
    suspend fun updateSetNumber(owner: String, syncId: String, setNumber: Int, updatedAt: Long)

    @Query("UPDATE planned_set SET deletedAt = :deletedAt, updatedAt = :updatedAt, syncStatus = 'PENDING_DELETE' WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND setNumber = :setNumber")
    suspend fun softDelete(owner: String, workoutId: Long, exerciseId: Long, setNumber: Int, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE planned_set SET deletedAt = NULL, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE ownerFirebaseUid = :owner AND workoutId = :workoutId AND exerciseId = :exerciseId AND setNumber = :setNumber AND deletedAt IS NOT NULL")
    suspend fun restoreLocal(owner: String, workoutId: Long, exerciseId: Long, setNumber: Int, updatedAt: Long)

    @Query("UPDATE planned_set SET syncStatus = :syncStatus, serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyAcknowledgementMetadata(owner: String, syncId: String, serverVersion: Long, updatedAt: Long, syncStatus: String)

    @Query("UPDATE planned_set SET reps = :reps, weight = :weight, durationSeconds = :durationSeconds, deletedAt = NULL, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteUpsert(owner: String, syncId: String, reps: Int?, weight: Double?, durationSeconds: Int?, serverVersion: Long, updatedAt: Long): Int

    @Query("UPDATE planned_set SET deletedAt = :deletedAt, syncStatus = 'SYNCED', serverVersion = :serverVersion, updatedAt = :updatedAt WHERE ownerFirebaseUid = :owner AND syncId = :syncId AND serverVersion < :serverVersion")
    suspend fun applyRemoteDelete(owner: String, syncId: String, deletedAt: Long, serverVersion: Long, updatedAt: Long): Int
}