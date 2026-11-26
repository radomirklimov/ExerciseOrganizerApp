package com.organizer.sync

import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncOutboxDao
import com.organizer.data.local.db.entities.SyncOutboxEntity
import com.organizer.data.remote.model.SyncPushRequestDto
import com.organizer.data.remote.model.SyncMutationRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPushCoordinator @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val tokenProvider: SyncTokenProvider,
    private val outboxDao: SyncOutboxDao,
    private val apiClient: SyncApiClient,
    private val contractMapper: SyncContractMapper,
    private val acknowledger: SyncOutboxAcknowledger,
    private val reconciler: SyncReconciler,
    private val transactions: SyncTransactionRunner,
    private val operationLocks: SyncOperationLocks,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun pushOneBatch(limit: Int = MAX_BATCH_SIZE): PushResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) {
            return PushResult(error = "Guest mode cannot push cloud data")
        }
        val lock = operationLocks.forOwner(owner)
        return lock.withLock { pushLocked(owner, limit.coerceIn(1, MAX_BATCH_SIZE)) }
    }

    private suspend fun pushLocked(owner: String, limit: Int): PushResult {
        val selection = try {
            transactions.run { selectBatch(owner, limit) }
        } catch (ex: OwnerChangedDuringPushException) {
            return PushResult(ownerChanged = true, error = "Authenticated account changed during selection")
        }

        var preInvalidRejected = 0
        if (selection.invalid.isNotEmpty()) {
            val terminal = markMappingFailuresTerminal(owner, selection.invalid, "Invalid local mutation")
            if (terminal.ownerChanged) return terminal.result
            preInvalidRejected = terminal.count
        }
        val selected = selection.mutations
        if (selected.isEmpty()) {
            return PushResult(
                skipped = selection.superseded,
                rejected = preInvalidRejected,
                error = if (preInvalidRejected > 0) "Invalid local mutation" else null,
            )
        }

        if (ownerProvider.currentOwner() != owner) {
            return PushResult(
                selected = selected.size,
                superseded = selection.superseded,
                ownerChanged = true,
                error = "Authenticated account changed before push",
            )
        }

        val mappingFailures = mutableMapOf<String, String>()
        val requests = selected.mapNotNull { mutation ->
            when (val mapped = contractMapper.toMutation(mutation, owner)) {
                is OutboxMappingResult.Success -> mapped.request
                is OutboxMappingResult.Failure -> {
                    mappingFailures[mutation.mutationId] = mapped.reason
                    null
                }
            }
        }
        fun withMapping(result: PushResult): PushResult =
            if (mappingFailures.isEmpty() && preInvalidRejected == 0) result
            else result.copy(rejected = result.rejected + mappingFailures.size + preInvalidRejected)

        // Mapping failures are deterministic (bad UUID, type, payload JSON or
        // owner). Retrying them unchanged can never succeed, so park them
        // terminally with a diagnostic code while preserving all local data.
        if (mappingFailures.isNotEmpty()) {
            val diagnostic = mappingFailures.values.distinct().joinToString("; ")
            val terminal = markMappingFailuresTerminal(owner, mappingFailures.keys, diagnostic)
            if (terminal.ownerChanged) return terminal.result
        }
        if (requests.isEmpty()) {
            return PushResult(
                selected = selected.size,
                superseded = selection.superseded,
                rejected = mappingFailures.size,
                error = mappingFailures.values.distinct().joinToString("; "),
            )
        }

        // Bind the credential to the captured user. From here on the request
        // carries A's token even if the ambient account switches to B; checks
        // above and below still guard all persistence.
        val token = tokenProvider.idTokenFor(owner)
        if (token == null) {
            return withMapping(
                PushResult(
                    selected = selected.size,
                    superseded = selection.superseded,
                    ownerChanged = ownerProvider.currentOwner() != owner,
                    error = "Authenticated user is no longer available for push",
                )
            )
        }
        val mutationIds = requests.map { it.mutationId }.toSet()
        try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
                outboxDao.markTransmitted(owner, mutationIds)
            }
        } catch (ex: OwnerChangedDuringPushException) {
            return PushResult(ownerChanged = true, error = "Authenticated account changed before transmission")
        }
        val apiResult = apiClient.push(SyncPushRequestDto(requests), "Bearer $token")
        return withMapping(when (apiResult) {
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
        })
    }

    /**
     * Explicit recovery for mutations parked in FAILED after a deterministic
     * request-level rejection. Nothing in the mutation is rewritten; only the
     * status returns to PENDING_UPLOAD so the next manual push can try again.
     */
    suspend fun requeueFailed(): Int {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) return 0
        val lock = operationLocks.forOwner(owner)
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

        val reconciliation = if (ack.acknowledged > 0) reconciler.reconcile(owner) else ReconciliationResult()
        val reconciled = reconciliation.reconsidered
        val recognized = results
            .filter { r -> selection.mutations.any { m -> m.mutationId == r.mutationId && m.recordSyncId == r.syncId } }
            .distinctBy { it.mutationId }
        val effectiveStatuses = recognized.map { r ->
            if (r.status == "DUPLICATE") r.originalStatus ?: "APPLIED" else r.status
        }
        return PushResult(
            selected = selection.mutations.size,
            sent = requests.size,
            applied = effectiveStatuses.count { it == "APPLIED" },
            duplicate = recognized.indices.count {
                recognized[it].status == "DUPLICATE" && effectiveStatuses[it] == "APPLIED"
            },
            conflicted = effectiveStatuses.count { it == "CONFLICT" },
            rejected = effectiveStatuses.count { it == "REJECTED" },
            retryable = retryable,
            skipped = ack.skippedUnknown + ack.skippedTerminal + ack.skippedInvalid,
            superseded = selection.superseded,
            reconciled = reconciled,
            ownerChanged = reconciliation.ownerChanged,
        )
    }

    private suspend fun selectBatch(owner: String, limit: Int): BatchSelection {
        if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
        val allRowsBeforeRecovery = outboxDao.getPending(owner)
        // Capture IN_FLIGHT parents before recovery normalises them to PENDING.
        val unresolvedParents = allRowsBeforeRecovery
            .filter {
                it.entityType in setOf(SyncEntityTypes.WORKOUT, SyncEntityTypes.CUSTOM_EXERCISE) &&
                    it.syncStatus in SyncOutboxStatus.BLOCKING
            }
            .map { it.recordSyncId }
            .toMutableSet()
        outboxDao.recoverInFlight(owner)

        // A later edit must never bypass an unresolved mutation for the same
        // record (FAILED, CONFLICTED or REJECTED_TERMINAL). Skip the whole
        // record until it is explicitly resolved or requeued.
        val allRows = outboxDao.getPending(owner)
        val blockedRecords = allRows
            .filter { it.syncStatus in SyncOutboxStatus.BLOCKING && it.syncStatus != SyncOutboxStatus.PENDING_UPLOAD }
            .map { Triple(it.entityType, it.recordSyncId, it.ownerFirebaseUid) }
            .toSet()
        val invalidIds = mutableSetOf<String>()
        // parent-first rule is applied before the batch is finalised below.
        val candidates = allRows.filter { row ->
            if (row.syncStatus != SyncOutboxStatus.PENDING_UPLOAD) return@filter false
            if (Triple(row.entityType, row.recordSyncId, row.ownerFirebaseUid) in blockedRecords) return@filter false
            if (row.entityType == SyncEntityTypes.WORKOUT_EXERCISE) {
                val dependencies = dependencySyncIds(row) ?: run {
                    invalidIds += row.mutationId
                    unresolvedParents += row.recordSyncId
                    return@filter false
                }
                if (dependencies.any { it in unresolvedParents }) return@filter false
            }
            if (row.entityType == SyncEntityTypes.FAVORITE_EXERCISE) {
                val dependencies = favoriteDependencySyncIds(row) ?: run {
                    invalidIds += row.mutationId
                    unresolvedParents += row.recordSyncId
                    return@filter false
                }
                if (dependencies.any { it in unresolvedParents }) return@filter false
            }
            if (row.entityType == SyncEntityTypes.SCHEDULED_WORKOUT) {
                val dependencies = scheduledWorkoutDependencySyncIds(row) ?: run {
                    invalidIds += row.mutationId
                    unresolvedParents += row.recordSyncId
                    return@filter false
                }
                if (dependencies.any { it in unresolvedParents }) return@filter false
            }
            true
        }
        val mappable = candidates.filter { row ->
            when (val mapped = contractMapper.toMutation(row, owner)) {
                is OutboxMappingResult.Success -> true
                is OutboxMappingResult.Failure -> {
                    invalidIds += row.mutationId
                    unresolvedParents += row.recordSyncId
                    false
                }
            }
        }
        val groups = mappable.groupBy { Triple(it.entityType, it.recordSyncId, it.ownerFirebaseUid) }

        // A chain is compressed only when every row is definitely never sent and
        // the final mutation can stand alone under backend semantics. UPSERT and
        // DELETE can represent the final state on their own; RESTORE cannot,
        // because an earlier unsent DELETE may not exist on the server yet.
        val representatives = groups.values.map { group ->
            val ordered = group.sortedWith(compareBy<SyncOutboxEntity> { it.createdAt }.thenBy { it.orderKey() })
            val final = ordered.last()
            val allClean = ordered.all {
                it.retryCount == 0 && it.lastError == null && !it.everTransmitted
            }
            val finalIsStandaloneSafe = final.operation == "UPSERT" || final.operation == "DELETE"
            val finalMaps = contractMapper.toMutation(final, owner) is OutboxMappingResult.Success

            if (ordered.size > 1 && allClean && finalIsStandaloneSafe && finalMaps) {
                RecordBatch(final, ordered.dropLast(1).map { it.mutationId }.toSet())
            } else {
                RecordBatch(ordered.first(), emptySet())
            }
        }

        val batch = representatives
            .sortedWith(
                compareBy<RecordBatch> { it.representative.typePriority() }
                    .thenBy { it.representative.createdAt }
                    .thenBy { it.representative.orderKey() }
            )
            .take(limit)
        val batchIds = batch.map { it.representative.mutationId }.toSet()
        val supersededIds = batch.flatMap { it.supersedeIds }.toSet()

        if (supersededIds.isNotEmpty()) outboxDao.markSuperseded(owner, supersededIds)
        if (batchIds.isNotEmpty()) outboxDao.markInFlight(owner, batchIds)

        return BatchSelection(batch.map { it.representative }, supersededIds.size, invalidIds)
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

    private suspend fun markMappingFailuresTerminal(
        owner: String,
        ids: Set<String>,
        diagnostic: String,
    ): RowOperationResult = try {
        transactions.run {
            if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPushException()
            outboxDao.markTerminalBatch(
                owner = owner,
                mutationIds = ids,
                status = SyncOutboxStatus.REJECTED_TERMINAL,
                errorCode = "INVALID_LOCAL_MUTATION",
                error = diagnostic,
                now = System.currentTimeMillis(),
                allowedStatuses = setOf(SyncOutboxStatus.PENDING_UPLOAD),
            )
        }.let { RowOperationResult(it, false, PushResult(ownerChanged = false)) }
    } catch (ex: OwnerChangedDuringPushException) {
        RowOperationResult(0, true, PushResult(ownerChanged = true, error = "Authenticated account changed during invalid mutation update"))
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

    /** Null means required dependency information is missing or malformed. */
    private fun dependencySyncIds(row: SyncOutboxEntity): Set<String>? {
        val payload = row.payload ?: return null
        val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        val workout = obj["workoutSyncId"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
        val ref = obj["exerciseRef"] as? JsonObject ?: return null
        val kind = ref["kind"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
        val custom = when (kind) {
            "PUBLIC" -> null
            "CUSTOM" -> ref["syncId"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
            else -> return null
        }
        return setOfNotNull(workout, custom)
    }

    /** Null means required dependency information is missing or malformed. */
    private fun favoriteDependencySyncIds(row: SyncOutboxEntity): Set<String>? {
        val payload = row.payload ?: return emptySet() // public favorite has no dependency
        val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        val ref = obj["exerciseRef"] as? JsonObject ?: return null
        val kind = ref["kind"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
        return when (kind) {
            "PUBLIC" -> emptySet()
            "CUSTOM" -> {
                val syncId = ref["syncId"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
                setOf(syncId)
            }
            else -> return null
        }
    }

    /** Null means required dependency information is missing or malformed. */
    private fun scheduledWorkoutDependencySyncIds(row: SyncOutboxEntity): Set<String>? {
        val payload = row.payload ?: return null
        val obj = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        val workout = obj["workoutSyncId"]?.runCatching { jsonPrimitive.content }?.getOrNull() ?: return null
        return setOf(workout)
    }

    private fun SyncOutboxEntity.typePriority(): Int = when (entityType) {
        SyncEntityTypes.CUSTOM_EXERCISE -> 0
        SyncEntityTypes.WORKOUT -> 1
        SyncEntityTypes.WORKOUT_EXERCISE -> 2
        SyncEntityTypes.FAVORITE_EXERCISE -> 3
        SyncEntityTypes.SCHEDULED_WORKOUT -> 4
        else -> 5
    }

    private fun SyncOutboxEntity.orderKey(): Long = outboxId

    private data class RecordBatch(
        val representative: SyncOutboxEntity,
        val supersedeIds: Set<String>,
    )

    private data class BatchSelection(
        val mutations: List<SyncOutboxEntity>,
        val superseded: Int,
        val invalid: Set<String> = emptySet(),
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