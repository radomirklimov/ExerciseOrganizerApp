package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncMutationRequestDto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPushCoordinator @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val outboxDao: SyncOutboxDao,
    private val apiClient: SyncApiClient,
    private val contractMapper: SyncContractMapper,
    private val acknowledger: SyncOutboxAcknowledger,
    private val reconciler: SyncReconciler,
    private val transactions: SyncTransactionRunner,
) {
    private val pushLocks = mutableMapOf<String, Mutex>()

    suspend fun pushOneBatch(limit: Int = MAX_BATCH_SIZE): PushResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) {
            return PushResult(error = "Guest mode cannot push cloud data")
        }
        val lock = synchronized(pushLocks) { pushLocks.getOrPut(owner) { Mutex() } }
        return lock.withLock { pushLocked(owner, limit.coerceIn(1, MAX_BATCH_SIZE)) }
    }

    private suspend fun pushLocked(owner: String, limit: Int): PushResult {
        val selection = try {
            transactions.run { selectBatch(owner, limit) }
        } catch (ex: OwnerChangedDuringPushException) {
            return PushResult(ownerChanged = true, error = "Authenticated account changed during selection")
        }

        val selected = selection.mutations
        if (selected.isEmpty()) return PushResult(skipped = selection.superseded)

        if (ownerProvider.currentOwner() != owner) {
            return PushResult(
                selected = selected.size,
                superseded = selection.superseded,
                ownerChanged = true,
                error = "Authenticated account changed before push",
            )
        }

        val requests = selected.mapNotNull { mutation ->
            when (val mapped = contractMapper.toMutation(mutation, owner)) {
                is OutboxMappingResult.Success -> mapped.request
                is OutboxMappingResult.Failure -> null
            }
        }
        val invalidIds = selected.map { it.mutationId }.toSet() - requests.map { it.mutationId }.toSet()
        if (invalidIds.isNotEmpty()) {
            val reset = resetRows(owner, invalidIds, "Invalid local mutation", retryable = true)
            if (reset.ownerChanged) return reset.result
        }
        if (requests.isEmpty()) {
            return PushResult(
                selected = selected.size,
                superseded = selection.superseded,
                skipped = invalidIds.size,
            )
        }

        val apiResult = apiClient.push(SyncPushRequestDto(requests))
        return when (apiResult) {
            is SyncApiResult.Success -> handleSuccess(owner, selection, requests, apiResult.data.results)
            is SyncApiResult.HttpError -> handleHttpError(owner, selection, apiResult)
            is SyncApiResult.TransportError,
            is SyncApiResult.MalformedResponse -> {
                val ids = requests.map { it.mutationId }.toSet()
                val reset = resetRows(owner, ids, "Sync transport or response failure", retryable = true)
                if (reset.ownerChanged) reset.result else PushResult(
                    selected = selected.size,
                    sent = requests.size,
                    superseded = selection.superseded,
                    retryable = reset.count,
                    error = "Network or malformed response failure",
                )
            }
        }
    }

    /**
     * Explicit recovery for mutations parked in FAILED after a deterministic
     * request-level rejection. Nothing in the mutation is rewritten; only the
     * status returns to PENDING_UPLOAD so the next manual push can try again.
     */
    suspend fun requeueFailed(): Int {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) return 0
        val lock = synchronized(pushLocks) { pushLocks.getOrPut(owner) { Mutex() } }
        return lock.withLock {
            try {
                transactions.run {
                    if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
                    val ids = outboxDao.findByStatus(owner, SyncOutboxStatus.FAILED).map { it.mutationId }.toSet()
                    if (ids.isEmpty()) {
                        0
                    } else {
                        outboxDao.resetToPending(
                            owner = owner,
                            mutationIds = ids,
                            error = null,
                            allowedStatuses = setOf(SyncOutboxStatus.FAILED),
                        )
                    }
                }
            } catch (ex: OwnerChangedDuringPushException) {
                0
            }
        }
    }

    private suspend fun handleHttpError(
        owner: String,
        selection: BatchSelection,
        error: SyncApiResult.HttpError,
    ): PushResult {
        val ids = selection.mutations.map { it.mutationId }.toSet()
        val detail = listOfNotNull(error.error, error.message).joinToString(": ").takeIf { it.isNotEmpty() }
        val diagnostic = "HTTP ${error.code}" + (detail?.let { ": $it" } ?: "")

        // 401 describes the credential, not the batch. Data and mutation IDs
        // stay unchanged; a fresh token makes the same request retryable.
        if (error.code == 401) {
            val reset = resetRows(owner, ids, diagnostic, retryable = true)
            return if (reset.ownerChanged) reset.result else PushResult(
                selected = selection.mutations.size,
                superseded = selection.superseded,
                retryable = reset.count,
                error = diagnostic,
            )
        }

        // Deterministic request-level errors (400, 413, ...) would fail again
        // unchanged on every manual push. FAILED is blocking but non-terminal:
        // local data is preserved, ordinary selection skips it, the error is
        // diagnosable, and requeueFailed() is the explicit recovery path.
        if (error.code in 400..499) {
            val marked = markFailedRows(owner, ids, diagnostic)
            return if (marked.ownerChanged) marked.result else PushResult(
                selected = selection.mutations.size,
                superseded = selection.superseded,
                failed = marked.count,
                error = diagnostic,
            )
        }

        val reset = resetRows(owner, ids, diagnostic, retryable = true)
        return if (reset.ownerChanged) reset.result else PushResult(
            selected = selection.mutations.size,
            superseded = selection.superseded,
            retryable = reset.count,
            error = diagnostic,
        )
    }

    private suspend fun handleSuccess(
        owner: String,
        selection: BatchSelection,
        requests: List<SyncMutationRequestDto>,
        results: List<com.organizer.data.remote.model.SyncMutationResultDto>,
    ): PushResult {
        val ack = acknowledger.acknowledge(owner, results)
        if (ack.ownerChanged) {
            return PushResult(
                selected = selection.mutations.size,
                sent = requests.size,
                superseded = selection.superseded,
                ownerChanged = true,
                error = "Authenticated account changed during acknowledgement",
            )
        }

        val unresolved = try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
                outboxDao.findByStatus(owner, SyncOutboxStatus.IN_FLIGHT)
                    .map { it.mutationId }.toSet()
            }
        } catch (ex: OwnerChangedDuringPushException) {
            return PushResult(
                selected = selection.mutations.size,
                sent = requests.size,
                superseded = selection.superseded,
                ownerChanged = true,
                error = "Authenticated account changed before retry reset",
            )
        }

        val retryable = if (unresolved.isEmpty()) 0 else {
            val reset = resetRows(owner, unresolved, "Missing or invalid push result", retryable = true)
            if (reset.ownerChanged) return reset.result
            reset.count
        }

        val reconciled = if (ack.acknowledged > 0) reconciler.reconcile().reconsidered else 0
        val recognized = results
            .filter { r -> selection.mutations.any { m -> m.mutationId == r.mutationId && m.recordSyncId == r.syncId } }
            .distinctBy { it.mutationId }
        return PushResult(
            selected = selection.mutations.size,
            sent = requests.size,
            applied = recognized.count { it.status == "APPLIED" },
            duplicate = recognized.count { it.status == "DUPLICATE" },
            conflicted = recognized.count { it.status == "CONFLICT" },
            rejected = recognized.count { it.status == "REJECTED" },
            retryable = retryable,
            skipped = ack.skippedUnknown + ack.skippedTerminal + ack.skippedInvalid,
            superseded = selection.superseded,
            reconciled = reconciled,
        )
    }

    private suspend fun selectBatch(owner: String, limit: Int): BatchSelection {
        if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
        outboxDao.recoverInFlight(owner)

        val candidates = outboxDao.findByStatus(owner, SyncOutboxStatus.PENDING_UPLOAD)
        val groups = candidates.groupBy { Triple(it.entityType, it.recordSyncId, it.ownerFirebaseUid) }

        // A chain is compressed only when every row is definitely never sent and
        // the final mutation can stand alone under backend semantics. UPSERT and
        // DELETE can represent the final state on their own; RESTORE cannot,
        // because an earlier unsent DELETE may not exist on the server yet.
        val representatives = groups.values.map { group ->
            val ordered = group.sortedWith(compareBy<SyncOutboxEntity> { it.createdAt }.thenBy { it.orderKey() })
            val final = ordered.last()
            val allClean = ordered.all { it.retryCount == 0 && it.lastError == null }
            val finalIsStandaloneSafe = final.operation == "UPSERT" || final.operation == "DELETE"
            val finalMaps = contractMapper.toMutation(final, owner) is OutboxMappingResult.Success

            if (ordered.size > 1 && allClean && finalIsStandaloneSafe && finalMaps) {
                RecordBatch(final, ordered.dropLast(1).map { it.mutationId }.toSet())
            } else {
                RecordBatch(ordered.first(), emptySet())
            }
        }

        val batch = representatives
            .sortedWith(compareBy<RecordBatch> { it.representative.createdAt }.thenBy { it.representative.orderKey() })
            .take(limit)
        val batchIds = batch.map { it.representative.mutationId }.toSet()
        val supersededIds = batch.flatMap { it.supersedeIds }.toSet()

        if (supersededIds.isNotEmpty()) outboxDao.markSuperseded(owner, supersededIds)
        if (batchIds.isNotEmpty()) outboxDao.markInFlight(owner, batchIds)

        return BatchSelection(batch.map { it.representative }, supersededIds.size)
    }

    private suspend fun resetRows(
        owner: String,
        ids: Set<String>,
        error: String?,
        retryable: Boolean,
    ): RowOperationResult = try {
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
            val count = if (retryable) {
                outboxDao.resetToPending(
                    owner, ids, error,
                    setOf(SyncOutboxStatus.IN_FLIGHT, SyncOutboxStatus.FAILED),
                )
            } else {
                0
            }
            count
        }.let { RowOperationResult(it, false, PushResult(ownerChanged = false)) }
    } catch (ex: OwnerChangedDuringPushException) {
        RowOperationResult(0, true, PushResult(ownerChanged = true, error = "Authenticated account changed during retry reset"))
    }

    private suspend fun markFailedRows(
        owner: String,
        ids: Set<String>,
        error: String,
    ): RowOperationResult = try {
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
            outboxDao.markFailedBatch(
                owner = owner,
                mutationIds = ids,
                error = error,
                allowedStatuses = setOf(SyncOutboxStatus.IN_FLIGHT),
            )
        }.let { RowOperationResult(it, false, PushResult(ownerChanged = false)) }
    } catch (ex: OwnerChangedDuringPushException) {
        RowOperationResult(0, true, PushResult(ownerChanged = true, error = "Authenticated account changed during failure update"))
    }

    private fun SyncOutboxEntity.orderKey(): Long = outboxId

    private data class RecordBatch(
        val representative: SyncOutboxEntity,
        val supersedeIds: Set<String>,
    )

    private data class BatchSelection(
        val mutations: List<SyncOutboxEntity>,
        val superseded: Int,
    )

    private data class RowOperationResult(
        val count: Int,
        val ownerChanged: Boolean,
        val result: PushResult,
    )

    companion object {
        const val MAX_BATCH_SIZE = 100
    }
}

/** Thrown inside a push persistence transaction to roll back an account-switched call. */
class OwnerChangedDuringPushException : RuntimeException()