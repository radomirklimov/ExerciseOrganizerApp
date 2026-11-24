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

    @Query("UPDATE sync_outbox SET retryCount = :retryCount, lastError = :error, syncStatus = :status WHERE ownerFirebaseUid = :owner AND mutationId = :mutationId")
    suspend fun updateRetry(owner: String, mutationId: String, retryCount: Int, error: String?, status: String)

    @Query("DELETE FROM sync_outbox WHERE mutationId = :mutationId AND ownerFirebaseUid = :owner")
    suspend fun delete(mutationId: String, owner: String)
}