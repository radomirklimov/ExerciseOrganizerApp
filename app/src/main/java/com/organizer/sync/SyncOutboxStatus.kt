package com.organizer.sync

/**
 * Canonical outbox state names.
 *
 * Blocking states keep a remote record staged as LOCAL_PENDING because local
 * work for the same record has not been fully resolved. Only [ACKNOWLEDGED]
 * releases that block. CONFLICTED and REJECTED_TERMINAL are terminal for the
 * individual mutation but still require explicit resolution of the record.
 * SUPERSEDED is terminal and non-blocking: a never-sent mutation replaced by a
 * newer local mutation for the same record. It is not a server acknowledgement.
 */
object SyncOutboxStatus {
    const val PENDING_UPLOAD = "PENDING_UPLOAD"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val FAILED = "FAILED"
    const val CONFLICTED = "CONFLICTED"
    const val REJECTED_TERMINAL = "REJECTED_TERMINAL"
    const val SUPERSEDED = "SUPERSEDED"
    const val ACKNOWLEDGED = "ACKNOWLEDGED"

    val BLOCKING: Set<String> = setOf(
        PENDING_UPLOAD,
        IN_FLIGHT,
        FAILED,
        CONFLICTED,
        REJECTED_TERMINAL,
    )
    val TERMINAL: Set<String> = setOf(
        ACKNOWLEDGED,
        CONFLICTED,
        REJECTED_TERMINAL,
        SUPERSEDED,
    )
}