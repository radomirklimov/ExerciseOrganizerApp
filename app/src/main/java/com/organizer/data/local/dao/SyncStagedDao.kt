package com.organizer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.organizer.data.local.db.entities.SyncStagedRecordEntity

@Dao
interface SyncStagedDao {
    /**
     * Atomic, version-monotonic staging write.
     *
     * The unique conflict target is (ownerFirebaseUid, entityType, syncId).
     * A row is replaced only when the incoming serverVersion is strictly newer.
     * The WHERE clause on DO UPDATE prevents older or duplicate deliveries from
     * resetting processingStatus, retry metadata, or stagedId.
     */
    @Query(
        """INSERT INTO sync_staged_record
           (ownerFirebaseUid, entityType, syncId, serverVersion, serverUpdatedAt,
            deleted, payload, processingStatus, retryCount, lastError, createdAt, lastAttemptAt)
           VALUES (:owner, :entityType, :syncId, :serverVersion, :serverUpdatedAt,
                   :deleted, :payload, :processingStatus, 0, NULL, :createdAt, NULL)
           ON CONFLICT(ownerFirebaseUid, entityType, syncId) DO UPDATE SET
               serverVersion = excluded.serverVersion,
               serverUpdatedAt = excluded.serverUpdatedAt,
               deleted = excluded.deleted,
               payload = excluded.payload,
               processingStatus = excluded.processingStatus,
               retryCount = 0,
               lastError = NULL,
               createdAt = excluded.createdAt,
               lastAttemptAt = NULL
           WHERE excluded.serverVersion > sync_staged_record.serverVersion"""
    )
    suspend fun upsertIfNewer(
        owner: String,
        entityType: String,
        syncId: String,
        serverVersion: Long,
        serverUpdatedAt: Long,
        deleted: Boolean,
        payload: String?,
        processingStatus: String,
        createdAt: Long,
    )

    @Query("SELECT * FROM sync_staged_record WHERE ownerFirebaseUid = :owner ORDER BY createdAt ASC")
    suspend fun getAll(owner: String): List<SyncStagedRecordEntity>

    @Query("SELECT * FROM sync_staged_record WHERE ownerFirebaseUid = :owner AND processingStatus = :status ORDER BY createdAt ASC")
    suspend fun getByStatus(owner: String, status: String): List<SyncStagedRecordEntity>

    @Query("SELECT * FROM sync_staged_record WHERE ownerFirebaseUid = :owner AND entityType = :entityType AND syncId = :syncId")
    suspend fun find(owner: String, entityType: String, syncId: String): SyncStagedRecordEntity?

    @Query(
        """UPDATE sync_staged_record
           SET processingStatus = :status, retryCount = :retryCount, lastError = :error, lastAttemptAt = :attemptAt
           WHERE ownerFirebaseUid = :owner AND stagedId = :stagedId"""
    )
    suspend fun updateStatus(
        owner: String,
        stagedId: Long,
        status: String,
        retryCount: Int,
        error: String?,
        attemptAt: Long,
    )
}