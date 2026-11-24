package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizer.data.local.db.entities.SyncOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner ORDER BY createdAt ASC")
    suspend fun getPending(owner: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner ORDER BY createdAt ASC")
    fun observe(owner: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId")
    suspend fun findByMutationId(owner: String, mutationId: String): SyncOutboxEntity?

    /**
     * A pending, never-retried entry is the only mutation state that is safe to
     * coalesce. Any other status may already have reached the backend.
     */
    @Query(
        """SELECT * FROM sync_outbox
           WHERE ownerFirebaseUid = :owner
           AND entityType = :entityType
           AND recordSyncId = :recordSyncId
           AND operation = :operation
           AND syncStatus = 'PENDING_UPLOAD'
           AND retryCount = 0
           AND lastError IS NULL
           ORDER BY createdAt DESC
           LIMIT 1"""
    )
    suspend fun findOpenMutation(
        owner: String,
        entityType: String,
        recordSyncId: String,
        operation: String,
    ): SyncOutboxEntity?

    @Query(
        """UPDATE sync_outbox
           SET payload = :payload, createdAt = :createdAt
           WHERE ownerFirebaseUid = :owner
           AND mutationId = :mutationId
           AND syncStatus = 'PENDING_UPLOAD'
           AND retryCount = 0
           AND lastError IS NULL"""
    )
    suspend fun updateOpenPayload(
        owner: String,
        mutationId: String,
        payload: String,
        createdAt: Long,
    )

    @Query("UPDATE sync_outbox SET retryCount = :retryCount, lastError = :error, syncStatus = :status WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId")
    suspend fun updateRetry(owner: String, mutationId: String, retryCount: Int, error: String?, status: String)

    @Query("DELETE FROM sync_outbox WHERE mutationId = :mutationId AND ownerFirebaseUid = :owner")
    suspend fun delete(mutationId: String, owner: String)

    @Query(
        """SELECT EXISTS(SELECT 1 FROM sync_outbox
           WHERE ownerFirebaseUid = :owner
           AND entityType = :entityType
           AND recordSyncId = :syncId
           AND syncStatus IN (:blockingStatuses))"""
    )
    suspend fun hasBlockingMutationForRecord(
        owner: String,
        entityType: String,
        syncId: String,
        blockingStatuses: Set<String>,
    ): Boolean
}