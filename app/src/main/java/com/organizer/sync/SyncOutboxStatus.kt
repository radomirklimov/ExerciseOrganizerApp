package com.organizer.sync

/**
 * Canonical outbox state names.
 *
 * The Room column is free TEXT and the current producer only writes
 * [PENDING_UPLOAD]. The remaining values are reserved for the future push
 * implementation: [IN_FLIGHT] and [FAILED] still represent local work and must
 * block remote application, while [ACKNOWLEDGED] is terminal and releases the
 * block.
 */
object SyncOutboxStatus {
    const val PENDING_UPLOAD = "PENDING_UPLOAD"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val FAILED = "FAILED"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"

    val BLOCKING: Set<String> = setOf(PENDING_UPLOAD, IN_FLIGHT, FAILED)
    val TERMINAL: Set<String> = setOf(ACKNOWLEDGED)
}