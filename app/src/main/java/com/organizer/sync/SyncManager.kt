package com.organizer.sync

import android.util.Log
import com.organizer.auth.OwnerProvider
import com.organizer.data.local.dao.SyncCursorDao
import com.organizer.data.local.dao.SyncStagedDao
import com.organizer.data.local.db.entities.SyncCursorEntity
import com.organizer.data.local.db.entities.SyncStagedStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val ownerProvider: OwnerProvider,
    private val tokenProvider: SyncTokenProvider,
    private val apiClient: SyncApiClient,
    private val contractMapper: SyncContractMapper,
    private val transactions: SyncTransactionRunner,
    private val cursorDao: SyncCursorDao,
    private val stagedDao: SyncStagedDao,
    private val stagedApplier: SyncStagedApplier,
    private val operationLocks: SyncOperationLocks,
    private val pushCoordinator: SyncPushCoordinator,
) {

    /** Manual one-page pull. Does not loop and does not schedule itself. */
    suspend fun pullOnePage(limit: Int = DEFAULT_PAGE_SIZE): PullResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) {
            return PullResult(error = "Guest mode cannot pull cloud data")
        }

        return operationLocks.forOwner(owner).withLock { pullLocked(owner, limit) }
    }

    private suspend fun pullLocked(owner: String, limit: Int): PullResult {
        // Bind the request to the captured user exactly as push does; never
        // let ambient Firebase state choose the credential for this page.
        val token = tokenProvider.idTokenFor(owner)
        if (token == null) {
            return PullResult(
                ownerChanged = ownerProvider.currentOwner() != owner,
                error = "Authenticated user is no longer available for pull",
            )
        }
        val persistedCursor = cursorDao.get(owner)?.cursor
        return performPull(owner, limit, persistedCursor, token, allowCursorReset = true)
    }

    private suspend fun retryAfterCursorExpiry(owner: String, limit: Int, token: String): PullResult {
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed before cursor reset")
        }
        try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPullException()
                cursorDao.delete(owner)
            }
        } catch (ex: OwnerChangedDuringPullException) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed during cursor reset")
        } catch (ex: Exception) {
            return PullResult(error = "Failed to clear expired cursor")
        }
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed before retry")
        }
        // Exactly one retry, and it never resets a cursor again.
        return performPull(owner, limit, null, token, allowCursorReset = false)
    }

    private suspend fun performPull(
        owner: String,
        limit: Int,
        cursor: String?,
        token: String,
        allowCursorReset: Boolean,
    ): PullResult {
        val apiResult = apiClient.changes(cursor, limit, "Bearer $token")
        val status = when (apiResult) {
            is SyncApiResult.Success -> 200
            is SyncApiResult.HttpError -> apiResult.code
            is SyncApiResult.TransportError, is SyncApiResult.MalformedResponse -> -1
        }
        Log.d(TAG, "pull:http method=GET path=api/sync/changes status=$status pageLimit=$limit")
        val changes = when (apiResult) {
            is SyncApiResult.Success -> contractMapper.toChanges(apiResult.data).getOrElse {
                return PullResult(error = "Malformed pull response")
            }
            is SyncApiResult.HttpError -> {
                if (allowCursorReset && apiResult.error == SyncApiClient.CURSOR_EXPIRED) {
                    return retryAfterCursorExpiry(owner, limit, token)
                }
                return PullResult(error = "HTTP ${apiResult.code}")
            }
            is SyncApiResult.TransportError -> return PullResult(error = "Network error")
            is SyncApiResult.MalformedResponse -> return PullResult(error = "Malformed pull response")
        }

        // The account may have changed while the request was in flight. Never
        // persist a response under a different owner.
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed during pull")
        }

        val now = System.currentTimeMillis()
        try {
            transactions.run {
                if (ownerProvider.currentOwner() != owner) {
                    throw OwnerChangedDuringPullException()
                }
                changes.records.forEach { record ->
                    stagedDao.upsertIfNewer(
                        owner = owner,
                        entityType = record.entityType,
                        syncId = record.syncId,
                        serverVersion = record.serverVersion,
                        serverUpdatedAt = record.serverUpdatedAtMillis,
                        deleted = record.deleted,
                        payload = record.payload?.toString(),
                        processingStatus = SyncStagedStatus.PENDING,
                        createdAt = now,
                    )
                }
            cursorDao.upsert(
                SyncCursorEntity(
                    ownerFirebaseUid = owner,
                    cursor = changes.nextCursor,
                    lastSyncedAt = now,
                    hasMore = changes.hasMore,
                    lastServerTimeMillis = changes.serverTimeMillis,
                    updatedAt = now,
                )
            )
        }
        } catch (ex: OwnerChangedDuringPullException) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed during staging")
        } catch (ex: Exception) {
            return PullResult(error = "Failed to persist pull page")
        }

        // The account may have changed between the staging transaction and
        // application. Never apply A's staged rows while B is signed in.
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed before applying pull")
        }
        val results = linkedMapOf<Long, StagedApplyResult>()
        stagedDao.getByStatus(owner, SyncStagedStatus.PENDING).forEach { staged ->
            results[staged.stagedId] = stagedApplier.process(owner, staged)
        }
        if (results.values.any { it == StagedApplyResult.OWNER_CHANGED }) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed while applying pull")
        }
        // A parent applied later in this same page may unblock a child that was
        // staged first. Retry waiting rows once within this pull.
        if (ownerProvider.currentOwner() != owner) {
            return PullResult(ownerChanged = true, error = "Authenticated account changed before dependency retry")
        }
        if (results.values.any { it == StagedApplyResult.WAITING_DEPENDENCY }) {
            stagedDao.resetToPending(owner, setOf(SyncStagedStatus.WAITING_DEPENDENCY))
            stagedDao.getByStatus(owner, SyncStagedStatus.PENDING).forEach { staged ->
                results[staged.stagedId] = stagedApplier.process(owner, staged)
            }
            if (results.values.any { it == StagedApplyResult.OWNER_CHANGED }) {
                return PullResult(ownerChanged = true, error = "Authenticated account changed during dependency retry")
            }
        }
        val outcomes = results.values
        return PullResult(
            nextCursorPreview = changes.nextCursor.take(8),
            received = changes.records.size,
            staged = changes.records.size,
            applied = outcomes.count { it == StagedApplyResult.APPLIED },
            waitingForDependency = outcomes.count { it == StagedApplyResult.WAITING_DEPENDENCY },
            conflictBlocked = outcomes.count { it == StagedApplyResult.CONFLICT_BLOCKED },
            unsupported = outcomes.count { it == StagedApplyResult.UNSUPPORTED },
            unsupportedSchema = outcomes.count { it == StagedApplyResult.UNSUPPORTED_SCHEMA },
            localPending = outcomes.count { it == StagedApplyResult.LOCAL_PENDING },
            failed = outcomes.count { it == StagedApplyResult.FAILED },
            hasMore = changes.hasMore,
        )
    }

    /**
     * Explicit recovery for staged rows whose application failed. Rows become
     * PENDING and are applied by the next manual pull. UNSUPPORTED_SCHEMA is
     * deliberately not requeued — it stays terminal until a compatible app
     * version explicitly reprocesses it.
     */
    suspend fun retryStagedFailed(): Int {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST) return 0
        return operationLocks.forOwner(owner).withLock {
            try {
                transactions.run {
                    if (ownerProvider.currentOwner() != owner) throw OwnerChangedDuringPullException()
                    stagedDao.resetToPending(
                        owner,
                        setOf(SyncStagedStatus.FAILED, SyncStagedStatus.WAITING_DEPENDENCY),
                    )
                }
            } catch (ex: OwnerChangedDuringPullException) {
                0
            }
        }
    }

    /**
     * One bounded automatic sync cycle: pull all available pages, then push
     * eligible outbox batches. Owner is captured once and rechecked around
     * every network operation. Never runs for guest/public.
     */
    suspend fun syncNow(limit: Int = DEFAULT_PAGE_SIZE): SyncRunResult {
        val owner = ownerProvider.currentOwner()
        if (owner == OwnerProvider.GUEST || owner == OwnerProvider.PUBLIC) {
            Log.d(TAG, "syncNow:skipped guestOrPublic")
            return SyncRunResult(skipped = true)
        }
        Log.d(TAG, "syncNow:start authenticated=true pull=api/sync/changes push=api/sync/push")
        var pulled = 0
        var pushed = 0
        var moreWork = false

        var pages = 0
        while (pages < MAX_PAGES_PER_RUN) {
            if (ownerProvider.currentOwner() != owner) {
                return SyncRunResult(pulled = pulled, pushed = pushed, ownerChanged = true)
            }
            val result = pullOnePage(limit)
            if (result.ownerChanged) {
                return SyncRunResult(pulled = pulled, pushed = pushed, ownerChanged = true)
            }
            if (result.error != null) {
                return SyncRunResult(pulled = pulled, pushed = pushed, retryable = true, error = result.error)
            }
            pulled += result.received
            pages++
            Log.d(TAG, "syncNow:pull page=$pages received=${result.received} hasMore=${result.hasMore} cursor=${result.nextCursorPreview} applied=${result.applied} waiting=${result.waitingForDependency} conflict=${result.conflictBlocked}")
            if (!result.hasMore) break
            if (pages == MAX_PAGES_PER_RUN) moreWork = true
        }

        var batches = 0
        while (batches < MAX_BATCHES_PER_RUN) {
            if (ownerProvider.currentOwner() != owner) {
                return SyncRunResult(pulled = pulled, pushed = pushed, ownerChanged = true)
            }
            val result = pushCoordinator.pushOneBatch()
            if (result.ownerChanged) {
                return SyncRunResult(pulled = pulled, pushed = pushed, ownerChanged = true)
            }
            pushed += result.sent
            batches++
            Log.d(TAG, "syncNow:push batch=$batches selected=${result.selected} sent=${result.sent} applied=${result.applied} duplicate=${result.duplicate} conflict=${result.conflicted} rejected=${result.rejected} retryable=${result.retryable}")
            if (result.retryable > 0) {
                return SyncRunResult(pulled = pulled, pushed = pushed, retryable = true, error = result.error ?: "Push retryable failure")
            }
            if (result.error != null) {
                return SyncRunResult(pulled = pulled, pushed = pushed, retryable = true, error = result.error)
            }
            if (result.selected == 0) break
            if (batches == MAX_BATCHES_PER_RUN && pushCoordinator.hasEligibleWork(owner)) moreWork = true
        }

        Log.d(TAG, "syncNow:end pulled=$pulled pushed=$pushed pages=$pages batches=$batches moreWork=$moreWork")
        return SyncRunResult(pulled = pulled, pushed = pushed, moreWork = moreWork)
    }

    companion object {
        private const val TAG = "SYNC"
        const val DEFAULT_PAGE_SIZE = 100
        const val MAX_PAGES_PER_RUN = 10
        const val MAX_BATCHES_PER_RUN = 10
    }
}

/** Thrown inside the staging transaction to roll back an account-switched pull. */
class OwnerChangedDuringPullException : RuntimeException()