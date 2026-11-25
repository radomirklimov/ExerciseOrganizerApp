package com.organizer.sync

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One mutex per authenticated owner, shared by push, pull and explicit retry
 * operations. Serialising these paths prevents a remote apply from racing an
 * in-flight mutation for the same local record.
 */
@Singleton
class SyncOperationLocks @Inject constructor() {
    private val locks = mutableMapOf<String, Mutex>()

    fun forOwner(owner: String): Mutex = synchronized(locks) { locks.getOrPut(owner) { Mutex() } }
}
