package com.organizer.sync

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor() {
    /**
     * Full push/pull orchestration belongs to a later milestone. The API
     * contract, outbox mapping and response mapping are implemented separately
     * in [SyncApiClient] and [SyncContractMapper].
     */
    suspend fun syncNow() = Unit
}