package com.organizer.sync

import androidx.room.withTransaction
import com.organizer.data.local.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/** Runs entity writes and outbox writes in one Room transaction. */
interface SyncTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomSyncTransactionRunner @Inject constructor(
    private val database: AppDatabase,
) : SyncTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
