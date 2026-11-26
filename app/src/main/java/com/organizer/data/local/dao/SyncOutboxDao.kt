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

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner ORDER BY createdAt ASC, outboxId ASC")
    suspend fun getPending(owner: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner AND syncStatus = :status ORDER BY createdAt ASC, outboxId ASC")
    suspend fun findByStatus(owner: String, status: String): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner ORDER BY createdAt ASC")
    fun observe(owner: String): Flow<List<SyncOutboxEntity>>

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId")
    suspend fun findByMutationId(owner: String, mutationId: String): SyncOutboxEntity?

    @Query("SELECT * FROM sync_outbox WHERE ownerFirebaseUid = :owner AND recordSyncId = :recordSyncId")
    suspend fun findByRecordSyncId(owner: String, recordSyncId: String): List<SyncOutboxEntity>

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
           AND everTransmitted = 0
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
           AND lastError IS NULL
           AND everTransmitted = 0"""
    )
    suspend fun updateOpenPayload(
        owner: String,
        mutationId: String,
        payload: String,
        createdAt: Long,
    )

    @Query("UPDATE sync_outbox SET retryCount = :retryCount, lastError = :error, syncStatus = :status WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId")
    suspend fun updateRetry(owner: String, mutationId: String, retryCount: Int, error: String?, status: String)

    @Query(
        """SELECT * FROM sync_outbox
           WHERE ownerFirebaseUid = :owner
           AND entityType = :entityType
           AND recordSyncId = :syncId
           ORDER BY createdAt ASC, outboxId ASC"""
    )
    suspend fun findByRecord(owner: String, entityType: String, syncId: String): List<SyncOutboxEntity>

    @Query(
        """UPDATE sync_outbox
           SET syncStatus = :status,
               serverVersion = :serverVersion,
               serverUpdatedAt = :serverUpdatedAt,
               acknowledgedAt = :acknowledgedAt,
               terminalErrorCode = :terminalErrorCode
           WHERE ownerFirebaseUid = :owner
           AND mutationId = :mutationId
           AND syncStatus IN (:allowedStatuses)"""
    )
    suspend fun markTerminal(
        owner: String,
        mutationId: String,
        status: String,
        serverVersion: Long?,
        serverUpdatedAt: Long?,
        acknowledgedAt: Long,
        terminalErrorCode: String?,
        allowedStatuses: Set<String>,
    ): Int

    @Query("UPDATE sync_outbox SET syncStatus = 'PENDING_UPLOAD', lastError = NULL WHERE ownerFirebaseUid = :owner AND syncStatus = 'IN_FLIGHT'")
    suspend fun recoverInFlight(owner: String): Int

    @Query(
        """UPDATE sync_outbox SET syncStatus = 'IN_FLIGHT', lastError = NULL
           WHERE ownerFirebaseUid = :owner
           AND mutationId IN (:mutationIds)
           AND syncStatus = 'PENDING_UPLOAD'"""
    )
    suspend fun markInFlight(owner: String, mutationIds: Set<String>): Int

    /** Marks actual transmission, immediately before the network request. */
    @Query(
        """UPDATE sync_outbox SET everTransmitted = 1
           WHERE ownerFirebaseUid = :owner AND mutationId IN (:mutationIds)
           AND syncStatus = 'IN_FLIGHT' AND everTransmitted = 0"""
    )
    suspend fun markTransmitted(owner: String, mutationIds: Set<String>): Int

    @Query(
        """UPDATE sync_outbox SET syncStatus = 'SUPERSEDED', lastError = NULL
           WHERE ownerFirebaseUid = :owner
           AND mutationId IN (:mutationIds)
           AND syncStatus = 'PENDING_UPLOAD'
           AND everTransmitted = 0"""
    )
    suspend fun markSuperseded(owner: String, mutationIds: Set<String>): Int

    /** Retires a resolved CONFLICTED mutation without rewriting its payload. */
    @Query(
        """UPDATE sync_outbox SET syncStatus = 'SUPERSEDED', lastError = NULL
           WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId AND syncStatus = 'CONFLICTED'"""
    )
    suspend fun markResolvedConflict(owner: String, mutationId: String): Int

    @Query(
        """UPDATE sync_outbox SET syncStatus = 'PENDING_UPLOAD', lastError = :error
           WHERE ownerFirebaseUid = :owner
           AND mutationId IN (:mutationIds)
           AND syncStatus IN (:allowedStatuses)"""
    )
    suspend fun resetToPending(
        owner: String,
        mutationIds: Set<String>,
        error: String?,
        allowedStatuses: Set<String>,
    ): Int

    @Query(
        """UPDATE sync_outbox SET syncStatus = 'FAILED', lastError = :error
           WHERE ownerFirebaseUid = :owner
           AND mutationId IN (:mutationIds)
           AND syncStatus IN (:allowedStatuses)"""
    )
    suspend fun markFailedBatch(
        owner: String,
        mutationIds: Set<String>,
        error: String,
        allowedStatuses: Set<String>,
    ): Int

    @Query(
        """UPDATE sync_outbox
           SET syncStatus = :status,
               terminalErrorCode = :errorCode,
               lastError = :error,
               acknowledgedAt = :now
           WHERE ownerFirebaseUid = :owner
           AND mutationId IN (:mutationIds)
           AND syncStatus IN (:allowedStatuses)"""
    )
    suspend fun markTerminalBatch(
        owner: String,
        mutationIds: Set<String>,
        status: String,
        errorCode: String?,
        error: String?,
        now: Long,
        allowedStatuses: Set<String>,
    ): Int

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